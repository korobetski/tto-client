package com.tripletriad.net

import com.tripletriad.log.Log
import com.tripletriad.protocol.MatchSubmitter
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.MatchVerdict
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.SubmissionResult

interface MatchReporter {

    suspend fun report(profileKey: String, transcript: MatchTranscript)

    suspend fun drain(profileKey: String): PlayerState?

    suspend fun forget(profileKey: String)

    object None : MatchReporter {
        override suspend fun report(profileKey: String, transcript: MatchTranscript) = Unit
        override suspend fun drain(profileKey: String): PlayerState? = null
        override suspend fun forget(profileKey: String) = Unit
    }
}

class QueuedMatchReporter(
    private val queue: TranscriptQueue,
    private val submitter: MatchSubmitter,
) : MatchReporter {

    // TooGenericExceptionCaught: the store is implemented per host, so a failed write throws
    // whatever that platform's file API throws — the same reasoning as `ProfileSession.guard`.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun report(profileKey: String, transcript: MatchTranscript) {
        try {
            queue.add(profileKey, transcript)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not queue a finished match for '$profileKey'" }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun drain(profileKey: String): PlayerState? {
        val results = try {
            queue.drain(profileKey, submitter)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not drain the pending queue for '$profileKey'" }
            return null
        }
        results.forEach { it.log(profileKey) }

        // `lastOrNull` over the credited ones: see `drain` on the interface. A duplicate is skipped
        // even though it does carry a profile — it is the state the server already had, and
        // adopting it would undo a match credited after it in the same drain.
        return results.asSequence()
            .filterIsInstance<SubmissionResult.Judged>()
            .mapNotNull { if (it.receipt.duplicate) null else it.receipt.player }
            .lastOrNull()
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun forget(profileKey: String) {
        try {
            queue.clear(profileKey)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not clear the pending queue for '$profileKey'" }
        }
    }

    private fun SubmissionResult.log(key: String) = when (this) {
        is SubmissionResult.Judged -> when (val v = verdict) {
            is MatchVerdict.Accepted -> if (receipt.duplicate) {
                Log.i(TAG) { "'$key': ${v.blue}-${v.red}, already credited" }
            } else {
                Log.i(TAG) { "'$key': accepted ${v.blue}-${v.red}, ${receipt.reward?.mgp} MGP" }
            }
            // Worth a warning rather than an info: an honest client should not be producing these,
            // so one in a log is either a bug in the transcript or somebody trying it on.
            is MatchVerdict.Rejected ->
                Log.w(TAG) { "'$key': rejected as ${v.reason} — ${v.detail}" }
        }
        is SubmissionResult.UpdateRequired ->
            Log.w(TAG) { "'$key': the server is on $serverVersion and wants a newer client" }
        is SubmissionResult.Failed ->
            Log.w(TAG) { "'$key': the server answered $status — $detail" }
        // Never reached: `drain` stops at the first of these rather than returning it.
        is SubmissionResult.Offline, SubmissionResult.Unauthenticated -> Unit
    }

    private companion object {
        const val TAG = "Pending"
    }
}
