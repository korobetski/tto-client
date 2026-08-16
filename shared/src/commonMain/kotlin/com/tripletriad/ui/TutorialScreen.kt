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
import com.tripletriad.model.Npc
import com.tripletriad.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * One lesson of the course — `TutorialScreen.as`, which is `PVEMatchScreen` with four methods
 * overridden, and the lessons this port added behind it. [LessonsScreen] is the list.
 *
 * The first is the original, unchanged: a scripted match under All Open, on a fixed hand, with the
 * opponent moving first and playing the worst card it can find, while nine lines explain what is
 * happening. It teaches the board, the digits and capture, and it names All Open — **one rule of
 * the seventeen the help screen lists**, which is what the rest answer. Those are one-move
 * positions, one rule each; see [TUTORIAL_COURSE].
 *
 * Everything that differs from an ordinary match is in the [MatchScript] each lesson builds; the
 * match itself is [MatchScreen], unmodified.
 *
 * ### The course leaves no mark on the record
 *
 * **No lesson counts**: not the win, not the defeat, not a draw, not the match counters behind
 * `STATS.FORFEITS`, and nothing is paid for finishing one. A tutorial is practice, and practice
 * that moved the record would make a player's win rate partly a record of being taught the rules —
 * a course of four would open every character on four wins. It is [MatchScript.counted], applied at
 * both ends: the match is never counted as started, and never credited when it ends.
 *
 * This **changes the first lesson**, which used to pay. `TutorialScreen.as:37-49` declares its own
 * `NPC` inline — the same id, name, icon, rule and fetish cards as the tt-master already in
 * `npcs.json`, but with `matchFee: 0`, a smaller `MGPReward` and item drop rates a third of the
 * catalogue's. That is the shape `shopScreen` had before Phase 2 pulled its price table out into
 * `ShopCatalog`: data written into a screen. Reading the catalogue entry instead meant the lesson
 * charged the tt-master's fee and paid its full reward, which was defensible at five MGP for one
 * match and is not the behaviour wanted now. The duplicate NPC record is still not the answer —
 * the payout is a property of *this* match, not of who is teaching it, and that is where the flag
 * lives.
 *
 * ### Three lines the original never showed
 * `opponentPhase` is called from **one** place — the red branch of `BaseMatchScreen.nextTurn`
 * (`:380`) — so it never runs on the player's turn. But `TutorialScreen` overrides it with branches
 * on `turn == 2` and `turn == 4`, which are blue's turns and therefore unreachable: lines 4, 5 and
 * 8 of its nine never appear. They are also the three most instructional ones ("Now it's your
 * turn!", "The numbers you can see each correspond to one side of the card"), which is what makes
 * it a defect rather than a design — the branches were written for the player's turn and hooked to
 * a callback that only fires on the opponent's.
 *
 * Here the lesson is driven off the placement count for both sides, so all nine play.
 *
 * @param tutor who teaches it — [tutorFor], which is the Triple Triad Master for an `ff14_`
 *   character and Kid for an `ff8_` one. The original could only ever have been the first, since it
 *   hard-codes `MODE = 'ff14_'`.
 * @param onHelp where the end panel's first action goes — the rule book, replacing Rematch
 *   (`TutorialRematchPanel.nextLesson` dispatches `NEXT_SCREEN`, which is `HELP_SCREEN`).
 * @param onExit the second action, and the back chevron. `exitBtnHandler` sends both to
 *   `PVE_SCREEN`, which here is the course.
 * @param from which lesson to open at, so the list can start one in the middle. The course then
 *   runs on from there, as it always did.
 * @param onFinished how many lessons are now done, once one has been. Called with the count rather
 *   than the index so the caller stores a number it can compare against the course's length
 *   without knowing anything about either.
 */
@Composable
@Suppress("LongParameterList")
internal fun TutorialScreen(
    catalog: CardCatalog,
    profile: GameSave,
    tutor: Npc,
    format: Format,
    clock: Clock,
    nextSeed: () -> Int?,
    onPersist: suspend (GameSave) -> Unit,
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
            clock = clock,
            nextSeed = nextSeed,
            onPersist = onPersist,
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

/**
 * What the end panel offers: the next lesson, or — after the last — the rule book.
 *
 * The rule book stays the course's destination, which is what the original ends on
 * (`TutorialRematchPanel.nextLesson` dispatches `NEXT_SCREEN`, which is `HELP_SCREEN`). It has
 * simply stopped being where the *first* lesson leads, because there is now something between them.
 */
private fun exitFor(step: Int, onHelp: () -> Unit, onNext: () -> Unit): ScriptExit =
    if (step < LAST_LESSON) {
        ScriptExit(StringKeys.LESSON_NEXT, onNext)
    } else {
        ScriptExit(StringKeys.HELP, onHelp)
    }

/**
 * The script for one lesson: the opening match, a rule position, or a rule match.
 *
 * Null when a puzzle cannot be built from [catalog] (see [puzzleSetup]) or when [step] is not a
 * lesson at all. **Nothing but the opening is identified by its index** — which row is which is the
 * row's own business, so a lesson added anywhere in [TUTORIAL_COURSE] needs no change here.
 *
 * `counted = false` on every one of them, first lesson and exam included; see this file's header.
 *
 * @param speakerKey whose name goes on the bubbles — the tutor's, and the only thing about them a
 *   lesson reads. Taken as the key rather than as the [Npc] so a test can build every lesson in the
 *   course without a catalogue of opponents; see `LessonRecordTest`.
 */
internal fun scriptFor(step: Int, speakerKey: String, catalog: CardCatalog): MatchScript? {
    val lesson = TUTORIAL_COURSE.getOrNull(step) ?: return null

    return when {
        step == FIRST_LESSON -> openingScript(speakerKey)
        lesson.puzzle != null -> puzzleScript(lesson.puzzle, speakerKey, catalog)
        lesson.drill != null -> drillScript(lesson.drill, speakerKey)
        else -> null
    }
}

/**
 * The ported `TutorialScreen`, whole: the fixed hand, the rigged flip, the doubled turn and the
 * opponent that plays its worst move.
 */
private fun openingScript(speakerKey: String): MatchScript = MatchScript(
    speakerKey = speakerKey,
    deck = tutorialDeck(),
    firstPlayer = CardColor.RED,
    turnLimit = TUTORIAL_TURN_LIMIT,
    aiOptions = MatchAiOptions.TUTOR,
    lesson = ::tutorialLines,
    counted = false,
    explains = true,
)

/**
 * One rule, one move.
 *
 * A puzzle needs none of what the opening match fixes — there is one card to play and the opponent
 * never moves again — but it does need the two things that lesson never asked for: its own rules and
 * its own board.
 */
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
    )
}

/**
 * One rule, a whole match — [TutorialDrill], which is where the reasoning for the shape lives.
 *
 * No `opening`, so the deal is a real one: that is the difference from a puzzle and the reason
 * these two rules can be shown at all. Everything else the script fixes is [TutorialDrill.tutoring]
 * spelled out — the opponent that plays to lose, the player opening so the lines land on the
 * placements they were written for, the ringed digits, and the doubled clock the lesson's own
 * sentences need. The exam turns all four off in one word.
 *
 * `counted = false` here as everywhere else in the course — the exam included, and *especially* the
 * exam: a real match that paid would make the course a repeatable source of MGP.
 */
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
)

/** The nine-line match the course opens with — the lesson this screen used to be, whole. */
internal const val FIRST_LESSON = 0

/**
 * The last lesson's index — the course is [TUTORIAL_COURSE], and this is its end.
 *
 * A **getter**, so reading it is the only thing that touches the course. As a stored `val` it was
 * evaluated while this file's own top-level properties were still being initialised, which is one
 * half of a cycle: the course's exam row calls [tutorialDeck], and if that lived here it would be
 * read before its own numbers had been assigned. It does not live here any more — see
 * `TutorialLessons.kt` — and this closes the other half, so neither file can start the other's
 * initialisation.
 */
internal val LAST_LESSON: Int get() = TUTORIAL_COURSE.size - 1

/**
 * Who teaches the lesson in [collection] — the opponent with the lowest id.
 *
 * `tt-master` in `ff14`, `kid` in `ff8`, and the choice is not arbitrary: both are id 1, both are
 * `LEVEL_NOVICE`, and **both declare `RULE_ALL_OPEN` and nothing else**, which is what the lesson's
 * second line announces. An opponent imposing Reverse would make the script say something untrue.
 *
 * Null only for an empty table, which `NpcBundleTest` rules out; the caller shows nothing rather
 * than crashing, on the same footing as the rest of [App]'s `?.let` chain.
 */
internal fun tutorFor(catalog: NpcCatalog, formatId: String): Npc? =
    catalog.playing(formatId).minByOrNull { it.id }

/**
 * The nine lines, placed on the turns they were written for.
 * Red opens, so red holds the even placements and the player the odd ones. Against the AS3's
 * 1-based `turn`, every index here is one lower.
 *
 * | Placement | AS3 turn | Lines | Reached in the original? |
 * |---|---|---|---|
 * | 0 | 1, red | 1, 2, 3 | yes |
 * | 1 | 2, blue | 4, 5 | **no** |
 * | 2 | 3, red | 6 if the player captured, then 7 | yes |
 * | 3 | 4, blue | 8 | **no** |
 * | 8 | 9, red | 9 | yes |
 *
 * @param blueScore the player's score. Line 6 says "See how my card changed color?" and the
 * original guards it with `if (scores.BLUE > 5)` — the score opens at five all, so above five means
 * the player has captured something. A congratulation for a capture that did not happen would be
 * worse than silence.
 */
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

/** The AS3's turns 1 to 4, which are placements 0 to 3 — see the table above. */
private const val TUTOR_OPENS = 0
private const val PLAYER_OPENS = 1
private const val TUTOR_REPLIES = 2
private const val PLAYER_REPLIES = 3

/** `bluePlayer.timer = 60` — see [MatchScript.turnLimit] for why it is double. */
private val TUTORIAL_TURN_LIMIT = 60.seconds

/** Five all, before anything is captured. */
private const val OPENING_SCORE = 5

/** The ninth and last placement, index 8 — the AS3's `turn == 9`. */
private const val LAST_PLACEMENT = 8
