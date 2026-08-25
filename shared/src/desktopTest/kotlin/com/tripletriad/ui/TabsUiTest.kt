package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TabsUiTest {
    @Test
    fun theCardsEntryOpensTheCollectionAndItsOtherTabIsTheDecks() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)

        onNodeWithTag(screenTabTestTag("decks")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }
        assertFalse(exists(CARD_GRID_TEST_TAG), "both tabs were showing at once")

        onNodeWithTag(screenTabTestTag("cards")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_GRID_TEST_TAG) }
    }

    @Test
    fun theDecksEntryOpensTheSameScreenOnTheOtherTab() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        assertTrue(exists(COLLECTION_TABS_TEST_TAG), "it should be the tabbed cards screen")
        onNodeWithTag(screenTabTestTag("cards")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_GRID_TEST_TAG) }
    }

    @Test
    fun backLeavesTheDeckEditorBeforeItLeavesTheScreen() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        backToDashboard()
    }

    @Test
    fun theShopEntryOpensTheShelfAndTheBagIsOneTabAway() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("store", SHOP_LIST_TEST_TAG)
        // Buy is in the purchase sheet now, and nothing has opened one. What says the shelf is
        // up is the shelf itself — its three headers.
        assertFalse(exists(SHOP_BUY_TEST_TAG), "nothing is picked, so there is nothing to buy")
        assertTrue(exists(shopShelfTestTag("boons")), "the shelf should name its sections")

        onNodeWithTag(screenTabTestTag("bag")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(INVENTORY_EMPTY_TEST_TAG) }

        assertFalse(exists(SHOP_LIST_TEST_TAG), "both tabs were showing at once")
        assertFalse(exists(shopShelfTestTag("boons")), "the shelf stayed over the bag")
    }

    @Test
    fun theBagEntryOpensTheSameScreenOnTheOtherTab() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_INVENTORY_TEST_TAG, INVENTORY_EMPTY_TEST_TAG)

        assertTrue(exists(STORE_TABS_TEST_TAG), "it should be the tabbed store screen")
        onNodeWithTag(screenTabTestTag("shop")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_LIST_TEST_TAG) }
    }
}
