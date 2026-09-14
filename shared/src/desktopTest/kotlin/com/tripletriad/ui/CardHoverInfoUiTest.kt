package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
import com.tripletriad.settings.InMemorySettingsStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The line a grid thumbnail shows under the mouse — see [CardHoverInfo].
 *
 * Driven with a mouse pointer, the only input Material's box opens on without a long press. The
 * two tests that expect nothing are only worth something beside the first, which proves the same
 * hover does open it.
 */
@OptIn(ExperimentalTestApi::class)
class CardHoverInfoUiTest {
    private val catalog = runBlocking { loadCardCatalog() }
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    @Test
    fun hoveringACardNamesItAndLeavingItClosesIt() = runComposeUiTest {
        setContent { TestApp(store = store(tooltips = true)) }
        openCards()
        val id = STARTER_CARDS.first()
        val card = catalog.byId.getValue(id)

        onNodeWithTag(cardCellTestTag(id)).performMouseInput { enter(center) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(cardTooltipTestTag(id)) }

        assertTrue(says(id, strings[card.nameKey]), "the tooltip does not name the card")
        assertTrue(says(id, cardFacts(strings, card)), "nor give its sides")
        val owned = freshSave().cards.getValue(id)
        assertTrue(says(id, "Owned$DOT_SEPARATOR$owned"), "nor say how many are owned")

        onNodeWithTag(cardCellTestTag(id)).performMouseInput { exit() }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(cardTooltipTestTag(id)) }
    }

    @Test
    fun theQuestionMarkKeepsItsCardBackUnderTheMouseToo() = runComposeUiTest {
        setContent { TestApp(store = store(tooltips = true)) }
        openCards()

        assertFalse(UNOWNED in STARTER_CARDS, "the fixture assumes this is unowned")
        onNodeWithTag(CARD_GRID_TEST_TAG).performScrollToNode(hasTestTag(cardCellTestTag(UNOWNED)))
        onNodeWithTag(cardCellTestTag(UNOWNED)).performMouseInput { enter(center) }
        waitForIdle()

        assertFalse(exists(cardTooltipTestTag(UNOWNED)), "the \"?\" named its card on hover")
    }

    @Test
    fun turnedOffAHoveredCardSaysNothing() = runComposeUiTest {
        setContent { TestApp(store = store(tooltips = false)) }
        openCards()
        val id = STARTER_CARDS.first()

        onNodeWithTag(cardCellTestTag(id)).performMouseInput { enter(center) }
        waitForIdle()

        assertFalse(exists(cardTooltipTestTag(id)), "the tooltip ignored the setting")
    }

    private fun ComposeUiTest.openCards() {
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)
    }

    private fun ComposeUiTest.says(id: Int, text: String): Boolean = onAllNodes(
        hasText(text) and hasAnyAncestor(hasTestTag(cardTooltipTestTag(id))),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()

    private fun store(tooltips: Boolean) =
        InMemorySettingsStore("""{"language":"en_US","card_tooltips":$tooltips}""")

    private companion object {
        /** `CollectionUiTest`'s unowned card: in the FF14 table, and in no starter deck. */
        val UNOWNED = Card.idFor(block = 1, number = 44)
    }
}
