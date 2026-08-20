package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.AppLocale
import com.tripletriad.net.ServerConnection
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AdaptiveUiTest {
    private val stub = PveStubServer()

    @Test
    fun aPhoneWidthWindowGetsTheBottomBar() = runComposeUiTest {
        setContent { Sized(PHONE) }
        newCharacter()

        assertTrue(exists(NAV_BAR_TEST_TAG), "a compact window should carry the bar")
        assertFalse(exists(NAV_RAIL_TEST_TAG), "and not the rail as well")
    }

    @Test
    fun aWindowWideEnoughGetsTheRailInstead() = runComposeUiTest {
        setContent { Sized(DESKTOP) }
        newCharacter()

        assertTrue(exists(NAV_RAIL_TEST_TAG), "a wide window should carry the rail")
        assertFalse(exists(NAV_BAR_TEST_TAG), "and not the bar as well")
    }

    @Test
    fun theCollectionStacksItsDetailOnAPhoneAndSetsItBesideTheGridOnAWindow() = runComposeUiTest {
        setContent { Sized(PHONE) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)

        val stackedDetail = onNodeWithTag(CARD_DETAIL_EMPTY_TEST_TAG).getUnclippedBoundsInRoot()
        val stackedGrid = onNodeWithTag(CARD_GRID_TEST_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            stackedDetail.bottom <= stackedGrid.top,
            "the detail should be above the grid: $stackedDetail vs $stackedGrid",
        )
    }

    @Test
    fun theCollectionPutsTheDetailBesideTheGridOnAWideWindow() = runComposeUiTest {
        setContent { Sized(DESKTOP) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)

        val detail = onNodeWithTag(CARD_DETAIL_EMPTY_TEST_TAG).getUnclippedBoundsInRoot()
        val grid = onNodeWithTag(CARD_GRID_TEST_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            detail.left >= grid.right,
            "the detail should be beside the grid: $detail vs $grid",
        )
    }

    @Test
    fun theMatchGetsASidePanelOnlyOnAWideWindow() = runComposeUiTest {
        setContent { Sized(DESKTOP, stub.connection) }
        startMatch()

        assertTrue(exists(MATCH_SIDE_TEST_TAG), "a wide window should put a panel beside the board")
        assertTrue(exists(MATCH_LOG_TEST_TAG), "and the panel should carry the move log")
    }

    @Test
    fun theMatchOnAPhoneKeepsTheWholeWidthForTheBoard() = runComposeUiTest {
        setContent { Sized(PHONE, stub.connection) }
        startMatch()

        assertFalse(exists(MATCH_SIDE_TEST_TAG), "a phone has no width to give a panel")
        assertTrue(exists(MATCH_RULES_TEST_TAG), "so the rules go above the board instead")
    }

    @Test
    fun theMoveLogRecordsWhoeverPlayed() = runComposeUiTest {
        setContent { Sized(DESKTOP, stub.connection) }
        startMatch()

        playOneCard()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { handSize(CardColor.RED) < HAND_SIZE }

        val entries = onAllNodesWithTag(MATCH_LOG_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .single()
            .children
            .size
        assertTrue(entries >= 2, "both sides have played, so the log should hold both: $entries")
    }

    private companion object {
        val PHONE = 380.dp

        val DESKTOP = 1100.dp
    }
}

@androidx.compose.runtime.Composable
private fun Sized(width: androidx.compose.ui.unit.Dp, server: ServerConnection? = null) {
    Box(modifier = Modifier.width(width).fillMaxHeight()) {
        App(store = settingsFor(AppLocale.EN_US), server = server)
    }
}
