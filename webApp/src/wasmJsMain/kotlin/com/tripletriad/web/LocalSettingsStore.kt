package com.tripletriad.web

import com.tripletriad.settings.SettingsStore
import com.tripletriad.storage.DocumentStore

/**
 * The player's settings as one document in a [DocumentStore].
 *
 * The desktop keeps them in a single file and has no use for this. The browser has nothing but
 * `localStorage`, which `BrowserDocumentStore` already partitions and tests, so the settings reuse
 * it rather than adding a second set of `js()` calls for the same map.
 */
class LocalSettingsStore(private val store: DocumentStore) : SettingsStore {
    override suspend fun read(): String? = store.read(KEY)

    override suspend fun write(text: String) = store.write(KEY, text)

    private companion object {
        const val KEY = "user"
    }
}
