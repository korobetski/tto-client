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
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.Card
import com.tripletriad.model.DailyQuestCatalog
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchView
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpMove
import com.tripletriad.protocol.PvpOutcome
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

const val PVP_BOARD_TEST_TAG: String = "pvp-board"
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
private fun PvpMatchSounds(board: Any?, view: MatchView?) {
    val audio = LocalAudio.current
    val pacing = LocalPacing.current
    val joinedAt = remember(board) { view?.placement ?: 0 }

    LaunchedEffect(board, view?.placement, pacing) {
        if (board == null || view == null) return@LaunchedEffect

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

/**
 * The match **and which board of it**, or null when there is no match.
 *
 * A Sudden Death rematch keeps the match id — it is the same match — and resets the cells and the
 * placement count. Everything on the board that remembers "what did I see first" is keyed on this
 * rather than on the id alone, because a second board is owed its own opening, its own coin flip
 * and its own clock; keyed on the id, a rematch arrived silently and the client treated it as the
 * first board rewound.
 *
 * The same key `PveMatchScreen` builds from `PveMatchView.rematch`, for the same reason. A named
 * function rather than an expression at the call site so that the pairing is a thing a test can
 * hold: everything downstream of it is a `remember` key, and a `remember` key that quietly stops
 * changing fails silently.
 */
internal fun pvpBoardKey(wire: PvpMatchView?): Pair<String, Int>? =
    wire?.let { it.matchId to it.rematch }

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
    val boardKey = pvpBoardKey(wire)

    val banners = pvpBannerQueue(boardKey, view)

    // Read before the early return below, like the queue above: a composable that is called on some
    // compositions and not others is not one Compose can keep state for.
    val intro = pvpIntro(boardKey, view)
    val revealed = openRevealed(boardKey ?: Unit, intro)
    // And the clock waits it out, as a PvE board's does: a turn that starts counting under the
    // rule captions is a turn the player spends watching rather than playing.
    val underway = pveIntroFinished(boardKey ?: Unit, intro)

    // And the sounds, which were absent for the same reason and are worth more: a capture the
    // player did not initiate is a thing that happens while they are looking elsewhere.
    PvpMatchSounds(boardKey, view)

    // Read here for the reason the two above are, and answering the same question a PvE board asks
    // with `PVE_OUTCOME_PAUSE_MS`: has the board finished being watched. This screen used to open
    // the panel on the frame the status changed, so the win caption played behind a scrim over a
    // board still mid-flip.
    val resultDue = outcomeDue(wire, view, session.isOver)

    // Above the early return with everything else Compose has to keep. Seeded from the match, so a
    // board replayed in a fixture makes the same forced move twice; it is only ever consulted when
    // a turn runs out, which is not something a transcript records.
    val autoRandom = remember(boardKey) { Random(boardKey.hashCode()) }

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

    val place: (Card, Int) -> Unit = { card, position ->
        selected = null
        scope.launch { session.play(moveFor(view, card, position)) }
    }

    /*
     * The clock the PvE board has always had, on the board that until now only threatened one.
     *
     * ### Counted here rather than from the server's deadline
     *
     * `PvpMatchView.deadline` is `TURN_MILLIS + GRACE_MILLIS` away — thirty seconds to move and two
     * *minutes* on top for coming back from a tunnel or a killed app. Drawing a ring from it would
     * either show a hundred and fifty second turn, which is not the turn, or subtract a grace this
     * side would have to keep its own copy of. So the thirty seconds are counted locally, exactly
     * as a PvE board counts them, and the grace stays what it is for: the server's patience with a
     * client that is not there. A client that *is* there now moves inside it every time.
     *
     * ### Which is what makes the two modes agree
     *
     * Running out used to mean losing the match by forfeit here and playing a card at random there.
     * It plays a card in both now — `autoPlay`, which draws a playable card and a free cell and
     * looks at nothing else, so a forced move is never a good one by accident. The server's forfeit
     * is untouched and becomes what it always should have been: the answer to an absent player,
     * not to a slow one.
     */
    val turnFraction =
        turnClock(boardKey ?: Unit, view, PVP_TURN_LIMIT, underway && !session.isBusy) {
            autoPlay(view, autoRandom)?.let { (card, position) -> place(card, position) }
        }

    val log = rememberViewMoveLog(boardKey ?: Unit, view)
    // A person's face, from the avatar they chose. Blank for an opponent whose account has no
    // character yet, which `OpponentFace` answers with their initial rather than with a hole.
    val face = OpponentFace.Person(wire.opponentAvatarId)

    MatchFrame(
        wide = LocalWideLayout.current,
        side = {
            MatchSidePanel(
                face = face,
                opponentName = wire.opponentName,
                rules = view.rules,
                log = log,
            )
        },
    ) { panelShown ->
        StatusBar(
            view = view,
            selected = selected,
            face = face,
            opponentName = wire.opponentName,
            turnFraction = turnFraction,
            showOpponent = !panelShown,
            outcomeTitle = null,
            // Navigates, and deliberately does **not** concede. The two modes part company here
            // and should: leaving a solo board costs nothing, while conceding a wagered match
            // costs cards, and a 34 dp icon in the corner is not where a player should be able to
            // do the second by mistake. Conceding stays the labelled button below the board, which
            // is the only control on this screen that says what it does.
            onExit = onExit,
        )
        BoardRules(view.rules, panelShown)

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // See `MatchScreen`: less what `PvpPlayArea` pads with.
            val layout = matchLayout(maxWidth - PlayAreaInset * 2, maxHeight - PlayAreaInset * 2)

            PvpPlayArea(
                view = view,
                layout = layout,
                selected = selected,
                revealed = revealed,
                onSelect = { card -> selected = if (selected?.id == card.id) null else card },
                onPlace = { position -> selected?.let { place(it, position) } },
                onDrop = place,
            )

            // The axis the hands are on, which the swap crossing travels along — see [HandAxis].
            MatchBannerOverlay(banners, HandAxis.of(layout.landscape))

            // Over the board rather than beside it, exactly where `OutcomePanel` lands on a PvE
            // board: it is a panel covering a finished match, not another row of the column.
            if (resultDue) {
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
}

/**
 * Whether the result panel is due yet.
 *
 * A PvE board waits `PVE_OUTCOME_PAUSE_MS` plus however long the last placement takes to finish
 * being watched, and this is that same beat: the match being over is a fact about the server, not
 * a cue to dim the board. Opening the panel on the frame the status changed put a scrim over a
 * chain still flipping and the win caption still playing. [quietMillis] measures it the way PvE
 * does — the longer of the placement's own animation and the captions drawn over it, because the
 * two play at once rather than in turn.
 *
 * ### The two cases with nothing to wait for
 *
 * A **forfeit**, taken or given, stills the board on the frame it happens: no placement is
 * animating, and `pvpBannerQueue` draws nothing for it because the placement count did not move.
 * The pause would sit between a player tapping Forfeit and being told what it cost them.
 *
 * A match **already over when this screen opened** — somebody coming back to read a settlement, or
 * sent back to it by [PvpSession.poll] once the winner has chosen. Nothing is animating, and a
 * three second dark board in front of an answer decided minutes ago is a wait with nothing at the
 * end of it. Told the way [PvpMatchSounds] and [pvpOpenRevealed] tell it: by what this client had
 * in front of it when it arrived.
 */
@Composable
private fun outcomeDue(wire: PvpMatchView?, view: MatchView?, isOver: Boolean): Boolean {
    val matchId = wire?.matchId
    val pacing = LocalPacing.current
    val arrivedOver = remember(matchId) { isOver }
    var due by remember(matchId) { mutableStateOf(arrivedOver) }

    LaunchedEffect(matchId, isOver, pacing) {
        if (!isOver || arrivedOver) return@LaunchedEffect
        val watching = wire != null && view != null &&
            wire.status != PvpMatchStatus.FORFEITED && wire.status != PvpMatchStatus.ABANDONED
        if (watching) delay(pacing * (PVP_OUTCOME_PAUSE_MS + quietMillis(checkNotNull(view))))
        due = true
    }

    return due
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
                        WitnessPhase(wire.opponentName, outcome, cards, now, onDone)

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

/**
 * The loser's side of a claim: the cards at stake, and the wait.
 *
 * ### Why the cards are here at all
 *
 * This was a name and a countdown. The board behind it says who owns what *now* and nothing about
 * what was dealt, so a player about to lose cards out of their collection had no way to know which
 * — they found out afterwards by noticing something missing. `PvpOutcome.pickFrom` is the loser's
 * dealt hand and now travels to both sides for exactly this: the winner chooses from it, the loser
 * watches it.
 *
 * Drawn with the same [PrizeRow] the winner taps, with nothing picked and nothing pickable. One
 * component rather than a read-only twin, because the two are looking at the same five cards and a
 * second implementation is a second thing to keep in step.
 *
 * ### Leaving is allowed and changes nothing
 *
 * The cards go whichever screen the loser is on — the server settles the claim on its own deadline.
 * What they are owed is the sight of *which*, and [PvpSession.clear] keeps that promise for
 * somebody who walks out: a dismissal taken before the match settled lapses the moment it does, and
 * the board comes back once with the answer on it.
 */
@Composable
private fun WitnessPhase(
    opponentName: String,
    outcome: PvpOutcome?,
    cards: Map<Int, Card>,
    now: Long,
    onDone: () -> Unit,
) {
    val strings = LocalStrings.current
    val deadline = outcome?.claimDeadline

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
    outcome?.pickFrom?.takeIf { it.isNotEmpty() }?.let { at ->
        PrizeRow(
            ids = at,
            cards = cards,
            picked = emptySet(),
            // Nothing is pickable: `enabled` in `PrizeRow` is `id in picked || picked.size < owed`,
            // and zero owed with nothing picked is false for every card. The loser is watching.
            owed = 0,
            onToggle = {},
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
        // And what the match *unlocked*, in the same words a solo match uses. `creditPvp` has
        // credited both of these since PvP was refereed; until `PvpOutcome` carried the ids there
        // was nothing to say, so a player earned an achievement here and only ever found out by
        // going to look at their profile.
        UnlockRows(
            achievements = outcome.achievementIds.mapNotNull(AchievementCatalog::get),
            quests = outcome.questIds.mapNotNull(DailyQuestCatalog::get),
            opponentName = wire.opponentName,
        )
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

/**
 * The beat between the board settling and the panel that covers it.
 *
 * The same number `PveMatchScreen` uses, and deliberately the same: the two screens end a match on
 * the same animations, so ending them at different speeds would be two answers to one question.
 * Named separately rather than shared because it is the one constant either screen would be
 * entitled to tune on its own.
 */
private const val PVP_OUTCOME_PAUSE_MS = 1_400L

/**
 * How long a turn is worth, as the player sees it.
 *
 * The same thirty seconds `PVE_TURN_LIMIT` is, and the same thirty the server's
 * `PvpMatchRow.TURN_MILLIS` means by a turn. Not read off `PvpMatchView.deadline`, which is this
 * plus two minutes of grace for a client that has gone away — see the note at the call site.
 */
private val PVP_TURN_LIMIT = 30.seconds
