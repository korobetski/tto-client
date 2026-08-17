package com.tripletriad.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tripletriad.log.Log
import com.tripletriad.storage.DocumentStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ServerEntry(
    val id: String,
    val label: String,
    val baseUrl: String,
) {
    companion object {
        fun of(baseUrl: String, label: String? = null): ServerEntry {
            val address = baseUrl.trim().trimEnd('/')
            return ServerEntry(
                id = stableKey(address),
                label = label?.takeIf { it.isNotBlank() } ?: address.substringAfter("://"),
                baseUrl = address,
            )
        }
    }
}

fun serverEntries(spec: String): List<ServerEntry> =
    spec.split(',')
        .mapNotNull { field ->
            val trimmed = field.trim()
            when {
                trimmed.isEmpty() -> null
                // `substringBefore`/`After` on the *first* `=`, so a label may not contain one but
                // an address may — and query strings do.
                '=' in trimmed -> ServerEntry.of(
                    baseUrl = trimmed.substringAfter('=').trim(),
                    label = trimmed.substringBefore('=').trim(),
                )

                else -> ServerEntry.of(trimmed)
            }
        }
        .filter { it.baseUrl.isNotEmpty() }
        .distinctBy { it.id }

@Serializable
private data class SelectionDocument(
    val version: Int = ServerDirectory.VERSION,
    val selectedId: String = "",
)

class ServerDirectory(
    private val store: DocumentStore,
    val entries: List<ServerEntry>,
) {
    init {
        require(entries.isNotEmpty()) { "a directory with no servers is a build with no server" }
    }

    var selected: ServerEntry by mutableStateOf(entries.first())
        private set

    suspend fun restore() {
        val storedId = read()?.selectedId ?: return
        val entry = entries.firstOrNull { it.id == storedId }
        if (entry == null) {
            Log.i(TAG) { "the stored server is no longer configured; using ${selected.label}" }
            return
        }
        selected = entry
    }

    suspend fun select(entry: ServerEntry): Boolean {
        require(entry in entries) { "'${entry.label}' is not a configured server" }
        if (entry.id == selected.id) return false

        selected = entry
        write(entry.id)
        Log.i(TAG) { "now using ${entry.label} (${entry.baseUrl})" }
        return true
    }

    // The store is per host, so a failed read throws whatever that platform's file API throws, and
    // a directory that cannot read its own choice still has a working default.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(): SelectionDocument? = try {
        store.read(KEY)?.let { Format.decodeFromString<SelectionDocument>(it) }
    } catch (failure: Exception) {
        Log.w(TAG, failure) { "could not read the selected server" }
        null
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun write(id: String) {
        try {
            store.write(KEY, Format.encodeToString(SelectionDocument(selectedId = id)))
        } catch (failure: Exception) {
            // Costs the choice on the next launch and nothing else; the player is on the server
            // they picked for as long as the app is running.
            Log.w(TAG, failure) { "could not remember the selected server" }
        }
    }

    companion object {
        private const val TAG = "Servers"

        const val COLLECTION = "servers"

        const val KEY = "selected"

        const val VERSION = 1

        private val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

internal fun stableKey(text: String): String {
    val normalized = text.lowercase()
    val safe = normalized.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    var hash = HASH_SEED
    for (character in normalized) {
        hash = hash * HASH_FACTOR + character.code
    }
    return "${safe.take(KEY_PREFIX_LENGTH)}-${hash.toString(HASH_RADIX)}"
}

private const val HASH_SEED = -2128831035
private const val HASH_FACTOR = 16777619
private const val HASH_RADIX = 36
private const val KEY_PREFIX_LENGTH = 24
