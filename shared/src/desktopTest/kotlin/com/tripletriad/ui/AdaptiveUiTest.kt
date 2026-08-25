package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.net.ServerConnection
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

    /**
     * **A phone gives the grid the whole screen, and the card arrives over it.**
     *
     * This used to assert the opposite — that the detail was stacked *above* the grid — and it was
     * right until the collection moved the narrow layout's panel into a `ModalBottomSheet`. The
     * panel had a fixed height whether or not anything was picked, so a third of a phone screen was
     * spent on the words "pick a card"; a sheet costs nothing until there is something to read.
     *
     * So with nothing selected there is no detail node at all. The wide layout still sets the two
     * side by side — [theCollectionPutsTheDetailBesideTheGridOnAWideWindow], which is where the
     * empty note still lives.
     */
    @Test
    fun theCollectionOnAPhoneSpendsNoHeightOnADetailNobodyAskedFor() = runComposeUiTest {
        setContent { Sized(PHONE) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)

        assertFalse(
            exists(CARD_DETAIL_EMPTY_TEST_TAG),
            "a phone should not spend a third of its height saying `pick a card`",
        )
        assertFalse(exists(CARD_SHEET_TEST_TAG), "and nothing is picked, so no sheet is up")
    }

    /** The other half of the trade: picked, the card covers the grid rather than shrinking it. */
    @Test
    fun pickingACardOnAPhoneBringsItUpAsASheetOverTheGrid() = runComposeUiTest {
        setContent { Sized(PHONE) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)
        val card = STARTER_CARDS.first()

        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(card)))
        onNodeWithTag(cardCellTestTag(card)).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_SHEET_TEST_TAG) }
        assertTrue(exists(CARD_DETAIL_TEST_TAG), "the sheet carries the card's own detail")
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
        TestApp(store = settingsFor(AppLocale.EN_US), server = server)
    }
}
