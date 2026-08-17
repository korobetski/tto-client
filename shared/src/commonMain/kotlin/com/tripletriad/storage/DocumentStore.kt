package com.tripletriad.storage

interface DocumentStore {
    suspend fun read(key: String): String?

    suspend fun write(key: String, text: String)

    suspend fun keys(): List<String>

    suspend fun delete(key: String)
}

fun sanitizeKey(key: String): String {
    require(key.isNotBlank()) { "document key must not be blank" }
    require(key.none { it == '/' || it == '\\' || it == ':' }) {
        "document key must not contain a path separator, was '$key'"
    }
    require(key != "." && key != "..") { "document key must not be '$key'" }
    return key
}

class InMemoryDocumentStore(
    initial: Map<String, String> = emptyMap(),
    private val failure: Throwable? = null,
) : DocumentStore {
    private val documents: MutableMap<String, String> = initial.toMutableMap()

    var writes: Int = 0
        private set

    val stored: Map<String, String> get() = documents.toMap()

    override suspend fun read(key: String): String? {
        failure?.let { throw it }
        return documents[sanitizeKey(key)]
    }

    override suspend fun write(key: String, text: String) {
        failure?.let { throw it }
        documents[sanitizeKey(key)] = text
        writes++
    }

    override suspend fun keys(): List<String> {
        failure?.let { throw it }
        return documents.keys.toList()
    }

    override suspend fun delete(key: String) {
        failure?.let { throw it }
        documents.remove(sanitizeKey(key))
    }
}
