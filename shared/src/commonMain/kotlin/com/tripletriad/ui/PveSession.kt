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
import com.tripletriad.net.PveClient
import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.PveMatchStatus
import com.tripletriad.protocol.PveMatchView
import com.tripletriad.protocol.PveMove

/**
 * A match against an opponent, held **on the server** and asked about from here.
 *
 * ### What replaced what
 *
 * This is where `MatchScreen`'s middle three hundred lines went. A solo match used to be dealt,
 * refereed, scored and credited in this process: it drew a seed, dealt both hands, ran `MatchAi`
 * from the same generator, and paid the player out of `MatchRewards.credit`. That is gone, and none
 * of it is here — the deal, the roulette, the toss, the opponent's moves and the payout are all
 * decisions this client is no longer entitled to make.
 *
 * What is left is a very small state machine: post a move, take the answer as the truth.
 *
 * ### Why there is no polling loop
 *
 * [PvpSession] polls because a person on the other side moves when they choose to. A program does
 * not: [play] posts one placement and the response already carries the reply. So the only
 * unprompted read here is [resume], and it happens once.
 *
 * ### Losing the connection is not losing the match
 *
 * A failed request sets [trouble] and changes nothing else. The match stays exactly where the
 * server has it, and [resume] picks it back up — the same call whether the app was backgrounded for
 * a second or killed on a train. That is the whole of "a dropped connection must not be an
 * abandon", and it needs no local persistence, because there is nothing here worth persisting.
 */
class PveSession internal constructor(
    private val client: PveClient,
    private val tokenOf: suspend () -> String?,
    private val onCredited: suspend (PlayerState) -> Unit = {},
) {
    /** The server's last word. Every other property on this class is derived from it. */
    var match: PveMatchView? by mutableStateOf(null)
        private set

    var isBusy: Boolean by mutableStateOf(false)
        private set

    /**
     * The last request that failed, or null.
     *
     * Named for what the player can do about it rather than for what went wrong. Every value it
     * takes means the same thing on screen — the board is out of date and the way back is to try
     * again — so the screen shows one panel and the detail goes to the log.
     */
    var trouble: AccountResult<*>? by mutableStateOf(null)
        private set

    /** Which of the player's decks to bring. Resolved against the profile the *server* holds. */
    var deck: Int by mutableStateOf(ANY_DECK)

    val isOver: Boolean get() = match?.status?.let { it != PveMatchStatus.PLAYING } == true

    /** The match as the screen renders it, or null when there is nothing to draw. */
    fun view(cards: Map<Int, Card>): MatchView? = match?.toMatchView(cards)

    /**
     * Sits down against an opponent, replacing whatever was in progress.
     *
     * The server abandons the live match itself — see `PveStore.open` and the partial unique index
     * behind it — so there is no order to get right here and no way for two matches to be live at
     * once even if two devices ask at the same moment.
     */
    suspend fun open(opponentIconId: String, formatId: String) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.open(token, opponentIconId, formatId, deck)) {
            is AccountResult.Ok -> match = result.value
            else -> {
                trouble = result
                Log.i(TAG) { "could not open a match against $opponentIconId: $result" }
            }
        }
    }

    /**
     * Places a card, and takes the exchange that comes back.
     *
     * The response carries the opponent's reply as well as this placement — `PveMatchView.plays` —
     * so this is the only request a turn costs. A refusal is not argued with: the board is re-read,
     * because a refused move means this client's idea of the position is wrong and the server's is
     * the one worth having.
     */
    suspend fun play(move: PveMove) = request {
        val token = tokenOf() ?: return@request
        val matchId = match?.matchId ?: return@request
        when (val result = client.play(token, matchId, move)) {
            is AccountResult.Ok -> adopt(result.value)
            is AccountResult.Offline -> {
                // Kept apart from the refusals below, because it is the one case where the move may
                // well have been *applied*: the request reached the server and the answer did not
                // come back. Re-reading is exactly right for both halves of that — it either shows
                // the move made or shows the board unchanged — and the player is told to reconnect
                // rather than left looking at a card that may or may not be on the board.
                trouble = result
                Log.i(TAG) { "the move may not have arrived: $result" }
            }

            else -> {
                Log.i(TAG) { "the move was refused, re-reading the match: $result" }
                refresh()
            }
        }
    }

    /**
     * The match in progress, if there is one. **This is resuming, and it is the whole of it.**
     *
     * Called on launch and after a reconnection. It takes no id because there is no id to have
     * kept: the question is "what am I in the middle of", and only the server knows. A killed
     * application, a tunnel and a flat battery are the same event, and none of them is an abandon.
     *
     * ### Why a board has to say which opponent it is asking about
     *
     * `GET /pve/matches/active` answers with a *little* more than "the match you are in": a match
     * that has just been settled stays findable for a couple of minutes, on purpose, so that a
     * player killed between placing the ninth card and reading the answer is still shown the
     * result they were already credited for. See `PveStore.recentFor`.
     *
     * That is right at launch and wrong on a board. A player who finishes a match, walks back to
     * the roster and challenges somebody else was handed the match they had just won — full board,
     * result panel and all — because the row was still inside that window and this screen took it
     * for the one it had asked for. Passing [against] makes the question the specific one a board
     * is actually asking: *am I in the middle of a match against this opponent.* A settled row and
     * a live row against somebody else are both answered no, and the caller opens a new match.
     *
     * @param against the opponent this screen is for, or null to take whatever the server offers —
     *   which only a caller with no particular board in mind should do.
     */
    suspend fun resume(against: String? = null) = request {
        val token = tokenOf() ?: return@request
        when (val result = client.current(token)) {
            is AccountResult.Ok -> match = result.value?.takeIf { it.answers(against) }
            else -> {
                trouble = result
                Log.i(TAG) { "could not read the match in progress: $result" }
            }
        }
    }

    /** Whether this is the match a board asking about [opponentIconId] was looking for. */
    private fun PveMatchView.answers(opponentIconId: String?): Boolean =
        opponentIconId == null ||
            (status == PveMatchStatus.PLAYING && this.opponentIconId == opponentIconId)

    /** Re-reads the match this session is holding. The recovery from anything that went wrong. */
    suspend fun refresh() {
        val token = tokenOf() ?: return
        val matchId = match?.matchId ?: return
        when (val result = client.match(token, matchId)) {
            is AccountResult.Ok -> adopt(result.value)
            else -> {
                trouble = result
                Log.i(TAG) { "could not re-read match $matchId: $result" }
            }
        }
    }

    /** Forgets the match on this screen. The server's row is untouched — see [PveMatchStatus]. */
    fun clear() {
        match = null
        trouble = null
    }

    /**
     * The answer, plus the one thing a settled match owes the rest of the app.
     *
     * `PveOutcome.player` is the profile **after** crediting, computed and written once on the
     * server. The client replaces what it holds with it rather than adding anything up, which is
     * the point of the field: two copies of a profile and a window in which they disagree is what
     * an item that never reaches the bag looks like from the inside.
     */
    private suspend fun adopt(next: PveMatchView) {
        match = next
        next.outcome?.player?.let { onCredited(it) }
    }

    private suspend inline fun request(block: () -> Unit) {
        isBusy = true
        trouble = null
        try {
            block()
        } finally {
            isBusy = false
        }
    }

    private companion object {
        const val TAG = "PveSession"
    }
}

@Composable
internal fun rememberPveSession(
    client: PveClient,
    onCredited: suspend (PlayerState) -> Unit = {},
    tokenOf: suspend () -> String?,
): PveSession = remember(client) { PveSession(client, tokenOf, onCredited) }
