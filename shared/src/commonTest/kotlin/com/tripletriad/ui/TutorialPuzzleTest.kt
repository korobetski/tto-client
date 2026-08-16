package com.tripletriad.ui

import com.tripletriad.model.CaptureKind
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchState
import com.tripletriad.model.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What each rule lesson actually does when it is played — [TUTORIAL_PUZZLES].
 *
 * **This is the test the lessons exist behind.** The positions were found by
 * `tools/find_lesson_positions.py`, which is a *second* implementation of rules that live in
 * `tto-core`: it can only ever suggest a position, never certify one. Here the suggestion is
 * played through the real engine, and what is asserted is the sentence the tutor says out loud —
 * that these two cards fall to Same, that a third falls to the chain behind them, and that **none
 * of it happens on raw power**.
 *
 * That last assertion is the one with teeth. A position where the placed card would have won
 * anyway still captures, still looks right, and still ends the lesson in a win — while teaching the
 * player that their 2 beat a 4 for a reason nobody named. Switch Same off in the lesson and
 * [sameCapturesBothNeighbours] fails; make the placed card strong enough to win on power and
 * [theRuleIsTheOnlyExplanation] fails. Neither can pass by accident while the other does.
 */
class TutorialPuzzleTest {

    /** Every id resolves, and each board and hand add up to a position that can be played. */
    @Test
    fun everyLessonBuilds() {
        for ((index, puzzle) in TUTORIAL_PUZZLES.withIndex()) {
            val setup = assertNotNull(puzzleSetup(puzzle, catalog), "lesson $index does not build")
            val state = setup.state

            assertEquals(
                puzzle.board.size,
                state.placement,
                "lesson $index: `placement` must match the cards down, or the wrong side moves",
            )
            assertEquals(
                CardColor.BLUE,
                state.currentPlayer,
                "lesson $index: the player has to be the one to move",
            )
            assertEquals(
                PUZZLE_CELL,
                state.playablePositions().singleOrNull(),
                "lesson $index: one cell free, and it is the one every line names",
            )
            assertEquals(
                1,
                state.currentHand.size,
                "lesson $index: a one-move lesson holds one card",
            )
        }
    }

    /** Same takes the two cards it matches, and both say so. */
    @Test
    fun sameCapturesBothNeighbours() {
        val played = play(SAME_LESSON)

        assertEquals(
            setOf(SAME_RIGHT_CELL, SAME_BELOW_CELL),
            played.capturedPositions(),
            "the card to the right and the card below are the two the lines describe",
        )
        assertEquals(
            setOf(CaptureKind.SAME),
            played.kinds(),
            "both should fall to Same rather than to power",
        )
    }

    /** Plus takes both on equal sums, with the weaker card. */
    @Test
    fun plusCapturesOnEqualSums() {
        val played = play(PLUS_LESSON)

        assertEquals(
            setOf(PLUS_ABOVE_CELL, PLUS_LEFT_CELL),
            played.capturedPositions(),
            "the card above and the card to the left are the two the lines add up",
        )
        assertEquals(setOf(CaptureKind.PLUS), played.kinds(), "both should fall to Plus")
    }

    /**
     * The combo lesson takes a third card the placement never touches.
     *
     * Two claims, because they are the two the closing line makes: two cards fall to Same, and one
     * more falls to the chain. `wave` above zero is what makes the third a combo generation rather
     * than simply a fourth neighbour.
     */
    @Test
    fun comboReachesACardThePlacementDoesNotTouch() {
        val played = play(COMBO_LESSON)
        val captures = played.lastPlay?.captures.orEmpty()

        assertEquals(EXPECTED_COMBO_CAPTURES, captures.size, "one placement, three cards")
        assertEquals(
            setOf(CaptureKind.SAME, CaptureKind.COMBO),
            played.kinds(),
            "two by Same and one behind them; a lesson about the chain needs the chain",
        )

        val chained = captures.single { it.kind == CaptureKind.COMBO }
        assertTrue(chained.wave >= 1, "a combo is a later wave, was ${chained.wave}")
        assertTrue(
            chained.position !in played.neighboursOfTheMove(),
            "the third card is not one the placed card is even beside — that is the point of it",
        )
    }

    /**
     * **Raw power captures nothing, in any of them.**
     *
     * Each position replayed with its rule switched off. If anything still turns, the lesson proves
     * nothing: the player would have won that exchange anyway, and the line explaining why is
     * false.
     */
    @Test
    fun theRuleIsTheOnlyExplanation() {
        for ((index, puzzle) in TUTORIAL_PUZZLES.withIndex()) {
            val setup = assertNotNull(puzzleSetup(puzzle, catalog))
            val plain = setup.state.copy(rules = GameRules())

            assertEquals(
                emptyList(),
                plain.play(plain.currentHand.single(), PUZZLE_CELL).lastPlay?.captures.orEmpty(),
                "lesson $index captures on power alone, so it does not demonstrate its rule",
            )
        }
    }

    /** No lesson ends in a defeat — every closing line is a congratulation. */
    @Test
    fun everyLessonIsWon() {
        for (lesson in TUTORIAL_PUZZLES.indices) {
            val played = play(lesson)
            val score = played.score

            assertTrue(played.isFinished, "lesson $lesson should end on the move it asks for")
            assertTrue(
                score.blue > score.red,
                "lesson $lesson ends $score, and a lesson should not end in a defeat",
            )
        }
    }

    /**
     * The card the lines describe still shows the numbers they name.
     *
     * The sentences are specific — "its right side is a 2", "both of my cards show a 7" — so a
     * re-import that renumbered block 1 would leave the tutor describing a card that is no longer
     * there, and every other test here would still pass: they assert what the *position* does, and
     * a renumbered position is still a valid one. This is the only check that the words are true.
     */
    @Test
    fun theNumbersTheLinesNameAreStillOnTheCard() {
        val dodo = assertNotNull(catalog[DODO_ID], "the card every lesson is played with")

        assertEquals(DODO_POWERS, listOf(dodo.top, dodo.right, dodo.bottom, dodo.left), "Dodo")
    }

    /** Builds the lesson and plays the one move it asks for. */
    private fun play(lesson: Int): MatchState {
        val setup = assertNotNull(puzzleSetup(TUTORIAL_PUZZLES[lesson], catalog))
        return setup.state.play(setup.state.currentHand.single(), PUZZLE_CELL)
    }

    private fun MatchState.capturedPositions(): Set<Int> =
        lastPlay?.captures.orEmpty().mapTo(mutableSetOf()) { it.position }

    private fun MatchState.kinds(): Set<CaptureKind> =
        lastPlay?.captures.orEmpty().mapTo(mutableSetOf()) { it.kind }

    /** The cells the placed card is actually beside. */
    private fun MatchState.neighboursOfTheMove(): Set<Int> =
        Side.entries.mapNotNullTo(mutableSetOf()) { board.neighbour(PUZZLE_CELL, it) }

    private companion object {
        /** The lessons resolve their card ids through this; see [LESSON_CATALOG]. */
        val catalog = LESSON_CATALOG

        const val SAME_LESSON = 0
        const val PLUS_LESSON = 1
        const val COMBO_LESSON = 2

        /** Middle-right and bottom-centre: the two sides Same matches. */
        const val SAME_RIGHT_CELL = 5
        const val SAME_BELOW_CELL = 7

        /** Top-centre and middle-left: the two that add to eleven. */
        const val PLUS_ABOVE_CELL = 1
        const val PLUS_LEFT_CELL = 3

        const val EXPECTED_COMBO_CAPTURES = 3

        const val DODO_ID = 257
        val DODO_POWERS = listOf(4, 2, 3, 4)
    }
}
