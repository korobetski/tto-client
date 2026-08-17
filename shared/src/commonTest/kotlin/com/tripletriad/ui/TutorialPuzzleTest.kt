package com.tripletriad.ui

import com.tripletriad.model.CaptureKind
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchState
import com.tripletriad.model.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TutorialPuzzleTest {

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
                puzzle.cell,
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
            chained.position !in played.neighboursOfTheMove(CENTRE),
            "the third card is not one the placed card is even beside — that is the point of it",
        )
    }

    @Test
    fun sameWallNeedsAWallAndUsesOne() {
        val puzzle = TUTORIAL_PUZZLES[SAME_WALL_LESSON]
        val played = play(SAME_WALL_LESSON)

        assertEquals(TOP_CENTRE, puzzle.cell, "Same Wall cannot be taught from the centre")
        assertEquals(setOf(SAME_WALL_BELOW_CELL), played.capturedPositions())
        assertEquals(setOf(CaptureKind.SAME_WALL), played.kinds(), "the wall is what makes it")
    }

    @Test
    fun reverseCapturesWithTheWeakerSide() {
        val played = play(REVERSE_LESSON)

        assertEquals(setOf(REVERSE_ABOVE_CELL), played.capturedPositions())
        assertEquals(setOf(CaptureKind.BASIC), played.kinds(), "Reverse works through raw power")
    }

    @Test
    fun fallenAceTurnsAnAceIntoTheWeakestSide() {
        val played = play(FALLEN_ACE_LESSON)

        assertEquals(setOf(FALLEN_ACE_ABOVE_CELL), played.capturedPositions())
        assertEquals(setOf(CaptureKind.BASIC), played.kinds())
    }

    @Test
    fun reverseAndFallenAceTogetherMakeAnAceUnbeatable() {
        val puzzle = TUTORIAL_PUZZLES[REVERSE_FALLEN_ACE_LESSON]
        val played = play(REVERSE_FALLEN_ACE_LESSON)

        assertEquals(setOf(REVERSE_FALLEN_ACE_BELOW_CELL), played.capturedPositions())
        assertEquals(setOf(CaptureKind.BASIC), played.kinds())
        assertEquals(
            listOf(GameRules(reverse = true), GameRules(fallenAce = true)),
            puzzle.baselines,
            "the pair is dead one rule at a time, which is the claim — not that raw power is",
        )
    }

    @Test
    fun theDecidingDigitsAreRingedOnBothCards() {
        val played = play(SAME_LESSON)

        assertEquals(
            mapOf(
                CENTRE to setOf(Side.RIGHT, Side.BOTTOM),
                SAME_RIGHT_CELL to setOf(Side.LEFT),
                SAME_BELOW_CELL to setOf(Side.TOP),
            ),
            captureHighlights(played.board, played.lastPlay),
            "the placed card lights what it attacked with, each captured card what lost",
        )
    }

    @Test
    fun theChainIsRingedAgainstTheCardThatTookIt() {
        val played = play(COMBO_LESSON)
        val lit = captureHighlights(played.board, played.lastPlay)

        assertEquals(
            setOf(Side.RIGHT, Side.BOTTOM),
            lit[CENTRE],
            "the placed card lights the two sides it attacked with, and no third",
        )
        assertEquals(
            setOf(Side.RIGHT),
            lit[COMBO_CHAINED_CELL],
            "the chained card lights the side facing the card that took it",
        )
        assertEquals(
            setOf(Side.TOP, Side.LEFT),
            lit[SAME_BELOW_CELL],
            "and that card lights twice: the side it lost on, and the side it then won with",
        )
    }

    @Test
    fun theChainIsALaterGenerationThanThePlacement() {
        val waves = captureWaves(play(COMBO_LESSON).lastPlay)

        assertEquals(0, waves[SAME_RIGHT_CELL], "the placement's own captures are the first wave")
        assertEquals(0, waves[SAME_BELOW_CELL])
        assertEquals(1, waves[COMBO_CHAINED_CELL], "and the chain is the one behind it")
        assertTrue(
            waveDelayMillis(play(COMBO_LESSON).lastPlay) > 0,
            "so the callers that have to wait for the cascade actually wait",
        )
        assertEquals(
            0L,
            waveDelayMillis(play(SAME_LESSON).lastPlay),
            "while an ordinary capture is paced exactly as it was",
        )
    }

    @Test
    fun anUntouchedBoardRingsNothing() {
        val setup = assertNotNull(puzzleSetup(TUTORIAL_PUZZLES[SAME_LESSON], catalog))

        assertEquals(emptyMap(), captureHighlights(setup.state.board, setup.state.lastPlay))
        assertEquals(emptyMap(), captureWaves(setup.state.lastPlay))
    }

    @Test
    fun elementalIsTheTilesDoing() {
        val puzzle = TUTORIAL_PUZZLES[ELEMENTAL_LESSON]
        val played = play(ELEMENTAL_LESSON)

        assertEquals(mapOf(CENTRE to CardType.LIGHTNING), puzzle.elements, "one tile, named")
        assertEquals(setOf(ELEMENTAL_ABOVE_CELL), played.capturedPositions())
        assertEquals(setOf(CaptureKind.BASIC), played.kinds(), "Elemental works through power")

        val setup = assertNotNull(puzzleSetup(puzzle.copy(elements = emptyMap()), catalog))
        assertEquals(
            emptyList(),
            setup.state.play(setup.state.currentHand.single(), puzzle.cell)
                .lastPlay?.captures.orEmpty(),
            "with the rule on but no element under the card, the same placement takes nothing",
        )
    }

    @Test
    fun theRuleIsTheOnlyExplanation() {
        for ((index, puzzle) in TUTORIAL_PUZZLES.withIndex()) {
            assertTrue(puzzle.baselines.isNotEmpty(), "lesson $index proves nothing about itself")

            for (baseline in puzzle.baselines) {
                val setup = assertNotNull(puzzleSetup(puzzle, catalog))
                val plain = setup.state.copy(rules = baseline)

                assertEquals(
                    emptyList(),
                    plain.play(plain.currentHand.single(), puzzle.cell)
                        .lastPlay?.captures.orEmpty(),
                    "lesson $index already captures under $baseline, so it demonstrates nothing",
                )
            }
        }
    }

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

    @Test
    fun theNumbersTheLinesNameAreStillOnTheCard() {
        val dodo = assertNotNull(catalog[DODO_ID], "the card most lessons are played with")
        val gayla = assertNotNull(catalog[GAYLA_ID], "the Elemental lesson's card")

        assertEquals(DODO_POWERS, listOf(dodo.top, dodo.right, dodo.bottom, dodo.left), "Dodo")
        assertEquals(
            GAYLA_POWERS,
            listOf(gayla.top, gayla.right, gayla.bottom, gayla.left),
            "Gayla",
        )
        assertEquals(
            CardType.LIGHTNING,
            gayla.type,
            "the Elemental lesson stands its card on its own element; the type is the lesson",
        )
    }

    private fun play(lesson: Int): MatchState {
        val puzzle = TUTORIAL_PUZZLES[lesson]
        val setup = assertNotNull(puzzleSetup(puzzle, catalog))
        return setup.state.play(setup.state.currentHand.single(), puzzle.cell)
    }

    private fun MatchState.capturedPositions(): Set<Int> =
        lastPlay?.captures.orEmpty().mapTo(mutableSetOf()) { it.position }

    private fun MatchState.kinds(): Set<CaptureKind> =
        lastPlay?.captures.orEmpty().mapTo(mutableSetOf()) { it.kind }

    private fun MatchState.neighboursOfTheMove(cell: Int): Set<Int> =
        Side.entries.mapNotNullTo(mutableSetOf()) { board.neighbour(cell, it) }

    private companion object {
        val catalog = LESSON_CATALOG

        const val SAME_LESSON = 0
        const val PLUS_LESSON = 1
        const val COMBO_LESSON = 2
        const val SAME_WALL_LESSON = 3
        const val REVERSE_LESSON = 4
        const val FALLEN_ACE_LESSON = 5
        const val REVERSE_FALLEN_ACE_LESSON = 6
        const val ELEMENTAL_LESSON = 7

        const val SAME_RIGHT_CELL = 5
        const val SAME_BELOW_CELL = 7

        const val PLUS_ABOVE_CELL = 1
        const val PLUS_LEFT_CELL = 3

        const val EXPECTED_COMBO_CAPTURES = 3

        const val COMBO_CHAINED_CELL = 6

        const val SAME_WALL_BELOW_CELL = 4

        const val REVERSE_ABOVE_CELL = 1
        const val FALLEN_ACE_ABOVE_CELL = 1

        const val REVERSE_FALLEN_ACE_BELOW_CELL = 7

        const val ELEMENTAL_ABOVE_CELL = 1

        const val DODO_ID = 257
        val DODO_POWERS = listOf(4, 2, 3, 4)

        const val GAYLA_ID = 518
        val GAYLA_POWERS = listOf(2, 1, 4, 4)
    }
}
