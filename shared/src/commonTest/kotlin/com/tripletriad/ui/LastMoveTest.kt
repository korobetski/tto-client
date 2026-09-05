package com.tripletriad.ui

import com.tripletriad.model.Board
import com.tripletriad.model.Capture
import com.tripletriad.model.CaptureKind
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchView
import com.tripletriad.model.PlayResult
import com.tripletriad.model.TurnOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two decisions behind "the last move should feel like one": when the board is knocked, and how
 * long it is left alone before the result panel opens.
 *
 * Both are plain functions on a view, which is why they are testable at all — the animation and the
 * delay they drive are not, and are not tested here.
 */
class LastMoveTest {
    @Test
    fun anOrdinaryTradeDoesNotKnockTheBoard() {
        // One card taken is every other turn in the game. A board that shook for those would be a
        // board that shook.
        assertNull(view(captures = 1).joltAt())
        assertNull(view(captures = 2).joltAt())
    }

    @Test
    fun aPlacementWithNothingToShowDoesNotKnockEither() {
        assertNull(view(captures = 0).joltAt())
        assertNull(view(lastPlay = null).joltAt())
    }

    @Test
    fun threeCardsIsWhereItStartsBeingATurnNobodyExpected() {
        assertEquals(6, view(captures = JOLT_CAPTURES, placement = 6).joltAt())
        assertEquals(7, view(captures = 4, placement = 7).joltAt())
    }

    @Test
    fun theKeyIsThePlacementSoTwoBigTurnsInARowBothLand() {
        // The failure this guards: keyed on the count, two consecutive three-card placements share
        // a key of 3 and the second passes in silence.
        val first = view(captures = 3, placement = 5).joltAt()
        val second = view(captures = 3, placement = 6).joltAt()

        assertTrue(first != second, "two big turns in a row produced the same key")
    }

    @Test
    fun anUnfinishedBoardGetsNoExtraBeat() {
        val open = view(captures = 1, placement = 5)

        assertEquals(quietMillis(open), settleFloor(open), "a mid-match turn was slowed")
    }

    @Test
    fun theBoardIsLeftAloneLongerOnceItIsFull() {
        val last = view(captures = 1, placement = 9)

        assertTrue(
            quietMillis(last) > settleFloor(last),
            "the ninth card is watched for no longer than the second",
        )
    }

    /** What the pause would be with no ending bonus: the placement's own animation. */
    private fun settleFloor(view: MatchView): Long = maxOf(
        settleMillis(view.lastPlay),
        MatchBanner.afterPlacement(view).sumOf { it.totalMillis }.toLong(),
    )

    private fun view(
        captures: Int = 0,
        placement: Int = 4,
        lastPlay: PlayResult? = play(captures),
    ) = MatchView(
        side = CardColor.BLUE,
        rules = GameRules(),
        board = Board(),
        ownHand = emptyList(),
        opponentHand = emptyList(),
        order = TurnOrder(CardColor.BLUE),
        placement = placement,
        lastPlay = lastPlay,
    )

    private fun play(captures: Int) = PlayResult(
        player = CardColor.BLUE,
        card = card,
        position = 4,
        captures = List(captures) { Capture(it, CaptureKind.BASIC, wave = 0) },
        handIndex = 0,
    )

    private val card = Card(
        id = Card.idFor(1, 1),
        nameKey = "STR_TEST_1",
        name = "Test",
        top = 5,
        right = 5,
        bottom = 5,
        left = 5,
        rarity = 1,
    )
}
