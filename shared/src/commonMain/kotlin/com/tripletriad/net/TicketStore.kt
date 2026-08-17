package com.tripletriad.net

import com.tripletriad.log.Log
import com.tripletriad.storage.DocumentStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class TicketDocument(
    val version: Int = TicketStore.VERSION,
    val seeds: List<Int> = emptyList(),
)

class TicketStore(private val store: DocumentStore) {

    suspend fun load(key: String): List<Int> {
        val raw = runCatching { store.read(document(key)) }.getOrNull() ?: return emptyList()
        val document = runCatching { Format.decodeFromString<TicketDocument>(raw) }.getOrNull()
        if (document == null) {
            Log.w(TAG) { "the stored seeds could not be read; starting from none" }
            return emptyList()
        }
        return document.seeds
    }

    suspend fun save(key: String, seeds: List<Int>) {
        store.write(document(key), Format.encodeToString(TicketDocument(seeds = seeds)))
    }

    private fun document(key: String) = "$key.tickets"

    companion object {
        internal const val VERSION = 1

        const val COLLECTION = "tickets"
        private const val TAG = "Tickets"

        private val Format = Json { ignoreUnknownKeys = true }
    }
}
