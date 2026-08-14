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
import com.tripletriad.protocol.PvpOutcome
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.launch
import kotlin.math.abs

const val PVP_BOARD_TEST_TAG: String = "pvp-board"
const val PVP_SCORE_TEST_TAG: String = "pvp-score"
const val PVP_TURN_TEST_TAG: String = "pvp-turn"
const val PVP_RESULT_TEST_TAG: String = "pvp-result"
const val PVP_FORFEIT_TEST_TAG: String = "pvp-forfeit"
const val PVP_DONE_TEST_TAG: String = "pvp-done"
const val PVP_PAYOUT_TEST_TAG: String = "pvp-payout"
const val PVP_STAKE_TEST_TAG: String = "pvp-stake"
const val PVP_WON_TEST_TAG: String = "pvp-won"
const val PVP_LOST_TEST_TAG: String = "pvp-lost"

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

    // The rule captions and the coin flip, exactly as a PvE match plays them. They were absent
    // here for the same reason the match had no artwork: this screen was written as "render what
    // the server says" and the announcements are not something the server says — they are derived
    // from the rules, which it does. See `serverIntroAnimations`.
    val banners = pvpBannerQueue(wire?.matchId, view)

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

        MatchBannerOverlay(banners)

        if (session.isOver) {
            PvpResult(
                // `wire.side`, not `view.side`: the view is mirrored so that this player is always
                // blue, and `forfeitedBy` is in the server's colours. See `PvpSession.view`.
                side = wire.side,
                status = wire.status,
                outcome = wire.outcome,
                cards = cards,
                onDone = {
                    session.clear()
                    onExit()
                },
            )
        }
    }
}

/**
 * The announcements this match owes the player, as an event the overlay can play.
 *
 * The PvE equivalent is `bannerQueue`, which watches a `MatchState` it owns. Here there is no state
 * to watch — only views arriving from a poll — so the effect is keyed on **what changed**: the
 * match id for the opening, and the placement count for everything after it.
 *
 * ### Why the placement count and not `lastPlay`
 *
 * `bannerQueue` keys on both because a state can change without the count moving. A poll cannot:
 * views arrive once a second and most of them are the same view, so keying on the value would
 * re-fire nothing but would compare a whole board every time. The count is the smaller key and it
 * moves exactly when a card is placed, which is exactly when there is something to say.
 *
 * ### Two things it deliberately stays silent about
 *
 * A board already under way plays no opening — a match resumed after the app was killed should not
 * announce Reverse as though it were starting. And the *first* view of a resumed match plays no
 * capture captions either, for the same reason: the placement it names may have happened minutes
 * ago, and a client that has just arrived owes the player the position rather than a replay of how
 * it got there. Both fall out of the same rule below.
 *
 * The placement count is also what [BannerEvent.at] is filled with, which is what it wants: two
 * Sames in a row earn equal caption lists and must still play twice, and the move number is the
 * monotonic marker that tells them apart.
 */
@Composable
private fun pvpBannerQueue(matchId: String?, view: MatchView?): BannerEvent? {
    var event by remember(matchId) { mutableStateOf<BannerEvent?>(null) }
    // What this client had seen when it arrived. Anything at or below it is history, not news.
    val joinedAt = remember(matchId) { view?.placement ?: 0 }

    LaunchedEffect(matchId, view?.placement) {
        if (matchId == null || view == null) return@LaunchedEffect

        val animations = when {
            view.placement > joinedAt -> MatchBanner.afterPlacement(view).asAnimations()
            view.placement > 0 -> emptyList()
            else -> serverIntroAnimations(view.rules, view.order.first)
        }
        animations.takeIf { it.isNotEmpty() }
            ?.let { event = BannerEvent(at = view.placement, animations = it) }
    }

    return event
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
                // Both travel on the view already — `MatchView.tally` has been on the wire since
                // PvP was refereed, precisely so a client can render what the referee computed
                // rather than recount it. This is the first thing to read them.
                rules = view.rules,
                tally = view.tally,
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
 *
 * @param side the colour the **server** dealt this player, which is not the one on the board: the
 *   board is mirrored so that a player is always blue. [forfeitedBy] arrives in server colours, so
 *   this is the only side it can honestly be compared against.
 */
@Composable
private fun PvpResult(
    side: CardColor,
    status: PvpMatchStatus,
    outcome: PvpOutcome?,
    cards: Map<Int, Card>,
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
                text = when (outcome?.result) {
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
                        if (outcome?.forfeitedBy == side) {
                            StringKeys.PVP_YOU_LEFT
                        } else {
                            StringKeys.PVP_THEY_LEFT
                        },
                    ],
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            // Everything below was on the wire from the first release and drawn by nothing: a
            // player was told "you win" and left to guess what it had been worth.
            if (outcome != null) {
                Payout(outcome = outcome, cards = cards)
            }

            WideButton(
                label = strings[
                    if ((outcome?.picksOwed ?: 0) > 0) {
                        StringKeys.PVP_CLAIM
                    } else {
                        StringKeys.BACK
                    },
                ],
                tag = PVP_DONE_TEST_TAG,
                onClick = onDone,
            )
        }
    }
}

/**
 * What the match paid, and what the wager moved.
 *
 * The three are kept apart on screen because they are three different facts: the payout is what
 * every match earns, the stake is what was risked, and the cards are what changed hands. A single
 * "+50 MGP" that quietly netted a 100 payout against a 50 loss would be the least informative true
 * number available.
 */
@Composable
private fun Payout(outcome: PvpOutcome, cards: Map<Int, Card>) {
    val strings = LocalStrings.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // What the match itself paid, which is the number a player actually looks for. It was on
        // the wire from the first release and always zero: the server rolled it inside `creditPvp`
        // and kept no record, so there was nothing to send. See `V6__match_payout.sql`.
        Text(
            text = "+${outcome.mgp} ${strings[StringKeys.MGP]} " +
                "$DOT_SEPARATOR +${outcome.xp} ${strings[StringKeys.XP]}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(PVP_PAYOUT_TEST_TAG),
        )
        if (outcome.stakeMgp != 0) {
            Text(
                text = strings.format(
                    if (outcome.stakeMgp > 0) {
                        StringKeys.PVP_STAKE_WON
                    } else {
                        StringKeys.PVP_STAKE_LOST
                    },
                    "${abs(outcome.stakeMgp)}",
                ),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag(PVP_STAKE_TEST_TAG),
            )
        }
        CardRow(StringKeys.PVP_WON_CARDS, outcome.cardsWon, cards, PVP_WON_TEST_TAG)
        CardRow(StringKeys.PVP_LOST_CARDS, outcome.cardsLost, cards, PVP_LOST_TEST_TAG)
    }
}

/** One side of the trade, as thumbnails. Absent entirely when nothing moved that way. */
@Composable
private fun CardRow(labelKey: String, ids: List<Int>, cards: Map<Int, Card>, tag: String) {
    if (ids.isEmpty()) return
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings[labelKey],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (id in ids) {
                cards[id]?.let { CardThumb(card = it, size = PrizeThumbSize) }
            }
        }
    }
}

/** Big enough to recognise a card by its art, small enough that five fit across a phone. */
private val PrizeThumbSize = 44.dp

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
