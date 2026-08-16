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
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchAiOptions
import com.tripletriad.model.MatchResult
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
 * The script for one lesson — the opening match, or one of the rule puzzles behind it.
 *
 * Null when a puzzle cannot be built from [catalog]; see [puzzleSetup].
 *
 * The first lesson keeps everything it had: the fixed hand, the rigged flip, the doubled turn and
 * the opponent that plays its worst move. A puzzle needs none of those — there is one card to play
 * and the opponent never moves again — but it does need the two things the first lesson never
 * asked for, its own rules and its own board.
 *
 * `counted = false` on every one of them, first lesson included; see this file's header.
 *
 * @param speakerKey whose name goes on the bubbles — the tutor's, and the only thing about them a
 *   lesson reads. Taken as the key rather than as the [Npc] so a test can build every lesson in the
 *   course without a catalogue of opponents; see `LessonRecordTest`.
 */
internal fun scriptFor(step: Int, speakerKey: String, catalog: CardCatalog): MatchScript? {
    if (step == FIRST_LESSON) {
        return MatchScript(
            speakerKey = speakerKey,
            deck = tutorialDeck(),
            firstPlayer = CardColor.RED,
            turnLimit = TUTORIAL_TURN_LIMIT,
            aiOptions = MatchAiOptions.TUTOR,
            lesson = ::tutorialLines,
            counted = false,
        )
    }
    val puzzle = TUTORIAL_COURSE.getOrNull(step)?.puzzle ?: return null
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
        outcomeLines = MatchResult.entries.associateWith { puzzle.closing },
        counted = false,
    )
}

/** The nine-line match the course opens with — the lesson this screen used to be, whole. */
internal const val FIRST_LESSON = 0

/** The last lesson's index — the course is [TUTORIAL_COURSE], and this is its end. */
internal val LAST_LESSON = TUTORIAL_COURSE.size - 1

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

/**
 * `BLUE_CARDS = [1, 3, 6, 7, 10]` (`TutorialScreen.as:54`) — the hand the lesson is written around.
 *
 * Fixed rather than chosen, and it has to be: line 5 tells the player to pick a card with a bigger
 * number on the touching side, which is only sound advice if the hand is known to contain one.
 *
 * These are card **numbers**, resolved against the set the character plays — so an `ff8_` character
 * is dealt the first, third, sixth, seventh and tenth FF8 cards, exactly as before. That used to
 * happen for free, because an id meant nothing without `MODE` to read it through; ids are global
 * now, so the indirection the lesson depends on has to be spelled out. Left implicit, the tutorial
 * would deal five FFXIV cards to an FFVIII character and then fail to resolve them.
 *
 * The lesson holds either way, because it never names a card.
 */
@Suppress("MagicNumber") // Transcribed card numbers: naming each one would say nothing it does not.
private val TUTORIAL_NUMBERS = listOf(1, 3, 6, 7, 10)

/** [TUTORIAL_NUMBERS] as ids in [collection]'s own set. */
/**
 * The five cards the lesson deals the player.
 *
 * Fixed to the first block rather than to the character's collection, which no longer exists. The
 * tutorial deals its own hand — the script fixes the deal — so these are not cards the player owns
 * and never were; what matters is that the nine written lines describe them.
 */
private fun tutorialDeck(): List<Int> =
    TUTORIAL_NUMBERS.map { Card.idFor(block = TUTORIAL_BLOCK, number = it) }

/** The block the lesson's five cards come from. See [tutorialDeck]. */
private const val TUTORIAL_BLOCK = 1

/** `bluePlayer.timer = 60` — see [MatchScript.turnLimit] for why it is double. */
private val TUTORIAL_TURN_LIMIT = 60.seconds

/** Five all, before anything is captured. */
private const val OPENING_SCORE = 5

/** The ninth and last placement, index 8 — the AS3's `turn == 9`. */
private const val LAST_PLACEMENT = 8
