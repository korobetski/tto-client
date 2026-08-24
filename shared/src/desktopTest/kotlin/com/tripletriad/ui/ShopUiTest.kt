package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.FF14_BLOCK
import com.tripletriad.data.Inventory
import com.tripletriad.data.ShopCatalog
import com.tripletriad.data.ShopOffer
import com.tripletriad.data.StarterPack
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.Card
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ShopUiTest {
    private fun profile(mgp: Int) = GameSave.new(createdAt = 0L).copy(mgp = mgp)

    private fun ComposeUiTest.openShop(documents: com.tripletriad.storage.InMemoryDocumentStore) {
        loadCharacter(documents)
        openFromBar("store", SHOP_LIST_TEST_TAG)
    }

    @Test
    fun buyingTakesTheMgpAndPutsTheItemInTheBag() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        onNodeWithTag(SHOP_BUY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).mgp == GameSave.STARTING_MGP - potion.price
        }

        val save = storedSave(documents)
        assertEquals(1, Inventory.count(save, potion.item), "the item should be in the bag")
        assertTrue(save.saveNumber >= 2, "the purchase was not written: ${save.saveNumber}")
    }

    @Test
    fun anUnaffordableOfferLeavesTheProfileAlone() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        val expensive = ShopCatalog.ff14.first { it.item == CardItem(MILLION_MGP_CARD) }
        onNodeWithTag(SHOP_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(shopOfferTestTag(expensive)))
        onNodeWithTag(shopOfferTestTag(expensive)).performClick()
        waitForIdle()

        onNodeWithTag(SHOP_BUY_TEST_TAG).assertIsNotEnabled()
        val save = storedSave(documents)
        assertEquals(GameSave.STARTING_MGP, save.mgp, "nothing should have been deducted")
        assertTrue(save.bag.isEmpty(), "and nothing should have been delivered")
    }

    @Test
    fun thereIsNothingToBuyUntilAnOfferIsPicked() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        // The button used to be a permanent bar at the foot of the screen, disabled for as long
        // as nothing was picked. It is in the purchase sheet now, so "nothing picked" is a
        // button that does not exist rather than one that cannot be pressed.
        assertFalse(exists(SHOP_SHEET_TEST_TAG), "no sheet before anything is picked")
        assertFalse(exists(SHOP_BUY_TEST_TAG), "and so no buy button")

        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_BUY_TEST_TAG) }

        onNodeWithTag(SHOP_BUY_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun buyingSaysWhatWasBought() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        onNodeWithTag(SHOP_BUY_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_NOTE_TEST_TAG) }
    }

    @Test
    fun buyingTheSameThingTwiceStacksIt() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        // The sheet now closes itself on a buy — see `StoreScreen.buy` — so the second purchase
        // reopens it on the same offer rather than pressing a button that is no longer there.
        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        onNodeWithTag(SHOP_BUY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).bag.isNotEmpty() }
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_BUY_TEST_TAG) }
        onNodeWithTag(SHOP_BUY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            Inventory.count(storedSave(documents), potion.item) == 2
        }

        assertEquals(1, storedSave(documents).bag.size, "one row, stack of two")
    }

    @Test
    fun bothShelvesAreOnOneScreen() = runComposeUiTest {
        val documents = seeded(profile(mgp = ENOUGH_FOR_ANY_PACK))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        // Only the item decides the tag; the price is derived and not what this test is about.
        val bronze = ShopOffer(BoosterItem(BoosterType.BRONZE), price = 1)
        onNodeWithTag(SHOP_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(shopOfferTestTag(bronze)))
        onNodeWithTag(shopOfferTestTag(bronze)).assertExists()

        onNodeWithTag(SHOP_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(shopOfferTestTag(ShopCatalog.ff8.last())))
        onNodeWithTag(shopOfferTestTag(ShopCatalog.ff8.last())).assertExists()
    }

    @Test
    fun aBoughtCardDoesNotEnterTheCollectionByItself() = runComposeUiTest {
        val offer = ShopCatalog.ff14.first { it.item == CardItem(CHEAP_CARD) }
        val documents = seeded(profile(mgp = offer.price))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        onNodeWithTag(SHOP_LIST_TEST_TAG).performScrollToNode(hasTestTag(shopOfferTestTag(offer)))
        onNodeWithTag(shopOfferTestTag(offer)).performClick()
        onNodeWithTag(SHOP_BUY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).bag.isNotEmpty() }

        val save = storedSave(documents)
        assertFalse(save.ownsCard(CHEAP_CARD), "buying is not owning")
        assertEquals(1, Inventory.count(save, CardItem(CHEAP_CARD)))
    }

    @Test
    fun theFreePackIsOfferedToACharacterThatCannotPlayAndThenGoesAway() = runComposeUiTest {
        val stranded = profile(mgp = 0).copy(cards = emptyMap(), decks = emptyList())
        val documents = seeded(stranded)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        assertTrue(exists(SHOP_STARTER_TEST_TAG), "a stranded character should be offered the pack")
        onNodeWithTag(SHOP_STARTER_CLAIM_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            !StarterPack.isOwedBy(storedSave(documents))
        }

        val save = storedSave(documents)
        for (id in starterFor(FF14_BLOCK).cards) {
            assertTrue(save.ownsCard(id), "starter card $id was not granted")
        }
        assertTrue(save.decks.first().isComplete, "and a deck was left ready to play")
        // The offer is gone the moment it is taken: it is read off the profile, not off a flag.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(SHOP_STARTER_TEST_TAG) }
    }

    @Test
    fun theFreePackIsAbsentForACharacterWithAStarterDeck() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        assertFalse(exists(SHOP_STARTER_TEST_TAG), "a playable character is owed nothing")
    }

    private companion object {
        val MILLION_MGP_CARD = Card.idFor(block = 1, number = 74)

        val CHEAP_CARD = Card.idFor(block = 1, number = 2)

        const val ENOUGH_FOR_ANY_PACK = 200_000
    }
}
