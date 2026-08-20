package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchAiOptions
import com.tripletriad.model.MatchResult
import com.tripletriad.model.Npc
import kotlin.time.Duration.Companion.seconds

@Composable
@Suppress("LongParameterList")
internal fun TutorialScreen(
    catalog: CardCatalog,
    profile: GameSave,
    tutor: Npc,
    format: Format,
    // No clock, no seed ticket and nothing to persist: a lesson credits nothing, so there is no
    // profile to amend and no deal for a ticket to protect. See [MatchScreen].
    onHelp: () -> Unit,
    onExit: () -> Unit,
    from: Int = FIRST_LESSON,
    onFinished: (Int) -> Unit = {},
) {
    var step by remember(tutor.nameKey, format, from) { mutableStateOf(from) }

    val script = remember(tutor.nameKey, format, step) {
        scriptFor(step, tutor.nameKey, catalog)
    }
    // Only reachable if a puzzle's card ids do not resolve in this catalogue, which
    // `TutorialPuzzleTest` rules out for the shipped one. Ending the course early is a better
    // answer than a blank screen, and the rule book is where it was going to end anyway.
    if (script == null) {
        LaunchedEffect(step) { onHelp() }
        return
    }

    // Keyed on the lesson rather than trusting the tutor to differ: `MatchScreen` re-deals on
    // `npc.iconId`, and every lesson here is taught by the same tutor — so without this the second
    // one would open on the first one's board. `CampaignMatchScreen` keys its rungs for the same
    // reason.
    key(step) {
        MatchScreen(
            catalog = catalog,
            profile = profile,
            npc = tutor,
            format = format,
            onExit = onExit,
            script = script,
            scriptExit = exitFor(step, onHelp) { step += 1 },
            // Reported when the result lands, not from the control the player happens to leave by:
            // a lesson played to the end is finished whether they go on to the next one, back to
            // the list or out to the rule book. Abandoning one mid-way produces no result and so
            // marks nothing, which is the distinction worth keeping.
            onResult = { onFinished(step + 1) },
        )
    }
}

private fun exitFor(step: Int, onHelp: () -> Unit, onNext: () -> Unit): ScriptExit =
    if (step < LAST_LESSON) {
        ScriptExit(StringKeys.LESSON_NEXT, onNext)
    } else {
        ScriptExit(StringKeys.LESSON_TO_RULES, onHelp)
    }

internal fun scriptFor(step: Int, speakerKey: String, catalog: CardCatalog): MatchScript? {
    val lesson = TUTORIAL_COURSE.getOrNull(step) ?: return null

    return when {
        step == FIRST_LESSON -> openingScript(speakerKey)
        lesson.puzzle != null -> puzzleScript(lesson.puzzle, speakerKey, catalog)
        lesson.drill != null -> drillScript(lesson.drill, speakerKey)
        else -> null
    }
}

private fun openingScript(speakerKey: String): MatchScript = MatchScript(
    speakerKey = speakerKey,
    deck = tutorialDeck(),
    firstPlayer = CardColor.RED,
    turnLimit = TUTORIAL_TURN_LIMIT,
    aiOptions = MatchAiOptions.TUTOR,
    lesson = ::tutorialLines,
    outcomeLines = mapOf(
        MatchResult.WIN to StringKeys.LESSON_BASICS_WIN,
        MatchResult.LOSE to StringKeys.LESSON_BASICS_LOSE,
        MatchResult.DRAW to StringKeys.LESSON_BASICS_DRAW,
    ),
    counted = false,
    explains = true,
    outcomeTitle = StringKeys.LESSON_COMPLETE,
)

private fun puzzleScript(
    puzzle: TutorialPuzzle,
    speakerKey: String,
    catalog: CardCatalog,
): MatchScript? {
    val opening = puzzleSetup(puzzle, catalog) ?: return null

    return MatchScript(
        speakerKey = speakerKey,
        // The hand is inside the opening; this is what stops the deck selector opening, and it is
        // the same list, so the two cannot disagree about what the player is holding.
        deck = puzzle.hand,
        turnLimit = TUTORIAL_TURN_LIMIT,
        // Every line is spoken before the one move there is to make.
        lesson = Lesson { placement, _ ->
            puzzle.lines.takeIf { placement == puzzle.board.size }.orEmpty()
        },
        rules = puzzle.rules,
        opening = opening,
        // Said over the outcome panel, whatever the panel says: the position is composed so the
        // player wins, but a lesson's closing sentence is about the rule and not about the score.
        outcomeLines = whateverHappens(puzzle.closing),
        counted = false,
        // The ringed digits are most of what a rule lesson has to say: the two numbers that
        // decided it, on the two cards that met. See `captureHighlights`.
        explains = true,
        // Not `You win !`: the position is composed so this cannot be lost. See
        // [MatchScript.outcomeTitle].
        outcomeTitle = StringKeys.LESSON_COMPLETE,
    )
}

private fun drillScript(drill: TutorialDrill, speakerKey: String): MatchScript = MatchScript(
    speakerKey = speakerKey,
    deck = drill.deck,
    firstPlayer = CardColor.BLUE.takeIf { drill.tutoring },
    turnLimit = TUTORIAL_TURN_LIMIT.takeIf { drill.tutoring },
    aiOptions = if (drill.tutoring) MatchAiOptions.TUTOR else MatchAiOptions(),
    lesson = Lesson { placement, _ -> drill.lines[placement].orEmpty() },
    outcomeLines = drill.outcomes,
    rules = drill.rules,
    counted = false,
    explains = drill.tutoring,
    // The exam is the one lesson whose result is worth announcing as a result, which is the same
    // flag every other difference answers to.
    outcomeTitle = StringKeys.LESSON_COMPLETE.takeIf { drill.tutoring },
)

internal const val FIRST_LESSON = 0

internal val LAST_LESSON: Int get() = TUTORIAL_COURSE.size - 1

internal fun tutorFor(catalog: NpcCatalog, formatId: String): Npc? =
    catalog.playing(formatId).minByOrNull { it.id }

internal fun tutorialLines(placement: Int, blueScore: Int): List<String> = when (placement) {
    TUTOR_OPENS -> listOf(StringKeys.TUTORIAL_1, StringKeys.TUTORIAL_2, StringKeys.TUTORIAL_3)
    PLAYER_OPENS -> listOf(StringKeys.TUTORIAL_4, StringKeys.TUTORIAL_5)
    TUTOR_REPLIES -> buildList {
        if (blueScore > OPENING_SCORE) add(StringKeys.TUTORIAL_6)
        add(StringKeys.TUTORIAL_7)
    }
    PLAYER_REPLIES -> listOf(StringKeys.TUTORIAL_8)
    LAST_PLACEMENT -> listOf(StringKeys.TUTORIAL_9)
    else -> emptyList()
}

private const val TUTOR_OPENS = 0
private const val PLAYER_OPENS = 1
private const val TUTOR_REPLIES = 2
private const val PLAYER_REPLIES = 3

private val TUTORIAL_TURN_LIMIT = 60.seconds

private const val OPENING_SCORE = 5

private const val LAST_PLACEMENT = 8
