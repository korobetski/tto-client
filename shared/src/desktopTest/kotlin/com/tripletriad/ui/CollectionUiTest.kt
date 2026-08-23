package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tripletriad.FF14_BLOCK
import com.tripletriad.FF8_BLOCK
import com.tripletriad.data.CardValue
import com.tripletriad.data.loadFormatCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
import com.tripletriad.model.CardType
import com.tripletriad.model.GameSave
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CollectionUiTest {
    private val catalog = kotlinx.coroutines.runBlocking { com.tripletriad.data.loadCardCatalog() }
    private val formats = kotlinx.coroutines.runBlocking { loadFormatCatalog() }
    private val strings = kotlinx.coroutines.runBlocking { loadStrings(AppLocale.EN_US) }

    private fun ComposeUiTest.openCards(block: Int = FF14_BLOCK) {
        newCharacter(block)
        openFromBar("cards", CARD_GRID_TEST_TAG)
    }

    @Test
    fun theTotalCountsWhatIsOwnedAgainstTheWholeTable() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
    }

    @Test
    fun theDetailPanelIsEmptyUntilACardIsPicked() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_DETAIL_EMPTY_TEST_TAG).assertExists()
        assertFalse(exists(CARD_DETAIL_TEST_TAG))

        onNodeWithTag(cardCellTestTag(STARTER_CARDS.first())).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }

        assertTrue(isVisible("Sides"), "the detail should state the four sides")
        assertTrue(isVisible("Rarity"), "and the rarity")
    }

    @Test
    fun tappingTheSameCardTwiceClosesTheDetail() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()
        val card = STARTER_CARDS.first()

        onNodeWithTag(cardCellTestTag(card)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
        onNodeWithTag(cardCellTestTag(card)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_EMPTY_TEST_TAG) }
    }

    @Test
    fun anUnownedCardIsStillListedAndStillReadable() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        assertFalse(UNOWNED_CARD in STARTER_CARDS, "the fixture assumes this is unowned")
        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(UNOWNED_CARD)))
        onNodeWithTag(cardCellTestTag(UNOWNED_CARD)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
    }

    @Test
    fun everyCharacterBrowsesOneTable() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards(FF8_BLOCK)

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
        // This replaces an assertion that an FFVIII character could *not* reach an FFXIV card.
        // That was `MODE`, and it is the thing document 19 removed: the card is in the table, not
        // owned, and buying it is now a legal ambition rather than an impossibility.
        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(FF14_ONLY_CARD)))
        onNodeWithTag(cardCellTestTag(FF14_ONLY_CARD)).assertExists()
    }

    @Test
    fun aSecondCopyShowsAsABadgeAndDoesNotInflateTheTotal() = runComposeUiTest {
        val twin = STARTER_CARDS.first()
        val single = STARTER_CARDS.last()
        val documents = seeded(
            GameSave.new(createdAt = 0L)
                .copy(cards = STARTER_CARDS.associateWith { 1 } + (twin to 3)),
        )
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromBar("cards", CARD_GRID_TEST_TAG)

        // Unmerged: the badge sits inside the cell's own `clickable`, which absorbs it. See
        // `existsUnmerged`.
        onNodeWithTag(cardCopiesTestTag(twin), useUnmergedTree = true).assertTextEquals("\u00d73")
        assertFalse(existsUnmerged(cardCopiesTestTag(single)), "one copy carries no badge")
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
    }

    @Test
    fun aCellIsExactlyTheFrameAndNeverWiderThanIt() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        // The frame art is drawn over the picture at `fillMaxSize`, so a cell that is any
        // bigger than the picture traces a box the picture does not fill — which is exactly
        // what `GridCells.Adaptive` did, handing each column its whole share of the width.
        onNodeWithTag(cardCellTestTag(STARTER_CARDS.first()))
            .assertWidthIsEqualTo(CELL_SIDE)
            .assertHeightIsEqualTo(CELL_SIDE)
    }
    private companion object {
        // 153 FF14 + 110 FF8 before the FF14 set completed to its full 454 across two blocks.
        const val ALL_CARDS = 564

        /** `card_frame.png`'s authored size, and so the cell's. See `CardListBody`. */
        val CELL_SIDE = 44.dp

        val UNOWNED_CARD = Card.idFor(block = 1, number = 44)

        val FF14_ONLY_CARD = Card.idFor(block = 1, number = 138)
    }

    // ---- Filters -----------------------------------------------------------

    @Test
    fun filteringByTypeNarrowsTheGridAndItsTotal() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_FILTERS_TEST_TAG).assertExists()
        onNodeWithTag(typeFilterTestTag(CardType.FIRE)).performClick()
        waitForIdle()

        val fire = catalog.all.count { it.type == CardType.FIRE }
        val held = STARTER_CARDS.count { catalog.byId[it]?.type == CardType.FIRE }
        assertTrue(fire in 1 until ALL_CARDS, "the fixture assumes some cards are fire")
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR" + "$held / $fire",
        )
    }

    @Test
    fun tappingTheChosenTypeAgainClearsIt() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(typeFilterTestTag(CardType.FIRE)).performClick()
        onNodeWithTag(typeFilterTestTag(CardType.FIRE)).performClick()
        waitForIdle()

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
    }

    @Test
    fun filteringBySetShowsOneTableAtATime() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(setFilterTestTag(FF8_BLOCK)).performClick()
        waitForIdle()

        val ff8 = catalog.block(FF8_BLOCK).size
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR" + "0 / $ff8",
        )
    }

    // ---- Selling -----------------------------------------------------------

    @Test
    fun aSpareCopyCanBeSoldFromTheCollection() = runComposeUiTest {
        val spare = STARTER_CARDS.first { it !in STARTER_DECK }
        val documents = seeded(freshSave().copy(mgp = 0))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromBar("cards", CARD_GRID_TEST_TAG)

        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(spare)))
        onNodeWithTag(cardCellTestTag(spare)).performClick()
        onNodeWithTag(CARD_SELL_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).mgp > 0 }

        val save = storedSave(documents)
        assertFalse(save.ownsCard(spare), "the sold copy is gone from the collection")
        assertEquals(CardValue.resaleOf(spare, catalog.byId), save.mgp)
    }

    @Test
    fun aCardADeckNeedsIsNotOffered() = runComposeUiTest {
        val inDeck = STARTER_DECK.first()
        val documents = seeded(freshSave())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromBar("cards", CARD_GRID_TEST_TAG)

        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(inDeck)))
        onNodeWithTag(cardCellTestTag(inDeck)).performClick()
        waitForIdle()

        assertTrue(exists(CARD_DETAIL_TEST_TAG), "the card is selected")
        assertFalse(exists(CARD_SELL_TEST_TAG), "a deck's own card must not be sellable")
    }

    @Test
    fun aRefusedSaleIsSaidOutLoud() = runComposeUiTest {
        val spare = STARTER_CARDS.first { it !in STARTER_DECK }
        setContent { Cards(freshSave()) { IntentOutcome.REFUSED } }

        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(spare)))
        onNodeWithTag(cardCellTestTag(spare)).performClick()
        onNodeWithTag(CARD_SELL_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(COLLECTION_NOTE_TEST_TAG) }

        assertTrue(
            isVisible(strings[StringKeys.NOTHING_HAPPENED]),
            "a refused sale said nothing",
        )
    }

    @Composable
    private fun Cards(profile: GameSave, onIntent: suspend (Intent) -> IntentOutcome) {
        CompositionLocalProvider(LocalStrings provides strings) {
            TripleTriadTheme {
                CollectionScreen(
                    profile = profile,
                    catalog = catalog,
                    format = formats.default!!,
                    initial = CollectionTab.CARDS,
                    onPersist = {},
                    onIntent = onIntent,
                    onBack = {},
                )
            }
        }
    }
}
