package com.tripletriad.net

import com.tripletriad.log.Log
import com.tripletriad.protocol.MatchSubmitter
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.SubmissionResult
import com.tripletriad.storage.DocumentStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class QueueDocument(
    val version: Int = TranscriptQueue.VERSION,
    val transcripts: List<MatchTranscript> = emptyList(),
)

class TranscriptQueue(
    private val store: DocumentStore,
    private val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(limit > 0) { "limit must be positive, was $limit" }
    }

    // TooGenericExceptionCaught: as in MatchHistoryRepository.all, the throwable depends on the
    // host's file API and there is no common supertype to name. ReturnCount: one exit per outcome.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun pending(profileKey: String): List<MatchTranscript> {
        val text = try {
            store.read(profileKey)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not read the pending queue for '$profileKey'" }
            return emptyList()
        } ?: return emptyList()

        return try {
            Format.decodeFromString<QueueDocument>(text).transcripts
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "the pending queue for '$profileKey' is unreadable; dropping it" }
            emptyList()
        }
    }

    suspend fun add(profileKey: String, transcript: MatchTranscript): Int {
        val combined = pending(profileKey) + transcript
        val kept = combined.takeLast(limit)
        val dropped = combined.size - kept.size
        if (dropped > 0) {
            Log.i(TAG) { "the pending queue for '$profileKey' is full; dropped $dropped oldest" }
        }
        write(profileKey, kept)
        return dropped
    }

    suspend fun clear(profileKey: String) = store.delete(profileKey)

    suspend fun drain(profileKey: String, submitter: MatchSubmitter): List<SubmissionResult> {
        val queued = pending(profileKey)
        if (queued.isEmpty()) return emptyList()

        val judged = mutableListOf<SubmissionResult>()
        for (transcript in queued) {
            val result = submitter.submit(transcript)
            if (result.holdsTheQueue()) {
                val held = queued.size - judged.size
                Log.i(TAG) { "drain stopped on $result: $held still pending" }
                break
            }
            judged += result
        }

        val remaining = queued.drop(judged.size)
        if (remaining.isEmpty()) clear(profileKey) else write(profileKey, remaining)
        return judged
    }

    private fun SubmissionResult.holdsTheQueue(): Boolean = when (this) {
        is SubmissionResult.Offline, SubmissionResult.Unauthenticated -> true
        is SubmissionResult.Judged,
        is SubmissionResult.UpdateRequired,
        is SubmissionResult.Failed,
        -> false
    }

    private suspend fun write(profileKey: String, transcripts: List<MatchTranscript>) {
        store.write(profileKey, Format.encodeToString(QueueDocument(transcripts = transcripts)))
    }

    companion object {
        private const val TAG = "Pending"

        const val COLLECTION = "pending"

        const val DEFAULT_LIMIT = 200

        const val VERSION = 1

        private val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
