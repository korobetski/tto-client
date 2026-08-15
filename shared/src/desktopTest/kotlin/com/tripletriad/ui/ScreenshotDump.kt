package com.tripletriad.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.tripletriad.i18n.AppLocale
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Temporary: renders screens to PNG so the refresh can be looked at. Not part of the suite. */
@OptIn(ExperimentalTestApi::class)
class ScreenshotDump {
    private val out = File(System.getProperty("tto.shots") ?: "/tmp/tto-shots").apply { mkdirs() }

    private fun ComposeUiTest.shot(name: String) {
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
    }

    @Test
    fun dump() = runDesktopComposeUiTest(width = 420, height = 900) {
        setContent { App(store = settingsFor(AppLocale.FR_FR)) }

        awaitMenu()
        shot("01-menu")

        newCharacter()
        shot("02-dashboard")

        openFromBar("cards", CARD_GRID_TEST_TAG)
        shot("03-cards")

        openFromBar("store", SHOP_LIST_TEST_TAG)
        shot("04-shop")

        openFromBar("play", OPPONENT_LIST_TEST_TAG)
        shot("05-opponents")

        openFromBar("home", DASHBOARD_PLAY_TEST_TAG)
        openFromDashboard(DASHBOARD_QUESTS_TEST_TAG, QUESTS_LIST_TEST_TAG)
        shot("06-quests")
        backToDashboard()

        openFromDashboard(DASHBOARD_STATS_TEST_TAG, STATS_TABLE_TEST_TAG)
        shot("07-stats")
        backToDashboard()

        openFromBar("play", OPPONENT_LIST_TEST_TAG)
        challenge()
        shot("08-match")
    }

    @Test
    fun options() = runDesktopComposeUiTest(width = 420, height = 900) {
        setContent { App(store = settingsFor(AppLocale.FR_FR)) }

        awaitMenu()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()
        shot("09-options")
    }
}
