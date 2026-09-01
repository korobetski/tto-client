package com.tripletriad.ui

import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.protocol.AuctionLot
import com.tripletriad.protocol.AuctionRefusal
import com.tripletriad.protocol.AuctionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sentences the house is read in.
 *
 * These are the only part of the auction that is a pure function of a value, so they are the part
 * worth asserting without a Compose rule — and the part where a mistake is invisible to every other
 * test in the tree, because a screen that writes the wrong sentence draws exactly as well as one
 * that writes the right one.
 *
 * The bundle here maps each refusal key to a marker rather than to real prose. [Strings.get] falls
 * back to *the key itself* for anything it does not hold, so a branch that reached for a key this
 * file does not declare comes back as `APP_AUCTION_...` and fails on the marker; the real
 * translations are `StringsBundleTest`'s business, and asserting them twice would only mean
 * editing two files to change a comma.
 */
class AuctionTextTest {

    // ---- Refusals ---------------------------------------------------------

    /**
     * Thirteen refusals, thirteen different things to do next.
     *
     * The `when` in `refusalText` is exhaustive, so the compiler already guarantees every refusal
     * is *answered*. What it cannot see is a copy-paste: two branches reaching for the same key
     * tells a player who cannot afford a bid to go and close a lot instead. Distinctness is the
     * assertion; being written from a declared key is what makes distinctness mean anything.
     */
    @Test
    fun everyRefusalIsWrittenFromItsOwnKey() {
        val said = AuctionRefusal.entries.associateWith { refusalText(strings, it) }

        for ((refusal, sentence) in said) {
            assertTrue(sentence.startsWith(MARKER), "$refusal is written from an undeclared key")
        }
        assertEquals(
            AuctionRefusal.entries.size,
            said.values.toSet().size,
            "two refusals share a sentence",
        )
    }

    /** The list above is only worth anything while it is the whole enum. */
    @Test
    fun theKeysCheckedHereAreAsManyAsTheHouseHasRefusals() {
        assertEquals(AuctionRefusal.entries.size, refusalKeys.size)
    }

    // ---- How a lot ended --------------------------------------------------

    @Test
    fun anOpenLotSaysNothingBecauseItsLineIsItsCountdown() {
        assertNull(statusText(strings, lot(AuctionStatus.OPEN)))
    }

    @Test
    fun eachWayALotCanEndReadsDifferently() {
        assertEquals("awaiting", statusText(strings, lot(AuctionStatus.AWAITING_SELLER)))
        assertEquals("unsold", statusText(strings, lot(AuctionStatus.UNSOLD)))
        assertEquals("cancelled", statusText(strings, lot(AuctionStatus.CANCELLED)))
    }

    @Test
    fun aSoldLotSaysWhatItWentFor() {
        assertEquals(
            "sold for 900",
            statusText(strings, lot(AuctionStatus.SOLD, topBid = 800, soldFor = 900)),
        )
    }

    /**
     * A sold lot with no settled figure on it still has to name a price.
     *
     * `soldFor` is written when the house settles; a lot read back before that — or by a client one
     * release behind the field — falls through to what it stands at, which is the same number in
     * every case but the one where the seller waived a reserve.
     */
    @Test
    fun aSoldLotWithNoSettledFigureFallsBackToWhatItStoodAt() {
        assertEquals(
            "sold for 800",
            statusText(strings, lot(AuctionStatus.SOLD, topBid = 800)),
        )
    }

    // ---- The countdown ----------------------------------------------------

    @Test
    fun hoursCarryTheMinutesLeftOver() {
        assertEquals("2h 5m", countdownText(strings, 2 * AN_HOUR + 5 * A_MINUTE))
    }

    @Test
    fun anHourExactlyHasNoMinutesOnIt() {
        assertEquals("1h 0m", countdownText(strings, AN_HOUR))
    }

    @Test
    fun secondsAppearOnlyUnderAMinute() {
        assertEquals("1m", countdownText(strings, A_MINUTE))
        assertEquals("59s", countdownText(strings, 59 * A_SECOND))
    }

    /**
     * Under-promising is the only safe direction when somebody is spending money on it.
     *
     * A lot with 119 seconds on it reads "1m", not "2m": a bidder who reads two minutes and comes
     * back in ninety seconds finds the lot closed. `PvpScreen.minutesLeft` rounds the other way,
     * and the difference is exactly that nothing is at stake in a table that expires.
     */
    @Test
    fun aCountdownRoundsDownAndNotUp() {
        assertEquals("1m", countdownText(strings, 2 * A_MINUTE - 1))
        assertEquals("0s", countdownText(strings, A_SECOND - 1))
    }

    @Test
    fun aLotWithNoTimeLeftIsOverRatherThanZeroSeconds() {
        assertEquals("over", countdownText(strings, 0L))
        assertEquals("over", countdownText(strings, -A_MINUTE))
    }

    // ---- Prices -----------------------------------------------------------

    @Test
    fun aPriceCarriesTheGamesOwnCoin() {
        assertEquals("100 MGP", priceText(strings, 100))
    }

    /**
     * The desk's numbers are grouped, as the shop's and the board's are.
     *
     * The escape rather than the character itself: this assertion is about *which* space, and a
     * narrow no-break space pasted into a source file is indistinguishable from the ordinary one
     * that would make it pass for the wrong reason. See `grouped`.
     */
    @Test
    fun aPriceOverAThousandIsGroupedTheWayEveryOtherPriceIs() {
        assertEquals("1\u202f000 MGP", priceText(strings, 1_000))
        assertEquals("1\u202f234\u202f567 MGP", priceText(strings, 1_234_567))
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun lot(
        status: AuctionStatus,
        topBid: Int? = null,
        soldFor: Int? = null,
    ) = AuctionLot(
        id = "a-lot",
        cardId = 1,
        startPrice = 100,
        endsAt = 0L,
        status = status,
        topBid = topBid,
        soldFor = soldFor,
    )

    private val refusalKeys = listOf(
        StringKeys.AUCTION_REFUSED_LOCKED,
        StringKeys.AUCTION_REFUSED_LOT_GONE,
        StringKeys.AUCTION_REFUSED_NOT_YOURS,
        StringKeys.AUCTION_REFUSED_TOO_MANY_LOTS,
        StringKeys.AUCTION_REFUSED_BELOW_FLOOR,
        StringKeys.AUCTION_REFUSED_RESERVE_BELOW_START,
        StringKeys.AUCTION_REFUSED_ABOVE_CEILING,
        StringKeys.AUCTION_REFUSED_CANNOT_AFFORD,
        StringKeys.AUCTION_REFUSED_BID_TOO_LOW,
        StringKeys.AUCTION_REFUSED_YOUR_OWN_LOT,
        StringKeys.AUCTION_REFUSED_ALREADY_LEADING,
        StringKeys.AUCTION_REFUSED_ALREADY_BID,
        StringKeys.AUCTION_REFUSED_NOT_YOUR_DECISION,
    )

    private val strings = Strings(
        AppLocale.EN_US,
        refusalKeys.withIndex().associate { (index, key) -> key to "$MARKER$index" } + mapOf(
            StringKeys.AUCTION_ENDED to "over",
            StringKeys.AUCTION_LEFT_HOURS to "{0}h {1}m",
            StringKeys.AUCTION_LEFT_MINUTES to "{0}m",
            StringKeys.AUCTION_LEFT_SECONDS to "{0}s",
            StringKeys.AUCTION_STATUS_AWAITING to "awaiting",
            StringKeys.AUCTION_STATUS_SOLD to "sold for {0}",
            StringKeys.AUCTION_STATUS_UNSOLD to "unsold",
            StringKeys.AUCTION_STATUS_CANCELLED to "cancelled",
            StringKeys.MGP to "MGP",
        ),
        emptyMap(),
    )

    private companion object {
        const val MARKER = "why-"

        const val A_SECOND = 1_000L
        const val A_MINUTE = 60 * A_SECOND
        const val AN_HOUR = 60 * A_MINUTE
    }
}
