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
import com.tripletriad.protocol.PvpChallenge
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpMove
import com.tripletriad.protocol.PvpTable
import com.tripletriad.protocol.PvpTableRequest
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
    /** This player's name, so the lobby can tell their own table from everybody else's. */
    private val hostName: String = "",
    /**
     * Called once a match has moved money or cards.
     *
     * The server owns the profile in a refereed match, so a settlement happens somewhere this
     * client cannot see and its local `GameSave` is stale the moment it lands. Nothing used to
     * re-read it at all: `PvpOutcome` carried the payout and the client ignored every field of it.
     */
    private val onSettled: suspend () -> Unit = {},
) {
    /** The match in progress, or null. */
    var match: PvpMatchView? by mutableStateOf(null)
        private set

    /** The tables on offer, this player's own included. */
    var tables: List<PvpTable> by mutableStateOf(emptyList())
        private set

    /**
     * Matches won and not yet collected.
     *
     * Kept apart from [match] because they answer different questions, and the difference costs a
     * card: [match] is the newest one, so a prize left uncollected disappears behind the next game
     * and is gone when the server's deadline picks for you.
     */
    var claims: List<PvpMatchView> by mutableStateOf(emptyList())
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

    /**
     * The colour the **server** dealt this player, or null with no match.
     *
     * The one place the true side is readable, and it exists because [view] deliberately hides it:
     * everything [PvpOutcome] reports — `forfeitedBy`, `blue`, `red` — is in server colours, and
     * comparing any of them against a mirrored view's `side` would be wrong exactly half the time.
     * A screen wanting "was that me?" asks here; a screen wanting a score reads [MatchView.score],
     * which is already told from this player's side.
     */
    val side: CardColor? get() = match?.side

    /** Whether the match on hand has ended, however it ended. */
    val isOver: Boolean get() = match?.status?.let { it != PvpMatchStatus.PLAYING } == true

    /**
     * The match as the board renders it, resolving card ids through [cards].
     *
     * Null when there is no match, and **also** null when an id resolves to nothing — which is a
     * catalogue disagreeing with the server's, not a frame to draw with a hole in it. See
     * [PvpMatchView.toMatchView].
     *
     * ### The view is always blue
     *
     * The server deals a side, so half of all PvP players are red. Drawing that literally would
     * mean two screens that do not resemble each other and a player whose own cards are the colour
     * the rest of the game has taught them is the opponent's — `MatchScreen` hard-codes blue as
     * *you* everywhere, from `playable()` to the win line. So the view is mirrored here: a red
     * player is shown a blue match against a red opponent, and the board, the hands and the score
     * all agree.
     *
     * Here rather than anywhere else, for two reasons. Not in `PvpMatchView.toMatchView`, which is
     * the declared inverse of `PvpMatchView.of` and is asserted to agree with it — a projection
     * that mirrored would no longer be one. Not in the screen, where `OwnRow`, `OpponentRow` and
     * `BoardGrid` would each have to remember to, and one of them eventually would not.
     */
    fun view(cards: Map<Int, Card>): MatchView? = match?.toMatchView(cards)?.asBlue()

    /** Asks the server what is happening, once. Safe to call when nothing is. */
    suspend fun poll() {
        val token = tokenOf() ?: return
        when (val result = client.currentMatch(token)) {
            is AccountResult.Ok -> {
                val wasPlaying = match?.status == PvpMatchStatus.PLAYING
                match = result.value
                // The transition is what is watched, not the state: a match that is over stays
                // over, and refreshing the profile on every poll of a finished board would be a
                // request a second for a number that stopped changing.
                val over = result.value?.status?.let { it != PvpMatchStatus.PLAYING } == true
                if (wasPlaying && over) onSettled()
            }

            else -> Log.i(TAG) { "could not read the match: $result" }
        }
    }

    /**
     * Polls the lobby — tables, invitations and prizes — until the caller's scope is cancelled.
     *
     * A separate loop from [watch] because it asks different questions of a player who is *not* in
     * a match. It stops the moment one exists, since the board's own loop takes over from there.
     */
    suspend fun watchLobby() {
        while (match == null) {
            delay(WAIT_MILLIS)
            refreshTables()
            poll()
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

    /** The table this player is hosting, or null. */
    val myTable: PvpTable? get() = tables.firstOrNull { it.hostName.equals(hostName, true) }

    /** Reads the lobby. */
    suspend fun refreshTables() {
        val token = tokenOf() ?: return
        when (val result = client.tables(token)) {
            is AccountResult.Ok -> tables = result.value
            else -> Log.i(TAG) { "could not read the lobby: $result" }
        }
    }

    /** Opens a table on the terms this player is offering. */
    suspend fun host(request: PvpTableRequest) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.openTable(token, request)) {
            is AccountResult.Ok -> tables = tables + result.value
            else -> failure = result
        }
    }

    /**
     * Withdraws it. Withdrawn locally whatever the server says.
     *
     * The same reasoning sign-out uses, and the same reasoning the old "leave the queue" used: the
     * player pressed a button, and leaving them advertising a match until the network comes back is
     * a strange answer to it.
     */
    suspend fun cancelTable(tableId: String) = request {
        val token = tokenOf() ?: return@request
        tables = tables.filterNot { it.id == tableId }
        val ignored = client.cancelTable(token, tableId)
        Log.i(TAG) { "withdrew table $tableId: $ignored" }
    }

    /** Joins one, which opens the match. */
    suspend fun join(tableId: String) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.joinTable(token, tableId)) {
            is AccountResult.Ok -> {
                tables = tables.filterNot { it.id == tableId }
                poll()
            }

            else -> failure = result
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

    /** Invites a named player, on stated terms. */
    suspend fun challenge(username: String, terms: PvpTableRequest) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.challenge(token, username, terms)) {
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

    /** Reads what this player has won and not collected. */
    suspend fun refreshClaims() {
        val token = tokenOf() ?: return
        when (val result = client.claims(token)) {
            is AccountResult.Ok -> claims = result.value
            else -> Log.i(TAG) { "could not read the claims: $result" }
        }
    }

    /**
     * Names the cards taken, and refreshes the profile once they have moved.
     *
     * [onSettled] fires here as well as when a match ends, because under One and Diff **this** is
     * the moment the cards change hands — a profile refreshed at the end of the board would show a
     * collection that has not been paid yet.
     */
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

    /**
     * Asks whether a match is in progress, and whether anything is owed. Called once at launch —
     * see the class KDoc.
     */
    suspend fun resume() {
        poll()
        refreshClaims()
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

/**
 * This view with the sides swapped, so the reader is blue. A blue view is returned unchanged.
 *
 * Every colour that is read is flipped together: the side, both hands, each placed card's owner,
 * and the turn order. Flipping a subset is the bug this exists to fix — the board carried the
 * server's colours while the hands carried the catalogue's default of blue.
 *
 * **Hands are assigned, not flipped.** A hand card comes straight out of the catalogue, where
 * `Card.owner` is a default rather than a fact, so there is nothing there to invert; the colour is
 * stated outright. A *placed* card is the opposite case — `PlacedCard.owner` is the authority, and
 * the `Card` inside it is left alone because `BoardCard` overrides it with the placed owner before
 * anything is drawn.
 *
 * What follows from flipping the order as well is that [MatchView.isMyTurn] and [MatchView.score]
 * stay right without knowing this happened: both are derived from `side` against `order`, and
 * `score` counts the unplayed hands by side. `playableHandIndices` is not touched, because it
 * indexes a hand, and a hand does not change length when it changes colour.
 */
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
    )
}

/** One session per connection, remembered across recomposition as [rememberAccountSession] is. */
@Composable
internal fun rememberPvpSession(
    client: PvpClient,
    hostName: String = "",
    onSettled: suspend () -> Unit = {},
    tokenOf: suspend () -> String?,
): PvpSession = remember(client, hostName) { PvpSession(client, tokenOf, hostName, onSettled) }
