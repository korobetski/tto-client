package com.tripletriad.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.tripletriad.i18n.AppLocale
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
    fun menu() = shoot("menu", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        awaitMenu()
    }

    @Test
    fun dashboard() = shoot("dashboard", DESKTOP) {
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
    fun opponents() = shoot("opponents", DESKTOP) {
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
    fun collection() = shoot("collection", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)
        onNodeWithTag(cardCellTestTag(STARTER_CARDS.first())).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
    }

    @Test
    fun cardDetail() = shoot("card_detail", DETAIL) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)
        onNodeWithTag(cardCellTestTag(STARTER_CARDS.first())).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
    }

    @Test
    fun deckBuilder() = shoot("deck_builder", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
    }

    @Test
    fun tutorial() = shoot("tutorial", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openLessons()
        onNodeWithTag(lessonRowTestTag(0)).performClick()
        waitUntil(timeoutMillis = TUTORIAL_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        waitUntil(timeoutMillis = TUTORIAL_TIMEOUT_MS) { exists(TALK_BUBBLE_TEST_TAG) }
    }

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

        val DESKTOP = Size(1280f, 960f)

        /**
         * 960 x 600 dp at [DENSITY], and both numbers are load-bearing.
         *
         * **The height, because the challenge has to be reachable.** [DESKTOP] is 640 x 480 dp —
         * shorter than the 560 x 640 dp window `:desktopApp` itself opens at — and a match is
         * started through the opponent detail sheet, whose own challenge button lands about
         * 503 dp down. On a 480 dp window that button is outside the window and the capture dies
         * on "cannot start a mouse gesture outside the Compose root bounds". Worth knowing beyond
         * this file: that is not the capture being fussy, it is a control a player cannot reach on
         * a window that short.
         *
         * **The width and the ratio, because of what the picture is for.** The move log beside the
         * board is the thing a landscape shot is supposed to show, and it appears at this size and
         * not at 960 x 540 — where the panel is dropped and the space it would have filled is left
         * empty on the right.
         */
        val BOARD = Size(1920f, 1200f)

        val DETAIL = Size(1280f, 1200f)

        val PHONE = Size(780f, 1688f)

        const val PLACEMENTS_SHOWN = 3

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
