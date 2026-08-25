package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.data.MatchPlan
import com.tripletriad.data.MatchReward
import com.tripletriad.data.PveMatches
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Board
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameSave
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchAi
import com.tripletriad.model.MatchOutcome
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchState
import com.tripletriad.model.MatchView
import com.tripletriad.model.Npc
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

const val BOARD_TEST_TAG: String = "board"
const val TURN_TEST_TAG: String = "turn"
const val SCORE_TEST_TAG: String = "score"
const val OUTCOME_TEST_TAG: String = "outcome"
const val NEW_MATCH_TEST_TAG: String = "new-match"

const val MATCH_RULES_TEST_TAG: String = "match-rules"

fun ruleHelpTestTag(ruleKey: String): String = "rule-help-$ruleKey"

const val MATCH_RESULT_TEST_TAG: String = "match-result"

const val MATCH_PAYOUT_TEST_TAG: String = "match-payout"

const val MATCH_DONE_TEST_TAG: String = "match-done"

const val MATCH_EXIT_TEST_TAG: String = "match-exit"

const val MATCH_OPPONENT_TEST_TAG: String = "match-opponent"

const val TURN_TIMER_TEST_TAG: String = "turn-timer"

const val TURN_TIMER_FILL_TEST_TAG: String = "turn-timer-fill"

fun turnTestTag(player: CardColor): String = "turn-${player.name.lowercase()}"

fun tileTestTag(position: Int): String = "tile-$position"

fun tileElementTestTag(position: Int): String = "tile-element-$position"

fun tileModifierTestTag(position: Int): String = "tile-modifier-$position"

fun handCardTestTag(owner: CardColor, slot: Int): String =
    "hand-${owner.name.lowercase()}-$slot"

/**
 * Whose turn it is, as a test tag — or none, while nobody may move.
 *
 * "It is your turn" is not quite [MatchView.isMyTurn], and the gap is a second long. In a refereed
 * match the answer to a placement carries the opponent's reply as well, and the screen walks them
 * one at a time (`PveExchange`): from the moment the player's card lands until the reply has been
 * shown, the order says blue and the board is still telling a story. The hand greys out for exactly
 * that stretch — `HandArea` narrows on [MatchView.playableHandIndices], which the referee leaves
 * empty until it is really the player's move again.
 *
 * So the tag follows the hand rather than the order. Only for the side holding the view: the other
 * side's playable slots are never computed, and demanding them would mean the opponent never has a
 * turn at all.
 */
private fun MatchView.turnTag(): String? = currentPlayer
    ?.takeIf { it != side || playableHandIndices.isNotEmpty() }
    ?.let(::turnTestTag)

/**
 * A **local** match, resolved in this process — and the tutorial is now all of it.
 *
 * ### Why this survived the move to a referee, and nothing else did
 *
 * Every other match is played on the server: it deals, tosses, moves the opponent, scores and pays.
 * That was not a preference. A transcript let the server *replay* a solo match, which meant this
 * process had to be running the same AI from the same seed — so it held the opponent's five cards
 * and every move they were going to make, from the first placement, in a match that really did
 * happen as claimed. See `PveMatchScreen`.
 *
 * A lesson is exempt because a lesson **settles nothing**. `MatchScript.counted` is false for every
 * one of them: no MGP, no XP, no drops, no started-match counter, nothing written to a profile.
 * There is nothing here to cheat *at*, and asking a referee to arbitrate a composed position whose
 * whole point is that it cannot be lost would be a round trip per placement to reach a foregone
 * conclusion.
 *
 * It is also the only place a script's *other* powers are still honoured — a fixed deal, a forced
 * coin flip, an opponent told to play its worst move (`MatchAiOptions.TUTOR`). Those are exactly
 * the things a client must not be able to ask a referee for, which is why `PveMatchScreen` takes a
 * script for its lines and ignores the rest of it.
 *
 * ### It renders from a view like everything else
 *
 * The state is here, so the view is derived here — `MatchView.of`, with the visibility the setup
 * produced. That keeps one rendering stack for lessons, refereed matches and player-versus-player
 * alike, and it is what makes an Open rule mean the same thing on all three.
 */
@Composable
@Suppress("LongParameterList")
internal fun MatchScreen(
    catalog: CardCatalog,
    profile: GameSave,
    npc: Npc,
    format: Format,
    script: MatchScript,
    onExit: () -> Unit,
    scriptExit: ScriptExit? = null,
    // Invented rather than drawn from the server's stock of tickets. A ticket exists to stop a
    // client choosing its own deal; a lesson's deal is *written down in the script*, so there is
    // nothing left for one to protect and spending it would burn an allowance on a match that can
    // never be credited.
    seed: Int = LESSON_SEED,
    turnLimit: Duration = DEFAULT_TURN_LIMIT,
    onResult: (MatchResult) -> Unit = {},
) {
    val audio = LocalAudio.current
    val strings = LocalStrings.current
    val pacing = LocalPacing.current
    // For the chain's audio, which has to outlive the placement that started it — see
    // [cascadeSounds].
    val scope = rememberCoroutineScope()

    val random = remember(seed, npc.iconId) { Random(seed) }

    /*
     * The generator for everything that must not touch the one above.
     *
     * The reason has changed; the separation has not. It used to be load-bearing — a draw on the
     * player's own turn shifted every later value and stopped the transcript replaying, which
     * surfaced as a rejection indistinguishable from cheating. Nothing replays a match on this side
     * of the wire any more. What is left is worth keeping anyway: a lesson that dealt a different
     * board because the player let one turn run out would be a lesson nobody could write.
     */
    val sideRandom = remember(seed, npc.iconId) { Random(seed.inv()) }

    val rules = remember(seed, npc.iconId) {
        script.rulesOr { PveMatches.rulesFor(npc, format, random) }
    }

    // The script's hand, always: every lesson names one, and a puzzle carries the same list inside
    // its opening so the two cannot disagree about what the player is holding. The fallback is for
    // a script that named none, and lands on the profile's own deck rather than on nothing.
    val deck = remember(seed, npc.iconId, rules) {
        script.deckFor(rules, profile) ?: PveMatches.playerDeck(profile)
    }

    val match = remember(seed, npc.iconId, deck) {
        script.matchOr(npc, rules) {
            PveMatches.assemble(
                profile = profile,
                npc = npc,
                catalog = catalog,
                format = format,
                random = random,
                plan = MatchPlan(rules, deck),
                forcedFlip = script.flip(),
            )
        }
    }
    val ai = remember(script) { MatchAi(script.aiOptions()) }

    var state by remember(match) { mutableStateOf(match.setup.state) }
    var visibility by remember(match) { mutableStateOf(match.setup.opponentVisibility) }

    /*
     * What to announce before the first move, carried from the setup rather than re-derived.
     *
     * `MatchSetup` decides this from the setup that actually happened, which is not the same as
     * reading the rules back: a sudden-death rematch is played under the same Random rule but its
     * hand was not re-dealt, so the rules say announce it and the setup says do not. Held as state
     * alongside `visibility` and for the same reason — the rematch replaces both.
     */
    var setup by remember(match) { mutableStateOf(match.setup) }
    var selected by remember(match) { mutableStateOf<Card?>(null) }
    var reward by remember(match) { mutableStateOf<MatchReward?>(null) }
    var settled by remember(match) { mutableStateOf(false) }

    /*
     * The board as the player may see it — here a projection of a state this process holds.
     *
     * The Chaos roll is seeded from the placement rather than drawn from either generator, so that
     * recomposing cannot offer a different card every frame. That was true of the old `playable`
     * and is the one piece of it worth carrying over.
     */
    val view = remember(state, visibility) {
        MatchView.of(
            state = state,
            side = CardColor.BLUE,
            opponentVisibility = visibility,
            random = Random(CHAOS_SEED + state.placement),
        )
    }

    LaunchedEffect(match) {
        audio.play(Sound.MATCH_OPEN)
    }

    val banners = bannerQueue(match, state, setup)

    /*
     * Whether the pre-match announcements are over and the match has actually begun.
     *
     * The original arms both clocks in `nextTurn`, which `letsGetStarted` calls **after** the whole
     * phase cascade (`BaseMatchScreen.as:250-252`). So the player's turn starts when they can move,
     * not while Reverse and Start are still on screen.
     */
    val underway = introFinished(match, setup)

    /*
     * What the script has to say before this placement, fixed for the whole of it.
     *
     * Read once per placement rather than on each recomposition, because the opponent's delay is
     * computed from its length and the bubbles are played from the same list: the two must not be
     * able to disagree about how many lines there are.
     */
    val lesson = remember(match, state.placement) { script.linesBefore(state) }
    val speech = rememberLessonSpeech(state.placement, lesson)

    // The opponent's turn. Keyed on the placement count, so it fires once per turn and again
    // after a sudden-death rematch resets it — and never while the player is to move.
    LaunchedEffect(match, state.placement, state.isFinished) {
        if (state.isFinished || state.currentPlayer != CardColor.RED) return@LaunchedEffect
        // Held until the lesson has stopped talking — however long that took. `lessonPause` used
        // to compute it from the line count, which stopped being true when a line became
        // dismissible; see the note where it was removed, in `MatchScript`.
        snapshotFlow { speech.isSpeaking }.first { !it }
        delay(
            pacing * (
                OPPONENT_PAUSE_MS + animationsFor(state, setup).sumOf { it.totalMillis } +
                    // A chain takes longer to finish turning than a single capture, and the
                    // opponent moving over the top of it would undo what the stagger is for.
                    waveDelayMillis(state.lastPlay)
                ),
        )
        val next = ai.play(state, random)
        if (next.placement > state.placement) {
            val reindexed = visibility.reindexedFor(next)
            visibility = reindexed
            state = next
            playMatchSounds(audio, scope, MatchView.of(next, CardColor.BLUE, reindexed), pacing)
        }
    }

    /*
     * The end of a lesson: announced, never credited.
     *
     * A sudden-death draw is not an ending and plays on (`PVEMatchScreen.as:63-68`), so the same
     * effect handles both by branching on the outcome. What used to follow — `MatchRewards.credit`,
     * a profile write and a transcript submission — is gone rather than gated:
     * `MatchScript.counted` is false for every lesson, so the whole path only ever produced a zero
     * reward and an unchanged profile. Saying so outright is shorter than a credit never earned.
     */
    LaunchedEffect(match, state.isFinished, state.placement) {
        val outcome = state.outcome() ?: return@LaunchedEffect
        if (settled) return@LaunchedEffect
        val result = MatchResult.of(outcome, CardColor.BLUE)
        if (result == null) {
            val rematch = MatchPreparation.prepareRematch(state, random)
            state = rematch.state
            visibility = rematch.opponentVisibility
            setup = rematch
            selected = null
            return@LaunchedEffect
        }
        settled = true
        onResult(result)

        // **Then** the panel, once the board has had a moment to be read. The last placement is the
        // one worth watching — in a lesson it is often the only one there was — and the panel is a
        // scrim over the whole board.
        delay(
            pacing * (
                animationsFor(state, setup).sumOf { it.totalMillis } + OUTCOME_PAUSE_MS +
                    waveDelayMillis(state.lastPlay)
                ),
        )
        reward = MatchReward(result = result, mgp = 0, xp = 0)
    }

    // The one guard, for both ways of playing a card. Tapping a cell plays whatever is selected;
    // dropping one plays what the finger is holding — and both have to check the same three
    // things, which is exactly the sort of pair that drifts apart.
    val place: (Card, Int) -> Unit = { card, position ->
        if (canPlay(view, card, position)) {
            val next = state.play(card, position)
            state = next
            selected = null
            playMatchSounds(audio, scope, MatchView.of(next, CardColor.BLUE, visibility), pacing)
        }
    }

    // The clock is held while a lesson line is up, as well as behind the intro — see
    // `LessonBubbles`. Resuming restarts the turn rather than continuing it, which is
    // `turnClock`'s own behaviour on any key change and is the generous direction to be wrong in.
    val turnFraction =
        turnClock(match, view, script.turnLimitOr(turnLimit), underway && !speech.isSpeaking) {
            autoPlay(view, sideRandom)?.let { (card, position) -> place(card, position) }
        }

    val wide = LocalWideLayout.current

    val log = rememberMoveLog(match, state)

    MatchFrame(
        wide = wide,
        side = {
            MatchSidePanel(
                npc = npc,
                opponentName = strings[npc.nameKey],
                rules = match.rules,
                log = log,
            )
        },
    ) { panelShown ->
        StatusBar(
            view = view,
            selected = selected,
            npc = npc,
            opponentName = strings[npc.nameKey],
            turnFraction = turnFraction,
            // With a panel the opponent has a whole column of their own, and drawing a 26 dp face
            // beside a 50 dp one is the sort of duplicate that looks like a bug. Keyed on whether
            // the panel was *drawn* rather than on the width — a phone in landscape is wide and
            // has no panel, and for a while that left the rules strip nowhere at all.
            showOpponent = !panelShown,
            // A lesson names itself here as well as on the panel — see [TurnLine].
            outcomeTitle = script.outcomeTitle,
            onExit = onExit,
        )
        BoardRules(match.rules, panelShown)

        // The play area takes whatever the status bar leaves and sizes every card to what it
        // actually got. Nothing below this line guesses at a screen size or a "chrome"
        // constant: `matchLayout` is handed measured bounds and derives one scale that the
        // whole arrangement is known to fit inside.
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PlayArea(
                view = view,
                selected = selected,
                // Less the padding `PlayArea` applies, so the scale is derived from the space
                // the cards actually get rather than from the space before the margin.
                layout = matchLayout(maxWidth - PlayAreaInset * 2, maxHeight - PlayAreaInset * 2),
                // The two facing digits that decided the last capture, in a lesson. Recomputed
                // with the state rather than remembered: it *is* a projection of the state, and
                // one that changes on every placement.
                highlights = script.highlights(state),
                // The chain, staggered. Not gated on the script: a combo looks the same in a
                // lesson, an ordinary match and a refereed one, and it is the *rule* being shown
                // rather than an explanation laid over it. See `captureWaves`.
                waves = captureWaves(state.lastPlay),
                onSelect = { if (it in view.playableCards) selected = it },
                onPlace = { position -> selected?.let { place(it, position) } },
                onDrop = place,
            )
            reward?.let {
                OutcomePanel(
                    reward = it,
                    opponentName = strings[npc.nameKey],
                    // So a dropped card can be called by its name rather than counted.
                    cards = catalog.byId,
                    next = scriptExit,
                    onDone = onExit,
                    title = script.outcomeTitle,
                )
            }
            // Over the board and under the outcome panel: a caption is allowed to cover
            // the cards it is describing, but never the thing the player has to tap.
            MatchBannerOverlay(banners)

            // Held behind the pre-match announcements, which is where the original puts it:
            // `opponentPhase` is reached from `nextTurn`, and `nextTurn` runs after the whole
            // cascade. A lesson talking over the Start banner would also be two things at once.
            LessonBubbles(speech = speech, script = script, enabled = underway)

            // And what the opponent says about how it went, over the panel as in `endGame`.
            OutcomeBubble(script = script, result = reward?.result)
        }
    }
}

internal fun playMatchSounds(
    audio: AudioPlayer,
    scope: CoroutineScope,
    view: MatchView,
    pacing: Pacing = Pacing.Default,
) {
    val captures = view.lastPlay?.captures.orEmpty()

    placementSound(audio, captures, finished = view.isFinished)
    scope.launch {
        cascadeSounds(
            audio = audio,
            captures = captures,
            won = (view.outcome() as? MatchOutcome.Win)?.let { it.winner == view.side },
            pacing = pacing,
        )
    }
}

@Composable
@Suppress("LongParameterList")
internal fun StatusBar(
    view: MatchView,
    selected: Card?,
    npc: Npc,
    opponentName: String,
    turnFraction: Float?,
    showOpponent: Boolean,
    outcomeTitle: String?,
    onExit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = MatchHeaderTopInset)) {
        StatusRow(
            view = view,
            selected = selected,
            npc = npc,
            opponentName = opponentName,
            showOpponent = showOpponent,
            outcomeTitle = outcomeTitle,
            onExit = onExit,
        )
        TurnTimerBar(fraction = turnFraction)
    }
}

@Composable
private fun TurnTimerBar(fraction: Float?) {
    Box(
        modifier = Modifier
            .testTag(TURN_TIMER_TEST_TAG)
            .fillMaxWidth()
            .padding(horizontal = SpaceSm)
            .height(TurnTimerHeight)
            .clip(TurnTimerShape)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = TIMER_TRACK_ALPHA)),
    ) {
        if (fraction != null) {
            Box(
                modifier = Modifier
                    .testTag(TURN_TIMER_FILL_TEST_TAG)
                    .fillMaxWidth(fraction)
                    .height(TurnTimerHeight)
                    .clip(TurnTimerShape)
                    // Amber near the end, which the original does not do — its bar is one colour
                    // the whole way down. Thirty seconds is long enough that a bar shortening is
                    // easy to miss, and the penalty for missing it is a card played at random.
                    .background(
                        if (fraction <= TIMER_URGENT) {
                            MaterialTheme.colorScheme.error
                        } else {
                            LocalTtoColors.current.transient
                        },
                    ),
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun StatusRow(
    view: MatchView,
    selected: Card?,
    npc: Npc,
    opponentName: String,
    showOpponent: Boolean,
    outcomeTitle: String?,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val strings = LocalStrings.current
        // The same control every other screen's back is, rather than the bare `‹` glyph this row
        // carried while it was the one screen outside the shell. An `IconButton` is a 48 dp touch
        // target where a `Text` was a 20 dp one, and it is the only back on screen that a player
        // could previously miss — the reason for the glyph was width, and an icon costs the same
        // in every language too. The Android system gesture still reaches the same place.
        IconButton(
            onClick = onExit,
            modifier = Modifier.testTag(MATCH_EXIT_TEST_TAG).size(ExitButtonSize),
        ) {
            Icon(
                imageVector = TtoIcons.Back,
                contentDescription = strings[StringKeys.BACK],
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            )
        }
        Score(view)
        // The turn line takes whatever the two fixed ends leave, and elides rather than growing.
        //
        // It used to be three items in a centred `spacedBy` row, which fitted because every
        // string was English: French is "au bleu de jouer — choisissez une carte" where English is
        // "blue to play — pick a card", and the extra 14 characters pushed "Match suivant" onto a
        // second line on a 1080 px screen. A row sized for one language is the oldest
        // localisation bug there is, so the *sentence* is the part that gives, not the controls.
        Box(
            modifier = Modifier
                .weight(1f)
                // Absent once the board is full, which is what makes `turn-blue` mean "the player
                // may move" rather than "the player moved last".
                .then(view.turnTag()?.let { Modifier.testTag(it) } ?: Modifier),
            contentAlignment = Alignment.Center,
        ) {
            TurnLine(view = view, selected = selected, outcomeTitle = outcomeTitle)
        }
        // The opponent's face and name where the "next match" control used to be. Abandoning a
        // match is the back control; restarting one is the end-of-match panel's business, and a
        // reset beside a live board is one mis-tap away from discarding a game in progress.
        //
        // The portrait is the 50 px art the opponent list already draws — the player chose a face
        // there, and until now the board did not show it the face they chose.
        if (showOpponent) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NpcPortrait(npc = npc, name = opponentName)
                Text(
                    text = opponentName,
                    color = CardColor.RED.edge,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(
                        MATCH_OPPONENT_TEST_TAG,
                    ).padding(vertical = SpaceXs),
                )
            }
        }
    }
}

@Composable
private fun Score(view: MatchView) {
    // Computable from a view because both hand *sizes* are public even when their contents are not
    // — see `MatchView.score`. The total is still ten at every placement.
    val score = view.score
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = CardColor.BLUE.edge)) { append(score.blue.toString()) }
            append(" — ")
            withStyle(SpanStyle(color = CardColor.RED.edge)) { append(score.red.toString()) }
        },
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(SCORE_TEST_TAG),
    )
}

@Composable
private fun TurnLine(view: MatchView, selected: Card?, outcomeTitle: String?) {
    val strings = LocalStrings.current
    val outcome = view.outcome()
    if (outcome != null) {
        Text(
            text = outcomeTitle?.let { strings[it] } ?: when (outcome) {
                is MatchOutcome.Win ->
                    if (outcome.winner == view.side) {
                        strings[StringKeys.YOU_WIN]
                    } else {
                        strings[StringKeys.YOU_LOSE]
                    }
                is MatchOutcome.Draw -> strings[StringKeys.DRAW]
                is MatchOutcome.SuddenDeath ->
                    "${strings[StringKeys.DRAW]} — ${strings[StringKeys.SUDDEN_DEATH]}"
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(OUTCOME_TEST_TAG),
        )
        return
    }
    val player = view.currentPlayer ?: return
    val side = strings[if (player == CardColor.BLUE) StringKeys.SIDE_BLUE else StringKeys.SIDE_RED]
    Text(
        // "red to play — pick a card" was right when a human moved both sides. With an opponent
        // playing itself, the other side's turn is something to wait for, not an instruction.
        // Asked of the *view* rather than of the colour: in a refereed match the player is not
        // necessarily blue, and "blue to play" is an instruction to the wrong person.
        text = when {
            !view.isMyTurn -> strings.format(StringKeys.OPPONENT_TURN, side)
            selected == null -> strings.format(StringKeys.TURN_PICK_CARD, side)
            else -> strings.format(StringKeys.TURN_PICK_CELL, side, selected.name)
        },
        color = player.edge,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(TURN_TEST_TAG),
    )
}

internal fun canPlay(view: MatchView, card: Card, position: Int): Boolean =
    view.isMyTurn &&
        view.board.isEmpty(position) &&
        card in view.playableCards

/**
 * The turn clock, and the fraction to draw, or null when nothing is being counted.
 *
 * Kept in a refereed match even though the server sets no deadline for one — a program is never
 * waiting, so `PveMatchStatus` has no forfeit and nothing on the server will ever take a turn away.
 * This is pace rather than enforcement: thirty seconds of a board that has stopped is what makes a
 * player look for the card they forgot they had selected. It plays a legal move on their behalf,
 * which the server accepts as an ordinary placement because that is exactly what it is.
 */
@Composable
internal fun turnClock(
    key: Any,
    view: MatchView,
    limit: Duration,
    running: Boolean,
    onExpired: () -> Unit,
): Float? {
    var remaining by remember(key) { mutableStateOf(limit) }
    val counting = running && !view.isFinished && view.isMyTurn

    LaunchedEffect(key, view.placement, view.currentPlayer, view.isFinished, running) {
        remaining = limit
        if (counting) {
            while (remaining > Duration.ZERO) {
                delay(TIMER_TICK)
                remaining -= TIMER_TICK
            }
            onExpired()
        }
    }

    return (remaining / limit).toFloat().takeIf { counting }
}

/**
 * A legal move at random, for a turn that ran out.
 *
 * Drawn from a generator the caller owns. In the local tutorial that used to matter a great deal —
 * a draw here would shift the match generator and stop the transcript replaying — and it no longer
 * does: nothing replays a match from a seed on this side of the wire any more.
 */
internal fun autoPlay(view: MatchView, random: Random): Pair<Card, Int>? {
    val cards = view.playableCards
    val free = (0 until Board.SIZE).filter { view.board.isEmpty(it) }
    return if (cards.isEmpty() || free.isEmpty()) {
        null
    } else {
        cards.random(random) to free.random(random)
    }
}

private const val OPPONENT_PAUSE_MS = 700L

private const val OUTCOME_PAUSE_MS = 1_400L

private val DEFAULT_TURN_LIMIT = 30.seconds

private val TIMER_TICK = 100.milliseconds

private const val TIMER_URGENT = 0.25f

private const val TIMER_TRACK_ALPHA = 0.4f

private const val CHAOS_SEED = 20260802

/**
 * The seed every lesson is dealt from.
 *
 * A constant, and correct as one: a lesson's deal is written into its script, so the generator is
 * only ever consulted for the leftovers — an element on a tile, a tie the tutor has to break.
 * Making those reproducible is the point. It is *not* a server-issued ticket and must never become
 * one: tickets exist to stop a client choosing its own deal, and a match that credits nothing has
 * nothing to protect.
 */
private const val LESSON_SEED = 20260817

private val TurnTimerHeight = 3.dp
private val TurnTimerShape = RoundedCornerShape(2.dp)

private val ExitButtonSize = 34.dp

private fun HandVisibility.reindexedFor(played: MatchState): HandVisibility =
    played.lastPlay
        ?.takeIf { it.player == CardColor.RED }
        ?.let { afterPlaying(it.handIndex) }
        ?: this
