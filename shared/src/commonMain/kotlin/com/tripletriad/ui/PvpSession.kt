package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.log.Log
import com.tripletriad.model.Card
import com.tripletriad.model.MatchView
import com.tripletriad.net.AccountResult
import com.tripletriad.net.PvpClient
import com.tripletriad.protocol.PvpChallenge
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpMove
import com.tripletriad.protocol.PvpStake
import kotlinx.coroutines.delay

/**
 * A match against another person, as the screens see it.
 *
 * ### What this holds, and what it deliberately does not
 *
 * It holds a [PvpMatchView] — the server's answer to "what may this player see" — and turns it into
 * a [MatchView] the board can render. It holds **no `MatchState`**, no hands but its own, and no
 * rules engine. That is the whole point: a client that could compute the next position could also
 * compute the opponent's hand, and then "do not look" would be the only thing protecting it.
 *
 * So every transition here is a round trip. That is slower than a local match and it is the price
 * of the guarantee.
 *
 * ### Polling, and why the interval is what it is
 *
 * There is no socket. [poll] asks once; [watch] asks on a loop while a screen is on top of it.
 * [WAIT_MILLIS] is used while waiting for something to happen — an opponent to be found, a turn to
 * come round — and it is a second because a turn arriving a second late is imperceptible in a game
 * where a turn takes thirty. Backing off further would save requests nobody is counting and make
 * the game feel broken.
 *
 * ### Resuming
 *
 * [resume] is called at launch, not on entering a screen. On a phone the system kills applications
 * without asking, and the player did not choose to leave — so the first question this client asks
 * the server is "am I in a match?", and the answer takes it straight back to the board.
 */
class PvpSession internal constructor(
    private val client: PvpClient,
    private val tokenOf: suspend () -> String?,
) {
    /** The match in progress, or null. */
    var match: PvpMatchView? by mutableStateOf(null)
        private set

    /** True while this player is in the quick queue with nobody found yet. */
    var isQueued: Boolean by mutableStateOf(false)
        private set

    /** The invitations standing in either direction. */
    var challenges: List<PvpChallenge> by mutableStateOf(emptyList())
        private set

    /** True while a request is in flight, so a button can disable itself. */
    var isBusy: Boolean by mutableStateOf(false)
        private set

    /**
     * What the last request refused with, or null.
     *
     * A **409 is not published here**. The server refusing a move means this client's view was
     * stale — somebody moved, or the clock ran out — and the answer is to poll, not to show the
     * player an error about a tap that was reasonable when they made it. See [play].
     */
    var failure: AccountResult<*>? by mutableStateOf(null)
        private set

    /** True once [resume] has run, whatever it found. The difference from "no match". */
    var isResumed: Boolean by mutableStateOf(false)
        private set

    /** Whether the match on hand has ended, however it ended. */
    val isOver: Boolean get() = match?.status?.let { it != PvpMatchStatus.PLAYING } == true

    /**
     * The match as the board renders it, resolving card ids through [cards].
     *
     * Null when there is no match, and **also** null when an id resolves to nothing — which is a
     * catalogue disagreeing with the server's, not a frame to draw with a hole in it. See
     * [PvpMatchView.toMatchView].
     */
    fun view(cards: Map<Int, Card>): MatchView? = match?.toMatchView(cards)

    /** Asks the server what is happening, once. Safe to call when nothing is. */
    suspend fun poll() {
        val token = tokenOf() ?: return
        when (val result = client.currentMatch(token)) {
            is AccountResult.Ok -> {
                match = result.value
                // Being in a match ends the queue, whichever of the two put us here.
                if (result.value != null) isQueued = false
            }

            else -> Log.i(TAG) { "could not read the match: $result" }
        }
    }

    /**
     * Polls until [until] says to stop, or the caller's scope is cancelled.
     *
     * Written as a loop the caller launches rather than as something this class starts on its own,
     * because the lifetime that matters is the *screen's*: a session polling in the background of
     * the shop would be a request a second for a match nobody is looking at.
     */
    suspend fun watch(until: () -> Boolean = { isOver }) {
        while (!until()) {
            delay(WAIT_MILLIS)
            poll()
        }
    }

    /** Joins the quick queue, or takes whoever was waiting. */
    suspend fun findMatch() = request {
        val token = tokenOf() ?: return@request
        when (val result = client.queue(token)) {
            is AccountResult.Ok -> {
                isQueued = result.value.waiting
                if (!result.value.waiting) poll()
            }

            else -> failure = result
        }
    }

    /** Leaves the queue. The queue is left locally whatever the server says — see [AccountClient]'s
     * sign-out for the same reasoning: the player pressed a button and should stop waiting. */
    suspend fun leaveQueue() = request {
        val token = tokenOf() ?: return@request
        isQueued = false
        when (val result = client.leaveQueue(token)) {
            is AccountResult.Ok -> Unit
            else -> Log.i(TAG) { "could not leave the queue: $result" }
        }
    }

    /** Reads the invitations. */
    suspend fun refreshChallenges() {
        val token = tokenOf() ?: return
        when (val result = client.challenges(token)) {
            is AccountResult.Ok -> challenges = result.value
            else -> Log.i(TAG) { "could not read the invitations: $result" }
        }
    }

    /** Invites a named player. */
    suspend fun challenge(username: String, stake: PvpStake = PvpStake.None) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.challenge(token, username, stake)) {
            is AccountResult.Ok -> challenges = challenges + result.value
            else -> failure = result
        }
    }

    /** Accepts an invitation, which opens the match. */
    suspend fun accept(challengeId: String) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.accept(token, challengeId)) {
            is AccountResult.Ok -> {
                challenges = challenges.filterNot { it.id == challengeId }
                poll()
            }

            else -> failure = result
        }
    }

    /** Declines an invitation, or withdraws one. Dropped locally either way. */
    suspend fun dropChallenge(challengeId: String) = request {
        val token = tokenOf() ?: return@request
        challenges = challenges.filterNot { it.id == challengeId }
        // The result is deliberately not read. The invitation is gone from this player's screen
        // either way, and an expiry that has already removed it server-side is not something to
        // report — it is the same outcome they asked for.
        val ignored = client.dropChallenge(token, challengeId)
        Log.i(TAG) { "dropped invitation $challengeId: $ignored" }
    }

    /**
     * Places a card.
     *
     * A refusal is answered by **polling**, not by reporting. The server is the referee, so a 409
     * says this client was working from a view that has since moved on — the opponent played, or
     * the clock ran out — and the useful response is to find out what actually happened. Showing
     * "that move is not allowed" would blame the player for a tap that was legal when they made it.
     */
    suspend fun play(move: PvpMove) = request {
        val token = tokenOf() ?: return@request
        val matchId = match?.matchId ?: return@request
        when (val result = client.play(token, matchId, move)) {
            is AccountResult.Ok -> match = result.value
            else -> {
                Log.i(TAG) { "the move was refused, re-reading the match: $result" }
                poll()
            }
        }
    }

    /** Concedes. */
    suspend fun forfeit() = request {
        val token = tokenOf() ?: return@request
        val matchId = match?.matchId ?: return@request
        when (val result = client.forfeit(token, matchId)) {
            is AccountResult.Ok -> match = result.value
            else -> failure = result
        }
    }

    /** Asks whether a match is in progress. Called once at launch — see the class KDoc. */
    suspend fun resume() {
        poll()
        isResumed = true
    }

    /** Forgets a finished match, so the lobby is not showing a board that is over. */
    fun clear() {
        match = null
    }

    private suspend inline fun request(block: () -> Unit) {
        isBusy = true
        failure = null
        try {
            block()
        } finally {
            isBusy = false
        }
    }

    private companion object {
        const val TAG = "PvpSession"

        /**
         * A second between polls.
         *
         * A turn arriving a second late is imperceptible in a game where a turn lasts thirty, and
         * backing off further would save requests nobody is counting at the cost of making the
         * game feel broken.
         */
        const val WAIT_MILLIS = 1_000L
    }
}

/** One session per connection, remembered across recomposition as [rememberAccountSession] is. */
@Composable
internal fun rememberPvpSession(client: PvpClient, tokenOf: suspend () -> String?): PvpSession =
    remember(client) { PvpSession(client, tokenOf) }
