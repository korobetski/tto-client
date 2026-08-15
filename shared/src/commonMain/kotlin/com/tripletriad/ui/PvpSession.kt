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

    /**
     * The finished match this player has already read and dismissed, if any.
     *
     * Cleared by nothing: a new match has a new id, so it passes the check on its own, and the old
     * id costs one string. See [clear].
     */
    private var dismissed: String? = null

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

    /**
     * Which of this player's decks they are bringing, as a slot in `GameSave.decks`.
     *
     * Held on the session rather than passed at each call site because it is a property of the
     * *player*, not of any one match: the same five cards are brought to a table they host, a table
     * they join and an invitation they accept, and asking three times would be asking the same
     * question three times. It is also why it is not part of `PvpTableRequest`'s terms on the way
     * out — see that class. [ANY_DECK] leaves the choice to the server, which is where it was until
     * a player could make it.
     */
    var deck: Int by mutableStateOf(ANY_DECK)

    /**
     * Where each of the three lists has got to — see [ListState].
     *
     * ### Why an empty list is not an answer on its own
     *
     * A lobby with no tables and a lobby nobody has asked look identical from the outside, and
     * rendering them the same way tells the player something false: "nobody is here" is an
     * *answer*, and somebody who reads it leaves — half a second before the first poll comes back
     * with four tables in it. `ProfileScreen` has always drawn this distinction for the local
     * profile list; this is it for the three that come over the network.
     *
     * ### And why a failure is a third thing
     *
     * A spinner that never stops is its own kind of lie. Without [ListState.FAILED] an unreachable
     * server leaves the lobby loading for ever, which is worse than the empty list it used to show
     * wrongly — that at least ended. The player is told the server could not be reached and given
     * something to press.
     *
     * Three states, three lists, because they are three requests arriving at three different times
     * and one flag would have to be wrong about two of them.
     */
    var tablesState: ListState by mutableStateOf(ListState.LOADING)
        private set

    var challengesState: ListState by mutableStateOf(ListState.LOADING)
        private set

    var claimsState: ListState by mutableStateOf(ListState.LOADING)
        private set

    /** Whether the match on hand has ended, however it ended. The board is dead from here on. */
    val isOver: Boolean get() = match?.status?.let { it != PvpMatchStatus.PLAYING } == true

    /**
     * Whether the winner still owes a choice — see [PvpMatchStatus.AWAITING_CLAIM].
     *
     * True on **both** sides while it lasts, which is what makes it useful: the winner is the one
     * with [PvpOutcome.picksOwed] set, and the loser is the one who can now be told that a card is
     * being taken out of their hand rather than finding out from their collection afterwards.
     */
    val isAwaitingClaim: Boolean get() = match?.status == PvpMatchStatus.AWAITING_CLAIM

    /**
     * Whether the match is finished **and paid**, which is not the same as [isOver].
     *
     * The distinction is what the board polls on. [isOver] is true the moment the ninth card lands,
     * including in the window where the winner has yet to name their prize — so a board that stops
     * at [isOver] stops asking precisely when the one remaining question is about to be answered.
     * The loser saw nothing of the choice made out of their own hand, and the winner's own claim,
     * made on another screen, was never reflected on the board they made it from.
     */
    val isSettled: Boolean
        get() = match?.status?.let {
            it != PvpMatchStatus.PLAYING && it != PvpMatchStatus.AWAITING_CLAIM
        } == true

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
                val arrived = result.value
                match = arrived?.takeUnless { it.matchId == dismissed }
                // The transition is what is watched, not the state: a match that is over stays
                // over, and refreshing the profile on every poll of a finished board would be a
                // request a second for a number that stopped changing.
                val over = arrived?.status?.let { it != PvpMatchStatus.PLAYING } == true
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

    /** Opens a table on the terms this player is offering. */
    suspend fun host(request: PvpTableRequest) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.openTable(token, request.copy(deck = deck))) {
            is AccountResult.Ok -> tables = tables + result.value
            else -> failure = result
        }
    }

    /**
     * Withdraws it. Withdrawn locally whatever the server says, but **not silently**.
     *
     * The local removal is the same reasoning sign-out uses, and the same reasoning the old "leave
     * the queue" used: the player pressed a button, and leaving them advertising a match until the
     * network comes back is a strange answer to it.
     *
     * ### Why the refusal is published, unlike [dropChallenge]'s
     *
     * The result used to be logged and dropped, on the reading that a table gone from this screen
     * is the outcome the player asked for. That reading holds for an invitation, which nothing puts
     * back. It does not hold here: this screen polls, so a table the server still has is **returned
     * by the very next `refreshTables`, one second later** — the row reappears, the Host button
     * turns back into Cancel, and a player who wanted to open a different table is left pressing a
     * button that visibly does nothing and is told nothing about why. The lobby already renders
     * [failure] as a note; this is the case that most needed one.
     */
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

    /** Joins one, which opens the match. */
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

    /** Reads the invitations. */
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

    /** Invites a named player, on stated terms. */
    suspend fun challenge(username: String, terms: PvpTableRequest) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.challenge(token, username, terms.copy(deck = deck))) {
            is AccountResult.Ok -> challenges = challenges + result.value
            else -> failure = result
        }
    }

    /** Accepts an invitation, which opens the match. */
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

    /**
     * Forgets a finished match, so the lobby is not showing a board that is over.
     *
     * The id is **remembered**, and that is the whole of it: the server keeps a settled match
     * readable for a couple of minutes so both players can see how it ended — see
     * `PvpStore.recentMatchFor` — so without this the next poll would hand it straight back, the
     * lobby's "a match exists, go to the board" effect would fire, and the player would be bounced
     * into the result screen they just dismissed, once a second, until the window closed.
     */
    fun clear() {
        dismissed = match?.matchId
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
 *
 * **The last play is flipped even though nothing currently reads its colour.** `MatchBanner` wants
 * its captures and the type of the card, not who played it. Leaving one un-mirrored field in an
 * otherwise mirrored view would be a trap for the next reader rather than an optimisation — the
 * rule this function states is that every colour in here is this player's, and a field that quietly
 * is not would be found the hard way.
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
        lastPlay = lastPlay?.let {
            it.copy(
                player = it.player.opposite(),
                card = it.card.copy(owner = it.player.opposite()),
            )
        },
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
