package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.height
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
        assertFalse(exists(SHOP_OFFER_DETAIL_TEST_TAG), "no sheet before anything is picked")
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

        // This window is wide, so the pick stays in the pane after a buy — see `StoreScreen.buy`
        // — and the second purchase is the same button pressed again. A phone's sheet closes
        // itself instead, which `ShopLayoutUiTest` covers at a phone's width.
        shelf("boons")
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
     * **Ten packs are one tap on the shortcut, one price and one delivery.**
     *
     * The count travels with the purchase — see `Intent.Buy` and `BuyRequest.count` — so this is
     * one write rather than ten, and the purse is charged `priceFor(10)` in a single step. The
     * purse is set to exactly ten of the price so the shortcut's own ceiling is ten: that button
     * offers what the purse allows, and what it offers is what `:core` will charge for.
     */
    @Test
    fun theQuantityShortcutBuysAsManyAsThePurseAllows() = runComposeUiTest {
        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        val documents = seeded(profile(mgp = potion.price * TEN))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("boons")
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_MOST_TEST_TAG) }
        onNodeWithTag(SHOP_MOST_TEST_TAG).performClick()
        waitForIdle()
        assertEquals("$TEN", lineOf(SHOP_COUNT_TEST_TAG), "the shortcut should jump to the most")

        onNodeWithTag(SHOP_BUY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).bag.isNotEmpty() }

        val save = storedSave(documents)
        assertEquals(TEN, Inventory.count(save, potion.item), "ten bought, ten delivered")
        assertEquals(0, save.mgp, "and ten paid for")
        assertEquals(1, save.bag.size, "one row, stack of ten")
    }

    /**
     * **The stepper is there only when there is a choice to make.**
     *
     * A purse that affords one is offered no quantity at all, and the button is the plain "Buy"
     * it always was; a purse that affords two starts at one, with only the "+" live. Both halves
     * matter: a row of dead buttons over every offer a starting purse can barely reach is the
     * clutter this feature would otherwise add to the sheet.
     */
    @Test
    fun theStepperAppearsOnlyWhenThePurseCanAffordASecond() = runComposeUiTest {
        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        val documents = seeded(profile(mgp = potion.price))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("boons")
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_BUY_TEST_TAG) }
        assertFalse(exists(SHOP_MORE_TEST_TAG), "one affordable, so nothing to step through")
        assertFalse(exists(SHOP_COUNT_TEST_TAG))
    }

    /** The other half: a purse for two opens the stepper at one, and it stops at two. */
    @Test
    fun aPurseForTwoOpensTheStepperAtOneAndStopsAtTwo() = runComposeUiTest {
        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        val documents = seeded(profile(mgp = potion.price * 2))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("boons")
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_MORE_TEST_TAG) }
        assertEquals("1", lineOf(SHOP_COUNT_TEST_TAG), "a stepper opens on one")
        onNodeWithTag(SHOP_FEWER_TEST_TAG).assertIsNotEnabled()

        onNodeWithTag(SHOP_MORE_TEST_TAG).performClick()
        waitForIdle()
        assertEquals("2", lineOf(SHOP_COUNT_TEST_TAG))
        onNodeWithTag(SHOP_MORE_TEST_TAG).assertIsNotEnabled()
        onNodeWithTag(SHOP_FEWER_TEST_TAG).assertIsEnabled()
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
     * **The gap is named in the sheet, and nowhere on the shelf.**
     *
     * "You need 400 more" is a match away and a grey button is not an instruction, so the number
     * is still written — but under the offer that was tapped, not under every price in the grid.
     * On the tile the red is the whole message: the line said the same thing a second time and
     * only some tiles had it, which is what left the grid at two heights.
     */
    @Test
    fun aPriceOutOfReachNamesTheGapInTheSheetAndNotOnTheTile() = runComposeUiTest {
        val documents = seeded(profile(mgp = GameSave.STARTING_MGP))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("cards")
        val expensive = ShopCatalog.ff14.first { it.item == CardItem(MILLION_MGP_CARD) }
        onNodeWithTag(SHOP_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(shopOfferTestTag(expensive)))

        assertFalse(
            exists(shopShortTestTag(expensive)),
            "the tile still carries the line the red replaced",
        )

        onNodeWithTag(shopOfferTestTag(expensive)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_OFFER_DETAIL_TEST_TAG) }

        val short = expensive.price - GameSave.STARTING_MGP
        assertEquals(
            "you need ${grouped(short)} more",
            lineOf(shopShortTestTag(expensive)),
            "the sheet should name the gap it is asking to be closed",
        )
    }

    /**
     * **Every tile on a shelf is the same height, affordable or not.**
     *
     * The shortfall line was drawn under the prices the purse could not reach and under no
     * others, so a grid of eleven packs came out at two heights with the rows stepping around
     * each other. Asserted on the boosters, where the cheapest is affordable on a starting purse
     * and the dearest is not.
     */
    @Test
    fun everyPackTileIsTheSameHeight() = runComposeUiTest {
        // Enough for the cheapest pack and nowhere near the dearest, which is the pair of states
        // the tile used to draw at two heights. The prices are the card table's — see
        // `BoosterPricing` — so the fixture names a purse between them rather than a literal.
        val documents = seeded(profile(mgp = MID_PURSE))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("boosters")
        // Any price: only the item's slug reaches the tag, and an offer must cost something.
        val cheapest = ShopOffer(BoosterItem(BoosterType.BRONZE), price = 1)
        val dearest = ShopOffer(BoosterItem(BoosterType.PLATINUM), price = 1)

        onNodeWithTag(SHOP_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(shopOfferTestTag(dearest)))
        val tall = onNodeWithTag(shopOfferTestTag(dearest)).getUnclippedBoundsInRoot().height
        onNodeWithTag(SHOP_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(shopOfferTestTag(cheapest)))
        assertEquals(
            tall,
            onNodeWithTag(shopOfferTestTag(cheapest)).getUnclippedBoundsInRoot().height,
            "an unaffordable pack is drawn taller than one the purse can reach",
        )
    }

    /** The five packs of the series carry the rank they are named after; a themed pack does not. */
    @Test
    fun aBasePackWearsItsRankAndAThemedPackDoesNot() = runComposeUiTest {
        val documents = seeded(profile(mgp = ENOUGH_FOR_ANY_PACK))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        val mithril = ShopOffer(BoosterItem(BoosterType.MITHRIL), price = 1)
        val beast = ShopOffer(BoosterItem(BoosterType.BEAST), price = 1)
        onNodeWithTag(SHOP_LIST_TEST_TAG).performScrollToNode(hasTestTag(shopOfferTestTag(mithril)))
        onNodeWithTag(shopRarityTestTag(mithril), useUnmergedTree = true)
            .assertContentDescriptionEquals("★★★★")
        onNodeWithTag(SHOP_LIST_TEST_TAG).performScrollToNode(hasTestTag(shopOfferTestTag(beast)))
        assertFalse(exists(shopRarityTestTag(beast)), "a themed pack has no rank in the series")
    }

    /** A pack out of reach says by how much under its price; one in reach says nothing there. */
    @Test
    fun aPackOutOfReachNamesTheGapOnItsTile() = runComposeUiTest {
        val documents = seeded(profile(mgp = MID_PURSE))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        val packs = ShopCatalog.boosterOffers(pvpCards.all.associateBy { it.id })
        val dearest = packs.maxBy { it.price }
        val cheapest = packs.minBy { it.price }
        onNodeWithTag(SHOP_LIST_TEST_TAG).performScrollToNode(hasTestTag(shopOfferTestTag(dearest)))
        assertEquals(
            "you need ${grouped(dearest.price - MID_PURSE)} more",
            lineOf(shopTileShortTestTag(dearest)),
        )
        onNodeWithTag(
            SHOP_LIST_TEST_TAG,
        ).performScrollToNode(hasTestTag(shopOfferTestTag(cheapest)))
        assertFalse(exists(shopTileShortTestTag(cheapest)), "a pack in reach was told it was short")
    }

    /**
     * **A purse that reaches no pack is shown the boons it does reach, on the packs' shelf.**
     *
     * And only then: with one pack affordable, the strip is noise over the shelf it sits on.
     */
    @Test
    fun aPurseShortOfEveryPackIsOfferedTheBoonsItReaches() = runComposeUiTest {
        val cheapestPack = ShopCatalog.boosterOffers(pvpCards.all.associateBy { it.id })
            .minOf { it.price }
        val boons = ShopCatalog.ff14.filter { it.item is PotionItem }
        val cheapestBoon = boons.minBy { it.price }
        assertTrue(cheapestBoon.price < cheapestPack, "the fixture needs a boon under every pack")
        val documents = seeded(profile(mgp = cheapestBoon.price))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("boosters")
        onNodeWithTag(SHOP_WITHIN_REACH_TEST_TAG).assertExists()
        onNodeWithTag(shopOfferTestTag(cheapestBoon)).assertExists()
        boons.filter { it.price > cheapestBoon.price }.forEach {
            assertFalse(exists(shopOfferTestTag(it)), "a boon out of reach was brought forward")
        }
    }

    @Test
    fun aPurseThatReachesAPackIsNotShownTheBoons() = runComposeUiTest {
        val documents = seeded(profile(mgp = MID_PURSE))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        shelf("boosters")
        assertFalse(exists(SHOP_WITHIN_REACH_TEST_TAG))
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

        /** Above the bronze pouch and below the platinum one, which `BoosterPricing` sets. */
        const val MID_PURSE = 1_000

        const val TEN = 10
    }
}
