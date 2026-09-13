package com.tripletriad.storage

/**
 * A [DocumentStore] over the page's `localStorage` — the browser's counterpart of the desktop's
 * directory per collection.
 *
 * ### One origin, one flat map
 *
 * `localStorage` has no folders, so each collection is a prefix: a document is stored as
 * `tto/<collection>/<key>`. Neither half may contain a `/` — [sanitizeKey] refuses it in both — so
 * one collection's prefix can never match another's keys, and [keys] needs no escaping to be exact.
 *
 * ### Synchronous, and able to fail
 *
 * Every call has finished when it returns: the browser gives this code one thread and the storage
 * API is blocking. What it can do is throw — storage blocked by the player's settings, some private
 * modes, a quota reached on write. That reaches the caller as a `Throwable`, which every caller
 * already has to handle: the other hosts' disks fail too, and [InMemoryDocumentStore]'s `failure`
 * is how that path is tested.
 *
 * ### Which collections belong here is the host's question
 *
 * Anything written here can be read by any script that runs on the page. For profiles, the server
 * list or the transcript queue that is the exposure the desktop's files have to other programs. For
 * `SessionStore`'s bearer token it is not obviously acceptable, and
 * `tto-server/docs/web-platform.md` leaves that decision to `:webApp`. So this class does not know
 * what it holds; the host chooses what to give it.
 */
class BrowserDocumentStore(collection: String) : DocumentStore {
    private val prefix = "$ROOT/${sanitizeKey(collection)}/"

    override suspend fun read(key: String): String? = storageGet(entry(key))

    override suspend fun write(key: String, text: String) = storageSet(entry(key), text)

    override suspend fun keys(): List<String> {
        // Collected in one pass before filtering, so the list is of what was there at the call.
        val all = (0 until storageLength()).mapNotNull { storageKey(it) }
        return all.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
    }

    override suspend fun delete(key: String) = storageRemove(entry(key))

    private fun entry(key: String): String = prefix + sanitizeKey(key)

    private companion object {
        const val ROOT = "tto"
    }
}

@Suppress("UnusedParameter") // read by name inside `js()`, where detekt cannot see it
private fun storageGet(key: String): String? = js("window.localStorage.getItem(key)")

@Suppress("UnusedParameter") // read by name inside `js()`, where detekt cannot see it
private fun storageSet(key: String, value: String): Unit =
    js("{ window.localStorage.setItem(key, value); }")

@Suppress("UnusedParameter") // read by name inside `js()`, where detekt cannot see it
private fun storageRemove(key: String): Unit = js("{ window.localStorage.removeItem(key); }")

private fun storageLength(): Int = js("window.localStorage.length")

@Suppress("UnusedParameter") // read by name inside `js()`, where detekt cannot see it
private fun storageKey(index: Int): String? = js("window.localStorage.key(index)")
