package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsProperties
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
import com.tripletriad.data.StarterCatalog
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
    /** A character with a box already opened — `GameSave.new` on its own owns nothing. */
    private fun profile(mgp: Int) = freshSave().copy(mgp = mgp)

    private fun ComposeUiTest.openShop(documents: com.tripletriad.storage.InMemoryDocumentStore) {
        loadCharacter(documents)
        openFromBar("store", SHOP_LIST_TEST_TAG)
    }

    /**
     * Walks to one shelf, which is now a thing the shop can be on the wrong one of.
     *
     * The shelves used to be three sections of one scroller and every offer was reachable by
     * scrolling far enough. One shelf shows at a time now — see `ShopBody` — so a test that wants
     * a potion has to ask for the boons the way a player does.
     */
    private fun ComposeUiTest.shelf(slug: String) {
        onNodeWithTag(shopShelfTestTag(slug)).performClick()
        waitForIdle()
    }

    /**
     * The words on one tagged line.
     *
     * Unmerged: every one of these lines is inside a tile that is itself clickable, and a
     * clickable node swallows its descendants' text into one blob.
     */
    private fun ComposeUiTest.lineOf(tag: String): String =
        onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

    @Test
    fun buyingTakesTheMgpAndPutsTheItemInTheBag() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("boons")
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("cards")
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        // The button used to be a permanent bar at the foot of the screen, disabled for as long
        // as nothing was picked. It is in the purchase sheet now, so "nothing picked" is a
        // button that does not exist rather than one that cannot be pressed.
        assertFalse(exists(SHOP_SHEET_TEST_TAG), "no sheet before anything is picked")
        assertFalse(exists(SHOP_BUY_TEST_TAG), "and so no buy button")

        shelf("boons")
        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_BUY_TEST_TAG) }

        onNodeWithTag(SHOP_BUY_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun buyingSaysWhatWasBought() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("boons")
        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        onNodeWithTag(SHOP_BUY_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_NOTE_TEST_TAG) }
    }

    @Test
    fun buyingTheSameThingTwiceStacksIt() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        // The sheet now closes itself on a buy — see `StoreScreen.buy` — so the second purchase
        // reopens it on the same offer rather than pressing a button that is no longer there.
        shelf("boons")
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
    fun eachShelfIsOneChipAwayAndOnlyOneIsOnScreen() = runComposeUiTest {
        val documents = seeded(profile(mgp = ENOUGH_FOR_ANY_PACK))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        // Only the item decides the tag; the price is derived and not what this test is about.
        val bronze = ShopOffer(BoosterItem(BoosterType.BRONZE), price = 1)
        val card = ShopCatalog.ff8.last()

        // The shop opens on the packs, and the cards are not underneath them any more: the rack
        // and the grid used to share one scroller, which is the second axis this replaces.
        onNodeWithTag(shopOfferTestTag(bronze)).assertExists()
        assertFalse(exists(shopOfferTestTag(card)), "the cards are on their own shelf")

        shelf("cards")
        onNodeWithTag(SHOP_LIST_TEST_TAG).performScrollToNode(hasTestTag(shopOfferTestTag(card)))
        onNodeWithTag(shopOfferTestTag(card)).assertExists()
        assertFalse(exists(shopOfferTestTag(bronze)), "and the packs went with their shelf")
    }

    /**
     * **A pack says how much of its pool the collection is still missing.**
     *
     * The number the purchase actually turns on, and the one the shelf never carried: two packs
     * at the same price are not the same offer when one of them can only hand back duplicates.
     * It is read off `BoosterType.pool` against the profile's own cards — see `packFacts` — so
     * nothing is fetched to say it.
     *
     * Asserted at both ends, because a line that says "6 still missing" whatever the collection
     * holds is not a fact about the collection.
     */
    @Test
    fun aPackSaysHowMuchOfItsPoolTheCollectionLacks() = runComposeUiTest {
        val pool = BoosterType.BRONZE.pool
        val documents = seeded(profile(mgp = ENOUGH_FOR_ANY_PACK).copy(cards = emptyMap()))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        val bronze = ShopOffer(BoosterItem(BoosterType.BRONZE), price = 1)
        onNodeWithTag(SHOP_LIST_TEST_TAG).performScrollToNode(hasTestTag(shopOfferTestTag(bronze)))
        assertEquals(
            "${pool.size} still missing",
            lineOf(shopPackMissingTestTag(bronze)),
            "a collection holding none of the pool is missing all of it",
        )
    }

    @Test
    fun aPackWhosePoolIsOwnedSaysSoInsteadOfCountingNothing() = runComposeUiTest {
        val owned = BoosterType.BRONZE.pool.associateWith { 1 }
        val documents = seeded(profile(mgp = ENOUGH_FOR_ANY_PACK).copy(cards = owned))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        val bronze = ShopOffer(BoosterItem(BoosterType.BRONZE), price = 1)
        onNodeWithTag(SHOP_LIST_TEST_TAG).performScrollToNode(hasTestTag(shopOfferTestTag(bronze)))
        assertEquals(
            "collection complete",
            lineOf(shopPackMissingTestTag(bronze)),
            "\"0 still missing\" is a sentence nobody writes",
        )
    }

    /**
     * **An unaffordable price says what is missing rather than only turning red.**
     *
     * "You need 400 more" is a match away and a grey button is not an instruction. The gap is
     * the client's own arithmetic over a price the client already has — the purchase itself is
     * still the server's to refuse, which is what `SHOP_BUY_TEST_TAG` staying disabled says.
     */
    @Test
    fun aPriceOutOfReachNamesTheGap() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("cards")
        val expensive = ShopCatalog.ff14.first { it.item == CardItem(MILLION_MGP_CARD) }
        onNodeWithTag(SHOP_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(shopOfferTestTag(expensive)))

        val short = expensive.price - GameSave.STARTING_MGP
        assertEquals(
            "you need ${grouped(short)} more",
            lineOf(shopShortTestTag(expensive)),
            "the shelf should name the gap it is asking to be closed",
        )

        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        shelf("boons")
        assertFalse(
            exists(shopShortTestTag(potion)),
            "an affordable offer is short of nothing and says nothing",
        )
    }

    @Test
    fun aBoughtCardDoesNotEnterTheCollectionByItself() = runComposeUiTest {
        val offer = ShopCatalog.ff14.first { it.item == CardItem(CHEAP_CARD) }
        // Not already owned: the box draws four of the block's commons, and this is one of them
        // under some seeds. What is being asserted is that buying does not add a card, which needs
        // a profile that does not have it to begin with.
        val documents = seeded(profile(mgp = offer.price).withoutCard(CHEAP_CARD))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("cards")
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        assertTrue(exists(SHOP_STARTER_TEST_TAG), "a stranded character should be offered the pack")
        onNodeWithTag(SHOP_STARTER_CLAIM_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            !StarterPack.isOwedBy(storedSave(documents))
        }

        val save = storedSave(documents)
        for (id in starterFor(FF14_BLOCK).deck) {
            assertTrue(save.ownsCard(id), "starter card $id was not granted")
        }
        assertEquals(
            StarterCatalog.SIZE,
            save.cards.size,
            "the repair deals the whole box: the authored five and the four it draws",
        )
        assertTrue(save.decks.first().isComplete, "and a deck was left ready to play")
        // The offer is gone the moment it is taken: it is read off the profile, not off a flag.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(SHOP_STARTER_TEST_TAG) }
    }

    @Test
    fun theFreePackIsAbsentForACharacterWithAStarterDeck() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        assertFalse(exists(SHOP_STARTER_TEST_TAG), "a playable character is owed nothing")
    }

    private companion object {
        val MILLION_MGP_CARD = Card.idFor(block = 1, number = 74)

        val CHEAP_CARD = Card.idFor(block = 1, number = 2)

        const val ENOUGH_FOR_ANY_PACK = 200_000
    }
}
