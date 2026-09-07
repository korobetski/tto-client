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
        openDecks()

        assertTrue(exists(COLLECTION_TABS_TEST_TAG), "it should be the tabbed cards screen")
        onNodeWithTag(screenTabTestTag("cards")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_GRID_TEST_TAG) }
    }

    @Test
    fun backLeavesTheDeckEditorBeforeItLeavesTheScreen() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openDecks()

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
        // up is the shelf itself — the chips that choose which of the three is on show.
        assertFalse(exists(SHOP_BUY_TEST_TAG), "nothing is picked, so there is nothing to buy")
        assertTrue(exists(shopShelfTestTag("boons")), "the shelf should name its sections")

        onNodeWithTag(screenTabTestTag("bag")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(INVENTORY_EMPTY_TEST_TAG) }

        assertFalse(exists(SHOP_LIST_TEST_TAG), "both tabs were showing at once")
        assertFalse(exists(shopShelfTestTag("boons")), "the shelf stayed over the bag")
    }

    /**
     * **The auction house is the store's third tab, not a screen behind a banner.**
     *
     * It was reached by tapping a row above the shelf, which is a navigation away from the store
     * to somewhere that lit the store's own tab in the bar. Asserted from the shelf: one tap, no
     * screen change — the store's tabs are still there behind the room.
     */
    @Test
    fun theAuctionHouseIsTheStoresThirdTab() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("store", SHOP_LIST_TEST_TAG)

        onNodeWithTag(screenTabTestTag("auction")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_SCREEN_TEST_TAG) }

        assertTrue(exists(STORE_TABS_TEST_TAG), "the store's tabs went with the shelf")
        assertFalse(exists(SHOP_LIST_TEST_TAG), "the shelf stayed under the auction house")

        // And back, which is what a tab is: the shelf returns without a navigation.
        onNodeWithTag(screenTabTestTag("shop")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_LIST_TEST_TAG) }
    }

    @Test
    fun theBagEntryOpensTheSameScreenOnTheOtherTab() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openInventory()

        assertTrue(exists(STORE_TABS_TEST_TAG), "it should be the tabbed store screen")
        onNodeWithTag(screenTabTestTag("shop")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_LIST_TEST_TAG) }
    }
}
