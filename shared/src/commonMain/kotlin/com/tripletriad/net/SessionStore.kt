package com.tripletriad.net

import com.tripletriad.log.Log
import com.tripletriad.storage.DocumentStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SessionDocument(
    val version: Int = SessionStore.VERSION,
    val token: String = "",
    val expiresAt: Long = 0L,
    val username: String = "",
)

class SessionStore(private val store: DocumentStore) {

    // Three returns for three outcomes — nothing stored, stored but spent, usable. Collapsing them
    // costs the log line that says which of the two failures happened.
    @Suppress("ReturnCount")
    suspend fun load(serverId: String, now: Long): StoredSession? {
        val document = read(serverId) ?: return null
        if (document.token.isEmpty() || document.expiresAt <= now) {
            Log.i(TAG) { "the stored session has expired" }
            return null
        }
        return StoredSession(document.token, document.expiresAt, document.username)
    }

    suspend fun lastUsername(serverId: String): String? =
        read(serverId)?.username?.takeIf { it.isNotEmpty() }

    // The store is implemented per host, so a failed read throws whatever that platform's file API
    // throws; and an unreadable session is a session to sign in for, not a crash on launch.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private suspend fun read(serverId: String): SessionDocument? {
        val text = try {
            store.read(serverId)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not read the stored session" }
            return null
        } ?: return null

        return try {
            Format.decodeFromString<SessionDocument>(text)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "the stored session is unreadable; dropping it" }
            null
        }
    }

    suspend fun save(serverId: String, session: StoredSession) {
        store.write(
            serverId,
            Format.encodeToString(
                SessionDocument(
                    token = session.token,
                    expiresAt = session.expiresAt,
                    username = session.username,
                ),
            ),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun clear(serverId: String) {
        try {
            store.delete(serverId)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not clear the stored session" }
        }
    }

    companion object {
        private const val TAG = "Session"

        const val COLLECTION = "session"

        const val VERSION = 1

        private val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

fun accountQueueKey(serverId: String, username: String): String =
    "$serverId.${stableKey(username)}"

data class StoredSession(
    val token: String,
    val expiresAt: Long,
    val username: String,
) {
    fun isValidAt(now: Long): Boolean = token.isNotEmpty() && expiresAt > now

    override fun toString(): String = "StoredSession(username=$username, expiresAt=$expiresAt)"
}
