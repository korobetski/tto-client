package com.tripletriad.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchView
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMove
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.launch

const val PVP_BOARD_TEST_TAG: String = "pvp-board"
const val PVP_SCORE_TEST_TAG: String = "pvp-score"
const val PVP_TURN_TEST_TAG: String = "pvp-turn"
const val PVP_RESULT_TEST_TAG: String = "pvp-result"
const val PVP_FORFEIT_TEST_TAG: String = "pvp-forfeit"
const val PVP_DONE_TEST_TAG: String = "pvp-done"

/** `pvp-card-<slot>` — one of this player's own cards. */
fun pvpHandTestTag(slot: Int): String = "pvp-card-$slot"

/** `pvp-back-<slot>` — a card the opponent holds that this player may not see. */
fun pvpBackTestTag(slot: Int): String = "pvp-back-$slot"

/**
 * A match against another person.
 *
 * ### Why this is a second board and not the match screen with a flag
 *
 * `MatchScreen` runs the match: it holds a `MatchState`, plays the opponent through `MatchAi`, and
 * animates from `lastPlay`. None of that is true here. The server holds the state, the opponent is
 * a person, and this screen's whole job is to **render what it is sent and post what is tapped**.
 *
 * A flag on the existing screen would mean every one of its branches asking which kind of match it
 * was in, on a screen whose central value would have to become nullable. The board itself is
 * shared — [BoardGrid] takes a `Board` for exactly this reason — and the rest is genuinely
 * different code.
 *
 * ### The opponent's cards are absent, not hidden
 *
 * `HandArea` draws the opponent's hand face-down: it *has* the cards and chooses not to show them,
 * which is fine against a program the same process is running. Here the client was never sent them,
 * so the row is drawn from `MatchView.opponentHand` — a list of nullable cards, where a null is a
 * card that does not exist on this device. Under All Open or Three Open the revealed ones arrive
 * and are drawn face-up in their own slots.
 *
 * ### One deadline, two things to say
 *
 * The server enforces a single expiry: thirty seconds of turn plus two minutes of grace. This shows
 * them as two states, because they mean different things to the player — *hurry up* and *they may
 * have lost their connection*. See `V2__pvp.sql`.
 */
@Composable
internal fun PvpMatchScreen(
    session: PvpSession,
    cards: Map<Int, Card>,
    now: Long,
    onExit: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val wire = session.match
    val view = session.view(cards)
    var selected by remember { mutableStateOf<Card?>(null) }

    // The whole channel. Cancelled when the screen goes away, which is what stops a poll a second
    // running behind the dashboard.
    LaunchedEffect(session) { session.watch() }

    // The opponent's move can make a selected card unplayable — Order and Chaos both move on. Kept
    // only while the server still lists it, so a stale highlight cannot survive a turn.
    LaunchedEffect(view?.playableHandIndices) {
        val stale = view != null &&
            selected?.let { card -> view.playableCards.none { it.id == card.id } } == true
        if (stale) {
            selected = null
        }
    }

    if (wire == null || view == null) {
        // Either there is no match, or a card id resolved to nothing — see [PvpSession.view]. The
        // second is a catalogue disagreeing with the server's, and neither is a board to draw.
        EmptyNote(strings[StringKeys.PVP_NO_MATCH], PVP_RESULT_TEST_TAG)
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PvpHeader(
                view = view,
                opponentName = wire.opponentName,
                deadline = wire.deadline,
                now = now,
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val layout = matchLayout(maxWidth, maxHeight)

                PvpPlayArea(
                    view = view,
                    layout = layout,
                    selected = selected,
                    onSelect = { card -> selected = if (selected?.id == card.id) null else card },
                    onPlace = { position ->
                        val card = selected ?: return@PvpPlayArea
                        selected = null
                        scope.launch { session.play(moveFor(view, card, position)) }
                    },
                    onDrop = { card, position ->
                        selected = null
                        scope.launch { session.play(moveFor(view, card, position)) }
                    },
                )
            }

            if (!session.isOver) {
                WideButton(
                    label = strings[StringKeys.PVP_FORFEIT],
                    tag = PVP_FORFEIT_TEST_TAG,
                    filled = false,
                    enabled = !session.isBusy,
                    onClick = { scope.launch { session.forfeit() } },
                )
            }
        }

        if (session.isOver) {
            PvpResult(
                view = view,
                status = wire.status,
                result = wire.outcome?.result,
                forfeitedBy = wire.outcome?.forfeitedBy,
                onDone = {
                    session.clear()
                    onExit()
                },
            )
        }
    }
}

/** Who is playing, the score, and whose turn it is. */
@Composable
private fun PvpHeader(view: MatchView, opponentName: String, deadline: Long?, now: Long) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${view.score.blue} — ${view.score.red}",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(PVP_SCORE_TEST_TAG),
        )
        Text(
            text = turnLine(view, opponentName, deadline, now, strings),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag(PVP_TURN_TEST_TAG),
        )
    }
}

/**
 * What the turn line says, and the one place the two halves of the deadline are told apart.
 *
 * Past the thirty seconds the *opponent* is late, not the player, so the line stops being a
 * countdown and becomes an explanation. Showing "0 seconds left" for two more minutes would say
 * the game was broken.
 */
private fun turnLine(
    view: MatchView,
    opponentName: String,
    deadline: Long?,
    now: Long,
    strings: com.tripletriad.i18n.Strings,
): String = when {
    view.isFinished -> strings[StringKeys.PVP_OVER]
    view.isMyTurn -> strings[StringKeys.TURN_PICK_CARD].let { line ->
        val left = deadline?.minus(now)?.coerceAtLeast(0L)
        if (left == null) line else "$line · ${left / MILLIS_PER_SECOND}s"
    }

    else -> strings.format(StringKeys.OPPONENT_TURN, opponentName)
}

/** The opponent's row, the board, and this player's row. */
@Composable
@Suppress("LongParameterList")
private fun PvpPlayArea(
    view: MatchView,
    layout: MatchLayout,
    selected: Card?,
    onSelect: (Card) -> Unit,
    onPlace: (Int) -> Unit,
    onDrop: (Card, Int) -> Unit,
) {
    val drag = rememberBoardDragState()

    // The ghost is drawn here rather than inside the hand for the reason `PlayArea` gives: anywhere
    // lower and the floating card would be clipped by the row it came out of. `origin` is this
    // box's position, because the cells register their bounds in root coordinates.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { drag.origin = it.positionInRoot() },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().testTag(PVP_BOARD_TEST_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            OpponentRow(view = view, layout = layout)
            BoardGrid(
                board = view.board,
                scale = layout.boardScale,
                drag = drag,
                held = selected ?: drag.card.takeIf { drag.isDragging },
                onPlace = onPlace,
            )
            OwnRow(
                view = view,
                layout = layout,
                selected = selected,
                drag = drag,
                onSelect = onSelect,
                onDrop = onDrop,
            )
        }
        DragGhost(drag = drag, scale = layout.scale)
    }
}

/**
 * The other player's hand: a back per card, and a face for each one the rules revealed.
 *
 * Slots are counted from the list's own length, which is the opponent's real card count — public
 * information, since anyone can see how many cards are left in front of them.
 */
@Composable
private fun OpponentRow(view: MatchView, layout: MatchLayout) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HandGap * layout.scale),
        modifier = Modifier.graphicsLayer {
            alpha = if (view.isMyTurn) INACTIVE_HAND_ALPHA else 1f
        },
    ) {
        for ((slot, card) in view.opponentHand.withIndex()) {
            if (card == null) {
                CardBack(
                    color = view.opponent,
                    scale = layout.scale,
                    modifier = Modifier.testTag(pvpBackTestTag(slot)),
                )
            } else {
                Box(modifier = Modifier.testTag(pvpHandTestTag(slot))) {
                    CardFace(card = card, scale = layout.scale)
                }
            }
        }
    }
}

/**
 * This player's own hand. Only what the server listed as playable responds at all.
 *
 * Both gestures, as in a PvE match: tap to select then tap a cell, or drag the card onto one.
 * `Card._draggable` gates the second the same way the first is gated — dragging a card the rules
 * forbid and watching the drop do nothing is worse feedback than a card that cannot be lifted.
 */
@Composable
@Suppress("LongParameterList")
private fun OwnRow(
    view: MatchView,
    layout: MatchLayout,
    selected: Card?,
    drag: BoardDragState,
    onSelect: (Card) -> Unit,
    onDrop: (Card, Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HandGap * layout.scale),
        modifier = Modifier.graphicsLayer {
            alpha = if (view.isMyTurn) 1f else INACTIVE_HAND_ALPHA
        },
    ) {
        for ((slot, card) in view.ownHand.withIndex()) {
            val playable = slot in view.playableHandIndices
            var coordinates by remember(slot) { mutableStateOf<LayoutCoordinates?>(null) }
            val lifted = drag.card?.id == card.id && drag.isDragging

            Box(
                modifier = Modifier
                    .testTag(pvpHandTestTag(slot))
                    .onGloballyPositioned { coordinates = it }
                    .then(
                        if (!playable) {
                            Modifier
                        } else {
                            Modifier.pointerInput(card.id, drag) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        coordinates?.let {
                                            drag.start(
                                                card,
                                                it.localToRoot(offset),
                                            )
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        coordinates?.let {
                                            drag.moveTo(it.localToRoot(change.position))
                                        }
                                    },
                                    onDragEnd = {
                                        drag.drop()?.let { (dropped, at) -> onDrop(dropped, at) }
                                    },
                                    onDragCancel = { drag.cancel() },
                                )
                            }
                        },
                    )
                    .clickable(enabled = playable) { onSelect(card) }
                    .graphicsLayer {
                        alpha = when {
                            // Dimmed rather than removed while in the air: taking it out of the row
                            // would re-lay-out the cards beside it mid-gesture.
                            lifted -> DRAG_SOURCE_ALPHA
                            playable -> 1f
                            else -> INACTIVE_HAND_ALPHA
                        }
                    },
            ) {
                CardFace(card = card, scale = layout.scale)
                if (selected?.id == card.id) {
                    Spacer(
                        modifier = Modifier
                            .size(CardSpriteWidth * layout.scale, CardSpriteHeight * layout.scale)
                            .border(
                                SelectionRingWidth,
                                LocalTtoColors.current.selectionRing,
                                TileShape,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * How it ended.
 *
 * A forfeit says so rather than being reported as a plain win or loss: "you won" and "you won
 * because they left" are not the same sentence to put in front of a player, and the second one is
 * the only honest description of a board that was never finished.
 */
@Composable
private fun PvpResult(
    view: MatchView,
    status: PvpMatchStatus,
    result: MatchResult?,
    forfeitedBy: CardColor?,
    onDone: () -> Unit,
) {
    val strings = LocalStrings.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.testTag(PVP_RESULT_TEST_TAG).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when (result) {
                    MatchResult.WIN -> strings[StringKeys.YOU_WIN]
                    MatchResult.LOSE -> strings[StringKeys.YOU_LOSE]
                    MatchResult.DRAW, null -> strings[StringKeys.DRAW]
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (status == PvpMatchStatus.FORFEITED) {
                Text(
                    text = strings[
                        if (forfeitedBy == view.side) {
                            StringKeys.PVP_YOU_LEFT
                        } else {
                            StringKeys.PVP_THEY_LEFT
                        },
                    ],
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            WideButton(
                label = strings[StringKeys.BACK],
                tag = PVP_DONE_TEST_TAG,
                onClick = onDone,
            )
        }
    }
}

/**
 * The move that puts [card] on [position].
 *
 * The slot is looked up rather than carried, because the two gestures know different things: a tap
 * has a selected card and a drag has the card under the finger, and neither has an index. The hand
 * closes up as cards are played, so a slot captured when the card was drawn would be stale.
 */
private fun moveFor(view: MatchView, card: Card, position: Int): PvpMove =
    PvpMove(handIndex = view.ownHand.indexOfFirst { it.id == card.id }, position = position)

private const val MILLIS_PER_SECOND = 1_000L
