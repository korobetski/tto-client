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
import androidx.compose.ui.test.performTextInput
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
    }

    @Test
    fun theDetailPanelIsEmptyUntilACardIsPicked() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()
        val card = STARTER_CARDS.first()

        onNodeWithTag(cardCellTestTag(card)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
        onNodeWithTag(cardCellTestTag(card)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_EMPTY_TEST_TAG) }
    }

    @Test
    fun anUnownedCardIsStillListedAndStillReadable() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        assertFalse(UNOWNED_CARD in STARTER_CARDS, "the fixture assumes this is unowned")
        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(UNOWNED_CARD)))
        onNodeWithTag(cardCellTestTag(UNOWNED_CARD)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
    }

    @Test
    fun everyCharacterBrowsesOneTable() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
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

    /**
     * A cell carries the element, because it is the same tile the rest of the app draws.
     *
     * The grid used to build its own cell — a thumbnail and a copy badge — while the deck builder,
     * the shop and the auction's picker each drew `CardTile`, which puts the element in the top
     * corner. Four arrangements of one object, and the collection was the only room where a card's
     * element was invisible until it was opened. See [CardCell].
     */
    @Test
    fun aCellCarriesTheElementTheRestOfTheAppPutsOnACard() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        // Read off the table rather than named: the starter deck happens to be elementless, and
        // an element is a fact about the *catalogue* this assertion should not hard-code a card of.
        val elemental = catalog.all.first { it.type != null && it.block == FF14_BLOCK }.id
        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(elemental)))

        assertTrue(existsUnmerged(cardTypeTestTag(elemental)), "no element on the cell")
    }

    @Test
    fun aCellIsExactlyTheFrameAndNeverWiderThanIt() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        // The frame art is drawn over the picture at `fillMaxSize`, so a cell that is any
        // bigger than the picture traces a box the picture does not fill — which is exactly
        // what `GridCells.Adaptive` did, handing each column its whole share of the width.
        onNodeWithTag(cardCellTestTag(STARTER_CARDS.first()))
            .assertWidthIsEqualTo(CELL_SIDE)
            .assertHeightIsEqualTo(CELL_SIDE)
    }

    @Test
    fun typingANameNarrowsTheGridToTheCardsThatAnswerToIt() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_SEARCH_TEST_TAG).performTextInput("dodo")
        waitForIdle()

        // Dodo is `STR_FF14_CARD_1` and the starter deck holds it, so the count is a fact about
        // one card rather than about how many the table happens to contain.
        onNodeWithTag(cardCellTestTag(DODO)).assertExists()
        assertFalse(exists(cardCellTestTag(FF14_ONLY_CARD)), "the grid was not narrowed")
    }

    @Test
    fun clearingTheFieldPutsTheWholeTableBack() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_SEARCH_TEST_TAG).performTextInput("dodo")
        waitForIdle()
        onNodeWithTag(CARD_SEARCH_CLEAR_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
    }

    @Test
    fun aNameNothingAnswersToSaysSoRatherThanShowingAnEmptyGrid() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_SEARCH_TEST_TAG).performTextInput("zzzzzz")
        waitForIdle()

        onNodeWithTag(CARD_NO_MATCH_TEST_TAG).assertExists()
        assertFalse(exists(CARD_GRID_TEST_TAG), "the grid should give way to the note")
    }

    @Test
    fun theMissingChipIsTheExactComplementOfTheOwnedOne() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_OWNED_FILTER_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / ${STARTER_CARDS.size}",
        )

        onNodeWithTag(CARD_MISSING_FILTER_TEST_TAG).performClick()
        waitForIdle()
        // Nothing on screen is owned, and the two chips are exclusive: picking one drops the other.
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR" + "0 / ${ALL_CARDS - STARTER_CARDS.size}",
        )
    }

    @Test
    fun tappingTheChosenHoldingChipAgainClearsIt() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_MISSING_FILTER_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(CARD_MISSING_FILTER_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
    }

    @Test
    fun theOrderIsChosenFromTheMenuAndTheGridFollowsIt() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        // Narrowed to what is owned first, so the assertion is about five cards whose order can be
        // computed here rather than about 564 whose first row depends on the whole table.
        onNodeWithTag(CARD_OWNED_FILTER_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(CARD_SORT_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(cardSortTestTag(CardSort.POWER)) }
        onNodeWithTag(cardSortTestTag(CardSort.POWER)).performClick()
        waitForIdle()

        val strongest = STARTER_CARDS.maxBy { catalog.byId.getValue(it).total }
        onNodeWithTag(cardCellTestTag(strongest)).assertExists()
        // The menu closes on a choice, and the choice is the one that is ticked next time.
        assertFalse(exists(cardSortTestTag(CardSort.POWER)), "the menu stayed open")
    }

    private companion object {
        // 153 FF14 + 110 FF8 before the FF14 set completed to its full 454 across two blocks.
        // Still 564, not 565, now that FF8 carries a 111th card: Mooba is secret, and a secret
        // card the fixture's profile does not own does not widen this total either — the same
        // filter that hides it from the grid hides it from the count under it. See
        // `SECRET_CARD_IDS` in `CardListBody.kt`.
        const val ALL_CARDS = 564

        /** `card_frame.png`'s authored size, and so the cell's. See `CardListBody`. */
        val CELL_SIDE = 44.dp

        val UNOWNED_CARD = Card.idFor(block = 1, number = 44)

        val FF14_ONLY_CARD = Card.idFor(block = 1, number = 138)

        /** `STR_FF14_CARD_1`, and the one card in the starter deck named "Dodo". */
        val DODO = Card.idFor(block = 1, number = 1)
    }

    // ---- Filters -----------------------------------------------------------

    @Test
    fun filteringByTypeNarrowsTheGridAndItsTotal() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(setFilterTestTag(FF8_BLOCK)).performClick()
        waitForIdle()

        // Mooba is in this block but the fixture profile does not own it, so the list — and the
        // total beneath it — hides that one card. See `SECRET_CARD_IDS` in `CardListBody.kt`.
        val ff8 = catalog.block(FF8_BLOCK).size - 1
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR" + "0 / $ff8",
        )
    }

    // ---- Selling -----------------------------------------------------------

    @Test
    fun aSpareCopyCanBeSoldFromTheCollection() = runComposeUiTest {
        val spare = STARTER_CARDS.first { it !in STARTER_DECK }
        val documents = seeded(freshSave().copy(mgp = 0))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
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
