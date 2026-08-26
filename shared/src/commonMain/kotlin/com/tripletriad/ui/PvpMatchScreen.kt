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

const val PVP_WAIT_TEST_TAG: String = "pvp-wait"

fun pvpHandTestTag(slot: Int): String = "pvp-card-$slot"

fun pvpBackTestTag(slot: Int): String = "pvp-back-$slot"

@Composable
private fun PvpMatchSounds(matchId: String?, view: MatchView?) {
    val audio = LocalAudio.current
    val pacing = LocalPacing.current
    val joinedAt = remember(matchId) { view?.placement ?: 0 }

    LaunchedEffect(matchId, view?.placement, pacing) {
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
            pacing = pacing,
        )
    }
}

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

    // Read before the early return below, like the queue above: a composable that is called on some
    // compositions and not others is not one Compose can keep state for.
    val revealed = pvpOpenRevealed(wire?.matchId, view)

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
                    revealed = revealed,
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
 * Whether the opponent's revealed cards are face up yet, on the screen that has no `MatchSetup`.
 *
 * Only a client that **arrived at the opening** has a turn owing: one joining a match already in
 * progress — a reconnection, a second device — missed the announcement, and cards that turned over
 * for it now would be reporting a moment that has passed. Empty intro, so [openRevealed] answers
 * true on the first frame and nothing animates.
 */
@Composable
private fun pvpOpenRevealed(matchId: String?, view: MatchView?): Boolean {
    val intro = remember(matchId, view != null) {
        if (view == null || view.placement > 0) {
            emptyList()
        } else {
            serverIntroAnimations(view.rules, view.order.first)
        }
    }

    return openRevealed(matchId ?: Unit, intro)
}

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

@Composable
@Suppress("LongParameterList")
private fun PvpPlayArea(
    view: MatchView,
    layout: MatchLayout,
    selected: Card?,
    revealed: Boolean,
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
            OpponentRow(view = view, layout = layout, revealed = revealed)
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

@Composable
private fun OpponentRow(view: MatchView, layout: MatchLayout, revealed: Boolean) {
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
                    RevealingCardFace(
                        card = card,
                        scale = layout.scale,
                        revealed = revealed,
                        slot = slot,
                    )
                }
            }
        }
    }
}

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
    val narrowed = handIsNarrowed(
        held = view.ownHand.size,
        playable = view.playableHandIndices.size,
        isMyTurn = view.isMyTurn,
    )

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
                    .handDrag(
                        enabled = playable,
                        card = card,
                        drag = drag,
                        at = { coordinates },
                        onDrop = onDrop,
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
                            narrowed && !playable -> INACTIVE_HAND_ALPHA
                            else -> 1f
                        }
                    },
            ) {
                CardFace(card = card, scale = layout.scale)
                // Never both, as in the PvE hand: a chosen card that has been picked up is simply
                // the selected one.
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
                } else if (narrowed && playable) {
                    PlayableRing(scale = layout.scale)
                }
            }
        }
    }
}

private fun Modifier.handDrag(
    enabled: Boolean,
    card: Card,
    drag: BoardDragState,
    at: () -> LayoutCoordinates?,
    onDrop: (Card, Int) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(card.id, drag) {
        detectDragGestures(
            onDragStart = { offset -> at()?.let { drag.start(card, it.localToRoot(offset)) } },
            onDrag = { change, _ ->
                change.consume()
                at()?.let { drag.moveTo(it.localToRoot(change.position)) }
            },
            onDragEnd = { drag.drop()?.let { (dropped, cell) -> onDrop(dropped, cell) } },
            onDragCancel = { drag.cancel() },
        )
    }
}

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

private val PrizeThumbSize = 40.dp

private fun moveFor(view: MatchView, card: Card, position: Int): PvpMove =
    PvpMove(handIndex = view.ownHand.indexOfFirst { it.id == card.id }, position = position)

private const val MILLIS_PER_SECOND = 1_000L
