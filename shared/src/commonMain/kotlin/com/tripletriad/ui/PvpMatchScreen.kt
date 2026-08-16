package com.tripletriad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchView
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMatchView
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

/** The loser, watching the winner name a card out of their hand. See `WitnessPhase`. */
const val PVP_WAIT_TEST_TAG: String = "pvp-wait"

/** `pvp-card-<slot>` — one of this player's own cards. */
fun pvpHandTestTag(slot: Int): String = "pvp-card-$slot"

/** `pvp-back-<slot>` — a card the opponent holds that this player may not see. */
fun pvpBackTestTag(slot: Int): String = "pvp-back-$slot"

/**
 * What each placement sounds like, for a board a referee is running.
 *
 * The mapping itself is shared with the PvE match — see [placementSound] and [cascadeSounds], and
 * the reason it is shared: a capture is the same event whoever resolved it. What is different here
 * is only *when* to play it, which is the same problem [pvpBannerQueue] solves and is solved the
 * same way: a client that joins a match in progress must not replay everything it missed as it
 * arrives, so anything at or below the placement it first saw is history rather than news.
 *
 * The cascade is awaited in place rather than launched, unlike the PvE screen's. Nothing in this
 * effect assigns the state it is keyed on — the view comes from the session — so a suspend here
 * survives to the end, and a *new* placement arriving cancels it, which is what should happen: the
 * board has moved on.
 *
 * The deal is announced too, on the first view of a match. `MatchScreen` plays it when the cards are
 * dealt; here they were dealt somewhere else, and the first sight of them is the nearest moment.
 */
@Composable
private fun PvpMatchSounds(matchId: String?, view: MatchView?) {
    val audio = LocalAudio.current
    val joinedAt = remember(matchId) { view?.placement ?: 0 }

    LaunchedEffect(matchId, view?.placement) {
        if (matchId == null || view == null) return@LaunchedEffect

        if (view.placement <= joinedAt) {
            // Nothing has been played since this client arrived. The one thing worth saying is
            // that there is a match at all, and only for a board still at its opening.
            if (view.placement == 0) audio.play(Sound.MATCH_OPEN)
            return@LaunchedEffect
        }

        val captures = view.lastPlay?.captures.orEmpty()
        placementSound(audio, captures, finished = view.isFinished)
        cascadeSounds(
            audio = audio,
            captures = captures,
            // From this player's side, which against a person is not always blue — see
            // [cascadeSounds]. A draw answers null and stays silent, as the original's does.
            won = if (view.isFinished) view.score.winner()?.let { it == view.side } else null,
        )
    }
}

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
    //
    // **Until settled, not until over.** The default is `isOver`, which is true from the ninth card
    // — including through the window where the winner still owes a choice. Stopping there is what
    // left the loser watching a dead board while a card was taken out of their hand somewhere they
    // could not see, and what left the winner's own claim invisible on the board they made it from.
    // See [PvpSession.isSettled].
    LaunchedEffect(session) { session.watch { session.isSettled } }

    // The rule captions and the coin flip, exactly as a PvE match plays them. They were absent
    // here for the same reason the match had no artwork: this screen was written as "render what
    // the server says" and the announcements are not something the server says — they are derived
    // from the rules, which it does. See `serverIntroAnimations`.
    val banners = pvpBannerQueue(wire?.matchId, view)

    // And the sounds, which were absent for the same reason and are worth more: a capture the
    // player did not initiate is a thing that happens while they are looking elsewhere.
    PvpMatchSounds(wire?.matchId, view)

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
                // See `MatchScreen`: less what `PvpPlayArea` pads with.
                val layout =
                    matchLayout(maxWidth - PlayAreaInset * 2, maxHeight - PlayAreaInset * 2)

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
                session = session,
                wire = wire,
                cards = cards,
                now = now,
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

/**
 * Who is playing, the score, whose turn it is — and **what is being played under**.
 *
 * The rule strip is the late addition and it closes a real gap: a PvE match names its rules above
 * the board (`BoardRules`) and in the side panel, and a PvP match named them nowhere. The opening
 * banners say them once, in the first seconds, and a match resumed after the app was killed does
 * not even get those — see [pvpBannerQueue]. So a player who wanted to know whether Random or
 * Reverse was in force had to remember the table they joined.
 *
 * Random is the case that made this visible: it changes only the **deal**, which the server does,
 * so the one way to tell it was in force was that the hand did not look like the chosen deck — an
 * observation indistinguishable from the rule not working. The strip says what the server says is
 * in force, which is the client's whole share of that question.
 *
 * `roulette` is not passed: on a *table* the draw is still pending and the strip says so, but by
 * the time there is a board the server has drawn and the result is in [MatchView.rules] already.
 */
@Composable
private fun PvpHeader(view: MatchView, opponentName: String, deadline: Long?, now: Long) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SpaceSm, end = SpaceSm, bottom = SpaceSm, top = MatchHeaderTopInset),
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
        RulesStrip(view.rules)
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
            // See `PlayArea`: nothing in a match touches the edge of the window.
            .padding(PlayAreaInset)
            .onGloballyPositioned { drag.origin = it.positionInRoot() },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().testTag(PVP_BOARD_TEST_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
            // The same break the PvE board puts between a hand and the board, and for the same
            // reason: `SpaceEvenly` made the gap whatever was left over, which on a short window
            // is nothing.
            verticalArrangement = Arrangement.spacedBy(HandBoardGap, Alignment.CenterVertically),
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
                // Nothing ringed, and not merely because this is not a lesson: `captureHighlights`
                // reads `MatchState.lastPlay`, and a refereed match has no `MatchState` on this
                // side at all — the referee resolved the placement and sent the board that came
                // out of it. Passed rather than defaulted so a board that *could* explain has to
                // say it is choosing not to.
                highlights = emptyMap(),
                // The stagger *is* passed: `MatchView.lastPlay` carries the same captures with the
                // same waves, so a combo the referee resolved turns exactly as one this client did.
                // That is the whole of "the same delay in every mode".
                waves = captureWaves(view.lastPlay),
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
                    // See `MatchBoard`: the match layer keeps a plain `clickable` so that
                    // adjacent cards do not grow into each other's hit areas, and states the role
                    // and the selection by hand.
                    .semantics { this.selected = selected?.id == card.id }
                    .clickable(enabled = playable, role = Role.Button) { onSelect(card) }
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
 * How it ended — and, when the wager owes one, **the choice that ends it**.
 *
 * ### The claim belongs to the board
 *
 * Under One and Diff a match is not over when the ninth card lands: somebody has to name the cards
 * they are taking, and until they do the server holds the match at
 * [PvpMatchStatus.AWAITING_CLAIM] and credits nothing. That choice used to happen on a screen of
 * its own, reached from the lobby, which broke the moment in two for both players. The winner left
 * the board, went somewhere else, and picked a card out of a hand they were no longer looking at.
 * The loser was sent back to the lobby immediately and the cards left their collection later, with
 * no account of when or which — the one event in this game that happens *to* a player and they were
 * the only party not present for it.
 *
 * So the panel has three faces, all of them over the board the match was played on:
 *
 * - **The winner owes picks** — the prizes, out of the loser's dealt hand, and no way out but to
 *   choose. There is deliberately no Back here: the wager is not settled, and a winner who wandered
 *   off would leave the loser waiting on the server's deadline.
 * - **The loser waits** — told who is choosing and out of whose hand, with the same countdown the
 *   winner is working against. A Back **is** offered: the deadline is the server's and can run for
 *   minutes, and a winner who closes their app must not be able to hold somebody else's session
 *   hostage. Leaving is a choice; being stuck is not.
 * - **Settled** — the result and what it paid, which is where every other ending starts.
 *
 * A forfeit says so rather than being reported as a plain win or loss: "you won" and "you won
 * because they left" are not the same sentence to put in front of a player, and the second one is
 * the only honest description of a board that was never finished.
 *
 * ### The scrim and the card are not decoration
 *
 * This was a bare `Column` over the finished board: unpainted text sitting on top of nine tiles of
 * card art, which is the one background the payout lines cannot be read against. It now takes the
 * same dressing `OutcomePanel` takes at the end of a PvE match — the theme's `scrim` over the dead
 * board, a `Surface` at [OutcomeElevation] under the text — so the two endings look like the same
 * game ending twice rather than two screens by different hands.
 *
 * A `Surface` and not an `AlertDialog` for the reason `OutcomePanel` gives, and here for one more:
 * [MatchBannerOverlay] is drawn after this in [PvpMatchScreen] and a popup would land over it.
 */
@Composable
private fun PvpResult(
    session: PvpSession,
    wire: PvpMatchView,
    cards: Map<Int, Card>,
    now: Long,
    onDone: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.testTag(PVP_RESULT_TEST_TAG).widthIn(max = ContentMaxWidth)
                .padding(SpaceLg),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = OutcomeElevation,
            shadowElevation = OutcomeElevation,
            border = BorderStroke(HairlineWidth, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(SpaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                val outcome = wire.outcome
                when {
                    (outcome?.picksOwed ?: 0) > 0 && outcome != null ->
                        ClaimPhase(session, wire, outcome, cards, now)

                    session.isAwaitingClaim ->
                        WitnessPhase(wire.opponentName, outcome?.claimDeadline, now, onDone)

                    else -> SettledPhase(wire, outcome, cards, onDone)
                }
            }
        }
    }
}

/**
 * The winner naming their prize, on the board they won it on.
 *
 * The same picker `PvpClaimScreen` draws — [PrizeRow] is shared between them precisely so the two
 * routes to this choice cannot drift — and the same rule about what is tappable: once enough are
 * picked the rest stop responding, because a tap that silently replaced an earlier choice would be
 * worse feedback than one that does nothing.
 *
 * Nothing follows the confirmation: [PvpSession.claim] replaces the match with the settled one, so
 * this panel simply becomes [SettledPhase] on the next frame, with the cards now listed under what
 * was won. That is the whole reason the claim lives here — the player sees what they took, in the
 * place they took it from.
 */
@Composable
private fun ClaimPhase(
    session: PvpSession,
    wire: PvpMatchView,
    outcome: PvpOutcome,
    cards: Map<Int, Card>,
    now: Long,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var picked by remember(wire.matchId) { mutableStateOf(emptySet<Int>()) }

    Text(
        text = strings[StringKeys.PVP_CLAIM_TITLE],
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = claimPrompt(outcome.picksOwed, picked.size, outcome.claimDeadline, now, strings),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.testTag(PVP_CLAIM_PROMPT_TEST_TAG),
    )
    PrizeRow(
        ids = outcome.pickFrom,
        cards = cards,
        picked = picked,
        owed = outcome.picksOwed,
        onToggle = { id -> picked = if (id in picked) picked - id else picked + id },
    )
    WideButton(
        label = strings[StringKeys.PVP_CLAIM_CONFIRM],
        tag = PVP_CLAIM_CONFIRM_TEST_TAG,
        enabled = picked.size == outcome.picksOwed && !session.isBusy,
        onClick = { scope.launch { session.claim(wire.matchId, picked.toList()) } },
    )
}

/**
 * What the loser sees while it happens.
 *
 * The board is still behind the scrim, which is the point: the cards being chosen from are the ones
 * that were just played on it. The countdown is the winner's own deadline, so the wait has a stated
 * end rather than being an indefinite spinner — and past it the server settles for the winner
 * anyway, which is what makes the number honest.
 *
 * Back is offered, and that is a deliberate departure from "the loser must be present for the
 * choice": present is what the app can offer, captive is not. The deadline belongs to the server
 * and can run for minutes; a winner who force-quits their app would otherwise hold this session
 * shut for all of it. The poll keeps running while the screen is up, so a player who stays sees the
 * cards go.
 */
@Composable
private fun WitnessPhase(opponentName: String, deadline: Long?, now: Long, onDone: () -> Unit) {
    val strings = LocalStrings.current

    Text(
        text = strings.format(StringKeys.PVP_CLAIM_WAIT, opponentName),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(PVP_WAIT_TEST_TAG),
    )
    deadline?.let {
        Text(
            text = "${(it - now).coerceAtLeast(0L) / MILLIS_PER_SECOND}s",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelMedium,
        )
    }
    WideButton(
        label = strings[StringKeys.BACK],
        tag = PVP_DONE_TEST_TAG,
        filled = false,
        onClick = onDone,
    )
}

/**
 * The ending, once there is nothing left to decide.
 *
 * @param wire the match in **server** colours. `wire.side` and not the view's: the board is
 *   mirrored so that a player is always blue, and `forfeitedBy` arrives in the server's colours, so
 *   this is the only side it can honestly be compared against. See [PvpSession.view].
 */
@Composable
private fun SettledPhase(
    wire: PvpMatchView,
    outcome: PvpOutcome?,
    cards: Map<Int, Card>,
    onDone: () -> Unit,
) {
    val strings = LocalStrings.current

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
    if (wire.status == PvpMatchStatus.FORFEITED) {
        Text(
            text = strings[
                if (outcome?.forfeitedBy == wire.side) {
                    StringKeys.PVP_YOU_LEFT
                } else {
                    StringKeys.PVP_THEY_LEFT
                },
            ],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
        )
    }

    // Everything below was on the wire from the first release and drawn by nothing: a player was
    // told "you win" and left to guess what it had been worth.
    if (outcome != null) {
        Payout(outcome = outcome, cards = cards)
    }

    WideButton(
        label = strings[StringKeys.BACK],
        tag = PVP_DONE_TEST_TAG,
        onClick = onDone,
    )
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
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
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
        Row(horizontalArrangement = Arrangement.spacedBy(SpaceXs)) {
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
