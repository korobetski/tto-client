package com.tripletriad.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.net.AuctionClient
import com.tripletriad.protocol.AuctionLot
import com.tripletriad.protocol.AuctionPage
import com.tripletriad.protocol.AuctionStatus
import com.tripletriad.protocol.Unlocks
import com.tripletriad.time.FixedClock
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pupitre, and the desk a card is consigned from.
 *
 * ### Every assertion here is about what the screen refuses to send
 *
 * The server is the only thing that decides whether a bid stands, and none of these tests pretend
 * otherwise — they never assert that a price was accepted. What they assert is the half the client
 * owns: that a bid the house is going to refuse is refused *here*, with the reason on screen and
 * **no request made**, and that the two numbers a player commits money against — the buyer's 3% and
 * the seller's 5% — are on screen before the button that takes them. [posts] is what proves the
 * "no request made" half; without it a greyed-out button and a button that fires anyway look the
 * same from the outside.
 *
 * ### Why most of this mounts a body rather than [AuctionScreen]
 *
 * `AuctionScreen` starts `AuctionSession.watch`, a poll that never finishes, and a test that waits
 * for idle waits for that — [room] is where the whole screen is mounted anyway, with the test clock
 * held still so the poll never gets a second tick. Everything else mounts a body, because that is
 * what lets the fixture put a lot in a *state* — awaiting the seller, already bid on — that no
 * sequence of clicks could reach. The lock and the no-server notice are the screen's own and are
 * covered by `LobbyUnlockUiTest`.
 */
@OptIn(ExperimentalTestApi::class)
class AuctionUiTest {

    @Test
    fun theDeskOpensOnTheFirstLotAndThenFollowsWhatIsPicked() {
        // `AuctionSession.refreshBoard` selects the first lot on a board that had none selected,
        // so the pane is never a blank half of a wide screen with a full list beside it.
        house(listOf(lot(), lot(id = OTHER, startPrice = 777, topBid = 900))) {
            onNodeWithTag(AUCTION_DESK_TEST_TAG).assertExists()
            onNodeWithText("777 MGP").assertDoesNotExist()

            onNodeWithTag(auctionLotTestTag(OTHER)).performClick()

            // The desk is the only place a starting price is written with its unit; the row
            // beside it prints the current price against a coin.
            onNodeWithText("777 MGP").assertExists()
        }
    }

    @Test
    fun theTotalTheBidderOwesIncludesTheThreePercent() {
        house(listOf(lot())) {
            onNodeWithTag(auctionLotTestTag(LOT)).performClick()
            onNodeWithTag(AUCTION_BID_FIELD_TEST_TAG).performTextReplacement("$FLOOR")

            // 100 bid, 3 tax. The bid is the auction; this is what leaves the purse.
            onNodeWithText("103 MGP").assertExists()
            onNodeWithTag(AUCTION_BID_TEST_TAG).assertIsEnabled()
        }
    }

    @Test
    fun aBidUnderTheStandingOneNeverLeavesTheDevice() {
        val engine = house(listOf(lot(topBid = 500, bidCount = 2))) {
            onNodeWithTag(auctionLotTestTag(LOT)).performClick()

            // The minimum is 525 — the standing bid plus 5%. 300 is not a slip of the thumb, it
            // is a player who has not looked, and the field is where they find out.
            onNodeWithTag(AUCTION_BID_FIELD_TEST_TAG).performTextReplacement("300")

            onNodeWithTag(AUCTION_BID_TEST_TAG).assertIsNotEnabled()
            onNodeWithTag("$AUCTION_BID_TEST_TAG-why").assertExists()
        }
        assertEquals(1, engine.posts(), "only the one read, and no bid")
    }

    @Test
    fun aBidThePurseCannotCoverNeverLeavesTheDevice() {
        val engine = house(listOf(lot()), mgp = 50) {
            onNodeWithTag(auctionLotTestTag(LOT)).performClick()
            onNodeWithTag(AUCTION_BID_FIELD_TEST_TAG).performTextReplacement("$FLOOR")

            onNodeWithTag(AUCTION_BID_TEST_TAG).assertIsNotEnabled()
            onNodeWithTag("$AUCTION_BID_TEST_TAG-why").assertExists()
        }
        assertEquals(1, engine.posts(), "only the one read, and no bid")
    }

    @Test
    fun theReserveIsANumberForTheSellerAndAFactForEverybodyElse() {
        house(listOf(lot())) {
            onNodeWithTag(auctionLotTestTag(LOT)).performClick()

            // No `reservePrice` on the wire, because the server only sends it to the seller —
            // publishing it would hand every bidder the figure to stop one MGP short of.
            onNodeWithText("Not met").assertExists()
        }
        house(listOf(lot(yours = true, reservePrice = 800))) {
            onNodeWithTag(auctionLotTestTag(LOT)).performClick()

            onNodeWithText("800 MGP").assertExists()
        }
    }

    @Test
    fun anUntouchedLotOfYourOwnCanBeWithdrawn() {
        house(listOf(lot(yours = true))) {
            onNodeWithTag(auctionLotTestTag(LOT)).performClick()

            onNodeWithTag(AUCTION_WITHDRAW_TEST_TAG).assertIsEnabled()
            onNodeWithTag(AUCTION_WITHDRAW_LOCKED_TEST_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun aSellerWhoseLotHasABidOnItIsToldTheRuleRatherThanShownNothing() {
        house(listOf(lot(yours = true, topBid = 500, bidCount = 1))) {
            onNodeWithTag(auctionLotTestTag(LOT)).performClick()

            onNodeWithTag(AUCTION_WITHDRAW_TEST_TAG).assertDoesNotExist()
            onNodeWithTag(AUCTION_WITHDRAW_LOCKED_TEST_TAG).assertExists()
        }
    }

    @Test
    fun aLotThatFellShortOfItsReserveIsPutBackToTheSeller() {
        val lot = lot(
            yours = true,
            topBid = 400,
            bidCount = 1,
            reservePrice = 800,
            status = AuctionStatus.AWAITING_SELLER,
        )
        house(listOf(lot)) {
            onNodeWithTag(auctionLotTestTag(LOT)).performClick()

            onNodeWithTag(AUCTION_ACCEPT_TEST_TAG).assertIsEnabled()
            onNodeWithTag(AUCTION_DECLINE_TEST_TAG).assertIsEnabled()
            onNodeWithTag(AUCTION_BID_FIELD_TEST_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun theListingFeeIsFivePercentOfTheReserveAndItMovesAsItIsTyped() {
        sell {
            // Seeded at the floor: 100 for a rank-1 card, what the shop pays for it.
            onNodeWithText("5 MGP").assertExists()

            onNodeWithTag(AUCTION_RESERVE_FIELD_TEST_TAG).performTextReplacement("1000")

            onNodeWithText("50 MGP").assertExists()
        }
    }

    @Test
    fun aStartPriceUnderTheShopPriceNeverLeavesTheDevice() {
        val engine = sell {
            onNodeWithTag(AUCTION_START_FIELD_TEST_TAG).performTextReplacement("50")

            onNodeWithTag(AUCTION_LIST_TEST_TAG).assertIsNotEnabled()
            onNodeWithTag("$AUCTION_LIST_TEST_TAG-why").assertExists()
        }
        assertEquals(0, engine.posts(), "nothing was consigned")
    }

    @Test
    fun aReserveUnderTheStartPriceNeverLeavesTheDevice() {
        val engine = sell {
            onNodeWithTag(AUCTION_START_FIELD_TEST_TAG).performTextReplacement("500")

            // The reserve is still at the floor, so the lot would cancel itself at a price the
            // seller has already said they will not take.
            onNodeWithTag(AUCTION_LIST_TEST_TAG).assertIsNotEnabled()
            onNodeWithTag("$AUCTION_LIST_TEST_TAG-why").assertExists()
        }
        assertEquals(0, engine.posts(), "nothing was consigned")
    }

    @Test
    fun aPriceAboveTheCeilingNeverLeavesTheDevice() {
        val engine = sell {
            // Twenty times the shop price is the ceiling `AuctionPolicy` ships with, and it is
            // there so a lot cannot be used to move a fortune between two accounts.
            onNodeWithTag(AUCTION_START_FIELD_TEST_TAG).performTextReplacement("9000")
            onNodeWithTag(AUCTION_RESERVE_FIELD_TEST_TAG).performTextReplacement("9000")

            onNodeWithTag(AUCTION_LIST_TEST_TAG).assertIsNotEnabled()
            onNodeWithTag("$AUCTION_LIST_TEST_TAG-why").assertExists()
        }
        assertEquals(0, engine.posts(), "nothing was consigned")
    }

    // ---- The room the three tabs are in -----------------------------------

    /**
     * The badge is on the middle tab and nowhere else.
     *
     * `MINE` is the only tab that carries a count, and it carries one because the thing behind it
     * *expires*: a seller whose reserve was missed has until `AuctionPolicy.sellerDecisionHours` to
     * answer, and a tab that read the same either way would lose them the sale by silence. The
     * paired test below is what makes the badge mean something — a count that is always there is
     * decoration.
     */
    @Test
    fun onlyTheTabHoldingADecisionCountsWhatIsBehindIt() {
        room(listOf(lot(yours = true, status = AuctionStatus.AWAITING_SELLER))) { _ ->
            onNodeWithTag(AUCTION_TABS_TEST_TAG).assertExists()
            onNodeWithText("My lots (1)").assertExists()
        }
    }

    @Test
    fun anOpenLotOfYourOwnIsNotSomethingWaitingOnYou() {
        room(listOf(lot(yours = true))) { _ ->
            onNodeWithText("My lots").assertExists()
            onNodeWithText("My lots (1)").assertDoesNotExist()
        }
    }

    /** Three tabs, three bodies, and one read behind all of them. */
    @Test
    fun eachTabDrawsItsOwnBodyWithoutReReadingTheHouse() {
        room(listOf(lot())) { engine ->
            onNodeWithTag(AUCTION_BOARD_TEST_TAG).assertExists()
            assertEquals(2, engine.posts(), "the room did not read both its lists")

            onNodeWithTag(screenTabTestTag("auction-sell")).performClick()
            mainClock.advanceTimeByFrame()
            onNodeWithTag(AUCTION_SELL_TEST_TAG).assertExists()
            onNodeWithTag(AUCTION_BOARD_TEST_TAG).assertDoesNotExist()

            onNodeWithTag(screenTabTestTag("auction-mine")).performClick()
            mainClock.advanceTimeByFrame()
            onNodeWithTag(AUCTION_MINE_TEST_TAG).assertExists()

            // Still two: the lots behind a tab nobody is looking at are the lots they will look
            // at next, and a tab switch that re-read would put a spinner over a list the screen
            // already had.
            assertEquals(2, engine.posts(), "changing tab went back to the house")
        }
    }

    // ---- Harness ----------------------------------------------------------

    /** The room, read once and then left alone: no poll, so no clock to wait out. */
    private fun house(
        lots: List<AuctionLot>,
        mgp: Int = PURSE,
        block: ComposeUiTest.() -> Unit,
    ): MockEngine {
        val engine = pageEngine(lots)
        runComposeUiTest {
            val session = sessionOn(engine)
            runBlocking { session.refreshBoard() }

            setContent {
                CompositionLocalProvider(
                    LocalStrings provides strings,
                    // The pane, not the sheet: a `ModalBottomSheet` animates in, and what these
                    // tests are about is on the desk either way. `AdaptiveUiTest` is where the
                    // narrow arrangement is checked.
                    LocalWideLayout provides true,
                ) {
                    TripleTriadTheme {
                        Column {
                            AuctionBoardBody(
                                session = session,
                                lots = session.board,
                                state = session.boardState,
                                profile = purse(mgp),
                                cards = catalog,
                                now = NOW,
                                tag = AUCTION_BOARD_TEST_TAG,
                                emptyText = "nothing",
                                onRefresh = {},
                            )
                        }
                    }
                }
            }
            block()
        }
        return engine
    }

    /**
     * The whole screen, with the clock held still.
     *
     * `AuctionRoom` starts a poll on its first frame, so the test clock is stopped before anything
     * is mounted: the first read still happens — it is what fills both lists — and the `delay` that
     * would start the second one never comes due, which is what keeps `waitForIdle` from waiting
     * for ever. Frames are then advanced by hand, one per thing that has to redraw — a stopped
     * clock is also a stopped recomposition, and a second of it is nowhere near the poll's five.
     * The profile sits exactly on the gate: a level short of it is a different screen entirely, and
     * `LobbyUnlockUiTest` is where that one is read.
     */
    private fun room(lots: List<AuctionLot>, block: ComposeUiTest.(MockEngine) -> Unit) {
        val engine = pageEngine(lots)
        runComposeUiTest {
            mainClock.autoAdvance = false
            val session = sessionOn(engine)

            setContent {
                CompositionLocalProvider(
                    LocalStrings provides strings,
                    LocalWideLayout provides true,
                ) {
                    TripleTriadTheme {
                        AuctionScreen(
                            profile = purse(PURSE).copy(
                                level = Unlocks.DEFAULT_AUCTION,
                                // A spare copy, so the consignment tab is the desk and not its
                                // empty note — which is a different branch and `sell` covers it.
                                cards = mapOf(cheap.id to 2),
                                decks = emptyList(),
                            ),
                            session = session,
                            cards = catalog,
                            clock = FixedClock(NOW),
                            onBack = {},
                        )
                    }
                }
            }
            // The screen's own first read, and then nothing: `AuctionRoom` reads both lists on
            // its first frame, and the poll behind it is five seconds away. A second of the test
            // clock is enough for the read to land and nowhere near enough for the poll.
            mainClock.advanceTimeBy(A_SECOND)
            block(engine)
        }
    }

    /** The consignment desk, with one card the player may part with and money for the fee. */
    private fun sell(block: ComposeUiTest.() -> Unit): MockEngine {
        val engine = pageEngine(emptyList())
        runComposeUiTest {
            val session = sessionOn(engine)

            setContent {
                CompositionLocalProvider(LocalStrings provides strings) {
                    TripleTriadTheme {
                        Column {
                            AuctionSellBody(
                                session = session,
                                // Two copies and no decks, so one of them is spare. A card a
                                // saved deck is built on is not offered here at all.
                                profile = purse(PURSE).copy(
                                    cards = mapOf(cheap.id to 2),
                                    decks = emptyList(),
                                ),
                                cards = catalog,
                                openLots = 0,
                            )
                        }
                    }
                }
            }
            block()
        }
        return engine
    }

    private fun sessionOn(engine: MockEngine): AuctionSession {
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        return AuctionSession(
            client = AuctionClient(http, { "http://server" }),
            tokenOf = { "token" },
            clock = FixedClock(NOW),
        )
    }

    private fun pageEngine(lots: List<AuctionLot>) = MockEngine {
        respond(
            content = json.encodeToString(AuctionPage(lots = lots, now = NOW)),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    /** How many times the house was actually spoken to. See this class's KDoc. */
    private fun MockEngine.posts(): Int = requestHistory.size

    @Suppress("LongParameterList")
    private fun lot(
        id: String = LOT,
        startPrice: Int = FLOOR,
        yours: Boolean = false,
        topBid: Int? = null,
        bidCount: Int = 0,
        reservePrice: Int? = null,
        status: AuctionStatus = AuctionStatus.OPEN,
    ) = AuctionLot(
        id = id,
        cardId = cheap.id,
        sellerName = "Kuplu",
        startPrice = startPrice,
        endsAt = NOW + AN_HOUR,
        status = status,
        topBid = topBid,
        bidCount = bidCount,
        reservePrice = reservePrice,
        yours = yours,
    )

    private fun purse(mgp: Int) = GameSave.new(username = "Nael", createdAt = 0L).copy(mgp = mgp)

    private val json = Json { ignoreUnknownKeys = true }

    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private val catalog: Map<Int, Card> by lazy { pvpCards.all.associateBy { it.id } }

    /** Rank 1, so the shop pays 100 for it and every number in this file is derived from that. */
    private val cheap: Card by lazy { pvpCards.all.first { it.rarity == 1 } }

    private companion object {
        const val LOT = "lot-1"
        const val OTHER = "lot-2"
        const val FLOOR = 100
        const val PURSE = 100_000
        const val NOW = 1_770_000_000_000L
        const val A_SECOND = 1_000L
        const val AN_HOUR = 60L * 60L * 1_000L
    }
}
