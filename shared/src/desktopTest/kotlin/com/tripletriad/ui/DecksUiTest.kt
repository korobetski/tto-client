package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.Card
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DecksUiTest {
    private fun ComposeUiTest.openDecks() {
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
    }

    @Test
    fun allFiveSlotsAreListedIncludingTheEmptyOnes() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openDecks()

        for (slot in 0 until GameSave.MAX_DECKS) {
            onNodeWithTag(deckSlotTestTag(slot)).assertExists()
        }
        assertTrue(isVisible(GameSave.DEFAULT_DECK_NAME), "slot 0 holds the starter deck")
        // An unnamed slot is labelled by its 1-based number rather than left blank.
        assertTrue(isVisible("Deck 5"), "an empty slot should still be named")
    }

    @Test
    fun openingASlotShowsItsCardsAndBackReturnsToTheSlots() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openDecks()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        for (position in 0 until HAND_SIZE) {
            onNodeWithTag(deckPositionTestTag(position)).assertExists()
        }

        // Back inside the editor returns to the slot list rather than leaving the screen.
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }
    }

    @Test
    fun removingACardAndSavingWritesTheShorterDeck() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        onNodeWithTag(deckPositionTestTag(0)).performClick()
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).decks.first().cards.size == HAND_SIZE - 1
        }

        val deck = storedSave(documents).decks.first()
        assertEquals(STARTER_DECK.drop(1), deck.cards, "the first position was removed")
        assertEquals(GameSave.DEFAULT_DECK_NAME, deck.name, "and the name is kept")
    }

    @Test
    fun leavingTheEditorWithoutSavingChangesNothing() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
        val before = storedSave(documents)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        onNodeWithTag(DECK_RESET_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        assertEquals(before.decks, storedSave(documents).decks, "Reset alone must not persist")
    }

    @Test
    fun anEmptySlotCanBeFilledFromTheCollection() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(1)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        // The deck's five, not the collection's nine: a slot takes `HAND_SIZE` and `plusCard`
        // ignores the rest, so clicking all nine would build the same deck and prove less.
        for (cardId in STARTER_DECK) {
            onNodeWithTag(deckPickTestTag(cardId)).performClick()
        }
        onNodeWithTag(DECK_NAME_TEST_TAG).performTextClearance()
        onNodeWithTag(DECK_NAME_TEST_TAG).performTextInput(SECOND_DECK)
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).decks.size == 2 }

        val second = storedSave(documents).decks[1]
        assertEquals(SECOND_DECK, second.name)
        assertEquals(STARTER_DECK, second.cards)
        assertTrue(second.isComplete, "five cards is a playable deck")
    }

    @Test
    fun aDeckCannotGrowPastFive() = runComposeUiTest {
        val extra = freshSave().copy(cards = (STARTER_CARDS + SIXTH_CARD).associateWith { 1 })
        val documents = seeded(extra)
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        // Slot 0 is already the complete starter deck, so this tap has nothing to add to.
        onNodeWithTag(deckPickTestTag(SIXTH_CARD)).performClick()
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        val deck = storedSave(documents).decks.first()
        assertEquals(HAND_SIZE, deck.cards.size)
        assertFalse(SIXTH_CARD in deck.cards, "a full deck should not have taken a sixth card")
    }

    @Test
    fun theEditorRefusesACardWhoseCopiesAreAllSpent() = runComposeUiTest {
        val single = STARTER_CARDS.first()
        val profile = GameSave.new(createdAt = 0L).copy(
            cards = STARTER_CARDS.associateWith { 1 },
            decks = listOf(Deck(name = "Half", cards = listOf(single))),
        )
        val documents = seeded(profile)
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        // The deck already holds the only copy, so this tap has nothing left to spend.
        onNodeWithTag(deckPickTestTag(single)).performClick()
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        assertEquals(listOf(single), storedSave(documents).decks.first().cards)
    }

    @Test
    fun theEditorAcceptsASecondCopyWhenOneIsOwned() = runComposeUiTest {
        val twin = STARTER_CARDS.first()
        val profile = GameSave.new(createdAt = 0L).copy(
            cards = STARTER_CARDS.associateWith { 1 } + (twin to 2),
            decks = listOf(Deck(name = "Half", cards = listOf(twin))),
        )
        val documents = seeded(profile)
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        // Unmerged: the badge sits inside the pick cell's `clickable`. See `existsUnmerged`.
        onNodeWithTag(deckRemainingTestTag(twin), useUnmergedTree = true)
            .assertTextEquals("\u00d71")
        onNodeWithTag(deckPickTestTag(twin)).performClick()
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        assertEquals(listOf(twin, twin), storedSave(documents).decks.first().cards)
    }

    /**
     * The starter deck in slot 0 and a named empty one under it.
     *
     * Two *named* slots, because a swap is only observable by what the names do — a fixture whose
     * second slot was the padding `withDeck` invents would let a swap that dropped a deck pass.
     */
    private fun twoDecks(): GameSave = freshSave().let { save ->
        save.copy(decks = save.decks + Deck(name = SECOND_DECK, cards = emptyList()))
    }

    private companion object {
        const val SECOND_DECK = "Second"

        val SIXTH_CARD = Card.idFor(block = 1, number = 44)

        /** Two five-stars from the shipped table — Bahamut and Hildibrand. `cards.json`. */
        val FIVE_STAR = Card.idFor(block = 1, number = 61)
        val OTHER_FIVE_STAR = Card.idFor(block = 1, number = 62)
    }

    @Test
    fun everyPickableCardShowsItsPowersAndItsType() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        val cards = kotlinx.coroutines.runBlocking { com.tripletriad.data.loadCardCatalog() }
        val shown = STARTER_CARDS.first()
        onNodeWithTag(DECK_PICK_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(deckPickTestTag(shown)))

        onNodeWithTag(cardStatsTestTag(shown), useUnmergedTree = true).assertExists()
        val typed = STARTER_CARDS.firstOrNull { cards.byId[it]?.type != null }
        if (typed != null) {
            onNodeWithTag(DECK_PICK_GRID_TEST_TAG)
                .performScrollToNode(hasTestTag(deckPickTestTag(typed)))
            onNodeWithTag(cardTypeTestTag(typed), useUnmergedTree = true).assertExists()
        }
    }

    // ---- Reordering ---------------------------------------------------------

    /**
     * A slot moved down swaps with the one below it, and the swap is on disk immediately.
     *
     * The list has no draft and no Save button — see `DeckSlots` — so "it moved" and "it was
     * written" are the same claim, and asserting only the first would pass on a screen that
     * forgets the reordering the moment the player leaves it.
     */
    @Test
    fun movingASlotDownSwapsItWithTheOneBelowAndWritesAtOnce() = runComposeUiTest {
        val documents = seeded(twoDecks())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckMoveDownTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).decks.first().name == SECOND_DECK
        }

        val decks = storedSave(documents).decks
        assertEquals(SECOND_DECK, decks[0].name)
        assertEquals(GameSave.DEFAULT_DECK_NAME, decks[1].name)
        assertEquals(STARTER_DECK, decks[1].cards, "a moved deck keeps its cards")
    }

    /** And moving it back up is the same swap in reverse, not a second displacement. */
    @Test
    fun movingASlotUpUndoesTheMoveDown() = runComposeUiTest {
        val documents = seeded(twoDecks())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
        val before = storedSave(documents).decks

        onNodeWithTag(deckMoveDownTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).decks.first().name == SECOND_DECK
        }
        onNodeWithTag(deckMoveUpTestTag(1)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).decks.first().name == GameSave.DEFAULT_DECK_NAME
        }

        assertEquals(before, storedSave(documents).decks)
    }

    /** The two arrows a swap would have nothing to swap with are inert rather than absent. */
    @Test
    fun theEndsOfTheListCannotBeMovedPastThem() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openDecks()

        onNodeWithTag(deckMoveUpTestTag(0)).assertIsNotEnabled()
        onNodeWithTag(deckMoveDownTestTag(0)).assertIsEnabled()
        val last = GameSave.MAX_DECKS - 1
        onNodeWithTag(deckMoveUpTestTag(last)).assertIsEnabled()
        onNodeWithTag(deckMoveDownTestTag(last)).assertIsNotEnabled()
    }

    /**
     * A card shifted right inside the editor changes the deck's order and nothing else.
     *
     * Order is the play sequence under `RULE_ORDER` — see `Deck.plusCard` — so this is the one
     * edit the editor could not make before without emptying the slot and rebuilding it.
     */
    @Test
    fun shiftingACardRightReordersTheDeckOnSave() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        onNodeWithTag(deckShiftRightTestTag(0)).performClick()
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        val expected = listOf(STARTER_DECK[1], STARTER_DECK[0]) + STARTER_DECK.drop(2)
        assertEquals(expected, storedSave(documents).decks.first().cards)
        assertEquals(HAND_SIZE, storedSave(documents).decks.first().cards.size, "nothing was lost")
    }

    /** Shifting left is the same move back, and the ends of the hand refuse it. */
    @Test
    fun theEndsOfTheHandCannotBeShiftedPastThem() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        onNodeWithTag(deckShiftLeftTestTag(0)).assertIsNotEnabled()
        onNodeWithTag(deckShiftRightTestTag(HAND_SIZE - 1)).assertIsNotEnabled()

        onNodeWithTag(deckShiftRightTestTag(0)).performClick()
        onNodeWithTag(deckShiftLeftTestTag(1)).performClick()
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        assertEquals(STARTER_DECK, storedSave(documents).decks.first().cards, "right then left")
    }

    /** An empty position has nothing to shift, so both of its arrows are inert. */
    @Test
    fun anEmptyPositionOffersNoShift() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(1)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        for (position in 0 until HAND_SIZE) {
            onNodeWithTag(deckShiftLeftTestTag(position)).assertIsNotEnabled()
            onNodeWithTag(deckShiftRightTestTag(position)).assertIsNotEnabled()
        }
    }

    // ---- A card the deck names and the profile no longer holds ---------------

    @Test
    fun aDeckNamingACardNoLongerOwnedSaysSo() = runComposeUiTest {
        val lost = STARTER_DECK.first()
        val documents = seeded(freshSave().withoutCard(lost))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        // Unmerged: the slot row is `ttoClickable`, which absorbs its descendants' semantics —
        // the same trap `deckPositionTestTag` documents one screen over.
        onNodeWithTag(deckMissingTestTag(0), useUnmergedTree = true).assertExists()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        onNodeWithTag(DECK_MISSING_TEST_TAG).assertExists()

        // And it clears the moment the offending position is taken out, which is what makes the
        // editor the place to repair it.
        onNodeWithTag(deckPositionTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(DECK_MISSING_TEST_TAG) }
    }

    @Test
    fun anIntactDeckIsNotWarnedAbout() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openDecks()

        onNodeWithTag(deckMissingTestTag(0), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(deckOverLimitTestTag(0), useUnmergedTree = true).assertDoesNotExist()
    }

    // ---- The star-rank caps -------------------------------------------------

    /**
     * The rule is on screen before it is met, and it counts.
     *
     * `DeckLimits` is enforced twice over on the server, so what the editor owes the player is
     * *foreknowledge*: a deck refused at the moment they tap Play is a rule they cannot act on.
     */
    @Test
    fun theEditorCountsEachCappedRank() = runComposeUiTest {
        val documents = seeded(withAces(deck = listOf(FIVE_STAR)))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        onNodeWithTag(DECK_LIMITS_TEST_TAG).assertTextEquals("Rank limits ★5 1 / 1  ·  ★4 0 / 2")
    }

    /** A second five-star cannot be picked, exactly as a copy that is already spent cannot. */
    @Test
    fun theEditorRefusesASecondFiveStar() = runComposeUiTest {
        val documents = seeded(withAces(deck = listOf(FIVE_STAR)))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        onNodeWithTag(DECK_PICK_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(deckPickTestTag(OTHER_FIVE_STAR)))
        onNodeWithTag(deckPickTestTag(OTHER_FIVE_STAR)).performClick()
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        assertEquals(listOf(FIVE_STAR), storedSave(documents).decks.first().cards)
    }

    /**
     * A deck that is *already* over a cap says so, in both places, and is repairable.
     *
     * The picker cannot build one — the previous case is why — so the only way to hold one is to
     * have saved it before the caps existed. That deck no longer appears in the selector, and a
     * screen that hides it without saying why is the failure this warning exists to prevent.
     */
    @Test
    fun aDeckOverACapSaysSoAndCanBeRepaired() = runComposeUiTest {
        val fill = HAND_SIZE - 2
        val over = listOf(FIVE_STAR, OTHER_FIVE_STAR) + STARTER_DECK.take(fill)
        val documents = seeded(withAces(deck = over))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        // Unmerged: the slot row is `ttoClickable`, which absorbs its descendants' semantics.
        onNodeWithTag(deckOverLimitTestTag(0), useUnmergedTree = true).assertExists()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        onNodeWithTag(DECK_OVER_LIMIT_TEST_TAG).assertExists()

        // And it clears as the offending position comes out — the editor is where it is fixed.
        onNodeWithTag(deckPositionTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(DECK_OVER_LIMIT_TEST_TAG) }
    }

    /**
     * A profile holding both of the table's first two five-stars, and a deck of [deck].
     *
     * The starter collection is deliberately kept alongside them: the caps are about *which* five
     * a player may bring, so a fixture that owned nothing else would prove only that a deck of two
     * cards is short.
     */
    private fun withAces(deck: List<Int>): GameSave = freshSave().let { save ->
        save.copy(
            cards = save.cards + mapOf(FIVE_STAR to 1, OTHER_FIVE_STAR to 1),
            decks = listOf(Deck(name = "Aces", cards = deck)),
        )
    }
}
