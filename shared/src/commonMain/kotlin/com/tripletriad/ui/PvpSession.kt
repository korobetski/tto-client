package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.log.Log
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.MatchView
import com.tripletriad.model.PlacedCard
import com.tripletriad.model.TurnOrder
import com.tripletriad.net.AccountResult
import com.tripletriad.net.PvpClient
import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.protocol.PvpChallenge
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpMove
import com.tripletriad.protocol.PvpTable
import com.tripletriad.protocol.PvpTableRequest
import kotlinx.coroutines.delay

class PvpSession internal constructor(
    private val client: PvpClient,
    private val tokenOf: suspend () -> String?,
    private val hostName: String = "",
    private val onSettled: suspend () -> Unit = {},
) {
    var match: PvpMatchView? by mutableStateOf(null)
        private set

    var tables: List<PvpTable> by mutableStateOf(emptyList())
        private set

    var claims: List<PvpMatchView> by mutableStateOf(emptyList())
        private set

    var challenges: List<PvpChallenge> by mutableStateOf(emptyList())
        private set

    var isBusy: Boolean by mutableStateOf(false)
        private set

    var failure: AccountResult<*>? by mutableStateOf(null)
        private set

    private var dismissed: String? = null

    /**
     * Whether [dismissed] was still owed a settlement when it was put away. See [clear].
     *
     * Two fields rather than one id, because "do not show me this again" and "do not show me this
     * again *yet*" are different instructions and only the match itself can tell them apart.
     */
    private var dismissedUnsettled: Boolean = false

    var isResumed: Boolean by mutableStateOf(false)
        private set

    val side: CardColor? get() = match?.side

    var deck: Int by mutableStateOf(ANY_DECK)

    var tablesState: ListState by mutableStateOf(ListState.LOADING)
        private set

    var challengesState: ListState by mutableStateOf(ListState.LOADING)
        private set

    var claimsState: ListState by mutableStateOf(ListState.LOADING)
        private set

    val isOver: Boolean get() = match?.status?.let { it != PvpMatchStatus.PLAYING } == true

    val isAwaitingClaim: Boolean get() = match?.status == PvpMatchStatus.AWAITING_CLAIM

    val isSettled: Boolean get() = match?.status?.isSettled == true

    fun view(cards: Map<Int, Card>): MatchView? = match?.toMatchView(cards)?.asBlue()

    suspend fun poll() {
        val token = tokenOf() ?: return
        when (val result = client.currentMatch(token)) {
            is AccountResult.Ok -> {
                val wasPlaying = match?.status == PvpMatchStatus.PLAYING
                val arrived = result.value
                // A dismissal taken while the match still owed a claim lapses the moment it
                // settles — see [clear]. Everything else stays dismissed, including this same
                // match the second time round.
                val lapsed = dismissedUnsettled && arrived?.status?.isSettled == true
                match = arrived?.takeUnless { it.matchId == dismissed && !lapsed }
                // The transition is what is watched, not the state: a match that is over stays
                // over, and refreshing the profile on every poll of a finished board would be a
                // request a second for a number that stopped changing.
                val over = arrived?.status?.let { it != PvpMatchStatus.PLAYING } == true
                if (wasPlaying && over) onSettled()
            }

            else -> Log.i(TAG) { "could not read the match: $result" }
        }
    }

    suspend fun watchLobby() {
        while (match == null) {
            delay(WAIT_MILLIS)
            refreshTables()
            poll()
        }
    }

    suspend fun watch(until: () -> Boolean = { isOver }) {
        while (!until()) {
            delay(WAIT_MILLIS)
            poll()
        }
    }

    val myTable: PvpTable? get() = tables.firstOrNull { it.hostName.equals(hostName, true) }

    suspend fun refreshTables() {
        val token = tokenOf() ?: return
        when (val result = client.tables(token)) {
            is AccountResult.Ok -> {
                tables = result.value
                tablesState = ListState.READY
            }

            else -> {
                // Not published on `failure`: this runs once a second, and a note per poll would
                // bury the screen. The list's own state is where a failed read is shown.
                tablesState = ListState.FAILED
                Log.i(TAG) { "could not read the lobby: $result" }
            }
        }
    }

    suspend fun host(request: PvpTableRequest) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.openTable(token, request.copy(deck = deck))) {
            is AccountResult.Ok -> tables = tables + result.value
            else -> failure = result
        }
    }

    suspend fun cancelTable(tableId: String) = request {
        val token = tokenOf() ?: return@request
        tables = tables.filterNot { it.id == tableId }
        when (val result = client.cancelTable(token, tableId)) {
            is AccountResult.Ok -> Log.i(TAG) { "withdrew table $tableId" }
            else -> {
                failure = result
                Log.i(TAG) { "could not withdraw table $tableId: $result" }
            }
        }
    }

    suspend fun join(tableId: String) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.joinTable(token, tableId, deck)) {
            is AccountResult.Ok -> {
                tables = tables.filterNot { it.id == tableId }
                poll()
            }

            else -> failure = result
        }
    }

    suspend fun refreshChallenges() {
        val token = tokenOf() ?: return
        when (val result = client.challenges(token)) {
            is AccountResult.Ok -> {
                challenges = result.value
                challengesState = ListState.READY
            }

            else -> {
                // Not published on `failure`: this runs once a second, and a note per poll would
                // bury the screen. The list's own state is where a failed read is shown.
                challengesState = ListState.FAILED
                Log.i(TAG) { "could not read the invitations: $result" }
            }
        }
    }

    suspend fun challenge(username: String, terms: PvpTableRequest) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.challenge(token, username, terms.copy(deck = deck))) {
            is AccountResult.Ok -> challenges = challenges + result.value
            else -> failure = result
        }
    }

    suspend fun accept(challengeId: String) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.accept(token, challengeId, deck)) {
            is AccountResult.Ok -> {
                challenges = challenges.filterNot { it.id == challengeId }
                poll()
            }

            else -> failure = result
        }
    }

    suspend fun dropChallenge(challengeId: String) = request {
        val token = tokenOf() ?: return@request
        challenges = challenges.filterNot { it.id == challengeId }
        // The result is deliberately not read. The invitation is gone from this player's screen
        // either way, and an expiry that has already removed it server-side is not something to
        // report — it is the same outcome they asked for.
        val ignored = client.dropChallenge(token, challengeId)
        Log.i(TAG) { "dropped invitation $challengeId: $ignored" }
    }

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

    suspend fun forfeit() = request {
        val token = tokenOf() ?: return@request
        val matchId = match?.matchId ?: return@request
        when (val result = client.forfeit(token, matchId)) {
            is AccountResult.Ok -> match = result.value
            else -> failure = result
        }
    }

    suspend fun refreshClaims() {
        val token = tokenOf() ?: return
        when (val result = client.claims(token)) {
            is AccountResult.Ok -> {
                claims = result.value
                claimsState = ListState.READY
            }

            else -> {
                // Not published on `failure`: this runs once a second, and a note per poll would
                // bury the screen. The list's own state is where a failed read is shown.
                claimsState = ListState.FAILED
                Log.i(TAG) { "could not read the claims: $result" }
            }
        }
    }

    suspend fun claim(matchId: String, cardIds: List<Int>) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.claim(token, matchId, cardIds)) {
            is AccountResult.Ok -> {
                claims = claims.filterNot { it.matchId == matchId }
                if (match?.matchId == matchId) match = result.value
                onSettled()
            }

            else -> failure = result
        }
    }

    suspend fun resume() {
        poll()
        refreshClaims()
        isResumed = true
    }

    /**
     * Puts the board away.
     *
     * [dismissed] is what stops it coming straight back: a settled match stays readable on the
     * server for a couple of minutes on purpose — see `PvpStore.RESULT_MILLIS` — so the next
     * [poll] would answer with the one that was just left and the lobby would bounce back to it.
     *
     * ### Why leaving twice is not the same as leaving once
     *
     * Leaving a **settled** match is "I have read the result", and it must never return. Leaving
     * one still `AWAITING_CLAIM` is not: it is the loser declining to watch the winner choose,
     * which they are entitled to do — the cards leave their collection whatever they do. What they
     * are not entitled to is being kept in the dark about *which* cards, and that was the effect,
     * because the id was struck off for good and no later poll could ever show them the
     * settlement. So an unsettled dismissal lapses the moment the match settles, the board comes
     * back once with `cardsLost` on it, and leaving *that* is the dismissal that sticks.
     */
    fun clear() {
        dismissed = match?.matchId
        dismissedUnsettled = match?.status?.isSettled == false
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

        const val WAIT_MILLIS = 1_000L
    }
}

private fun MatchView.asBlue(): MatchView = if (side == CardColor.BLUE) {
    this
} else {
    copy(
        side = CardColor.BLUE,
        board = board.copy(
            cells = board.cells.map { placed ->
                placed?.let { PlacedCard(card = it.card, owner = it.owner.opposite()) }
            },
        ),
        ownHand = ownHand.map { it.copy(owner = CardColor.BLUE) },
        opponentHand = opponentHand.map { it?.copy(owner = CardColor.RED) },
        order = TurnOrder(order.first.opposite()),
        lastPlay = lastPlay?.let {
            it.copy(
                player = it.player.opposite(),
                card = it.card.copy(owner = it.player.opposite()),
            )
        },
    )
}

/**
 * Whether this match is paid and finished with, as opposed to merely off the board.
 *
 * `AWAITING_CLAIM` is the state the distinction exists for: nine cards are placed, nothing is
 * credited, and a card is about to leave somebody's collection. Reading it as an ending is what
 * `isOver` does, and it is the right reading for "stop accepting moves" and the wrong one for
 * everything to do with settling.
 */
private val PvpMatchStatus.isSettled: Boolean
    get() = this != PvpMatchStatus.PLAYING && this != PvpMatchStatus.AWAITING_CLAIM

@Composable
internal fun rememberPvpSession(
    client: PvpClient,
    hostName: String = "",
    onSettled: suspend () -> Unit = {},
    tokenOf: suspend () -> String?,
): PvpSession = remember(client, hostName) { PvpSession(client, tokenOf, hostName, onSettled) }
