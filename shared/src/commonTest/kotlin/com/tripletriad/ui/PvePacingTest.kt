package com.tripletriad.ui

import com.tripletriad.model.Capture
import com.tripletriad.model.CaptureKind
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchState
import com.tripletriad.model.MatchView
import com.tripletriad.protocol.Placement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The refereed opponent waits for its own banners before replying.**
 *
 * `PveExchange` paces the walk with `quietMillis`, and the claim is the one the local screen has
 * always made: a reply that lands while the caption for the placement before it is still on screen
 * reads as a program answering, not as an opponent taking a turn. `settleMillis` alone measured the
 * board and not the captions, so a Same-capture turn was paced as though nothing had been
 * announced — which is the regression these numbers exist to hold shut.
 *
 * The figures are asserted against `MatchBanner`'s own durations rather than copied, so a caption
 * that is retimed moves the expectation with it instead of breaking this.
 */
class PvePacingTest {

    @Test
    fun anOrdinaryPlacementWaitsForTheTurnCaption() {
        val played = view.after(placement(CardColor.BLUE, blue.first(), position = 0), blue.first())

        // Nothing was captured, so the board is quiet in `LAND_MS` — but the turn is still being
        // announced, and that is the longer of the two.
        assertEquals(MatchBanner.RED_TURN.totalMillis.toLong(), quietMillis(played))
        assertTrue(
            quietMillis(played) > settleMillis(played.lastPlay),
            "the caption outlasts the board, so it must be what sets the pace",
        )
    }

    @Test
    fun aCaptureWaitsForItsOwnCaptionAsWellAsTheTurn() {
        val mine = blue.first()
        val opened = view.after(placement(CardColor.RED, red.first(), position = 0), red.first())
        val took = opened.after(
            placement(
                CardColor.BLUE,
                mine,
                position = 1,
                captures = listOf(Capture(0, CaptureKind.SAME, wave = 0)),
            ),
            mine,
        )

        val expected = MatchBanner.SAME.totalMillis + MatchBanner.RED_TURN.totalMillis
        assertEquals(expected.toLong(), quietMillis(took), "Same and the turn are both announced")
    }

    /**
     * A combo is the case the old pacing got most wrong: four seconds of caption, answered in one.
     */
    @Test
    fun aComboIsGivenTheWholeChainToPlayOut() {
        val mine = blue.first()
        val opened = view.after(placement(CardColor.RED, red.first(), position = 0), red.first())
        val chained = opened.after(
            placement(
                CardColor.BLUE,
                mine,
                position = 1,
                captures = listOf(
                    Capture(0, CaptureKind.SAME, wave = 0),
                    Capture(2, CaptureKind.COMBO, wave = 1),
                ),
            ),
            mine,
        )

        val quiet = quietMillis(chained)
        assertEquals(
            (
                MatchBanner.SAME.totalMillis + MatchBanner.COMBO.totalMillis +
                    MatchBanner.RED_TURN.totalMillis
                ).toLong(),
            quiet,
        )
        assertTrue(
            quiet >= COMBO_FLOOR_MS,
            "a combo used to be answered in 1.4s of the 4.4s it takes to announce: $quiet",
        )
    }

    /** Never shorter than the board itself: the captions are the floor, not the ceiling. */
    @Test
    fun theBoardStillSetsThePaceWhenNothingIsAnnounced() {
        assertTrue(quietMillis(view) >= settleMillis(view.lastPlay))
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun card(number: Int, power: Int) = Card(
        id = Card.idFor(block = 1, number = number),
        nameKey = "STR_TEST_$number",
        name = "Test $number",
        top = power,
        right = power,
        bottom = power,
        left = power,
        rarity = 1,
    )

    private fun hand(from: Int, power: Int) = (from until from + HAND_SIZE).map { card(it, power) }

    private val blue = hand(from = 1, power = 8)
    private val red = hand(from = 11, power = 2)

    private val state = MatchState.start(blueHand = blue, redHand = red, first = CardColor.BLUE)

    private val view = MatchView.of(state, CardColor.BLUE, HandVisibility.HIDDEN)

    private fun placement(
        player: CardColor,
        card: Card,
        position: Int,
        captures: List<Capture> = emptyList(),
    ) = Placement(
        player = player,
        cardId = card.id,
        position = position,
        captures = captures,
        handIndex = 0,
    )

    private companion object {
        /** What the old `settleMillis`-only pacing gave a combo, and the bar it has to clear. */
        const val COMBO_FLOOR_MS = 2_000L
    }
}
