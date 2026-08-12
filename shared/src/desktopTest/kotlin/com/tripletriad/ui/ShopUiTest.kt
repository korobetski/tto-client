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

/**
 * The shop: what is on each shelf, and that a purchase is atomic and written.
 *
 * The two things the original got wrong are both asserted here rather than only in
 * [com.tripletriad.data.ShopCatalogTest], because both were screen-level: the AS3 deducted the
 * price before checking it could be paid (`shopScreen.as:144-146`), and it never called `Save.save`
 * at all (`:149`), so every purchase was lost on quit.
 */
@OptIn(ExperimentalTestApi::class)
class ShopUiTest {
    private fun profile(mgp: Int) = GameSave.new(createdAt = 0L).copy(mgp = mgp)

    private fun ComposeUiTest.openShop(documents: com.tripletriad.storage.InMemoryDocumentStore) {
        loadCharacter(documents)
        openFromBar("store", SHOP_LIST_TEST_TAG)
    }

    /** A fresh character has exactly enough for one 50 MGP potion, twice over. */
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

    /**
     * An offer out of reach cannot be bought, and neither half of the transaction happens.
     *
     * The AS3 subtracted first and used the check only to decide whether the button stayed lit, so
     * this is the assertion that the order was fixed.
     */
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

    /** Buy is dead with nothing picked, which is `buyBtn.isEnabled = false` at construction. */
    @Test
    fun buyIsDeadUntilAnOfferIsPicked() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        onNodeWithTag(SHOP_BUY_TEST_TAG).assertIsNotEnabled()

        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        waitForIdle()

        onNodeWithTag(SHOP_BUY_TEST_TAG).assertIsEnabled()
    }

    /**
     * A purchase says what was bought.
     *
     * The gap this closes is the original's: `buyBtn_triggeredHandler` deducted the price, pushed
     * the item and returned, so a 50 MGP potion and a 30 000 MGP card looked identical from the
     * player's side — a number in the corner changed. Asserted through the snackbar's tag rather
     * than its wording, which names a catalogue entry and is `ShopCatalogTest`'s business.
     *
     * `waitUntil` and not `waitForIdle`: the note is transient by design, and a wait that ran the
     * clock to quiescence would advance past its four seconds and find nothing.
     */
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

    /** Two of the same offer stack into one row rather than becoming two — the AS3 `push`ed. */
    @Test
    fun buyingTheSameThingTwiceStacksIt() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        onNodeWithTag(SHOP_BUY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).bag.isNotEmpty() }
        onNodeWithTag(SHOP_BUY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            Inventory.count(storedSave(documents), potion.item) == 2
        }

        assertEquals(1, storedSave(documents).bag.size, "one row, stack of two")
    }

    /**
     * Both shelves are on one screen, which is what taking `MODE` out of the shop looks like.
     *
     * This replaces a test asserting that an FFVIII profile was offered no booster pack. That was
     * true because the shelf was chosen by the character's collection, and it is not a fact about
     * the shop any more: the app plays the widest format, so every offer that format admits is on
     * sale to everybody. That no booster *pool* names an FFVIII id is still true and still data —
     * it belongs to `ShopCatalog`, not to a screen.
     */
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

    /** A bought card is a bag item; using it is the step that adds it to the collection. */
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

    /**
     * The free pack is on the shelf only for a character that cannot field a hand.
     *
     * The recovery path for accounts already stored on a server, which [StarterPack.opened] can
     * only help before they exist. See [com.tripletriad.data.StarterPack].
     */
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

    /** And it is absent for everybody else, so it is a repair and not a giveaway. */
    @Test
    fun theFreePackIsAbsentForACharacterWithAStarterDeck() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        assertFalse(exists(SHOP_STARTER_TEST_TAG), "a playable character is owed nothing")
    }

    private companion object {
        /** `STR_FF14_CARD_74`, at 1,000,000 MGP the most expensive thing in the game. */
        val MILLION_MGP_CARD = Card.idFor(block = 1, number = 74)

        /** `STR_FF14_CARD_2`, at 120 MGP the cheapest card on the shelf. */
        val CHEAP_CARD = Card.idFor(block = 1, number = 2)

        /** More than the dearest pack: absence is the shelf's doing, not the purse's. */
        const val ENOUGH_FOR_ANY_PACK = 200_000
    }
}
