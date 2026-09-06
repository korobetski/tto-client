package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
            // Not one of the starter five, so Use is offered.
            CardItem(SELLABLE_CARD, stack = 2),
            // One of them, so Use is refused — `InventoryScreen.as:111`.
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

    @Test
    fun aPotionSaysWhatDrinkingItBuys() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertEquals(
            "MGP boosted for your next ${PotionType.MGP.modifier.value} matches",
            lineOf(inventoryEffectTestTag(PotionItem(PotionType.MGP))),
            "the row does not say what the potion does",
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
            lineOf(inventoryEffectTestTag(BoosterItem(BoosterType.BRONZE)))
                .endsWith("$missing still missing"),
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

    @Test
    fun useIsOfferedForACardAlreadyInTheCollectionAndSaysHowMany() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        onNodeWithTag(inventoryUseTestTag(CardItem(STARTER_CARDS.first()))).assertIsEnabled()
        assertTrue(isVisible("already owned \u00d71"), "the row still says it is not the first")
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

    private companion object {
        val SELLABLE_CARD = Card.idFor(block = 1, number = 44)
    }
}
