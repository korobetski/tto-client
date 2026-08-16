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
            chained.position !in played.neighboursOfTheMove(CENTRE),
            "the third card is not one the placed card is even beside — that is the point of it",
        )
    }

    /**
     * Same Wall counts the wall as an ace, and is taught from a cell that has one.
     *
     * The lesson that cannot be taught from the centre — `touchesAceWall` needs a side facing a
     * wall, and the centre is the one cell with none. Asserted as the *cell* as well as the
     * capture, because a position moved to the middle would still build, still look right, and
     * silently stop demonstrating anything.
     */
    @Test
    fun sameWallNeedsAWallAndUsesOne() {
        val puzzle = TUTORIAL_PUZZLES[SAME_WALL_LESSON]
        val played = play(SAME_WALL_LESSON)

        assertEquals(TOP_CENTRE, puzzle.cell, "Same Wall cannot be taught from the centre")
        assertEquals(setOf(SAME_WALL_BELOW_CELL), played.capturedPositions())
        assertEquals(setOf(CaptureKind.SAME_WALL), played.kinds(), "the wall is what makes it")
    }

    /** Under Reverse the lower number is the stronger one, and the placement lives on that. */
    @Test
    fun reverseCapturesWithTheWeakerSide() {
        val played = play(REVERSE_LESSON)

        assertEquals(setOf(REVERSE_ABOVE_CELL), played.capturedPositions())
        assertEquals(setOf(CaptureKind.BASIC), played.kinds(), "Reverse works through raw power")
    }

    /** A 1 takes an A: Fallen Ace drops the 10 to 0 before anything else looks at it. */
    @Test
    fun fallenAceTurnsAnAceIntoTheWeakestSide() {
        val played = play(FALLEN_ACE_LESSON)

        assertEquals(setOf(FALLEN_ACE_ABOVE_CELL), played.capturedPositions())
        assertEquals(setOf(CaptureKind.BASIC), played.kinds())
    }

    /**
     * The pair, and the interaction that is the whole lesson.
     *
     * Neither rule alone captures here — [theRuleIsTheOnlyExplanation] is what asserts that, from
     * the puzzle's own baselines — so this only has to show that together they do, and that raw
     * power is *not* the baseline being claimed: an ace beats a 4 without any rule at all, which
     * is exactly why this lesson needed different baselines from the other six.
     */
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

    /**
     * The board rings **both** digits of each pair that decided a capture.
     *
     * A capture is a comparison between two facing sides, so lighting one of them would be half an
     * explanation — the point is that this 2 met that 2. Checked on Same, where the two pairs are
     * the two the lines name.
     */
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

    /**
     * **The chain is ringed against the card that took it**, not against the placement.
     *
     * The third card did not lose to the card the player put down — it lost to one of the two that
     * had just turned. So the pair to light is that one and this one, and the placed card is *not*
     * credited with a capture it never made. This is the assertion that would catch
     * [captureHighlights] falling back to "everything belongs to the placement", which looks
     * plausible on a board and is the wrong lesson.
     */
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

    /**
     * The chain turns **a generation later**, which is what makes a combo visible as a wave.
     *
     * Read off the engine's own `wave` rather than measured on screen: what the board does with the
     * number is a delay per generation ([COMBO_WAVE_MS]), and what this pins is that the number
     * separates the two events at all. A position where everything came back wave 0 would animate
     * as one flash and teach that a combo is simply a big capture.
     */
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

    /** Nothing is ringed before a card has been played. */
    @Test
    fun anUntouchedBoardRingsNothing() {
        val setup = assertNotNull(puzzleSetup(TUTORIAL_PUZZLES[SAME_LESSON], catalog))

        assertEquals(emptyMap(), captureHighlights(setup.state.board, setup.state.lastPlay))
        assertEquals(emptyMap(), captureWaves(setup.state.lastPlay))
    }

    /**
     * **The rule being taught is the only explanation** — every lesson, every baseline.
     *
     * Each position is replayed under the rule sets it claims to be dead under
     * ([TutorialPuzzle.baselines]). For six of the seven that is the empty set: if raw power still
     * turns a card, the player would have won that exchange anyway and the line explaining why is
     * false. For the pair lesson the baselines are Reverse and Fallen Ace *one at a time*, because
     * an ace captures plenty on raw power and the claim there is the interaction.
     *
     * Also asserts the baselines are not vacuous — a puzzle with an empty list would pass this
     * test by asking nothing, which is the failure mode of a check driven by its own data.
     */
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
        val puzzle = TUTORIAL_PUZZLES[lesson]
        val setup = assertNotNull(puzzleSetup(puzzle, catalog))
        return setup.state.play(setup.state.currentHand.single(), puzzle.cell)
    }

    private fun MatchState.capturedPositions(): Set<Int> =
        lastPlay?.captures.orEmpty().mapTo(mutableSetOf()) { it.position }

    private fun MatchState.kinds(): Set<CaptureKind> =
        lastPlay?.captures.orEmpty().mapTo(mutableSetOf()) { it.kind }

    /** The cells the placed card is actually beside. */
    private fun MatchState.neighboursOfTheMove(cell: Int): Set<Int> =
        Side.entries.mapNotNullTo(mutableSetOf()) { board.neighbour(cell, it) }

    private companion object {
        /** The lessons resolve their card ids through this; see [LESSON_CATALOG]. */
        val catalog = LESSON_CATALOG

        const val SAME_LESSON = 0
        const val PLUS_LESSON = 1
        const val COMBO_LESSON = 2
        const val SAME_WALL_LESSON = 3
        const val REVERSE_LESSON = 4
        const val FALLEN_ACE_LESSON = 5
        const val REVERSE_FALLEN_ACE_LESSON = 6

        /** Middle-right and bottom-centre: the two sides Same matches. */
        const val SAME_RIGHT_CELL = 5
        const val SAME_BELOW_CELL = 7

        /** Top-centre and middle-left: the two that add to eleven. */
        const val PLUS_ABOVE_CELL = 1
        const val PLUS_LEFT_CELL = 3

        const val EXPECTED_COMBO_CAPTURES = 3

        /** Bottom-left: the card the chain reaches, one generation after the placement. */
        const val COMBO_CHAINED_CELL = 6

        /** The centre, seen from cell 1: the card Same Wall takes. */
        const val SAME_WALL_BELOW_CELL = 4

        /** Top-centre, seen from the middle: the card Reverse and Fallen Ace each take. */
        const val REVERSE_ABOVE_CELL = 1
        const val FALLEN_ACE_ABOVE_CELL = 1

        /** Bottom-centre: the card the pair takes. */
        const val REVERSE_FALLEN_ACE_BELOW_CELL = 7

        const val DODO_ID = 257
        val DODO_POWERS = listOf(4, 2, 3, 4)
    }
}
