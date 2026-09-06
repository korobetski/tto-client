package com.tripletriad.ui

import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.protocol.AuctionLot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the sale room is narrowed and ordered, and how a deadline becomes a shape.
 *
 * Pure functions, held here for the reason `AuctionTextTest` gives: a room in the wrong order
 * draws exactly as well as one in the right order, so no test that mounts the screen and looks for
 * rows can tell them apart. The Compose half — that the chips are wired to these, and that the
 * ring is on the row rather than the sentence — is `AuctionUiTest`'s.
 */
class AuctionRoomTest {

    // ---- The search -------------------------------------------------------

    @Test
    fun aSearchKeepsTheLotsWhoseCardIsNamedThat() {
        val kept = shown(query = "tonb")

        assertEquals(listOf(TONBERRY), kept.map { it.cardId }, "the search matched the wrong lots")
    }

    /** Nobody types the capitals, and nobody should have to. */
    @Test
    fun aSearchIgnoresCaseAndTheSpaceAroundIt() {
        assertEquals(listOf(TONBERRY), shown(query = "  TONBERRY ").map { it.cardId })
    }

    @Test
    fun anEmptySearchIsNotASearch() {
        assertEquals(THREE_LOTS, shown(query = "").size)
    }

    /**
     * A lot for a card this catalogue has never heard of is searchable by nothing.
     *
     * It still shows in an unsearched room — the row draws its price and its countdown without a
     * name — but a query it cannot be matched against must not match *everything*, which is what
     * an empty name and a `contains` would do together if the empty query were not caught first.
     */
    @Test
    fun aLotWhoseCardIsUnknownIsNotMatchedByEveryQuery() {
        val strange = lot(id = "x", cardId = UNKNOWN, endsAt = 1L, price = 1)

        assertTrue(roomLots(listOf(strange), "a", AuctionSort.ENDING, ::name, { true }).isEmpty())
        assertEquals(1, roomLots(listOf(strange), "", AuctionSort.ENDING, ::name, { true }).size)
    }

    // ---- The orders -------------------------------------------------------

    @Test
    fun theRoomOpensOnWhatIsAboutToGo() {
        assertEquals(
            listOf("soon", "later", "last"),
            shown(sort = AuctionSort.ENDING).map { it.id },
        )
    }

    @Test
    fun thePriceOrderIsWhatAPurseCanReachFirst() {
        assertEquals(listOf("last", "soon", "later"), shown(sort = AuctionSort.PRICE).map { it.id })
    }

    /**
     * Two lots at one price keep the order the room is really about.
     *
     * Without the tie-break they would swap places between two polls of a list that redraws every
     * five seconds, which is a row moving under the finger about to tap it.
     */
    @Test
    fun twoLotsAtTheSamePriceAreStillSortedByTheirDeadline() {
        val tied = listOf(
            lot(id = "b", cardId = TONBERRY, endsAt = 200L, price = 500),
            lot(id = "a", cardId = TONBERRY, endsAt = 100L, price = 500),
        )

        val order = roomLots(tied, "", AuctionSort.PRICE, ::name, { true }).map { it.id }

        assertEquals(listOf("a", "b"), order)
    }

    /** The chip narrows rather than only sorting — see [AuctionSort.MISSING]. */
    @Test
    fun theMissingChipHidesTheCardsAlreadyOwned() {
        val kept = shown(sort = AuctionSort.MISSING, missing = { it.cardId == TONBERRY })

        assertEquals(listOf(TONBERRY), kept.map { it.cardId })
    }

    @Test
    fun theMissingChipStillReadsDeadlineFirst() {
        val kept = shown(sort = AuctionSort.MISSING, missing = { true })

        assertEquals(listOf("soon", "later", "last"), kept.map { it.id })
    }

    /** The two controls are one question: a search inside a narrowed room stays narrowed. */
    @Test
    fun theSearchAndTheChipBothHold() {
        val kept = shown(
            query = "cactuar",
            sort = AuctionSort.MISSING,
            missing = { it.cardId == TONBERRY },
        )

        assertTrue(kept.isEmpty(), "a card that is owned came back because it was searched for")
    }

    // ---- The ring ---------------------------------------------------------

    @Test
    fun aClosedRingIsALotWithNothingLeft() {
        assertEquals(1f, ringFill(0L))
        assertEquals(1f, ringFill(-A_MINUTE))
    }

    /**
     * The curve spends its resolution where the decisions are.
     *
     * The three assertions are the whole design: a lot a day out is an empty ring, the last two
     * minutes — the window in which a bid pushes the deadline back — are the last tenth of it, and
     * an hour out sits near the middle. A linear fill would spend 0.1% of the circle on those two
     * minutes and leave every lot in its final hour looking like every other.
     */
    @Test
    fun theRingSpendsItsResolutionOnTheEndOfTheSale() {
        assertTrue(ringFill(URGENT_MILLIS) > 0.8f, "the last two minutes read as empty")
        assertTrue(ringFill(A_DAY) < 0.05f, "a lot a day out reads as urgent")
        // The middle of the ring is the middle of the *attention*, not of the day: an hour out is
        // about half a circle. A linear fill would put an hour at 96% and pass both lines above
        // while giving the final hour — the only hour anybody watches — no resolution at all.
        assertTrue(ringFill(AN_HOUR) in 0.3f..0.7f, "an hour out drew ${ringFill(AN_HOUR)}")
    }

    @Test
    fun theRingOnlyEverFillsAsTimePasses() {
        val marks = listOf(A_DAY, 6 * AN_HOUR, AN_HOUR, 10 * A_MINUTE, A_MINUTE, A_SECOND)

        for ((longer, shorter) in marks.zipWithNext()) {
            assertTrue(
                ringFill(longer) < ringFill(shorter),
                "$longer ms left drew a fuller ring than $shorter",
            )
        }
    }

    /** More time left than any lot may be listed for is still a ring, not a negative one. */
    @Test
    fun aLotBeyondTheLongestListingIsAnEmptyRingRatherThanAnError() {
        assertEquals(0f, ringFill(3 * A_DAY))
    }

    // ---- The number inside it ---------------------------------------------

    @Test
    fun theRingCarriesOneUnitAndNotTwo() {
        assertEquals("2h", shortCountdown(strings, 2 * AN_HOUR + 5 * A_MINUTE))
        assertEquals("5m", shortCountdown(strings, 5 * A_MINUTE + 30 * A_SECOND))
        assertEquals("30s", shortCountdown(strings, 30 * A_SECOND))
        assertEquals("over", shortCountdown(strings, 0L))
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun shown(
        query: String = "",
        sort: AuctionSort = AuctionSort.ENDING,
        missing: (AuctionLot) -> Boolean = { true },
    ) = roomLots(lots, query, sort, ::name, missing)

    private fun name(lot: AuctionLot): String = names[lot.cardId].orEmpty()

    private fun lot(id: String, cardId: Int, endsAt: Long, price: Int) = AuctionLot(
        id = id,
        cardId = cardId,
        startPrice = price,
        endsAt = endsAt,
    )

    private val lots = listOf(
        lot(id = "later", cardId = CACTUAR, endsAt = 2_000L, price = 900),
        lot(id = "soon", cardId = TONBERRY, endsAt = 1_000L, price = 500),
        lot(id = "last", cardId = CACTUAR, endsAt = 3_000L, price = 100),
    )

    private val names = mapOf(TONBERRY to "Tonberry", CACTUAR to "Cactuar")

    private val strings = Strings(
        AppLocale.EN_US,
        mapOf(
            StringKeys.AUCTION_ENDED to "over",
            StringKeys.AUCTION_HOURS to "{0}h",
            StringKeys.AUCTION_LEFT_MINUTES to "{0}m",
            StringKeys.AUCTION_LEFT_SECONDS to "{0}s",
        ),
        emptyMap(),
    )

    private companion object {
        const val TONBERRY = 1
        const val CACTUAR = 2
        const val UNKNOWN = 3
        const val THREE_LOTS = 3
        const val A_SECOND = 1_000L
        const val A_MINUTE = 60_000L
        const val AN_HOUR = 60 * A_MINUTE
        const val A_DAY = 24 * AN_HOUR
    }
}
