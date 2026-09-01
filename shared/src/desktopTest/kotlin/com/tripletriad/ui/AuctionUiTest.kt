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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    /**
     * The refusal is still there — but it now takes a seller who has *stated* a reserve to reach
     * it.
     *
     * This used to be one keystroke away: type a starting price, and the reserve left at the floor
     * put the lot in a state that cancels itself at a price the seller had already refused. The
     * reserve follows the start until it is typed into, so the pair only crosses when somebody
     * means it — which is what this types, in that order.
     */
    @Test
    fun aReserveTheSellerSetUnderTheirOwnStartPriceNeverLeavesTheDevice() {
        val engine = sell {
            // 200 and not the 100 already in the field: replacing text with the same text is
            // not an edit, and the seller has to have *stated* a reserve for it to stop
            // following the start price.
            onNodeWithTag(AUCTION_RESERVE_FIELD_TEST_TAG).performTextReplacement("200")
            onNodeWithTag(AUCTION_START_FIELD_TEST_TAG).performTextReplacement("500")

            onNodeWithTag(AUCTION_LIST_TEST_TAG).assertIsNotEnabled()
            onNodeWithTag("$AUCTION_LIST_TEST_TAG-why").assertExists()
        }
        assertEquals(0, engine.posts(), "nothing was consigned")
    }

    /** And the ordinary case, which is one number and not two. */
    @Test
    fun aStartPriceCarriesTheReserveWithItUntilTheSellerHasOneOfTheirOwn() {
        sell {
            onNodeWithTag(AUCTION_START_FIELD_TEST_TAG).performTextReplacement("500")

            // 5% of a reserve that moved with it. Left behind at the floor the fee would read
            // 5 MGP and the button would be grey.
            onNodeWithText("25 MGP").assertExists()
            onNodeWithTag(AUCTION_LIST_TEST_TAG).assertIsEnabled()
        }
    }

    @Test
    fun aReserveTheSellerHasTypedIsNotMovedUnderThem() {
        sell {
            onNodeWithTag(AUCTION_RESERVE_FIELD_TEST_TAG).performTextReplacement("1000")
            onNodeWithTag(AUCTION_START_FIELD_TEST_TAG).performTextReplacement("500")

            // Still 5% of the seller's own thousand, not of the five hundred they just typed.
            onNodeWithText("50 MGP").assertExists()
        }
    }

    /** The shop price, twice it, five times it, the ceiling — and typing for the rest. */
    @Test
    fun aPriceChipFillsTheFieldAndTakesTheReserveWithIt() {
        sell {
            onNodeWithTag(auctionPriceTestTag(FLOOR * 2)).performClick()

            onNodeWithText("10 MGP").assertExists()
            onNodeWithTag(AUCTION_LIST_TEST_TAG).assertIsEnabled()
        }
    }

    /**
     * A phone opens the room as a list, and the desk arrives when a lot is picked.
     *
     * The desk on a narrow screen is a modal sheet, and the board used to arrive with one already
     * over it: `AuctionSession.refreshBoard` picked the first lot so that a *wide* screen's pane
     * was never blank, which on a phone threw a lot nobody had chosen over the list — and a modal
     * sheet takes the input as well as the screen, so the tabs behind it did not answer either.
     * The choice moved into the wide branch of [AuctionBoardBody], where there is a pane to fill.
     */
    @Test
    fun aPhoneOpensTheRoomAsAListRatherThanAsASheetOverOne() {
        house(listOf(lot(), lot(id = OTHER)), wide = false) {
            onNodeWithTag(AUCTION_BOARD_TEST_TAG).assertExists()
            assertFalse(
                exists(AUCTION_DESK_SHEET_TEST_TAG),
                "the room opened on a lot the player had not picked",
            )

            onNodeWithTag(auctionLotTestTag(OTHER)).performClick()
            waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_DESK_SHEET_TEST_TAG) }
        }
    }

    /**
     * The lectern reads its card in the panel the collection reads a card in.
     *
     * It drew a `CardFace` and a name and stopped there: the powers and the rarity — the two facts
     * a bidder is putting a price on — were written nowhere on the one screen in the game where
     * somebody commits four figures to a card. [CardPanel] is that block, and `cardFacts` is the
     * line it says them in, so this assertion is the collection's own sentence read at the desk.
     */
    @Test
    fun theLecternReadsItsCardInTheCollectionsOwnPanel() {
        house(listOf(lot())) {
            onNodeWithTag(AUCTION_DESK_CARD_TEST_TAG).assertExists()
            onNodeWithText(cardFacts(strings, cheap)).assertExists()
        }
    }

    /**
     * A row on the board reads its card the way every other list of cards in the app does.
     *
     * The framed thumbnail and the four powers beside it are `CardTile` and `CardStatsLine` — the
     * two composables the collection, the deck builder and the shop draw a card with — rather than
     * a `CardFace` shrunk to 0.42, which is what this row was until the digits on it stopped being
     * legible. The desk beside it still draws the full sprite, and neither tag belongs to that:
     * `CardFace` carries no thumbnail and prints no stats line.
     *
     * Unmerged, because both sit inside the row's own `clickable`, which absorbs their semantics.
     */
    @Test
    fun aLotOnTheBoardIsReadWithTheAppsOwnCardRow() {
        house(listOf(lot())) {
            assertTrue(existsUnmerged(thumbTestTag(cheap.textureId)), "no thumbnail on the row")
            assertTrue(existsUnmerged(cardStatsTestTag(cheap.id)), "no powers on the row")
        }
    }

    // ---- Finding the card ---------------------------------------------------

    /**
     * The desk says which card it is about to sell, by name.
     *
     * It used to say it in a strip of 0.6-scale pictures with no names on them, one of which was
     * outlined. Naming the card is what makes the rest of the form readable: every number under it
     * — the floor, the ceiling, the fee — is a fact about *that* card.
     */
    @Test
    fun theDeskNamesTheCardItIsAboutToConsign() {
        sell {
            onNodeWithText(strings[cheap.nameKey]).assertExists()
        }
    }

    @Test
    fun anotherCardIsChosenFromTheGridAndTheDeskFollowsIt() {
        sell(held = mapOf(cheap.id to 2, dear.id to 2)) {
            val other = if (first.id == cheap.id) dear else cheap
            onNodeWithText(strings[first.nameKey]).assertExists()

            onNodeWithTag(AUCTION_SELL_PICK_TEST_TAG).performClick()
            onNodeWithTag(AUCTION_SELL_GRID_TEST_TAG).assertExists()
            onNodeWithTag(auctionSellCardTestTag(other.id)).performClick()

            // Back on the desk, on the other card — and the desk is the desk again, not the grid.
            onNodeWithTag(AUCTION_SELL_GRID_TEST_TAG).assertDoesNotExist()
            onNodeWithText(strings[other.nameKey]).assertExists()
        }
    }

    @Test
    fun thePickerCanBeLeftWithoutChangingWhatIsOnTheDesk() {
        sell(held = mapOf(cheap.id to 2, dear.id to 2)) {
            onNodeWithTag(AUCTION_SELL_PICK_TEST_TAG).performClick()
            onNodeWithTag(AUCTION_SELL_BACK_TEST_TAG).performClick()

            onNodeWithTag(AUCTION_SELL_GRID_TEST_TAG).assertDoesNotExist()
            onNodeWithText(strings[first.nameKey]).assertExists()
        }
    }

    /**
     * The collection's own filters, over the seller's own spares.
     *
     * The point of the picker is that a card can be *found*, and 565 pictures in id order is not
     * finding. One rarity chip is enough to prove the row is wired to the grid; the chips
     * themselves are the card list's, and `CardListUiTest` is where they are read.
     */
    @Test
    fun theFiltersNarrowWhatThePickerOffers() {
        sell(held = mapOf(cheap.id to 2, dear.id to 2)) {
            onNodeWithTag(AUCTION_SELL_PICK_TEST_TAG).performClick()
            onNodeWithTag(auctionSellCardTestTag(dear.id)).assertExists()

            onNodeWithTag(rarityFilterTestTag(cheap.rarity)).performClick()

            onNodeWithTag(auctionSellCardTestTag(cheap.id)).assertExists()
            onNodeWithTag(auctionSellCardTestTag(dear.id)).assertDoesNotExist()
        }
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
    @Suppress("LongParameterList")
    private fun house(
        lots: List<AuctionLot>,
        mgp: Int = PURSE,
        wide: Boolean = true,
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
                    // tests are about is on the desk either way. The one test that is about the
                    // sheet passes `wide = false`.
                    LocalWideLayout provides wide,
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
                            sets = pvpCards.sets,
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

    /**
     * The consignment desk, with cards the player may part with and money for the fee.
     *
     * @param held how many copies of what. Two of one card by default — enough that one is spare,
     *   which is the only condition the desk cares about. Pass more to reach the picker, which is
     *   a control for a collection rather than for a card.
     */
    private fun sell(
        held: Map<Int, Int> = mapOf(cheap.id to 2),
        block: ComposeUiTest.() -> Unit,
    ): MockEngine {
        val engine = pageEngine(emptyList())
        runComposeUiTest {
            val session = sessionOn(engine)

            setContent {
                CompositionLocalProvider(LocalStrings provides strings) {
                    TripleTriadTheme {
                        Column {
                            AuctionSellBody(
                                session = session,
                                // No decks, so every copy is spare. A card a saved deck is built
                                // on is not offered here at all.
                                profile = purse(PURSE).copy(
                                    cards = held,
                                    decks = emptyList(),
                                ),
                                cards = catalog,
                                sets = pvpCards.sets,
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

    /** A second card, of another rarity, so the picker has something to pick between. */
    private val dear: Card by lazy { pvpCards.all.first { it.rarity == 3 } }

    /**
     * The one the desk opens on: the lowest id among the spares, which is what `AuctionSellBody`
     * chooses so that the form is never a hole. Read rather than assumed — which of the two it is
     * depends on the catalog, and this file should not have an opinion about that.
     */
    private val first: Card by lazy { listOf(cheap, dear).minBy { it.id } }

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
