package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.data.CampaignMessages
import com.tripletriad.data.MatchCredit
import com.tripletriad.data.MatchReward
import com.tripletriad.data.PveMatch
import com.tripletriad.data.PveMatches
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.CardColor
import com.tripletriad.model.CoinFlip
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchAiOptions
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchSetup
import com.tripletriad.model.MatchState
import com.tripletriad.model.Npc
import kotlin.time.Duration

/**
 * A match that has been written in advance.
 *
 * One parameter on [MatchScreen] rather than six, and **pure data**: no lambdas, because the script
 * is a `remember` key down there and a lambda rebuilt on each recomposition would look like a new
 * script and re-deal the hands.
 *
 * The AS3 does all of this by subclassing: `TutorialScreen`, `CCGroupMatchScreen` and
 * `GSGroupMatchScreen` all extend `PVEMatchScreen` and override between two and four methods.
 * Composables do not subclass, and the alternative worth avoiding was three copies of the same
 * 400-line match screen with a few lines different in each.
 *
 * **Every field is optional except the speaker**, because the two kinds of scripted match want
 * almost disjoint halves of it: the tutorial fixes the deal, the flip, the clock and the opponent's
 * strategy and says a great deal; a tournament rung changes none of those and says at most two
 * sentences.
 *
 * Every field is read through one of the nullable extensions below rather than with an elvis at the
 * call site. That is not decoration: six `script?.x ?: y` in one composable is six branches, and
 * [MatchScreen] is already at the cyclomatic complexity detekt allows.
 *
 * @property speakerKey whose name goes on the bubbles. The opponent's own, rather than the
 *   `STR_NPC_TT_Master` the AS3 writes into every `new TalkAnim(...)`: which opponent speaks
 *   depends on the collection and, in a ladder, on the rung.
 * @property deck the five cards the player is dealt, or null to ask as usual. Fixed in the
 *   tutorial, so the deck selector never opens and the lesson can talk about what is in the hand;
 *   a tournament rung deals normally.
 * @property firstPlayer who moves first, or null for a real toss. [CardColor.RED] in the tutorial,
 *   so the opponent has something to demonstrate before the player is asked to do anything. The
 *   ladders call `pof.randomRolls()` and take their chances.
 * @property turnLimit how long a turn lasts, or null for the ordinary thirty seconds.
 *   `TutorialScreen.as:58-59` doubles it, which is the original compensating for its own lesson:
 *   the lines play *during* the turn they describe, and a thirty-second clock would time out
 *   behind them.
 * @property aiOptions how the opponent plays. [MatchAiOptions.TUTOR] loses on purpose; a ladder
 *   opponent plays to win like any other.
 * @property lesson the lines to speak before a given placement. Keys, not sentences — resolving
 *   them needs [LocalStrings], which is not available where a script is built. A ladder's lines
 *   *are* their own keys, deliberately; see [CampaignMessages].
 * @property outcomeLines what is said once the result is known — `talk(_messages.win)` and its two
 *   siblings, which the ladders play from `endGame` just before the panel opens. A map rather than
 *   a function so the whole type stays comparable.
 * @property rules what the match is played under, or null to ask the opponent as usual. A rule
 *   lesson is the only thing that needs this: which rules a match has are otherwise a property of
 *   who is being played, and a lesson is played against a tutor who declares none of them.
 * @property opening the position to start from, or null to deal one. **This is what makes a
 *   one-move puzzle possible** — [MatchSetup] is a plain value, so a board eight cards deep is as
 *   constructible as an empty one. See [puzzleSetup] for the invariants it has to keep.
 * @property rewarded whether finishing pays. False for a lesson, and the reason is the one
 *   [TutorialScreen] has always had to argue about: a scripted match the player cannot lose would
 *   otherwise be a repeatable source of MGP, and three of them is three times the argument. The
 *   match is still *counted* — see [creditFor].
 */
internal data class MatchScript(
    val speakerKey: String,
    val deck: List<Int>? = null,
    val firstPlayer: CardColor? = null,
    val turnLimit: Duration? = null,
    val aiOptions: MatchAiOptions = MatchAiOptions(),
    val lesson: Lesson = Lesson.Silent,
    val outcomeLines: Map<MatchResult, String> = emptyMap(),
    val rules: GameRules? = null,
    val opening: MatchSetup? = null,
    val rewarded: Boolean = true,
)

/**
 * What the end panel offers where an ordinary match offers Rematch.
 *
 * Both scripted screens replace that control rather than adding a third: `TutorialRematchPanel`'s
 * two buttons are Help and Quit, and `CCGroupRematchPanel`'s are Next Match and Quit — each in the
 * places Rematch and Back had. **Null means no control at all**, which is the last rung of a
 * ladder: `if (_params.NEXT_STEP < 7)` simply does not build the button.
 */
internal class ScriptExit(val labelKey: String, val onLeave: () -> Unit)

/**
 * What is said, and when.
 *
 * Keyed on the **placement index** rather than on a turn counter, because that is the number
 * [MatchState] actually carries. The AS3's `turn` is 1-based and pre-incremented, so its turn *n*
 * is placement *n − 1*.
 */
internal fun interface Lesson {
    /**
     * The string keys to speak before the placement at [placement].
     *
     * @param blueScore the player's score as the lines are about to play, which one line branches
     *   on — it is the sentence that congratulates a capture, and it must not appear if nothing was
     *   captured.
     */
    fun linesBefore(placement: Int, blueScore: Int): List<String>

    companion object {
        /** Says nothing — ten of the thirteen tournament rungs, and every ordinary match. */
        val Silent: Lesson = Lesson { _, _ -> emptyList() }

        /**
         * One line as the match opens and nothing after: a ladder's `messages.start`.
         *
         * `letsGetStarted` is where the ladders play it, which is after the whole pre-match
         * cascade and before the first card goes down — placement zero.
         */
        fun opening(line: String?): Lesson = Lesson { placement, _ ->
            listOfNotNull(line.takeIf { placement == FIRST_PLACEMENT })
        }
    }
}

/** Before any card is down. */
private const val FIRST_PLACEMENT = 0

/**
 * The hand to play with, or null when the player still has to be asked for one.
 *
 * A script always deals its own; otherwise the selector opens unless `RULE_RANDOM` is in force,
 * which draws from the whole collection and never asks (`BaseMatchScreen.as:120-135`).
 */
internal fun MatchScript?.deckFor(rules: GameRules, profile: GameSave): List<Int>? =
    this?.deck ?: PveMatches.playerDeck(profile).takeIf { rules.random }

/** The rigged flip a script needs, or null for a real toss. */
internal fun MatchScript?.flip(): CoinFlip? = this?.firstPlayer?.let(CoinFlip::forced)

/**
 * What the match is played under: the script's rules, or [otherwise].
 *
 * [otherwise] is a lambda rather than a value because the ordinary answer **draws** — a roulette
 * opponent's rules are rolled from the match generator, and rolling them for a lesson that then
 * discards them would shift every later value the server expects. See [PveMatches.rulesFor].
 */
internal fun MatchScript?.rulesOr(otherwise: () -> GameRules): GameRules =
    this?.rules ?: otherwise()

/**
 * The position to play: the script's opening, or a freshly dealt [otherwise].
 *
 * The rules and the opponent are passed in rather than read off the opening, because [PveMatch]
 * carries all three and the other two are already settled by the time this is asked. Same reason
 * [otherwise] is a lambda as above: dealing draws.
 */
internal fun MatchScript?.matchOr(npc: Npc, rules: GameRules, otherwise: () -> PveMatch): PveMatch =
    this?.opening?.let { PveMatch(setup = it, npc = npc, rules = rules) } ?: otherwise()

/**
 * What a finished match pays — [earn], unless the script says it pays nothing.
 *
 * An unrewarded match is still **ended**, which is not a detail: `STATS.FORFEITS` is
 * `startedMatches - endedMatches`, and [MatchScreen] counts every match as started the moment it
 * opens. A lesson that paid nothing and closed nothing would leave a forfeit behind it, so the
 * player's record would show three abandoned matches for finishing the tutorial.
 *
 * The reward is built rather than omitted because the outcome panel is what says the lesson is
 * over: zero MGP and zero XP is the honest version of it, and the panel already renders that.
 */
internal fun MatchScript?.creditFor(
    result: MatchResult,
    playing: GameSave,
    earn: () -> MatchCredit,
): MatchCredit = if (this?.rewarded != false) {
    earn()
} else {
    MatchCredit(
        save = playing.endingMatch(),
        reward = MatchReward(result = result, mgp = 0, xp = 0),
    )
}

/** How the opponent plays: the script's strategy, or the ordinary one. */
internal fun MatchScript?.aiOptions(): MatchAiOptions = this?.aiOptions ?: MatchAiOptions()

/** The script's turn length, or [otherwise]. */
internal fun MatchScript?.turnLimitOr(otherwise: Duration): Duration = this?.turnLimit ?: otherwise

/**
 * The end panel's first action: a script's own exit, or an ordinary rematch.
 *
 * Null when a script says there is nowhere to go, which is the end of a ladder. The choice lives
 * here for the reason given on [MatchScript]: one branch fewer in [MatchScreen].
 */
internal fun nextAction(
    script: MatchScript?,
    exit: ScriptExit?,
    rematch: () -> Unit,
): ScriptExit? = if (script == null) ScriptExit(StringKeys.REMATCH, rematch) else exit

/** What is said before [state]'s next placement. Empty without a script. */
internal fun MatchScript?.linesBefore(state: MatchState): List<String> =
    this?.lesson?.linesBefore(state.placement, state.score.blue).orEmpty()

/**
 * How far through a turn's lines the lesson has got.
 *
 * Hoisted out of [LessonBubbles] because two other things have to know: the turn clock, which is
 * held while a line is up, and the opponent, which waits for the lesson to finish before it moves.
 * Both used to be answered by arithmetic over a fixed 6.1-second cadence — see [LessonBubbles] —
 * and arithmetic stops being an answer the moment a line can be dismissed early.
 */
@Stable
internal class LessonSpeech(private val lines: List<String>) {
    private var spoken by mutableStateOf(0)

    /** The line on screen, or null once they have all been said. */
    val current: String? get() = lines.getOrNull(spoken)

    /** Whether a line is still to be read. */
    val isSpeaking: Boolean get() = current != null

    /** This line is done; the next one, if any, follows. */
    fun advance() {
        spoken += 1
    }
}

/**
 * The lines for one placement, played once.
 *
 * @param key restarts the queue. The placement, so each turn's lines play once.
 */
@Composable
internal fun rememberLessonSpeech(key: Any, lines: List<String>): LessonSpeech =
    remember(key, lines) { LessonSpeech(lines) }

/**
 * Plays a lesson's lines one after another, as bubbles.
 *
 * Sequential and gapped, matching the original's `setTimeout(talk, 6100, n)` cascade: a bubble
 * lives 5.8s — 0.4s in, 5s up, 0.4s out — and the next follows. Reproduced rather than run
 * back-to-back because the pause is what makes two paragraphs read as two paragraphs. A line may
 * now also be **tapped away** before its five seconds are up; see [TalkBubble].
 *
 * ### The clock is held now, which it was not
 *
 * The original does not stop the turn timer for a lesson line — it doubles the turn to sixty
 * seconds instead (`TutorialScreen.as:58`, [MatchScript.turnLimit]) — and this port followed it.
 * That is a sound trade for nine lines and a bad one for a curriculum: the lines are the lesson,
 * and a player who reads them is spending their turn on it. So [MatchScreen] holds the clock while
 * a line is up, which is a deliberate deviation from the original and is recorded as one. The
 * doubled turn stays, because it is what the first lesson was written around.
 *
 * @param enabled whether the match has actually begun — false while the pre-match captions are
 *   still playing. The gate lives here rather than at the call site for the reason given on
 *   [MatchScript]: one branch fewer in a composable that has no room for another.
 */
@Composable
internal fun LessonBubbles(speech: LessonSpeech, script: MatchScript?, enabled: Boolean) {
    if (!enabled || script == null) return
    val strings = LocalStrings.current

    speech.current?.let { line ->
        TalkBubble(
            message = strings[line],
            speaker = strings[script.speakerKey],
            // `TalkBubble` is keyed on its own message, so two consecutive lines are two bubbles
            // and this only has to advance the index.
            onFinished = speech::advance,
        )
    }
}

/**
 * What the opponent says once the result is known — `talk(_messages.win)` and its two siblings.
 *
 * Over the outcome panel rather than instead of it, which is what the original does: `endGame`
 * adds the `TalkAnim` and then schedules `rematch` behind `intervalDuration`, so for two seconds
 * both are on screen. The bubble sits at the top and the panel in the middle, so neither covers
 * the other.
 *
 * The line is a sentence rather than a key, and goes through [Strings.get] anyway: a key no bundle
 * defines resolves to itself, which is the documented fallback and is exactly what is wanted here.
 * The ladders' dialogue has no keys because the original never gave it any: see
 * [CampaignMessages].
 */
@Composable
internal fun OutcomeBubble(script: MatchScript?, result: MatchResult?) {
    val line = result?.let { script?.outcomeLines?.get(it) } ?: return
    val strings = LocalStrings.current
    var said by remember(line) { mutableStateOf(false) }

    if (!said) {
        TalkBubble(message = line, speaker = strings[checkNotNull(script).speakerKey]) {
            said = true
        }
    }
}

/*
 * `lessonPause` used to live here: the line count times 6.1 seconds, which is how long the AI was
 * held back before it moved — `setTimeout(AI, 18300)` after three lines, `setTimeout(AI, 12200)`
 * after two (`TutorialScreen.opponentPhase`).
 *
 * It is gone because a line can now be dismissed with a tap, and a computed duration cannot know
 * that: a player who read three lines in six seconds would have watched the opponent sit still for
 * another twelve. [MatchScreen] waits on [LessonSpeech.isSpeaking] instead, which is the same claim
 * — "the lesson has finished talking" — stated as a fact rather than as arithmetic about one.
 */
