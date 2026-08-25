package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
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
        val documents = seeded(GameSave.new(createdAt = 0L))
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
        // The deck's five, not the collection's ten: a slot takes `HAND_SIZE` and `plusCard`
        // ignores the rest, so clicking all ten would build the same deck and prove less.
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
        val extra = GameSave.new(createdAt = 0L)
            .copy(cards = (STARTER_CARDS + SIXTH_CARD).associateWith { 1 })
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

    private companion object {
        const val SECOND_DECK = "Second"

        val SIXTH_CARD = Card.idFor(block = 1, number = 44)
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
    }
}
