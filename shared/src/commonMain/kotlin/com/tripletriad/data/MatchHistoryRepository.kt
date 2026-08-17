package com.tripletriad.data

import com.tripletriad.log.Log
import com.tripletriad.model.MatchRecord
import com.tripletriad.model.MatchResult
import com.tripletriad.model.OpponentKind
import com.tripletriad.storage.DocumentStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class MatchTally(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
) {
    val played: Int get() = wins + losses + draws
    val winRate: Float get() = if (played == 0) 0f else wins.toFloat() / played

    companion object {
        fun of(records: List<MatchRecord>): MatchTally = MatchTally(
            wins = records.count { it.result == MatchResult.WIN },
            losses = records.count { it.result == MatchResult.LOSE },
            draws = records.count { it.result == MatchResult.DRAW },
        )
    }
}

@Serializable
private data class HistoryDocument(
    val version: Int = MatchHistoryRepository.VERSION,
    val matches: List<MatchRecord> = emptyList(),
)

class MatchHistoryRepository(
    private val store: DocumentStore,
    private val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(limit > 0) { "limit must be positive, was $limit" }
    }

    // TooGenericExceptionCaught: as in [SaveRepository.load], the throwable depends on the host's
    // file API and there is no common supertype to name. ReturnCount: three exits, one per outcome
    // (unreadable store, no document, parsed) — all three yield a list and all three log.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun all(profileKey: String): List<MatchRecord> {
        val text = try {
            store.read(profileKey)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not read match history for '$profileKey'" }
            return emptyList()
        } ?: return emptyList()

        return try {
            Format.decodeFromString<HistoryDocument>(
                text,
            ).matches.sortedByDescending { it.timestamp }
        } catch (failure: Exception) {
            // Deliberately not rethrown and not repaired in place: a damaged history reads as
            // empty, and the next append rewrites the document from scratch. Losing history is
            // survivable in a way that losing a profile is not, which is why this differs from
            // SaveRepository.
            Log.w(
                TAG,
                failure,
            ) { "match history for '$profileKey' is unreadable; treating as empty" }
            emptyList()
        }
    }

    suspend fun append(profileKey: String, record: MatchRecord): Int {
        val existing = all(profileKey).filterNot { it.id == record.id }
        val combined = (listOf(record) + existing).sortedByDescending { it.timestamp }
        val kept = combined.take(limit)
        val dropped = combined.size - kept.size
        if (dropped > 0) {
            Log.i(TAG) { "match history for '$profileKey' is full; dropped $dropped oldest" }
        }
        store.write(profileKey, Format.encodeToString(HistoryDocument(matches = kept)))
        return dropped
    }

    suspend fun recent(profileKey: String, count: Int = 10): List<MatchRecord> =
        all(profileKey).take(count)

    suspend fun againstNpc(profileKey: String, npcIconId: String): List<MatchRecord> =
        all(profileKey).filter {
            it.opponentKind == OpponentKind.NPC && it.opponentName == npcIconId
        }

    suspend fun withRule(profileKey: String, ruleKey: String): List<MatchRecord> =
        all(profileKey).filter { ruleKey in it.rules.activeRuleKeys() }

    suspend fun tally(profileKey: String, kind: OpponentKind? = null): MatchTally =
        MatchTally.of(all(profileKey).filter { kind == null || it.opponentKind == kind })

    suspend fun clear(profileKey: String) = store.delete(profileKey)

    companion object {
        private const val TAG = "History"

        const val COLLECTION = "history"

        const val DEFAULT_LIMIT = 2_000

        const val VERSION = 1

        private val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
