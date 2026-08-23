package com.tripletriad.ui

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
        GameSave.new(createdAt = 0L),
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
        openFromDashboard(DASHBOARD_INVENTORY_TEST_TAG, INVENTORY_LIST_TEST_TAG)
    }

    private fun ComposeUiTest.select(item: Item) {
        onNodeWithTag(inventoryRowTestTag(item)).performClick()
        waitForIdle()
    }

    @Test
    fun aFreshCharactersBagSaysItIsEmpty() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        openFromDashboard(DASHBOARD_INVENTORY_TEST_TAG, INVENTORY_EMPTY_TEST_TAG)

        assertFalse(exists(INVENTORY_LIST_TEST_TAG), "an empty bag should not draw a list")
    }

    @Test
    fun theActionsAppearOnlyOnceSomethingIsSelected() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertFalse(exists(INVENTORY_USE_TEST_TAG), "the footer is up with nothing selected")

        select(CardItem(SELLABLE_CARD))

        onNodeWithTag(INVENTORY_USE_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun tappingTheSelectedRowAgainClearsTheSelection() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(CardItem(SELLABLE_CARD))
        assertTrue(exists(INVENTORY_USE_TEST_TAG), "the footer should be up")

        select(CardItem(SELLABLE_CARD))

        assertFalse(exists(INVENTORY_USE_TEST_TAG), "a second tap should put the footer away")
    }

    @Test
    fun sellingACardPaysForItAndLeavesTheRest() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)
        val before = storedSave(documents).mgp

        select(CardItem(SELLABLE_CARD))
        onNodeWithTag(INVENTORY_SELL_TEST_TAG).performClick()
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
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(BoosterItem(BoosterType.BRONZE))

        onNodeWithTag(INVENTORY_SELL_TEST_TAG).assertIsNotEnabled()
        onNodeWithTag(INVENTORY_SELL_ALL_TEST_TAG).assertIsNotEnabled()
        // And Use is live, which is what stops a pack being stuck in the bag now that Discard is
        // gone: the two item kinds that cannot be sold are exactly the two that are consumed.
        onNodeWithTag(INVENTORY_USE_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun usingACardAddsItToTheCollection() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertFalse(storedSave(documents).ownsCard(SELLABLE_CARD))

        select(CardItem(SELLABLE_CARD))
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).ownsCard(SELLABLE_CARD) }

        assertEquals(
            1,
            Inventory.count(storedSave(documents), CardItem(SELLABLE_CARD)),
            "the used copy should be consumed",
        )
    }

    @Test
    fun usingACardShowsIt() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(CardItem(SELLABLE_CARD))
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(UNLOCKED_CARD_TEST_TAG) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(UNLOCKED_CARD_TEST_TAG) }
    }

    @Test
    fun openingAPackRevealsNothing() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(BoosterItem(BoosterType.BRONZE))
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
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
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(CardItem(STARTER_CARDS.first()))

        onNodeWithTag(INVENTORY_USE_TEST_TAG).assertIsEnabled()
        assertTrue(isVisible("already owned \u00d71"), "the row still says it is not the first")
    }

    @Test
    fun openingAPackRevealsItsCardsAndPutsThemInTheBag() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)
        val cardsBefore = storedSave(documents).cards

        select(BoosterItem(BoosterType.BRONZE))
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
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
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertEquals(0, storedSave(documents).boons.mgp)

        select(PotionItem(PotionType.MGP))
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).boons.mgp > 0 }

        val save = storedSave(documents)
        assertEquals(PotionType.MGP.modifier.value, save.boons.mgp, "the potion's own value")
        assertEquals(0, Inventory.count(save, PotionItem(PotionType.MGP)), "and it is consumed")
    }

    @Test
    fun sellingAllEmptiesTheStackAndPaysForEveryOne() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        val before = storedSave(documents)
        val held = Inventory.count(before, CardItem(SELLABLE_CARD))
        val each = Inventory.priceOf(CardItem(SELLABLE_CARD), cards)
        check(held > 1) { "the fixture needs a stack to empty, had $held" }

        select(CardItem(SELLABLE_CARD, stack = held))
        onNodeWithTag(INVENTORY_SELL_ALL_TEST_TAG).performClick()

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
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(CardItem(STARTER_CARDS.first()))

        onNodeWithTag(INVENTORY_SELL_ALL_TEST_TAG).assertIsNotEnabled()
        onNodeWithTag(INVENTORY_SELL_TEST_TAG).assertIsEnabled()
    }

    private companion object {
        val SELLABLE_CARD = Card.idFor(block = 1, number = 44)
    }
}
