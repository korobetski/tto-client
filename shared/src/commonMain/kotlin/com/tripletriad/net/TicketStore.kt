package com.tripletriad.net

import com.tripletriad.log.Log
import com.tripletriad.storage.DocumentStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The stored shape. Versioned, like every other document this app writes. */
@Serializable
private data class TicketDocument(
    val version: Int = TicketStore.VERSION,
    val seeds: List<Int> = emptyList(),
)

/**
 * Seeds the server issued, kept across launches so a match can be played with no network.
 *
 * ### Why this is on disk at all
 *
 * Because without it, "offline" would mean "offline since the last time the app was open". The
 * server hands out a stock precisely so that a player on a plane can keep playing, and a stock held
 * only in memory is gone the moment the app is killed — which on a phone is whenever the system
 * feels like it. A player who launches the game with no network and finds they cannot start a match
 * has lost the thing the whole ticket design was protecting.
 *
 * ### It is not a secret, and does not need to be
 *
 * Unlike the token next door, a seed is worth nothing to anyone who reads it. It is a number this
 * server will accept **once**, from **this account**, for **one** match — and the match still has
 * to be really played and replayed before anything is paid. Somebody who steals the file gets a
 * list of numbers they cannot spend.
 *
 * ### Keyed per account, not per server
 *
 * A ticket belongs to an account: the server checks `(account_id, seed)`. Two players sharing a
 * device have separate stocks for the same reason they have separate queues — see
 * [accountQueueKey], whose key this reuses so the two cannot disagree about whose document it is.
 */
class TicketStore(private val store: DocumentStore) {

    /** The seeds held for [key], oldest first, or empty when there are none. */
    suspend fun load(key: String): List<Int> {
        val raw = runCatching { store.read(document(key)) }.getOrNull() ?: return emptyList()
        val document = runCatching { Format.decodeFromString<TicketDocument>(raw) }.getOrNull()
        if (document == null) {
            Log.w(TAG) { "the stored seeds could not be read; starting from none" }
            return emptyList()
        }
        return document.seeds
    }

    /** Replaces what is held for [key]. */
    suspend fun save(key: String, seeds: List<Int>) {
        store.write(document(key), Format.encodeToString(TicketDocument(seeds = seeds)))
    }

    private fun document(key: String) = "$key.tickets"

    companion object {
        internal const val VERSION = 1

        /** The subdirectory a host store uses, as `TranscriptQueue.COLLECTION` is for the queue. */
        const val COLLECTION = "tickets"
        private const val TAG = "Tickets"

        private val Format = Json { ignoreUnknownKeys = true }
    }
}
