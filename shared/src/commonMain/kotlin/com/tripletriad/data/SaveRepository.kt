package com.tripletriad.data

import com.tripletriad.log.Log
import com.tripletriad.model.GameSave
import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.SaveCodec
import com.tripletriad.storage.SaveCorruptException
import kotlinx.serialization.json.Json
import kotlin.random.Random

data class SaveSlot(
    val key: String,
    val save: GameSave,
) {
    val username: String get() = save.username
    val lastSave: Long get() = save.lastSave
}

sealed interface SaveLoadFailure {
    data object NotFound : SaveLoadFailure

    data class Corrupt(val cause: Throwable) : SaveLoadFailure

    data class Unreadable(val cause: Throwable) : SaveLoadFailure
}

class SaveRepository(
    private val store: DocumentStore,
    private val random: Random = Random.Default,
) {
    // TooGenericExceptionCaught: a `DocumentStore` is implemented per host, so what a failing read
    // throws is whatever that platform's file API throws — `java.io.IOException` on Android and the
    // JVM, something else again on Native. There is no common supertype below `Exception` to name,
    // and this method's contract is that *any* storage failure becomes a `SaveLoadFailure` rather
    // than an escaping throwable. Nothing is swallowed: every branch logs and reports.
    // ReturnCount: three early returns, one per distinct failure, each returning at the point the
    // failure is known. Collapsing them into one exit would mean a nullable accumulator and a
    // second `when` to interpret it.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun load(key: String): Result<GameSave> {
        val blob = try {
            store.read(key)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not read profile '$key'" }
            return Result.failure(SaveLoadException(SaveLoadFailure.Unreadable(failure)))
        } ?: return Result.failure(SaveLoadException(SaveLoadFailure.NotFound))

        return try {
            Result.success(Format.decodeFromString<GameSave>(SaveCodec.decode(blob)).sane())
        } catch (failure: SaveCorruptException) {
            Log.w(TAG, failure) { "profile '$key' is not readable" }
            Result.failure(SaveLoadException(SaveLoadFailure.Corrupt(failure)))
        } catch (failure: Exception) {
            // A well-formed blob whose JSON is not a profile: the codec was happy, the schema was
            // not. Reported as corrupt because that is what it is from the player's point of view.
            Log.w(TAG, failure) { "profile '$key' decoded but is not a valid save" }
            Result.failure(SaveLoadException(SaveLoadFailure.Corrupt(failure)))
        }
    }

    suspend fun save(save: GameSave, at: Long): GameSave {
        val stamped = save.sane().copy(lastSave = at, saveNumber = save.saveNumber + 1)
        store.write(keyFor(stamped), SaveCodec.encode(Format.encodeToString(stamped), random))
        return stamped
    }

    // TooGenericExceptionCaught: see [load]. A store that cannot be enumerated must not stop the
    // load screen appearing, so this degrades to an empty list and logs why.
    @Suppress("TooGenericExceptionCaught")
    suspend fun list(): List<SaveSlot> {
        val keys = try {
            store.keys()
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not list profiles" }
            return emptyList()
        }
        return keys
            .mapNotNull { key -> load(key).getOrNull()?.let { SaveSlot(key, it) } }
            .sortedByDescending { it.lastSave }
    }

    suspend fun keys(): List<String> = store.keys()

    suspend fun delete(key: String) = store.delete(key)

    suspend fun isEmpty(): Boolean = keys().isEmpty()

    suspend fun create(
        username: String = GameSave.DEFAULT_USERNAME,
        createdAt: Long,
        starter: Starter? = null,
    ): GameSave {
        val fresh = GameSave.new(username = username, createdAt = createdAt)
        return save(starter?.let { StarterPack.opened(fresh, it) } ?: fresh, createdAt)
    }

    companion object {
        private const val TAG = "Save"

        const val COLLECTION = "saves"

        fun keyFor(save: GameSave): String {
            val name = save.username
                .lowercase()
                .map { if (it in ALLOWED_KEY_CHARS || it.isLetterOrDigit()) it else '_' }
                .joinToString("")
                .trim()
                .ifEmpty { "profile" }
            return "$name - ${save.creationDate}"
        }

        private const val ALLOWED_KEY_CHARS = " -_"

        private val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

class SaveLoadException(val failure: SaveLoadFailure) : Exception(failure.toString())
