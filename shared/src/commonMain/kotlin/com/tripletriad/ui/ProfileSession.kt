package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.data.SaveRepository
import com.tripletriad.data.SaveSlot
import com.tripletriad.data.Starter
import com.tripletriad.log.Log
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.storage.DocumentStore
import com.tripletriad.time.Clock
import kotlin.random.Random

class ProfileSession internal constructor(
    private val repository: SaveRepository,
    private val clock: Clock,
) {
    var slots: List<SaveSlot> by mutableStateOf(emptyList())
        private set

    var active: GameSave? by mutableStateOf(null)
        private set

    var isBusy: Boolean by mutableStateOf(false)
        private set

    var failure: Throwable? by mutableStateOf(null)
        private set

    var isLoaded: Boolean by mutableStateOf(false)
        private set

    suspend fun refresh() {
        isBusy = true
        slots = repository.list()
        isLoaded = true
        isBusy = false
    }

    fun select(save: GameSave) {
        active = save
        failure = null
    }

    fun clearActive() {
        active = null
    }

    /**
     * Makes a character and opens its box.
     *
     * @param cards the card table the four unauthored cards are drawn from — see
     *   `StarterPack.drawn`. Empty before startup has read it, which deals the authored five and
     *   nothing else; the screen behind this one is only reachable once it has.
     */
    suspend fun create(
        username: String,
        starter: Starter? = null,
        cards: Map<Int, Card> = emptyMap(),
        random: Random = Random.Default,
    ) {
        val name = username.trim().ifEmpty { GameSave.DEFAULT_USERNAME }
        guard("create '$name'") {
            active = repository.create(name, clock.nowMillis(), starter, cards, random)
        }
    }

    suspend fun delete(key: String) {
        val wasActive = slots.firstOrNull { it.key == key }?.save == active
        guard("delete '$key'") {
            repository.delete(key)
            if (wasActive) active = null
        }
    }

    suspend fun persist(save: GameSave) {
        guard("write '${SaveRepository.keyFor(save)}'") {
            active = repository.save(save, clock.nowMillis())
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun guard(what: String, block: suspend () -> Unit) {
        isBusy = true
        failure = try {
            block()
            null
        } catch (error: Exception) {
            Log.w(TAG, error) { "could not $what" }
            error
        }
        slots = repository.list()
        isLoaded = true
        isBusy = false
    }

    private companion object {
        const val TAG = "Profile"
    }
}

@Composable
internal fun rememberProfileSession(documents: DocumentStore, clock: Clock): ProfileSession {
    val repository = remember(documents) { SaveRepository(documents) }
    return remember(repository, clock) { ProfileSession(repository, clock) }
}
