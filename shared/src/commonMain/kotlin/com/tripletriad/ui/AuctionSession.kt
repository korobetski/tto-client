package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.log.Log
import com.tripletriad.net.AccountResult
import com.tripletriad.net.AuctionClient
import com.tripletriad.protocol.AuctionDuration
import com.tripletriad.protocol.AuctionLot
import com.tripletriad.protocol.AuctionLotRequest
import com.tripletriad.protocol.AuctionOutcome
import com.tripletriad.protocol.AuctionPage
import com.tripletriad.protocol.AuctionRefusal
import com.tripletriad.protocol.AuctionStatus
import com.tripletriad.protocol.BidRequest
import com.tripletriad.protocol.ListCardRequest
import com.tripletriad.protocol.PlayerState
import com.tripletriad.time.Clock
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The auction house, as one screen's worth of state.
 *
 * ### The clock is the server's, not this device's
 *
 * Every lot is a deadline, and a deadline is the one thing a client must not compute from its own
 * clock: a phone half a minute fast shows a lot as closed while it is still taking bids, and one
 * half a minute slow invites a bid that will be refused. So [AuctionPage.now] comes back with every
 * read, [skew] is the difference from this device's clock at the moment it arrived, and [remaining]
 * is the only place a countdown is computed. It is a correction rather than a synchronisation —
 * the drift between two reads is a second, which is the tick the countdown redraws at anyway.
 *
 * ### A refusal is not a failure
 *
 * [failure] is a dead server, a stale build, a throttle or a shut gate: the request never reached
 * the house. [refusal] is the house's answer — outbid, too low, not yours — and it always arrives
 * with a profile and a lot beside it, so the screen redraws to the truth in the same frame it
 * reports the refusal. They are two fields because they are read by two different parts of the
 * screen and only one of them is worth a retry.
 *
 * ### Why the operation id is minted per press and not per lot
 *
 * It exists to make a *double tap* one action, and the guard against that is [isBusy] holding the
 * button down until the answer lands. The id is what makes the retry underneath safe: it survives
 * the round trip, so a request the network duplicated settles once. A player who deliberately bids
 * twice has made two decisions and gets two bids, which is the behaviour they asked for.
 */
class AuctionSession internal constructor(
    private val client: AuctionClient,
    private val tokenOf: suspend () -> String?,
    private val clock: Clock,
    private val onProfile: suspend (PlayerState) -> Unit = {},
) {
    var board: List<AuctionLot> by mutableStateOf(emptyList())
        private set

    var boardState: ListState by mutableStateOf(ListState.LOADING)
        private set

    var mine: List<AuctionLot> by mutableStateOf(emptyList())
        private set

    var mineState: ListState by mutableStateOf(ListState.LOADING)
        private set

    /**
     * The lot the pane on the right is showing, or null on an empty board.
     *
     * Held as an id rather than as a lot, because the lot itself is replaced by every refresh — a
     * bid lands, the price changes — and a captured copy would go stale while it was on screen.
     */
    var selectedId: String? by mutableStateOf(null)
        private set

    var isBusy: Boolean by mutableStateOf(false)
        private set

    var failure: AccountResult<*>? by mutableStateOf(null)
        private set

    var refusal: AuctionRefusal? by mutableStateOf(null)
        private set

    /** This device's clock minus the server's, as of the last read. See the class KDoc. */
    private var skew: Long = 0L

    /** Both lists at once, because a lot can be in either and the pane reads whichever holds it. */
    val selected: AuctionLot?
        get() = selectedId?.let { id -> (board + mine).firstOrNull { it.id == id } }

    /** The lots waiting on a decision from this player. The reason [mine] has a badge. */
    val awaitingMe: List<AuctionLot>
        get() = mine.filter { it.yours && it.status == AuctionStatus.AWAITING_SELLER }

    /**
     * Milliseconds left on [lot], corrected for [skew], never negative.
     *
     * @param deviceNow this device's clock, passed in rather than read here so the value comes from
     *   `rememberNow` and the countdown recomposes once a second instead of never.
     */
    fun remaining(lot: AuctionLot, deviceNow: Long): Long =
        (lot.endsAt - (deviceNow - skew)).coerceAtLeast(0L)

    fun select(lotId: String?) {
        selectedId = lotId
        // The pane changing is the player asking a new question; the answer to the old one is not
        // about the lot they are now looking at.
        refusal = null
    }

    // ------------------------------------------------------------------ reads

    suspend fun refreshBoard(cardId: Int? = null) {
        val token = tokenOf() ?: return
        when (val result = client.browse(token, cardId)) {
            is AccountResult.Ok -> {
                adopt(result.value)
                board = result.value.lots
                boardState = ListState.READY
            }

            else -> {
                // Not published on `failure`: this polls, and a note per poll would bury the
                // screen. The list's own state is where a failed read is shown — the same
                // reading `PvpSession` makes of its lobby.
                boardState = ListState.FAILED
                Log.i(TAG) { "could not read the board: $result" }
            }
        }
    }

    suspend fun refreshMine() {
        val token = tokenOf() ?: return
        when (val result = client.mine(token)) {
            is AccountResult.Ok -> {
                adopt(result.value)
                mine = result.value.lots
                mineState = ListState.READY
            }

            else -> {
                mineState = ListState.FAILED
                Log.i(TAG) { "could not read my lots: $result" }
            }
        }
    }

    /** Both lists, for the screen's first frame and for its poll. */
    suspend fun refresh(cardId: Int? = null) {
        refreshBoard(cardId)
        refreshMine()
    }

    /**
     * Re-reads until the screen goes away.
     *
     * Once every [WAIT_MILLIS] rather than once a second: a lot moves when somebody bids on it,
     * which is minutes apart for all but the last two, and the countdown is drawn from a clock
     * this already corrected rather than from the poll. Polling faster would buy a fresher price
     * at the cost of the bucket the bid itself has to fit through.
     */
    suspend fun watch(cardId: Int? = null) {
        while (true) {
            delay(WAIT_MILLIS)
            refresh(cardId)
        }
    }

    // ------------------------------------------------------------------ writes

    suspend fun listCard(
        cardId: Int,
        startPrice: Int,
        reservePrice: Int,
        duration: AuctionDuration,
    ) = request { token ->
        client.list(
            token,
            ListCardRequest(cardId, startPrice, reservePrice, duration, newOperationId()),
        )
    }

    suspend fun bid(lotId: String, amount: Int) = request { token ->
        client.bid(token, BidRequest(lotId, amount, newOperationId()))
    }

    suspend fun withdraw(lotId: String) = request { token ->
        client.withdraw(token, AuctionLotRequest(lotId, newOperationId()))
    }

    suspend fun decide(lotId: String, accept: Boolean) = request { token ->
        val lot = AuctionLotRequest(lotId, newOperationId())
        if (accept) client.accept(token, lot) else client.decline(token, lot)
    }

    /**
     * The shape every write here has: busy while it runs, and three things read out of the answer.
     *
     * The lot in the outcome is folded back into whichever list held it rather than triggering a
     * refresh, so the price the player just moved is on screen in the same frame as the button
     * coming back — a re-read would put a poll's worth of latency between the two.
     */
    private suspend inline fun request(block: (String) -> AccountResult<AuctionOutcome>) {
        isBusy = true
        failure = null
        refusal = null
        try {
            val token = tokenOf() ?: return
            when (val result = block(token)) {
                is AccountResult.Ok -> {
                    val outcome = result.value
                    refusal = outcome.refusal
                    outcome.lot?.let(::replace)
                    onProfile(outcome.player)
                }

                else -> {
                    failure = result
                    Log.w(TAG) { "the house could not be reached: $result" }
                }
            }
        } finally {
            isBusy = false
        }
    }

    /**
     * Puts [lot] back where it was found, and follows it.
     *
     * A lot that finished leaves the board and stays in [mine], which is what makes "my lot sold
     * while I was looking at it" readable rather than a row that vanishes. A lot this player has
     * just become the top bidder on joins [mine] for the same reason: it is theirs to watch now.
     */
    private fun replace(lot: AuctionLot) {
        board = if (lot.status.isOpen) {
            if (board.any { it.id == lot.id }) {
                board.map { if (it.id == lot.id) lot else it }
            } else {
                board + lot
            }
        } else {
            board.filterNot { it.id == lot.id }
        }
        mine = if (mine.any { it.id == lot.id }) {
            mine.map { if (it.id == lot.id) lot else it }
        } else {
            mine + lot
        }
        selectedId = lot.id
    }

    /** Records the server's clock against this device's. See the class KDoc. */
    private fun adopt(page: AuctionPage) {
        skew = clock.nowMillis() - page.now
    }

    private fun newOperationId(): String =
        "${clock.nowMillis().toString(RADIX)}-${Random.nextLong().toULong().toString(RADIX)}"

    private companion object {
        const val TAG = "AuctionSession"

        const val WAIT_MILLIS = 5_000L

        /** Base 36, the same one `AccountSession` mints its ids in. Shorter, and still opaque. */
        const val RADIX = 36
    }
}

@Composable
internal fun rememberAuctionSession(
    client: AuctionClient,
    clock: Clock,
    onProfile: suspend (PlayerState) -> Unit = {},
    tokenOf: suspend () -> String?,
): AuctionSession =
    remember(client, clock) { AuctionSession(client, tokenOf, clock, onProfile) }
