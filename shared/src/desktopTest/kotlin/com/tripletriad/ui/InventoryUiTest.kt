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

/**
 * The bag: what is in it, and what Use, Sell and Discard do to the file.
 *
 * Every assertion is against the decoded `.sav` rather than the screen, because the AS3's bag is
 * exactly where screen and file came apart — `useBtnHandler` mutated `Game.PROFILE_DATAS` and saved
 * only as a side effect of the `sortBag()` call at its end. A test that read the list back off the
 * screen would have passed against that.
 */
@OptIn(ExperimentalTestApi::class)
class InventoryUiTest {
    /** The shipped table, because a card's resale is its rarity and only this knows it. */
    private val cards: Map<Int, Card> =
        kotlinx.coroutines.runBlocking { com.tripletriad.data.loadCardCatalog() }.all
            .associateBy { it.id }

    /** A character whose bag holds one of each kind that behaves differently. */
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

    /** Nothing is actionable until a row is picked — the three buttons are not even drawn. */
    @Test
    fun theActionsAppearOnlyOnceSomethingIsSelected() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertFalse(exists(INVENTORY_USE_TEST_TAG), "the footer is up with nothing selected")

        select(CardItem(SELLABLE_CARD))

        onNodeWithTag(INVENTORY_USE_TEST_TAG).assertIsEnabled()
    }

    /**
     * Tapping the selected row again puts the actions away.
     *
     * The bag is a radio group with no "none" entry, so the row itself has to be the way out —
     * `listHandler` in the original had the same job. Worth pinning because the footer acts on
     * whatever is selected: a selection that could not be cleared would leave three live buttons
     * pointed at an item the player had stopped thinking about.
     */
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

    /** Selling pays what the card's rarity is worth — see `CardValue` — and takes one off. */
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

    /** Only a card item is sellable, so both Sell buttons are dead on a pack. */
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

    /** Using a card item is how a card enters the collection. */
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

    /**
     * The card is **shown**, not merely named.
     *
     * `UnlockCardAnim` is the one thing the original does on this screen that a line of text
     * cannot. The player has usually never seen the card — that is what makes it worth having —
     * and the note beside the button gives them its name and nothing else.
     *
     * The reveal clears itself, which is the half worth asserting: it is drawn over the whole
     * screen, so one that never left would cover the bag for the rest of the session.
     */
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

    /**
     * Opening a pack reveals nothing, because nothing was unlocked.
     *
     * `useBtnHandler` plays the animation in the card branch alone (`:236-245`). A pack yields
     * another **bag** item, so revealing it here would show off a card the player does not own —
     * and the obvious implementation, "play it whenever Use produces a card id", does exactly
     * that: `PackOpened` carries one too.
     */
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

    /**
     * Use is **offered** for a card the profile already owns, and the row says it is a duplicate.
     *
     * The inverse of what this test asserted, and the inversion is the point. The AS3 enables the
     * button from [Item.useable] and disables it two lines later for an owned card
     * (`InventoryScreen.as:107-113`), because a second copy did nothing. A second copy is now a
     * card the player can put in a deck — § 1 of
     * `docs/migration/20-CARD-COPIES-AND-PLATFORM-ACCOUNTS.md` — so refusing would withhold the
     * one thing that makes a duplicate worth keeping. The fact is still shown; only the refusal
     * has gone. See `ownedNote`.
     */
    @Test
    fun useIsOfferedForACardAlreadyInTheCollectionAndSaysHowMany() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(CardItem(STARTER_CARDS.first()))

        onNodeWithTag(INVENTORY_USE_TEST_TAG).assertIsEnabled()
        assertTrue(isVisible("already owned \u00d71"), "the row still says it is not the first")
    }

    /**
     * Opening a pack turns its cards over, and they land in the **bag** rather than the collection.
     *
     * That second half is the original's behaviour and the whole point of a pack: what comes out
     * can be used or sold. See `ItemUse.PackOpened`.
     *
     * The first half is new. A pack deals [BoosterType.size] cards now, and they are revealed one
     * tap at a time on [PackRevealScreen] — so the note this test used to look for is gone, and
     * what it looks for instead is the reveal, the right number of face-down slots, and the way
     * out. The drawn ids are not pinned: `App` uses the default random.
     */
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
        for (slot in 0 until BoosterType.BRONZE.size) {
            onNodeWithTag(packSlotTestTag(slot), useUnmergedTree = true).assertExists()
        }
        assertFalse(
            existsUnmerged(packSlotTestTag(BoosterType.BRONZE.size)),
            "no slot past the pack's size",
        )

        repeat(BoosterType.BRONZE.size + 1) {
            onNodeWithTag(PACK_REVEAL_ACTION_TEST_TAG).performClick()
        }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(PACK_REVEAL_TEST_TAG) }

        val save = storedSave(documents)
        assertEquals(cardsBefore, save.cards, "a pack must not add to the collection directly")
        assertEquals(0, Inventory.count(save, BoosterItem(BoosterType.BRONZE)), "the pack is spent")
        val drawn = save.bag.filterIsInstance<CardItem>()
            .filter { it.cardId in BoosterType.BRONZE.pool }
        assertEquals(
            BoosterType.BRONZE.size,
            drawn.sumOf { it.stack },
            "every card the pack dealt should be in the bag: ${save.bag}",
        )
    }

    /** A potion raises its boon rather than doing anything to the collection. */
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

    /**
     * **Sell all** empties the stack in one tap, and is paid for every one of them.
     *
     * This replaced Discard, which destroyed an item for nothing and needed a two-tap arm to be
     * safe. Being paid is not something to protect a player from, so the arm went with it — and the
     * assertion below is the one that would have caught the arm being removed from the *wrong*
     * button: the whole stack goes, not one of it.
     */
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

    /**
     * At a stack of one it is disabled, because it would be the button beside it.
     *
     * Two controls that do the same thing invite the player to wonder which one they got wrong.
     */
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
        /** An ff14 card outside [STARTER_CARDS], so it is neither owned nor a starter. */
        val SELLABLE_CARD = Card.idFor(block = 1, number = 44)

        /** `CardItem.as:25` — `value = _cardId * 4`. */
    }
}
