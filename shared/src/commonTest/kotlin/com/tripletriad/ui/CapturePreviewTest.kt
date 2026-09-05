package com.tripletriad.ui

import com.tripletriad.model.Board
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchView
import com.tripletriad.model.TurnOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the board offers to tell a player before they commit a card.
 *
 * The rendering is not here — `MatchBoardTest` drives that. What is here is the answer itself,
 * which has to be the referee's answer and not a second opinion: every case below is one the engine
 * decides, and the point of the function is that it asks rather than re-derives.
 */
class CapturePreviewTest {
    @Test
    fun nothingAimedIsNothingToSay() {
        assertEquals(emptySet(), capturePreview(view(), card = strong, at = null))
        assertEquals(emptySet(), capturePreview(view(), card = null, at = CENTRE))
    }

    @Test
    fun aTakenCellIsNotAMove() {
        val board = Board().place(CENTRE, weak.copy(owner = CardColor.RED), CardColor.RED)

        assertEquals(emptySet(), capturePreview(view(board), strong, CENTRE))
    }

    @Test
    fun theOpponentsTurnPreviewsNothing() {
        // The referee leaves `playableHandIndices` empty while it is telling the last exchange, and
        // the hand greys out for exactly that stretch. Ringing cells under a hand that cannot be
        // played would be the board offering a move it is about to refuse.
        val board = Board().place(TOP_MID, weak.copy(owner = CardColor.RED), CardColor.RED)

        assertEquals(
            emptySet(),
            capturePreview(view(board).copy(order = TurnOrder(CardColor.BLUE)), strong, CENTRE),
        )
    }

    @Test
    fun theCellThatWouldFlipIsNamed() {
        // A 9 on top against a 2 facing down: the ordinary comparison, and the whole point.
        val board = Board().place(TOP_MID, weak.copy(owner = CardColor.RED), CardColor.RED)

        assertEquals(setOf(TOP_MID), capturePreview(view(board), strong, CENTRE))
    }

    @Test
    fun aPlacementThatTakesNothingRingsNothing() {
        val board = Board().place(TOP_MID, strong.copy(owner = CardColor.RED), CardColor.RED)

        assertEquals(emptySet(), capturePreview(view(board), weak, CENTRE))
    }

    @Test
    fun theOwnCardsAreNeverRinged() {
        val board = Board().place(TOP_MID, weak, CardColor.BLUE)

        assertTrue(capturePreview(view(board), strong, CENTRE).isEmpty(), "it ringed its own card")
    }

    @Test
    fun itAnswersUnderWhateverRulesTheBoardIsPlayingUnder() {
        // Reverse, and the preview has to turn over with it — which it does because it asks the
        // engine rather than comparing two numbers itself. The weak card now takes the strong one.
        val board = Board().place(TOP_MID, strong.copy(owner = CardColor.RED), CardColor.RED)
        val reversed = view(board).copy(rules = GameRules(reverse = true))

        assertEquals(setOf(TOP_MID), capturePreview(reversed, weak, CENTRE))
    }

    @Test
    fun aChainIsPreviewedWhole() {
        // Same fires on the two 5s, and the card it flips takes a third on the way. A preview that
        // stopped at the direct captures would under-promise on the one placement worth ringing.
        val same = Card(
            id = Card.idFor(1, 90),
            nameKey = "STR_TEST_90",
            name = "Same",
            top = 5,
            right = 5,
            bottom = 5,
            left = 5,
            rarity = 1,
        )
        val board = Board()
            .place(TOP_MID, edged(91, bottom = 5, left = 9), CardColor.RED)
            .place(MID_LEFT, edged(92, right = 5), CardColor.RED)
            .place(TOP_LEFT, edged(93, right = 2, bottom = 2), CardColor.RED)

        val preview = capturePreview(view(board), same, CENTRE)

        assertEquals(setOf(TOP_MID, MID_LEFT, TOP_LEFT), preview, "the chain was cut short")
    }

    /**
     * A board it is the player's turn on, whatever is already down.
     *
     * The first player is chosen from the count rather than fixed, because `TurnOrder` alternates
     * from whoever opened: with three cards down, blue is on move only if red opened. A fixture
     * that pinned the opener would be testing a different question on every board size — and
     * `theOpponentsTurnPreviewsNothing` is the one case that wants the other answer, which it asks
     * for explicitly.
     */
    private fun view(board: Board = Board()): MatchView {
        val filled = board.cells.count { it != null }
        return MatchView(
            side = CardColor.BLUE,
            rules = GameRules(same = true),
            board = board,
            ownHand = listOf(strong),
            opponentHand = emptyList(),
            order = TurnOrder(if (filled % 2 == 0) CardColor.BLUE else CardColor.RED),
            placement = filled,
            playableHandIndices = listOf(0),
        )
    }

    private fun edged(number: Int, top: Int = 1, right: Int = 1, bottom: Int = 1, left: Int = 1) =
        Card(
            id = Card.idFor(1, number),
            nameKey = "STR_TEST_$number",
            name = "Test $number",
            top = top,
            right = right,
            bottom = bottom,
            left = left,
            rarity = 1,
        )

    private val strong = Card(
        id = Card.idFor(1, 1),
        nameKey = "STR_TEST_1",
        name = "Strong",
        top = 9,
        right = 9,
        bottom = 9,
        left = 9,
        rarity = 1,
    )

    private val weak = Card(
        id = Card.idFor(1, 2),
        nameKey = "STR_TEST_2",
        name = "Weak",
        top = 2,
        right = 2,
        bottom = 2,
        left = 2,
        rarity = 1,
    )

    private companion object {
        const val TOP_LEFT = 0
        const val TOP_MID = 1
        const val MID_LEFT = 3
        const val CENTRE = 4
    }
}
