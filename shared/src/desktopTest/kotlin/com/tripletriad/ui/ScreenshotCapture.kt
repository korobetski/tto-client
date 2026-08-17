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

/**
 * The eight screenshots `README.md` asks for, taken by the app itself.
 *
 * ### Why this is a test and not a person with a phone
 *
 * `App()` already runs with no filesystem, no network and no machine locale — that is what its
 * inert defaults are for — so the harness that drives the UI tests can drive the same screens to
 * the same states and photograph them. The alternative in the README is `adb screencap`, which
 * needs a device, produces a different frame every run, and cannot be asked for the French
 * dashboard or a board three cards deep.
 *
 * It is **skipped unless `-Ptto.screenshots` is passed**, so `./gradlew build` neither writes into
 * the repository nor pays for it. `:shared` forwards that property into the test JVM — see
 * `shared/build.gradle.kts`, and note the `-P`: a `-D` reaches Gradle's own JVM and not this one.
 *
 * ```bash
 * ./gradlew :shared:desktopTest --tests "*ScreenshotCapture*" -Ptto.screenshots=1
 * ```
 *
 * Files land in `docs/screenshots/`, named as the README lists them, and are overwritten in place.
 *
 * **Not verified:** nothing here asserts anything — a capture that framed the wrong screen would
 * pass. What each one is *of* is pinned by waiting on the same landmark tags the UI tests use, so
 * a screen that stopped rendering fails the wait rather than producing a blank picture; that the
 * result is worth looking at is a human's judgement.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotCapture {

    /** The main menu, once every startup phase has finished. */
    @Test
    fun menu() = shoot("menu", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        awaitMenu()
    }

    /** The hub a loaded character lands on. */
    @Test
    fun dashboard() = shoot("dashboard", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
    }

    /** A match under way rather than freshly dealt: three cards are down, so the board reads. */
    @Test
    fun matchLandscape() = shoot("match_landscape", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        playSomeOfAMatch()
    }

    /** The same match at a phone's aspect, which is a different layout and not a scaled one. */
    @Test
    fun matchPortrait() = shoot("match_portrait", PHONE) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        playSomeOfAMatch()
    }

    /**
     * The collection, wide enough that the detail sits beside the grid.
     *
     * With a card picked, so the second pane holds one rather than the "pick a card" placeholder:
     * the two-pane layout is the thing worth showing, and an empty half does not show it.
     */
    @Test
    fun collection() = shoot("collection", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)
        onNodeWithTag(cardCellTestTag(STARTER_CARDS.first())).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
    }

    /** One card, cropped to the detail pane — the close-up the README asks for. */
    @Test
    fun cardDetail() = shoot("card_detail", DETAIL) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)
        onNodeWithTag(cardCellTestTag(STARTER_CARDS.first())).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
    }

    /** The starter deck open in the editor, not the list of slots in front of it. */
    @Test
    fun deckBuilder() = shoot("deck_builder", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
    }

    /** The first lesson mid-sentence: the board behind, the tutor's bubble over it. */
    @Test
    fun tutorial() = shoot("tutorial", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openLessons()
        onNodeWithTag(lessonRowTestTag(0)).performClick()
        waitUntil(timeoutMillis = TUTORIAL_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        waitUntil(timeoutMillis = TUTORIAL_TIMEOUT_MS) { exists(TALK_BUBBLE_TEST_TAG) }
    }

    /**
     * Three cards down and the turn back with the player, with **no banner over the board**.
     *
     * `BLUE TURN` is drawn across the middle of the board for a second after every handover, so a
     * capture taken the moment the turn passes photographs the caption rather than the position.
     */
    private suspend fun SkikoComposeUiTest.playSomeOfAMatch() {
        startMatch()
        repeat(PLACEMENTS_SHOWN) { playOneCard() }
        awaitPlayer()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(MATCH_BANNER_TEST_TAG) }
    }

    /**
     * Drives one screen and writes what the window holds when [block] returns.
     *
     * The whole surface, not a node: a screenshot of a screen is the screen. [cardDetail] is the
     * one exception and crops itself.
     */
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

        /**
         * Two device pixels to the dp, so the text in these is legible at the size a README
         * renders them and the card art is photographed at the resolution it ships at.
         */
        const val DENSITY = 2f

        /** 640x480 dp — over the 600 dp threshold, so the rail and the two-pane layouts. */
        val DESKTOP = Size(1280f, 960f)

        /**
         * Taller than any screen needs, and only [cardDetail] uses it: the crop is bounded by the
         * window, so a lore paragraph longer than the frame comes out cut off mid-sentence.
         */
        val DETAIL = Size(1280f, 1200f)

        /** 390x844 dp, an iPhone 14's window: under the threshold, so the bar and one pane. */
        val PHONE = Size(780f, 1688f)

        /** Enough of the board filled to show a capture, and not so much that the match ends. */
        const val PLACEMENTS_SHOWN = 3

        /** `docs/screenshots/`, found from the module rather than from the working directory. */
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
