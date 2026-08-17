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
import com.tripletriad.data.MatchRewards
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
import com.tripletriad.model.Npc
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.TranscriptMove
import com.tripletriad.time.Clock
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

// `CyclomaticComplexMethod` is suppressed for one branch, added deliberately and worth the count:
// the guard that refuses to draw a board when there is no seed to play it on. The alternative was
// splitting the body into a second composable behind a fifteen-parameter call, which would move the
// complexity rather than remove it and put a seam through the middle of the largest screen here.
// Everything else in this function was already inside the limit and should stay that way.
@Composable
@Suppress("LongParameterList", "CyclomaticComplexMethod")
internal fun MatchScreen(
    catalog: CardCatalog,
    profile: GameSave,
    npc: Npc,
    format: Format,
    clock: Clock,
    nextSeed: () -> Int?,
    onPersist: suspend (GameSave) -> Unit,
    onExit: () -> Unit,
    onTranscript: suspend (MatchTranscript) -> Unit = {},
    turnLimit: Duration = DEFAULT_TURN_LIMIT,
    script: MatchScript? = null,
    scriptExit: ScriptExit? = null,
    onResult: (MatchResult) -> Unit = {},
) {
    val audio = LocalAudio.current
    val strings = LocalStrings.current
    // For the chain's audio, which has to outlive the placement that started it — see
    // [cascadeSounds].
    val scope = rememberCoroutineScope()
    var matchIndex by remember(npc.iconId) { mutableStateOf(0) }

    // The seed, kept rather than thrown away, because it is the *whole* of a transcript's
    // randomness: from it alone the server re-derives the roulette, the deal, the coin flip and
    // every one of the opponent's moves.
    //
    // **Drawn rather than invented.** On an account it is one the server issued and will accept
    // once — a client that chose its own would be choosing its own deal, which is what
    // `RejectionReason.UNKNOWN_SEED` exists to stop. Null means an account has played its offline
    // stock down and has to reconnect; the board is not drawn, because a match played on a seed
    // this server will refuse is a match played for nothing.
    //
    // A **scripted** match invents one instead, and must: `reportTranscript` never submits one —
    // the script forces the coin flip, fixes the deal and changes the opponent's strategy, none of
    // which a seed carries — so a ticket spent on the tutorial would be a ticket spent on a match
    // that can never be credited.
    val seed = remember(matchIndex, npc.iconId) { seedFor(script, clock, nextSeed) }
    if (seed == null) {
        NoSeedNotice(profile, onExit)
        return
    }

    // The match generator. The deal, the coin flip and every AI tie-break draw from it, in exactly
    // the order `TranscriptVerifier` replays them.
    val random = remember(matchIndex, npc.iconId) { Random(seed) }

    /*
     * The generator for everything that must NOT touch the one above.
     *
     * `TranscriptVerifier` states the invariant: **on the player's own turn, nothing may draw from
     * the match generator.** The server replays a match by re-running the same stream, so any draw
     * the server does not know about shifts every later value and the transcript stops replaying —
     * which surfaces as a rejection, and a rejection is indistinguishable from cheating.
     *
     * Two callers here would otherwise break it, and neither is obvious:
     *
     *   - the deck selector's Random button, which draws *before* the deal;
     *   - the turn timer's auto-play, which draws twice on the player's turn.
     *
     * Derived from the same seed rather than freshly created, so a test with a `FixedClock` stays
     * fully deterministic. (The Chaos rule already had its own generator and needs nothing here.)
     */
    val sideRandom = remember(matchIndex, npc.iconId) { Random(seed.inv()) }

    // Resolved before the deck is asked for, because the answer decides whether it is asked at all:
    // under `RULE_RANDOM` the hand comes from the whole collection and the selector never opens
    // (`BaseMatchScreen.as:120-135`). Drawn once and passed into `assemble`, which would otherwise
    // roll the roulette a second time and play under rules the player was never shown.
    val rules = remember(matchIndex, npc.iconId) {
        script.rulesOr { PveMatches.rulesFor(npc, format, random) }
    }

    /*
     * The profile this match is played by, captured **once** when the match screen opens.
     * Not `profile` read inside the effects below: that parameter tracks `session.active`, which
     * changes the moment `onPersist` returns, so the crediting effect would see a profile that had
     * already had `startingMatch` applied and apply it a second time — one match counted as two
     * started. Capturing it here is also what makes the two effects agree on which profile they are
     * amending.
     *
     * A lesson is not counted at all and this is where that begins — see [MatchScript.counted],
     * and [MatchScript.creditFor] for the other end of it.
     */
    val playing = remember(matchIndex, npc.iconId) { script.startingMatch(profile) }

    // `PVEScreen.as:244` — the match is counted as started when it is launched, not when it ends,
    // which is what makes `STATS.FORFEITS` (`STARTED_MATCHES - ENDED_MATCHES`) mean anything. That
    // is *before* the deck selector in the original too, since the selector is inside the match
    // screen: walking out of it is the forfeit this counter was designed for.
    //
    // Written here, unlike the original: the AS3 increments the counter on a global and only saves
    // in `endGame`, so a match abandoned before the last placement loses the increment and forfeits
    // can never be anything but zero. Persisting at the start is what the field was designed for.
    LaunchedEffect(matchIndex, npc.iconId) {
        onPersist(playing)
    }

    // Null until the player has chosen, which under Random — or under a script, which deals a hand
    // its lines are written around — they never are asked to.
    var deck by remember(matchIndex, npc.iconId) {
        mutableStateOf(script.deckFor(rules, profile))
    }
    val chosen = deck
    if (chosen == null) {
        DeckSelectorScreen(
            profile = profile,
            catalog = catalog,
            npc = npc,
            rules = rules,
            format = format,
            onChoose = { deck = it },
            onBack = onExit,
            // `sideRandom`, not the match generator: the Random button draws before the deal, so
            // using it here would shift every value the server expects. See `sideRandom`.
            random = sideRandom,
        )
        return
    }

    val match = remember(matchIndex, npc.iconId, chosen) {
        script.matchOr(npc, rules) {
            PveMatches.assemble(
                profile = profile,
                npc = npc,
                catalog = catalog,
                format = format,
                random = random,
                plan = MatchPlan(rules, chosen),
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

    /*
     * Whether the result has been settled — credited, persisted and reported.
     *
     * Separate from [reward], which used to be the guard, because the two no longer happen at the
     * same moment: the panel is held back for a beat so the last placement can be *seen* (see
     * `OUTCOME_PAUSE_MS`), and a guard that waited with it would be open for the length of that
     * pause. Crediting happens immediately, so leaving during the pause costs nothing.
     */
    var settled by remember(match) { mutableStateOf(false) }

    /*
     * What the player did, in order — the only part of the match the server cannot derive for
     * itself, and therefore the only part a transcript has to carry.
     *
     * A plain list rather than a `mutableStateListOf`: nothing renders it, and making it observable
     * would recompose the whole screen on every placement for no visible effect.
     */
    val moves = remember(match) { mutableListOf<TranscriptMove>() }

    /*
     * Whether this match went to a sudden-death rematch.
     * It suppresses submission, because `MatchTranscript` describes **one** nine-placement match:
     * it has a single seed, a single deck and a single list of moves, with no way to say "and then
     * the hands were regrouped and it was played again". Submitting the first nine moves of such a
     * match would be worse than submitting nothing — the server would happily replay them, score
     * the draw, and return a verdict that contradicts the reward already credited for the
     * sudden-death result.
     *
     * A known gap in the format rather than a bug here. See
     * docs/migration/09-PHASE-5-NETWORK.md.
     */
    var suddenDeath by remember(match) { mutableStateOf(false) }

    /*
     * Whether this match is one the server could replay. See `reportTranscript`.
     * Computed here rather than at the call site so the crediting effect reads a value instead of
     * an expression — `MatchScreen` is at the complexity detekt allows and this is the branch that
     * carries its reasoning best on its own.
     */
    val unrepeatable = suddenDeath || script != null

    // The deal, which is now a frame later than the screen opening: the cards are dealt once a deck
    // is settled on, and that is what this sound is announcing.
    LaunchedEffect(match) {
        audio.play(Sound.MATCH_OPEN)
    }

    val banners = bannerQueue(match, state, setup)

    /*
     * Whether the pre-match announcements are over and the match has actually begun.
     *
     * The original arms both clocks in `nextTurn`, which `letsGetStarted` calls **after** the whole
     * phase cascade (`BaseMatchScreen.as:250-252`). So the player's thirty seconds start when they
     * can move, not while Reverse and Start are still on screen. This port started the clock at
     * composition and had the intro running against it — under an ordinary thirty-second limit that
     * is a few seconds of a turn silently spent, and a test with a short limit found the match
     * playing itself out before the Start banner had left.
     */
    val underway = introFinished(match, setup)

    /*
     * What the script has to say before this placement, fixed for the whole of it.
     *
     * Read once per placement rather than on each recomposition, because the opponent's delay is
     * computed from its length and the bubbles are played from the same list: the two must not be
     * able to disagree about how many lines there are. The score is read here for the same reason —
     * one line branches on whether the player has captured anything, and it is asking about the
     * board as it stands *before* the move being announced.
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
            OPPONENT_PAUSE_MS + animationsFor(state, setup).sumOf { it.totalMillis } +
                // A chain takes longer to finish turning than a single capture, and the opponent
                // moving over the top of it would undo what the stagger is for.
                waveDelayMillis(state.lastPlay),
        )
        val next = ai.play(state, random)
        if (next.placement > state.placement) {
            visibility = visibility.reindexedFor(next)
            state = next
            playMatchSounds(audio, scope, next)
        }
    }

    // Crediting, once, when the match resolves. A sudden-death draw credits nothing and plays on
    // (`PVEMatchScreen.as:63-68`), so the same effect handles both by branching on the outcome.
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
            suddenDeath = true
            return@LaunchedEffect
        }
        val credit = script.creditFor(result, playing) {
            MatchRewards.credit(
                save = playing,
                npc = npc,
                result = result,
                rules = match.rules,
                at = clock.nowMillis(),
                random = random,
            )
        }
        settled = true
        onPersist(credit.save)

        // Told after the credit and after the persist, so a caller acting on it — a ladder
        // deciding which rung comes next — cannot observe a result the profile has not yet been
        // paid for.
        onResult(result)

        // After the credit, and never instead of it. The profile is the player's; the transcript is
        // the server's business, and a server that is down must not cost anybody their reward.
        reportTranscript(
            onTranscript = onTranscript,
            // A scripted match is not replayable either, and for a sharper reason than sudden
            // death: the server re-runs the seed through the *real* setup and the *real* AI, and a
            // script forces the coin flip, fixes the deal and makes the opponent play its worst
            // move. Nothing about it would reproduce, so submitting one asks to be rejected —
            // which is indistinguishable from being caught cheating.
            unrepeatable = unrepeatable,
            seed = seed,
            formatId = format.id,
            profile = profile,
            npc = npc,
            deck = chosen,
            moves = moves.toList(),
        )

        // **Then** the panel, once the board has had a moment to be read.
        //
        // The last placement is the one worth watching — it is the one that just flipped cards, and
        // in a lesson it is the *only* one — and the panel is a scrim over the whole board. It used
        // to arrive on the same frame as the result, so what the rule had done was covered before
        // it could be looked at. Held for as long as the placement's own captions run, plus a beat.
        delay(
            animationsFor(state, setup).sumOf { it.totalMillis } + OUTCOME_PAUSE_MS +
                waveDelayMillis(state.lastPlay),
        )
        reward = credit.reward
    }

    // Rematch, or whatever a script says instead — see `nextAction`.
    val next = nextAction(script, scriptExit) {
        audio.play(Sound.NEW_MATCH)
        matchIndex += 1
    }

    // The one guard, for both ways of playing a card. Tapping a cell plays whatever is selected;
    // dropping one plays what the finger is holding — and both have to check the same three
    // things, which is exactly the sort of pair that drifts apart.
    val place: (Card, Int) -> Unit = { card, position ->
        if (canPlay(state, card, position)) {
            val next = state.play(card, position)
            // Recorded here and nowhere else, which is the point of `place` being the single guard:
            // tapping a cell, dropping a card and the turn timer all arrive through it, so a
            // timed-out turn is written down exactly like a chosen one.
            moves += TranscriptMove(cardId = card.id, position = position)
            state = next
            selected = null
            playMatchSounds(audio, scope, next)
        }
    }

    // The clock is held while a lesson line is up, as well as behind the intro — see
    // `LessonBubbles`. Resuming restarts the turn rather than continuing it, which is
    // `turnClock`'s own behaviour on any key change and is the generous direction to be wrong in.
    val turnFraction =
        turnClock(match, state, script.turnLimitOr(turnLimit), underway && !speech.isSpeaking) {
            // `sideRandom`: this is the player's turn, and the match generator is off limits.
            autoPlay(state, sideRandom)?.let { (card, position) -> place(card, position) }
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
            state = state,
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
            outcomeTitle = script?.outcomeTitle,
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
                state = state,
                selected = selected,
                visibility = visibility,
                // Less the padding `PlayArea` applies, so the scale is derived from the space
                // the cards actually get rather than from the space before the margin.
                layout = matchLayout(maxWidth - PlayAreaInset * 2, maxHeight - PlayAreaInset * 2),
                playable = playable(state),
                // The two facing digits that decided the last capture, in a lesson. Recomputed
                // with the state rather than remembered: it *is* a projection of the state, and
                // one that changes on every placement.
                highlights = script.highlights(state),
                // The chain, staggered. Not gated on the script: a combo looks the same in a
                // lesson, an ordinary match and a refereed one, and it is the *rule* being shown
                // rather than an explanation laid over it. See `captureWaves`.
                waves = captureWaves(state.lastPlay),
                onSelect = { if (it in playable(state)) selected = it },
                onPlace = { position -> selected?.let { place(it, position) } },
                onDrop = place,
            )
            reward?.let {
                OutcomePanel(
                    reward = it,
                    opponentName = strings[npc.nameKey],
                    // So a dropped card can be called by its name rather than counted.
                    cards = catalog.byId,
                    next = next,
                    onDone = onExit,
                    title = script?.outcomeTitle,
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

@Suppress("LongParameterList")
private suspend fun reportTranscript(
    onTranscript: suspend (MatchTranscript) -> Unit,
    unrepeatable: Boolean,
    seed: Int,
    formatId: String,
    profile: GameSave,
    npc: Npc,
    deck: List<Int>,
    moves: List<TranscriptMove>,
) {
    if (unrepeatable) return
    onTranscript(
        MatchTranscript(
            seed = seed,
            formatId = formatId,
            opponentIconId = npc.iconId,
            deck = deck,
            // Stated by the client, which is backwards and known to be — the server will hold the
            // profile once accounts exist. See `MatchTranscript.ownedCards`.
            ownedCards = profile.cards,
            moves = moves,
        ),
    )
}

private fun playable(state: MatchState): List<Card> =
    if (state.currentPlayer != CardColor.BLUE) {
        emptyList()
    } else {
        state.playableCards(Random(CHAOS_SEED + state.placement))
    }

private fun playMatchSounds(audio: AudioPlayer, scope: CoroutineScope, state: MatchState) {
    val captures = state.lastPlay?.captures.orEmpty()

    placementSound(audio, captures, finished = state.isFinished)
    scope.launch {
        cascadeSounds(
            audio = audio,
            captures = captures,
            won = (state.outcome() as? MatchOutcome.Win)?.let { it.winner == CardColor.BLUE },
        )
    }
}

@Composable
private fun StatusBar(
    state: MatchState,
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
            state = state,
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
private fun StatusRow(
    state: MatchState,
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
        Score(state)
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
                .then(state.currentPlayer?.let { Modifier.testTag(turnTestTag(it)) } ?: Modifier),
            contentAlignment = Alignment.Center,
        ) {
            TurnLine(state = state, selected = selected, outcomeTitle = outcomeTitle)
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
                NpcPortrait(npc = npc, name = opponentName, size = BannerPortraitSize)
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
private fun Score(state: MatchState) {
    val score = state.score
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
private fun TurnLine(state: MatchState, selected: Card?, outcomeTitle: String?) {
    val strings = LocalStrings.current
    val outcome = state.outcome()
    if (outcome != null) {
        Text(
            text = outcomeTitle?.let { strings[it] } ?: when (outcome) {
                is MatchOutcome.Win ->
                    if (outcome.winner == CardColor.BLUE) {
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
    val player = state.currentPlayer ?: return
    val side = strings[if (player == CardColor.BLUE) StringKeys.SIDE_BLUE else StringKeys.SIDE_RED]
    Text(
        // "red to play — pick a card" was right when a human moved both sides. With an opponent
        // playing itself, the red turn is something to wait for, not an instruction.
        text = when {
            player == CardColor.RED -> strings.format(StringKeys.OPPONENT_TURN, side)
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

private fun canPlay(state: MatchState, card: Card, position: Int): Boolean =
    state.currentPlayer == CardColor.BLUE &&
        state.board.isEmpty(position) &&
        card in playable(state)

@Composable
private fun turnClock(
    key: Any,
    state: MatchState,
    limit: Duration,
    running: Boolean,
    onExpired: () -> Unit,
): Float? {
    var remaining by remember(key) { mutableStateOf(limit) }
    val counting = running && !state.isFinished && state.currentPlayer == CardColor.BLUE

    LaunchedEffect(key, state.placement, state.currentPlayer, state.isFinished, running) {
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

private fun autoPlay(state: MatchState, random: Random): Pair<Card, Int>? {
    val cards = playable(state)
    val free = (0 until Board.SIZE).filter { state.board.isEmpty(it) }
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

private val TurnTimerHeight = 3.dp
private val TurnTimerShape = RoundedCornerShape(2.dp)

private val ExitButtonSize = 34.dp

private val BannerPortraitSize = 26.dp

private fun HandVisibility.reindexedFor(played: MatchState): HandVisibility =
    played.lastPlay
        ?.takeIf { it.player == CardColor.RED }
        ?.let { afterPlaying(it.handIndex) }
        ?: this
