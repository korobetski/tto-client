package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.CardValue
import com.tripletriad.data.Inventory
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.Card
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InventoryUiTest {
    private val cards: Map<Int, Card> =
        kotlinx.coroutines.runBlocking { com.tripletriad.data.loadCardCatalog() }.all
            .associateBy { it.id }

    private fun withBag(): GameSave = Inventory.addAll(
        freshSave(),
        listOf(
            // Not one of the starter five, so the row calls it new.
            CardItem(SELLABLE_CARD, stack = 2),
            // One of them, so the row calls it a duplicate.
            CardItem(STARTER_CARDS.first()),
            BoosterItem(BoosterType.BRONZE),
            PotionItem(PotionType.MGP),
        ),
    )

    private fun ComposeUiTest.openBag(documents: com.tripletriad.storage.InMemoryDocumentStore) {
        loadCharacter(documents)
        openInventory()
    }

    /** The row's overflow menu, which is where the two sales live. */
    private fun ComposeUiTest.openMenu(item: Item) {
        onNodeWithTag(inventoryMenuTestTag(item)).performClick()
        waitForIdle()
    }

    /** The text of one tagged line, unmerged so a row's own lines stay separable. */
    private fun ComposeUiTest.lineOf(tag: String): String =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }

    @Test
    fun aFreshCharactersBagSaysItIsEmpty() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        openInventory()

        assertFalse(exists(INVENTORY_LIST_TEST_TAG), "an empty bag should not draw a list")
    }

    @Test
    fun everyRowCarriesItsOwnActionsWithNothingSelected() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        // No tap first: the bar under the list is gone, and with it the select-then-act detour.
        for (item in listOf(CardItem(SELLABLE_CARD), BoosterItem(BoosterType.BRONZE))) {
            onNodeWithTag(inventoryUseTestTag(item)).assertIsEnabled()
            onNodeWithTag(inventoryMenuTestTag(item)).assertIsEnabled()
        }
    }

    @Test
    fun theBagIsGroupedByKind() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        // The fixture holds one of each kind, so all three headers are due.
        for (group in BagGroup.entries) {
            assertTrue(
                exists(inventoryGroupTestTag(group.slug)),
                "no header for ${group.slug}",
            )
        }
    }

    @Test
    fun anEmptyBagOffersTheShop() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openInventory()

        onNodeWithTag(INVENTORY_SHOP_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_LIST_TEST_TAG) }
    }

    /** One line on the row, the sentence behind the ⋮ — a phone row had it wrap into three. */
    @Test
    fun aPotionSaysHowLongOnItsRowAndWhatItBuysInItsMenu() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)
        val potion = PotionItem(PotionType.MGP)
        val matches = PotionType.MGP.modifier.value

        assertEquals("next $matches matches", lineOf(inventoryGistTestTag(potion)))
        assertFalse(existsUnmerged(inventoryEffectTestTag(potion)), "the sentence is on the row")

        onNodeWithTag(inventoryMenuTestTag(potion)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(inventoryEffectTestTag(potion)) }
        assertEquals(
            "Gil boosted for your next $matches matches",
            lineOf(inventoryEffectTestTag(potion)),
            "the menu does not say what the potion does",
        )
    }

    @Test
    fun aPackInTheBagSaysHowMuchOfItIsNew() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        val pool = BoosterType.BRONZE.pool
        val missing = pool.count { !storedSave(documents).ownsCard(it) }
        check(missing > 0) { "the fixture needs a pack with something new in it" }
        assertTrue(
            lineOf(inventoryGistTestTag(BoosterItem(BoosterType.BRONZE)))
                .endsWith("$missing missing"),
            "the pack row does not count what the collection lacks",
        )
    }

    @Test
    fun sellingACardPaysForItAndLeavesTheRest() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)
        val before = storedSave(documents).mgp

        sellItem(CardItem(SELLABLE_CARD))
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).mgp > before }

        val save = storedSave(documents)
        assertEquals(
            before + CardValue.resaleOf(SELLABLE_CARD, cards),
            save.mgp,
            "the card's own resale value",
        )
        assertEquals(1, Inventory.count(save, CardItem(SELLABLE_CARD)), "one of the two sold")
    }

    @Test
    fun aPackCannotBeSoldByEitherButton() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        val pack = BoosterItem(BoosterType.BRONZE)
        openMenu(pack)

        // Both entries are still in the menu, greyed: a row whose menu is two lines on one item
        // and none on the next says nothing about *why*.
        onNodeWithTag(inventorySellTestTag(pack)).assertIsNotEnabled()
        onNodeWithTag(inventorySellAllTestTag(pack)).assertIsNotEnabled()
        // And Use is live, which is what stops a pack being stuck in the bag now that Discard is
        // gone: the two item kinds that cannot be sold are exactly the two that are consumed.
        onNodeWithTag(inventoryUseTestTag(pack)).assertIsEnabled()
    }

    @Test
    fun usingACardAddsItToTheCollection() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertFalse(storedSave(documents).ownsCard(SELLABLE_CARD))

        useItem(CardItem(SELLABLE_CARD))
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).ownsCard(SELLABLE_CARD) }

        assertEquals(
            1,
            Inventory.count(storedSave(documents), CardItem(SELLABLE_CARD)),
            "the used copy should be consumed",
        )
    }

    /**
     * At the shipped pace: this test's whole claim is that the reveal is *held* long enough to be
     * seen and then goes away on its own.
     *
     * `UnlockedCard` is 300ms in, 1.4s held, 200ms out. Scaled by [TEST_PACING] that is 38ms end to
     * end, and the first `waitUntil` misses the tag altogether — the card comes and goes between
     * two polls. Waiting for something to appear and then disappear cannot be made fast without
     * ceasing to be the same check.
     */
    @Test
    fun usingACardShowsIt() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                documents = documents,
                pacing = Pacing.Default,
            )
        }
        openBag(documents)

        useItem(CardItem(SELLABLE_CARD))

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(UNLOCKED_CARD_TEST_TAG) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(UNLOCKED_CARD_TEST_TAG) }
    }

    @Test
    fun openingAPackRevealsNothing() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        useItem(BoosterItem(BoosterType.BRONZE))
        // The pack leaving the bag is the signal the use went through. Its own size is not: a
        // pack out and a card in leaves it unchanged.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            Inventory.count(storedSave(documents), BoosterItem(BoosterType.BRONZE)) == 0
        }

        assertFalse(exists(UNLOCKED_CARD_TEST_TAG), "a pack unlocked nothing to show")
    }

    /**
     * **A duplicate is sold from its row and still added from its menu.**
     *
     * The row's face carries the likelier act, and a copy of a card already held is almost always
     * sold. Adding it is not taken away \u2014 it is one tap further.
     */
    @Test
    fun aDuplicateIsSoldFromItsRowAndAddedFromItsMenu() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        val duplicate = CardItem(STARTER_CARDS.first())
        assertEquals("Duplicate", lineOf(inventoryBadgeTestTag(duplicate)))
        assertTrue(isVisible("already owned \u00d71"), "the row still says it is not the first")
        onNodeWithTag(inventorySellTestTag(duplicate)).assertIsEnabled().assertTextEquals("Sell")
        assertFalse(exists(inventoryUseTestTag(duplicate)), "add is on the face of a duplicate")

        openMenu(duplicate)
        onNodeWithTag(inventoryUseTestTag(duplicate)).assertIsEnabled()
    }

    @Test
    fun aNewCardIsAddedFromItsRowAndSoldFromItsMenu() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        val fresh = CardItem(SELLABLE_CARD, stack = 2)
        assertEquals("New", lineOf(inventoryBadgeTestTag(fresh)))
        onNodeWithTag(inventoryUseTestTag(fresh)).assertTextEquals("Add")
        assertFalse(exists(inventorySellTestTag(fresh)), "sell is on the face of a new card")
        openMenu(fresh)
        onNodeWithTag(inventorySellTestTag(fresh)).assertIsEnabled()
    }

    /** Each kind of item is used by its own verb; the fixture holds a pack and a potion. */
    @Test
    fun aPackIsOpenedAndAPotionActivated() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        onNodeWithTag(inventoryUseTestTag(BoosterItem(BoosterType.BRONZE))).assertTextEquals("Open")
        onNodeWithTag(inventoryUseTestTag(PotionItem(PotionType.MGP))).assertTextEquals("Activate")
        assertFalse(
            exists(inventoryBadgeTestTag(BoosterItem(BoosterType.BRONZE))),
            "a pack is neither new nor a duplicate",
        )
    }

    /** Below two of either, the bar would be a row's own button said a second time. */
    @Test
    fun theBulkActionsWaitForTwoOfSomething() = runComposeUiTest {
        // One new card (a stack of two is still one card) and one duplicate copy.
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertFalse(exists(INVENTORY_ADD_NEW_TEST_TAG))
        assertFalse(exists(INVENTORY_SELL_DUPLICATES_TEST_TAG))
    }

    /** Two duplicate copies earn the sale on their own; one new card beside them earns no add. */
    @Test
    fun oneNewCardBesideTwoDuplicatesIsOfferedOnlyTheSale() = runComposeUiTest {
        val documents = seeded(
            Inventory.addAll(
                freshSave(),
                listOf(CardItem(SELLABLE_CARD), CardItem(STARTER_CARDS.first(), stack = 2)),
            ),
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertTrue(exists(INVENTORY_SELL_DUPLICATES_TEST_TAG), "two duplicates are not offered")
        assertFalse(exists(INVENTORY_ADD_NEW_TEST_TAG), "a single new card is offered in bulk")
    }

    /**
     * **"Add the new ones" adds one copy of each card the collection lacks, and nothing else.**
     *
     * The second copy of a new card stays in the bag: it is a duplicate the moment the first lands.
     */
    @Test
    fun addingTheNewOnesAddsOneCopyOfEach() = runComposeUiTest {
        val documents = seeded(withCards())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        onNodeWithTag(INVENTORY_ADD_NEW_TEST_TAG).assertTextEquals("Add the new ones (2)")
            .performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).copiesOf(SECOND_NEW_CARD) == 1
        }

        val save = storedSave(documents)
        assertEquals(1, save.copiesOf(SELLABLE_CARD))
        assertEquals(1, Inventory.count(save, CardItem(SELLABLE_CARD)), "the second copy was added")
        assertEquals(
            2,
            Inventory.count(save, CardItem(STARTER_CARDS.first())),
            "a duplicate was added",
        )
    }

    @Test
    fun sellingTheDuplicatesTakesASecondTapAndSellsOnlyThem() = runComposeUiTest {
        val documents = seeded(withCards())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        val duplicate = CardItem(STARTER_CARDS.first())
        val payout = Inventory.priceOf(duplicate, cards) * 2
        val before = storedSave(documents).mgp

        onNodeWithTag(INVENTORY_SELL_DUPLICATES_TEST_TAG)
            .assertTextEquals("Sell duplicates (2)$DOT_SEPARATOR$payout")
            .performClick()
        waitForIdle()
        assertEquals(2, Inventory.count(storedSave(documents), duplicate), "one tap sold them")

        onNodeWithTag(INVENTORY_SELL_DUPLICATES_TEST_TAG)
            .assertTextEquals("Confirm: sell 2 for $payout")
            .performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            Inventory.count(storedSave(documents), duplicate) == 0
        }

        val save = storedSave(documents)
        assertEquals(before + payout, save.mgp)
        assertEquals(2, Inventory.count(save, CardItem(SELLABLE_CARD)), "a new card was sold")
    }

    @Test
    fun openingAPackRevealsItsCardsAndPutsThemInTheBag() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)
        val cardsBefore = storedSave(documents).cards

        useItem(BoosterItem(BoosterType.BRONZE))
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PACK_REVEAL_TEST_TAG) }

        // Every card is on screen from the start, face down. Turning them over is the player's.
        //
        // Unmerged: the whole screen is one `clickable` — a tap anywhere reveals the next card —
        // and Compose folds the slots' semantics into it. See `existsUnmerged`.
        for (slot in 0 until BoosterType.BRONZE.cardCount) {
            onNodeWithTag(packSlotTestTag(slot), useUnmergedTree = true).assertExists()
        }
        assertFalse(
            existsUnmerged(packSlotTestTag(BoosterType.BRONZE.cardCount)),
            "no slot past the pack's size",
        )

        repeat(BoosterType.BRONZE.cardCount + 1) {
            onNodeWithTag(PACK_REVEAL_ACTION_TEST_TAG).performClick()
        }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(PACK_REVEAL_TEST_TAG) }

        val save = storedSave(documents)
        assertEquals(cardsBefore, save.cards, "a pack must not add to the collection directly")
        assertEquals(0, Inventory.count(save, BoosterItem(BoosterType.BRONZE)), "the pack is spent")
        val drawn = save.bag.filterIsInstance<CardItem>()
            .filter { it.cardId in BoosterType.BRONZE.pool }
        assertEquals(
            BoosterType.BRONZE.cardCount,
            drawn.sumOf { it.stack },
            "every card the pack dealt should be in the bag: ${save.bag}",
        )
    }

    @Test
    fun drinkingAPotionRaisesTheBoon() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertEquals(0, storedSave(documents).boons.mgp)

        useItem(PotionItem(PotionType.MGP))
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).boons.mgp > 0 }

        val save = storedSave(documents)
        assertEquals(PotionType.MGP.modifier.value, save.boons.mgp, "the potion's own value")
        assertEquals(0, Inventory.count(save, PotionItem(PotionType.MGP)), "and it is consumed")
    }

    /** The luck boon is the port's, so the bar that shows the other two must learn to show it. */
    @Test
    fun drinkingALuckPotionShowsItsBoonOnTheBar() = runComposeUiTest {
        val documents = seeded(Inventory.add(freshSave(), PotionItem(PotionType.LUCK)))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)
        assertTrue(onAllNodesWithContentDescription("Luck").fetchSemanticsNodes().isEmpty())

        useItem(PotionItem(PotionType.LUCK))
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).boons.luck > 0 }

        assertEquals(PotionType.LUCK.modifier.value, storedSave(documents).boons.luck)
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            onAllNodesWithContentDescription("Luck").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun sellingAllEmptiesTheStackAndPaysForEveryOne() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        val before = storedSave(documents)
        val held = Inventory.count(before, CardItem(SELLABLE_CARD))
        val each = Inventory.priceOf(CardItem(SELLABLE_CARD), cards)
        check(held > 1) { "the fixture needs a stack to empty, had $held" }

        sellAllItems(CardItem(SELLABLE_CARD, stack = held))

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            Inventory.count(storedSave(documents), CardItem(SELLABLE_CARD)) == 0
        }
        assertEquals(
            before.mgp + each * held,
            storedSave(documents).mgp,
            "selling $held paid for fewer than $held",
        )
    }

    @Test
    fun sellAllIsInertWhenThereIsOnlyOne() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        val single = CardItem(STARTER_CARDS.first())
        openMenu(single)

        onNodeWithTag(inventorySellAllTestTag(single)).assertIsNotEnabled()
        onNodeWithTag(inventorySellTestTag(single)).assertIsEnabled()
    }

    @Test
    fun sellingAWholeStackTakesASecondTap() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        val held = Inventory.count(storedSave(documents), CardItem(SELLABLE_CARD))
        val each = Inventory.priceOf(CardItem(SELLABLE_CARD), cards)
        check(held > 1) { "the fixture needs a stack to confirm over, had $held" }

        openMenu(CardItem(SELLABLE_CARD))
        onNodeWithTag(inventorySellAllTestTag(CardItem(SELLABLE_CARD))).performClick()
        waitForIdle()

        assertEquals(
            held,
            Inventory.count(storedSave(documents), CardItem(SELLABLE_CARD)),
            "one tap emptied the stack",
        )
        assertTrue(
            isVisible("Confirm: sell $held for ${each * held}"),
            "the second tap is not named, so nothing says what it will do",
        )
    }

    /** Two new cards, one of them twice, and two copies of a card the collection holds. */
    private fun withCards(): GameSave = Inventory.addAll(
        freshSave(),
        listOf(
            CardItem(SELLABLE_CARD, stack = 2),
            CardItem(SECOND_NEW_CARD),
            CardItem(STARTER_CARDS.first(), stack = 2),
        ),
    )

    private companion object {
        val SELLABLE_CARD = Card.idFor(block = 1, number = 44)
        val SECOND_NEW_CARD = Card.idFor(block = 1, number = 45)
    }
}
