package com.tripletriad.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.XpTable
import com.tripletriad.protocol.AuctionLot
import com.tripletriad.protocol.AuctionStatus
import com.tripletriad.protocol.Unlocks
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ScreenshotCapture {

    /**
     * A referee, for the two captures that photograph a board.
     *
     * They had none, and had been failing since the deal moved to the server: `startMatch` signs
     * in and asks for a match, so without a connection the dashboard never arrives and the capture
     * times out. The two match pictures in `docs/screenshots/` predate that change — this is what
     * makes them re-takeable rather than frozen.
     *
     * Only these two need it. Everything else here is a screen the app draws from its own shipped
     * data.
     */
    private val stub = PveStubServer()

    @Test
    fun title() = shoot("title", PHONE) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        // Settled on what it is going to offer, not merely present: the first frame is
        // drawn before `session.refresh()` answers, and a picture of that frame is a
        // picture of a screen mid-thought.
        awaitTitleChoice("new")
    }

    @Test
    fun dashboard() = shoot("dashboard", PHONE) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
    }

    @Test
    fun matchLandscape() = shoot("match_landscape", BOARD) {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        playSomeOfAMatch()
    }

    @Test
    fun matchPortrait() = shoot("match_portrait", PHONE) {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        playSomeOfAMatch()
    }

    /**
     * The roster, and the one screen here shot without a card on screen anywhere.
     *
     * No server, like every capture in this file: the rows, the shelves and the ladders are all
     * drawn from the shipped `npcs.json`, and only *playing* one of them needs a referee. What the
     * picture would gain from a stub is the resume button — see `ResumeMatchUiTest` — and that is
     * a state the roster is usually not in.
     */
    @Test
    fun opponents() = shoot("opponents", PHONE) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        // `openOpponents` waits on `OPPONENT_LIST_TEST_TAG`, which is the landmark that pins
        // which screen this caught. Not the random-opponent button, which reads as the obvious
        // thing to wait for and is wrong: it is a `LazyColumn` item below the shelves, so on this
        // window it is never composed and the wait only ever times out.
        openOpponents()
        waitForIdle()
    }

    @Test
    fun collection() = shoot("collection", PHONE) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)
        onNodeWithTag(cardCellTestTag(STARTER_CARDS.first())).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
        // The sheet is *composed* the moment the card is picked and still sliding up: a picture
        // taken on that landmark alone catches it half off the bottom of the screen, with the
        // Sell button below the edge. This is the frame after it lands.
        waitForIdle()
    }

    @Test
    fun cardDetail() = shoot("card_detail", PHONE) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)
        onNodeWithTag(cardCellTestTag(STARTER_CARDS.first())).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
    }

    @Test
    fun deckBuilder() = shoot("deck_builder", PHONE) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
    }

    @Test
    fun tutorial() = shoot("tutorial", PHONE) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openLessons()
        onNodeWithTag(lessonRowTestTag(0)).performClick()
        waitUntil(timeoutMillis = TUTORIAL_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        waitUntil(timeoutMillis = TUTORIAL_TIMEOUT_MS) { exists(TALK_BUBBLE_TEST_TAG) }
    }

    /**
     * The room: what is up for sale, and the lot the desk is open on.
     *
     * ### Why this one builds its own server
     *
     * The house is the only screen here that cannot be photographed off the shipped data. It needs
     * a counterparty — with no server it is a stated fact and not a room, see [AuctionScreen] — and
     * it needs a profile past the level gate, which is a fact about the *server's* copy of the
     * save. Both come from [PveStubServer], with the lots handed to it: nothing in this repository
     * runs an auction, so the alternative to a fixture is a photograph of an empty room.
     *
     * ### The clock runs here, unlike in `AuctionUiTest`
     *
     * That file stops it because `AuctionSession.watch` polls until the screen goes away and a
     * test that waits for *idle* waits for the poll. A capture never waits for idle: it waits for
     * one landmark at a time, which the poll does not delay. And the desk on a phone is a sheet,
     * which has to animate in — under a stopped clock it never arrives at all, and the picture is
     * of the list with nothing over it.
     */
    @Test
    fun auctionRoom() = shoot("auction_room", PHONE) {
        val server = house()
        setContent { App(store = settingsFor(AppLocale.EN_US), server = server.connection) }
        openDashboard()

        onNodeWithTag(DASHBOARD_AUCTION_TEST_TAG).performScrollTo().performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_BOARD_TEST_TAG) }
        onNodeWithTag(auctionLotTestTag(SHOWN_LOT)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_DESK_SHEET_TEST_TAG) }
    }

    /** The consignment desk: the card being sold, the two prices, and what the house charges. */
    @Test
    fun auctionSell() = shoot("auction_sell", PHONE) {
        val server = house()
        setContent { App(store = settingsFor(AppLocale.EN_US), server = server.connection) }
        openDashboard()

        onNodeWithTag(DASHBOARD_AUCTION_TEST_TAG).performScrollTo().performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_BOARD_TEST_TAG) }
        onNodeWithTag(screenTabTestTag("auction-sell")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_SELL_PICK_TEST_TAG) }
    }

    /**
     * The consignment picker: which of my spare cards to sell.
     *
     * The same grid, the same cells and the same filter chips as the collection — see
     * `CardFilters` and `CardGrid`, which both rooms are built from — over the shorter list of
     * what a seller may actually part with.
     */
    @Test
    fun auctionPicker() = shoot("auction_picker", PHONE) {
        val server = house()
        setContent { App(store = settingsFor(AppLocale.EN_US), server = server.connection) }
        openDashboard()

        onNodeWithTag(DASHBOARD_AUCTION_TEST_TAG).performScrollTo().performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_BOARD_TEST_TAG) }
        onNodeWithTag(screenTabTestTag("auction-sell")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_SELL_PICK_TEST_TAG) }
        onNodeWithTag(AUCTION_SELL_PICK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_SELL_GRID_TEST_TAG) }
    }

    /**
     * A house with something in it, and a character allowed through the door.
     *
     * `level` as well as `xp`: the gate reads the level, and a profile adopted from a server is
     * taken as sent rather than put back through `GameSave.sane()`, which is what derives one from
     * the other on the way in and out of a local save.
     */
    private fun house(): PveStubServer {
        val catalog = runBlocking { loadCardCatalog() }
        val sold = catalog.all.first { it.id == SOLD_CARD }
        val server = PveStubServer(
            save = freshSave().copy(
                xp = XpTable.thresholdFor(Unlocks.DEFAULT_AUCTION),
                level = Unlocks.DEFAULT_AUCTION,
                mgp = PURSE,
                // Spare copies and no deck built on them, so the desk opens on a card the seller
                // may actually part with.
                cards = catalog.all.take(SPARES).associate { it.id to 2 },
                decks = emptyList(),
            ),
        )
        server.lots = listOf(
            lot(SHOWN_LOT, sold.id, price = 1_200, bids = 3, endsIn = 2 * AN_HOUR),
            lot("lot-2", catalog.all[1].id, price = 350, bids = 0, endsIn = 20 * A_MINUTE),
            lot("lot-3", catalog.all[2].id, price = 9_500, bids = 11, endsIn = 6 * AN_HOUR),
        )
        return server
    }

    private fun lot(id: String, cardId: Int, price: Int, bids: Int, endsIn: Long) = AuctionLot(
        id = id,
        cardId = cardId,
        sellerName = "Kuplu Kopp",
        startPrice = price,
        endsAt = FixedClock.DEFAULT_MILLIS + endsIn,
        status = AuctionStatus.OPEN,
        topBid = price.takeIf { bids > 0 },
        bidCount = bids,
        reservePrice = null,
        yours = false,
    )

    private suspend fun SkikoComposeUiTest.playSomeOfAMatch() {
        startMatch()
        repeat(PLACEMENTS_SHOWN) { playOneCard() }
        awaitPlayer()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(MATCH_BANNER_TEST_TAG) }
    }

    private fun shoot(name: String, size: Size, block: suspend SkikoComposeUiTest.() -> Unit) {
        assumeTrue("pass -P$FLAG to write screenshots", System.getProperty(FLAG) != null)
        runSkikoComposeUiTest(size = size, density = Density(DENSITY)) {
            block()
            val image = if (name == "card_detail") {
                onNodeWithTag(CARD_DETAIL_TEST_TAG).captureToImage()
            } else {
                captureToImage()
            }
            write(name, image)
        }
    }

    private companion object {
        const val FLAG = "tto.screenshots"

        const val DENSITY = 2f

        /**
         * 960 x 600 dp at [DENSITY], and both numbers are load-bearing.
         *
         * **The height, because the challenge has to be reachable.** A match is started through
         * the opponent detail sheet, whose own challenge button lands about 503 dp down, so on a
         * 480 dp-tall window it falls outside and the capture dies on "cannot start a mouse
         * gesture outside the Compose root bounds". Worth knowing beyond this file: that is not
         * the capture being fussy, it is a control a player cannot reach on a window that short.
         *
         * **The width and the ratio, because of what the picture is for.** The move log beside the
         * board is the thing a landscape shot is supposed to show, and it appears at this size and
         * not at 960 x 540 — where the panel is dropped and the space it would have filled is left
         * empty on the right.
         */
        val BOARD = Size(1920f, 1200f)

        /**
         * 390 x 844 dp, and every picture here but the landscape board is taken on it.
         *
         * The desktop window these used to be taken on was 640 x 480 dp — wider than any phone and
         * shorter than most, which is the one shape the app is never actually used at. Worse, it is
         * the shape that hides half the layout work: `LocalWideLayout` is on above 600 dp, so every
         * capture showed the two-pane arrangement and none of them showed the sheets, the stacked
         * rows and the full-screen grids a phone gets. The README is a picture of the app as it is
         * played.
         */
        val PHONE = Size(780f, 1688f)

        const val PLACEMENTS_SHOWN = 3

        /** The lot the room opens on, and the one the sheet is opened over. */
        const val SHOWN_LOT = "lot-1"

        /** Elemental, and with prose long enough to show what the desk does with it. */
        const val SOLD_CARD = 0x010e

        const val SPARES = 40

        const val PURSE = 50_000

        const val A_MINUTE = 60L * 1_000L

        const val AN_HOUR = 60L * A_MINUTE

        val OUTPUT: File
            get() {
                val from = File(".").absoluteFile
                var here = from
                while (!File(here, "settings.gradle.kts").exists()) {
                    here = here.parentFile ?: error("no repository root above $from")
                }
                return File(here, "docs/screenshots").apply { mkdirs() }
            }

        fun write(name: String, image: ImageBitmap) {
            val file = File(OUTPUT, "$name.png")
            ImageIO.write(image.toAwtImage(), "png", file)
            println("screenshot: ${file.absolutePath} (${image.width}x${image.height})")
        }
    }
}
