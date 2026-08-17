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
    fun matchLandscape() = shoot("match_landscape", DESKTOP) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        playSomeOfAMatch()
    }

    @Test
    fun matchPortrait() = shoot("match_portrait", PHONE) {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        playSomeOfAMatch()
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
