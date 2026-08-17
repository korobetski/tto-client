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
import com.tripletriad.FF8_BLOCK
import com.tripletriad.FF8_FORMAT
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DeckSelectorUiTest {
    private val cards = runBlocking { loadCardCatalog() }
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    private fun withDecks(): GameSave = GameSave.new(createdAt = 0L).copy(
        cards = (STARTER + EXTRA).associateWith { 1 },
        decks = listOf(
            Deck(name = "Starters", cards = STARTER),
            Deck(name = "Halfling", cards = STARTER.take(2)),
            Deck(name = "Heavies", cards = EXTRA),
        ),
    )

    private fun ComposeUiTest.reachSelector(documents: InMemoryDocumentStore) {
        loadCharacter(documents)
        openOpponents()
        scrollToOpponent(TEST_OPPONENT)
        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_SELECT_CHOOSE_TEST_TAG) }
    }

    private fun nameOf(cardId: Int): String {
        val card = cards.block(FF14_BLOCK).first { it.id == cardId }
        return english[card.nameKey]
    }

    private fun ComposeUiTest.turnLine(): String {
        val node = onNodeWithTag(TURN_TEST_TAG).fetchSemanticsNode()
        return node.config[SemanticsProperties.Text].joinToString("") { it.text }
    }

    @Test
    fun onlyCompleteDecksAreOffered() = runComposeUiTest {
        val documents = seeded(withDecks())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        onNodeWithTag(deckChoiceTestTag(0)).assertExists()
        onNodeWithTag(deckChoiceTestTag(1)).assertExists()
        assertFalse(exists(deckChoiceTestTag(2)), "only two of the three decks are complete")
        assertTrue(isVisible("Starters"))
        assertTrue(isVisible("Heavies"))
        assertFalse(isVisible("Halfling"), "a partial deck is not playable")
    }

    @Test
    fun theChosenDeckIsTheHandThatIsDealt() = runComposeUiTest {
        val documents = seeded(withDecks())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        // Row 1 is `Heavies`: save slot 2, slot 1 having been filtered out as incomplete.
        onNodeWithTag(deckChoiceTestTag(1)).performClick()
        onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        awaitPlayer()

        assertEquals(HAND_SIZE, handSize(CardColor.BLUE))
        onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()
        waitForIdle()

        val line = turnLine()
        assertTrue(
            EXTRA.any { line.contains(nameOf(it)) },
            "the hand should be the Heavies deck; the turn line reads \"$line\"",
        )
        assertFalse(
            STARTER.any { line.contains(nameOf(it)) },
            "and not the starter deck; the turn line reads \"$line\"",
        )
    }

    @Test
    fun anUnnamedDeckIsLabelledByItsSaveSlotAndNotItsRow() = runComposeUiTest {
        val profile = GameSave.new(createdAt = 0L).copy(
            cards = (STARTER + EXTRA).associateWith { 1 },
            decks = listOf(
                Deck(name = "Starters", cards = STARTER),
                Deck(name = "", cards = emptyList()),
                Deck(name = "", cards = emptyList()),
                Deck(name = "", cards = emptyList()),
                Deck(name = "", cards = EXTRA),
            ),
        )
        val documents = seeded(profile)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        onNodeWithTag(deckChoiceTestTag(1)).assertExists()
        assertTrue(isVisible("Deck 5"), "the label follows the slot")
        assertFalse(isVisible("Deck 2"), "and is not renumbered to the row it happens to sit on")
    }

    @Test
    fun theFirstDeckIsAlreadySelected() = runComposeUiTest {
        val documents = seeded(withDecks())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun withNoCompleteDeckTheListSaysSoAndRandomStillPlays() = runComposeUiTest {
        val profile = GameSave.new(createdAt = 0L)
            .copy(decks = listOf(Deck(name = "Half", cards = STARTER.take(HALF_A_DECK))))
        val documents = seeded(profile)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        onNodeWithTag(DECK_SELECT_EMPTY_TEST_TAG).assertExists()
        onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).assertIsNotEnabled()

        onNodeWithTag(DECK_SELECT_RANDOM_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        awaitPlayer()

        assertEquals(HAND_SIZE, handSize(CardColor.BLUE))
    }

    @Test
    fun theRandomRuleSkipsTheSelectorEntirely() = runComposeUiTest {
        val documents = seeded(freshSave(createdAt = 0L, block = FF8_BLOCK))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openOpponents()

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(opponentRowTestTag(RANDOM_OPPONENT)))
        scrollToOpponent(RANDOM_OPPONENT)
        onNodeWithTag(opponentRowTestTag(RANDOM_OPPONENT)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }

        assertFalse(exists(DECK_SELECT_CHOOSE_TEST_TAG), "Random must not ask for a deck")
    }

    @Test
    fun theRandomOpponentIsTheOneTheFixtureAssumes() {
        val npcs = runBlocking { loadNpcCatalog() }
        val npc = npcs.available(FF8_FORMAT, NOON, ANY_LEVEL)
            .first { it.iconId == RANDOM_OPPONENT }

        assertTrue(npc.gameRules().random, "$RANDOM_OPPONENT should impose RULE_RANDOM")
        assertFalse(npc.gameRules().roulette, "and should not draw more rules on top")
    }

    @Test
    fun backingOutOfTheSelectorStillCountsAsAForfeit() = runComposeUiTest {
        val documents = seeded(withDecks())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).startedMatches == 1 }
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitOpponents()

        val save = storedSave(documents)
        assertEquals(1, save.startedMatches)
        assertEquals(0, save.endedMatches)
        assertEquals(1, save.forfeits, "started minus ended")
    }

    private companion object {
        val STARTER = starterFor(FF14_BLOCK).deck

        val EXTRA = listOf(44, 45, 51, 63, 74).map { Card.idFor(block = 1, number = it) }

        const val RANDOM_OPPONENT = "ma-dincht"

        const val NOON = 12

        const val HALF_A_DECK = 3
    }
}
