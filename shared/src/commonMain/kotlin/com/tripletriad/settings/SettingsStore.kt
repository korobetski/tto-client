package com.tripletriad.settings

interface SettingsStore {
    suspend fun read(): String?

    suspend fun write(text: String)
}

class InMemorySettingsStore(
    initial: String? = null,
    private val failure: Throwable? = null,
) : SettingsStore {
    private var text: String? = initial

    var writes: Int = 0
        private set

    val stored: String? get() = text

    override suspend fun read(): String? = failure?.let { throw it } ?: text

    override suspend fun write(text: String) {
        failure?.let { throw it }
        this.text = text
        writes++
    }
}
