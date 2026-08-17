package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.data.MatchCredit
import com.tripletriad.data.MatchReward
import com.tripletriad.data.PveMatch
import com.tripletriad.data.PveMatches
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.CardColor
import com.tripletriad.model.CoinFlip
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchAiOptions
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchSetup
import com.tripletriad.model.MatchState
import com.tripletriad.model.Npc
import com.tripletriad.model.Side
import kotlin.time.Duration

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
    val counted: Boolean = true,
    val explains: Boolean = false,
    val outcomeTitle: String? = null,
)

internal class ScriptExit(val labelKey: String, val onLeave: () -> Unit)

internal fun interface Lesson {
    fun linesBefore(placement: Int, blueScore: Int): List<String>

    companion object {
        val Silent: Lesson = Lesson { _, _ -> emptyList() }

        fun opening(line: String?): Lesson = Lesson { placement, _ ->
            listOfNotNull(line.takeIf { placement == FIRST_PLACEMENT })
        }
    }
}

private const val FIRST_PLACEMENT = 0

internal fun MatchScript?.deckFor(rules: GameRules, profile: GameSave): List<Int>? =
    this?.deck ?: PveMatches.playerDeck(profile).takeIf { rules.random }

internal fun MatchScript?.flip(): CoinFlip? = this?.firstPlayer?.let(CoinFlip::forced)

internal fun MatchScript?.rulesOr(otherwise: () -> GameRules): GameRules =
    this?.rules ?: otherwise()

internal fun MatchScript?.matchOr(npc: Npc, rules: GameRules, otherwise: () -> PveMatch): PveMatch =
    this?.opening?.let { PveMatch(setup = it, npc = npc, rules = rules) } ?: otherwise()

internal fun MatchScript?.startingMatch(profile: GameSave): GameSave =
    if (this?.counted != false) profile.startingMatch(againstNpc = true) else profile

internal fun MatchScript?.creditFor(
    result: MatchResult,
    playing: GameSave,
    earn: () -> MatchCredit,
): MatchCredit = if (this?.counted != false) {
    earn()
} else {
    MatchCredit(save = playing, reward = MatchReward(result = result, mgp = 0, xp = 0))
}

internal fun MatchScript?.highlights(state: MatchState): Map<Int, Set<Side>> =
    if (this?.explains == true) captureHighlights(state.board, state.lastPlay) else emptyMap()

internal fun MatchScript?.aiOptions(): MatchAiOptions = this?.aiOptions ?: MatchAiOptions()

internal fun MatchScript?.turnLimitOr(otherwise: Duration): Duration = this?.turnLimit ?: otherwise

internal fun nextAction(
    script: MatchScript?,
    exit: ScriptExit?,
    rematch: () -> Unit,
): ScriptExit? = if (script == null) ScriptExit(StringKeys.REMATCH, rematch) else exit

internal fun MatchScript?.linesBefore(state: MatchState): List<String> =
    this?.lesson?.linesBefore(state.placement, state.score.blue).orEmpty()

@Stable
internal class LessonSpeech(private val lines: List<String>) {
    private var spoken by mutableStateOf(0)

    val current: String? get() = lines.getOrNull(spoken)

    val isSpeaking: Boolean get() = current != null

    fun advance() {
        spoken += 1
    }
}

@Composable
internal fun rememberLessonSpeech(key: Any, lines: List<String>): LessonSpeech =
    remember(key, lines) { LessonSpeech(lines) }

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

@Composable
internal fun OutcomeBubble(script: MatchScript?, result: MatchResult?) {
    val line = result?.let { script?.outcomeLines?.get(it) } ?: return
    val strings = LocalStrings.current
    var said by remember(line) { mutableStateOf(false) }

    if (!said) {
        TalkBubble(message = strings[line], speaker = strings[checkNotNull(script).speakerKey]) {
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
