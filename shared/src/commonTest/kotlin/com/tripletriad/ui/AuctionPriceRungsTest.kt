package com.tripletriad.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The prices the consignment desk offers as chips.
 *
 * Four numbers derived from two: the shop price a card is worth, and the ceiling the house will
 * carry. Everything here is about the *edges* — a ceiling low enough to swallow a rung, a card with
 * no room at all — because the middle of the range is the case that was never going to be wrong.
 * `AuctionUiTest` is where a chip is pressed.
 */
class AuctionPriceRungsTest {

    @Test
    fun theRungsClimbFromTheShopPriceToTheCeiling() {
        assertEquals(listOf(100, 200, 500, 2_000), priceRungs(floor = 100, ceiling = 2_000))
    }

    @Test
    fun aRungPastTheCeilingIsNotOffered() {
        // Twice the floor still fits; five times it does not, and the ceiling takes its place.
        assertEquals(listOf(100, 200, 300), priceRungs(floor = 100, ceiling = 300))
    }

    @Test
    fun aCeilingThatIsAlreadyARungIsNotOfferedTwice() {
        assertEquals(listOf(100, 200, 500), priceRungs(floor = 100, ceiling = 500))
    }

    /**
     * A card with no room to price is one rung, which the desk draws as no chips at all.
     *
     * Nothing in the shipped catalogue is priced this way — the ceiling is twenty times the card's
     * worth — but the two numbers arrive from `AuctionPolicy`, which is tuned on the server, and a
     * row of four identical chips is what a tuning to 1 would otherwise produce.
     */
    @Test
    fun aCardWithNoRoomToPriceOffersOneRung() {
        assertEquals(listOf(100), priceRungs(floor = 100, ceiling = 100))
    }
}
