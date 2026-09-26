package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.tripletriad.FF14_BLOCK
import com.tripletriad.FF8_BLOCK
import com.tripletriad.data.CardValue
import com.tripletriad.data.loadFormatCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.ACE_POWER
import com.tripletriad.model.Card
import com.tripletriad.model.CardType
import com.tripletriad.model.Deeds
import com.tripletriad.model.GameSave
import com.tripletriad.model.Side
import com.tripletriad.protocol.Unlocks
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import com.tripletriad.settings.UnownedCards
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

    /** The shipped roster, so a card's drop lines are the real ones. See [cardSources]. */
    private val opponents = kotlinx.coroutines.runBlocking { com.tripletriad.data.loadNpcCatalog() }

    private fun ComposeUiTest.openCards(block: Int = FF14_BLOCK) {
        newCharacter(block)
        openFromBar("cards", CARD_GRID_TEST_TAG)
    }

    /**
     * Open one of the filter menus and choose a line from it.
     *
     * Two taps rather than one, and waited on in between: a `DropdownMenu` is composed into its own
     * window when it opens, so the item does not exist until the anchor has been clicked and the
     * frame has run. See [CardFilterMenus].
     */
    private fun ComposeUiTest.pickFilter(menu: String, item: String) {
        onNodeWithTag(menu).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(item) }
        onNodeWithTag(item).performClick()
        waitForIdle()
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

    /**
     * The window here is 1024 × 768 at density 1, which fits the card five times over; what stops
     * it at 208 dp is the ceiling — the art's own 208 px — and that is the number worth pinning.
     */
    @Test
    fun tappingThePictureShowsTheCardAtItsOwnResolutionUntilTappedAgain() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(cardCellTestTag(STARTER_CARDS.first())).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
        assertFalse(existsUnmerged(CARD_ZOOM_TEST_TAG))

        onNodeWithTag(CARD_PANEL_FACE_TEST_TAG, useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(CARD_ZOOM_TEST_TAG) }
        val zoomed = onNodeWithTag(CARD_ZOOM_FACE_TEST_TAG, useUnmergedTree = true)
            .getBoundsInRoot()
        assertEquals(208.dp, zoomed.right - zoomed.left, "not the art pixel for pixel")

        onNodeWithTag(CARD_ZOOM_TEST_TAG, useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !existsUnmerged(CARD_ZOOM_TEST_TAG) }
    }

    /**
     * A card nobody has is a "?" in the grid and `???` in the detail: still listed, still opened,
     * still saying where it comes from — but not what it is. See [UnknownCardTile].
     *
     * The name is looked for in content descriptions as well as in text, because that is where
     * [CardFace] would leak it: its semantics label is the name and the four sides.
     */
    @Test
    fun anUnownedCardIsAQuestionMarkThatOpensWithoutItsName() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()
        val card = catalog.byId.getValue(UNOWNED_CARD)
        val name = strings[card.nameKey]

        assertFalse(UNOWNED_CARD in STARTER_CARDS, "the fixture assumes this is unowned")
        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(UNOWNED_CARD)))
        assertTrue(existsUnmerged(unknownCardTestTag(UNOWNED_CARD)), "the cell is not a \"?\"")
        assertFalse(
            existsUnmerged(thumbTestTag(card.textureId)),
            "the cell drew the card's picture",
        )
        assertTrue(
            onAllNodes(
                hasText(catalogueNumber(card)) and
                    hasAnyAncestor(hasTestTag(cardCellTestTag(UNOWNED_CARD))),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty(),
            "the \"?\" printed the card's number, which the game's tile does not",
        )

        onNodeWithTag(cardCellTestTag(UNOWNED_CARD)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }

        assertTrue(isVisible("???"), "the detail should stand ??? in for the name")
        assertTrue(isVisible("No. 044"), "the detail should give the card's number")
        assertTrue(isVisible("Rarity"), "and its rarity")
        onNodeWithTag(CARD_SOURCES_TEST_TAG).assertExists()
        assertFalse(isVisible(name), "the detail printed the name")
        assertTrue(
            onAllNodes(hasContentDescription(name, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
            "the detail read the name out",
        )
        assertFalse(isVisible("Sides"), "the detail gave the four sides away")
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
        // Read off the table rather than named: the starter deck happens to be elementless, and
        // an element is a fact about the *catalogue* this assertion should not hard-code a card of.
        // Owned, because a card that is not is a "?" — see the next test.
        val elemental = catalog.all.first { it.type != null && it.block == FF14_BLOCK }.id
        val save = freshSave()
        val documents = seeded(save.copy(cards = save.cards + (elemental to 1)))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromBar("cards", CARD_GRID_TEST_TAG)

        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(elemental)))

        assertTrue(existsUnmerged(cardTypeTestTag(elemental)), "no element on the cell")
    }

    @Test
    fun anUnownedCellKeepsItsElementToItself() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        val elemental = catalog.all
            .first { it.type != null && it.block == FF14_BLOCK && it.id !in STARTER_CARDS }.id
        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(elemental)))

        waitForIdle()
        assertTrue(existsUnmerged(unknownCardTestTag(elemental)), "the cell is not a \"?\"")
        assertFalse(existsUnmerged(cardTypeTestTag(elemental)), "the \"?\" wears the element")
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
        // Nothing on screen is owned, and the segments are exclusive: one is always the one lit.
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR" + "0 / ${ALL_CARDS - STARTER_CARDS.size}",
        )
    }

    @Test
    fun theWholeCollectionIsOneSegmentAwayFromEitherHalf() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        // The way back that the pair of chips did not have: "all" was whatever was left when
        // neither of the other two was lit. See `Held` in `CardListBody.kt`.
        onNodeWithTag(CARD_MISSING_FILTER_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(CARD_ANY_FILTER_TEST_TAG).performClick()
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
        // computed here rather than about 585 whose first row depends on the whole table.
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

    @Test
    fun aCardSaysWhereItComesFrom() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(cardCellTestTag(CHOCOBO)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }

        onNodeWithTag(CARD_SOURCES_TEST_TAG).assertExists()
        // Chocobo is on the shelf *and* in two drop tables — the certain source leads, and the
        // two opponents follow it. Both facts were in `cards.json` and `npcs.json` all along.
        onNodeWithTag(cardSourceTestTag("shop")).assertExists()
        onNodeWithTag(cardSourceTestTag("npc-guhtwint")).assertExists()
        assertTrue(isVisible("20%"), "the drop rate is not on screen")
    }

    /**
     * Driven on the panel itself: since every shipped card gained a source (see
     * `CardSourceIndexTest.everyCardCanBeComeBy`), no card in the grid reaches this state.
     */
    @Test
    fun aCardNothingOffersSaysThatInsteadOfShowingABlank() = runComposeUiTest {
        setContent { CardSources(sources = emptyList()) }

        onNodeWithTag(CARD_SOURCES_NONE_TEST_TAG).assertExists()
    }

    @Test
    fun theSourcesAreNamedInTheLanguageOnScreen() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.FR_FR)) }
        openCards()

        onNodeWithTag(cardCellTestTag(CHOCOBO)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }

        assertTrue(isVisible("Où la trouver"), "the heading is not in French")
    }

    @Test
    fun anUnownedCardIsFoundByItsNumberAndNotByTheNameItHides() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()
        val chocobo = catalog.byId.getValue(CHOCOBO)

        assertFalse(CHOCOBO in STARTER_CARDS, "the fixture assumes this is unowned")
        onNodeWithTag(CARD_SEARCH_TEST_TAG).performTextInput(strings[chocobo.nameKey])
        waitForIdle()
        assertFalse(exists(cardCellTestTag(CHOCOBO)), "the name found the card it is hidden on")

        onNodeWithTag(CARD_SEARCH_CLEAR_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(CARD_SEARCH_TEST_TAG).performTextInput(catalogueNumber(chocobo))
        waitForIdle()
        onNodeWithTag(cardCellTestTag(CHOCOBO)).assertExists()
    }

    @Test
    fun aDimmedCardIsItsOwnPictureAndOpensWithItsName() = runComposeUiTest {
        setContent { TestApp(store = unownedAs(UnownedCards.DIMMED)) }
        openCards()
        val card = catalog.byId.getValue(UNOWNED_CARD)

        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(UNOWNED_CARD)))
        assertTrue(existsUnmerged(thumbTestTag(card.textureId)), "the cell is not the picture")
        assertFalse(existsUnmerged(unknownCardTestTag(UNOWNED_CARD)), "the cell is still a \"?\"")

        onNodeWithTag(cardCellTestTag(UNOWNED_CARD)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
        assertTrue(isVisible(strings[card.nameKey]), "the detail kept the name back")
        assertTrue(isVisible("Sides"), "the detail kept the sides back")
    }

    @Test
    fun odinHintsAtTheCardLosingHimEarns() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards(FF8_BLOCK)
        val odin = catalog.byId.getValue(Deeds.ODIN_FF8)

        onNodeWithTag(CARD_SEARCH_TEST_TAG).performTextInput("${odin.number}")
        waitForIdle()
        onNodeWithTag(cardCellTestTag(odin.id)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }

        onNodeWithTag(CARD_HINT_TEST_TAG).assertExists()
    }

    @Test
    fun gilgameshIsNotListedUntilOwned() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards(FF8_BLOCK)
        val gilgamesh = catalog.byId.getValue(GILGAMESH)

        onNodeWithTag(CARD_SEARCH_TEST_TAG).performTextInput("${gilgamesh.number}")
        waitForIdle()

        assertFalse(exists(cardCellTestTag(GILGAMESH)), "the secret card is in the grid")
    }

    @Test
    fun hiddenCardsLeaveTheGridButNotTheCount() = runComposeUiTest {
        setContent { TestApp(store = unownedAs(UnownedCards.HIDDEN)) }
        openCards()

        // Every cell composed, and not only the ones on screen: five owned cards fit in one row.
        val cells = onAllNodes(
            SemanticsMatcher("is a card cell") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("card-cell-") == true
            },
        ).fetchSemanticsNodes()
        assertEquals(STARTER_CARDS.size, cells.size, "the grid is not only the owned cards")
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
        // "Missing" would be an empty grid by definition.
        assertFalse(exists(CARD_MISSING_FILTER_TEST_TAG), "the segments are still drawn")
    }

    // ---- The landscape panel ---------------------------------------------------

    /** A window the panel fits in — see `FilterPanelMinWidth`. Density 1, so its pixels are dp. */
    private fun wideWindow(block: suspend SkikoComposeUiTest.() -> Unit) = runSkikoComposeUiTest(
        size = Size(WIDE_WINDOW_WIDTH, WIDE_WINDOW_HEIGHT),
        density = Density(1f),
        block = block,
    )

    /** Scrolled to first: the panel scrolls on its own, and a chip below its fold takes no tap. */
    private fun ComposeUiTest.tapInPanel(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    /** [ids]' cells, top row first and left to right within a row. */
    private fun ComposeUiTest.cellsInReadingOrder(ids: List<Int>): List<Int> {
        val bounds = ids.associateWith { onNodeWithTag(cardCellTestTag(it)).getBoundsInRoot() }
        return ids.sortedWith(
            compareBy({ bounds.getValue(it).top }, { bounds.getValue(it).left }),
        )
    }

    @Test
    fun aWideWindowKeepsTheFiltersOpenBesideTheGridRatherThanInMenus() = wideWindow {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_FILTER_PANEL_TEST_TAG).assertExists()
        assertFalse(exists(CARD_FILTERS_TEST_TAG), "the menus are drawn as well")
        // What the panel does not carry stays over the grid.
        onNodeWithTag(CARD_SEARCH_TEST_TAG).assertExists()
        onNodeWithTag(CARD_OWNED_FILTER_TEST_TAG).assertExists()
    }

    @Test
    fun aWindowTooNarrowForThePanelKeepsTheMenus() = runComposeUiTest {
        // The default test window, 1024 wide: room for the rail and the detail pane, not for this.
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_FILTERS_TEST_TAG).assertExists()
        assertFalse(exists(CARD_FILTER_PANEL_TEST_TAG), "the panel squeezed in")
    }

    @Test
    fun twoElementsLitTogetherAdmitTheCardsOfEither() = wideWindow {
        // Dimmed, as in the menu's test: under the "?" an element only answers for owned cards.
        setContent { TestApp(store = unownedAs(UnownedCards.DIMMED)) }
        openCards()

        tapInPanel(typeFilterTestTag(CardType.FIRE))
        tapInPanel(typeFilterTestTag(CardType.ICE))

        val picked = setOf(CardType.FIRE, CardType.ICE)
        val either = catalog.all.count { it.type in picked }
        val held = STARTER_CARDS.count { catalog.byId[it]?.type in picked }
        val fire = catalog.all.count { it.type == CardType.FIRE }
        assertTrue(either > fire, "the fixture assumes some ice cards")
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR" + "$held / $either",
        )
    }

    @Test
    fun theResetSaysHowMuchItUndoesAndPutsTheWholeTableBack() = wideWindow {
        setContent { TestApp(store = unownedAs(UnownedCards.DIMMED)) }
        openCards()
        onNodeWithTag(CARD_FILTER_RESET_TEST_TAG).assertIsNotEnabled()

        tapInPanel(rarityFilterTestTag(1))
        tapInPanel(rarityFilterTestTag(2))
        tapInPanel(typeFilterTestTag(CardType.FIRE))
        onNodeWithTag(CARD_FILTER_RESET_TEST_TAG).assertTextEquals("Reset (3)")

        tapInPanel(CARD_FILTER_RESET_TEST_TAG)

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
        onNodeWithTag(CARD_FILTER_RESET_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun theReverseChipReadsTheGridFromTheOtherEnd() = wideWindow {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()
        // Owned only, as in the menu's test: cells few enough to state their order here.
        onNodeWithTag(CARD_OWNED_FILTER_TEST_TAG).performClick()
        waitForIdle()
        // What this character owns. Not all of [STARTER_CARDS], which is another starter's deck.
        val owned = catalog.all.filter { exists(cardCellTestTag(it.id)) }
        assertTrue(owned.size > 1, "one cell has no order to reverse")

        // Read once the right way round first, so the reversed reading is not true of any order.
        assertEquals(
            owned.sortedWith(CardSort.NUMBER.comparator()).map { it.id },
            cellsInReadingOrder(owned.map { it.id }),
        )

        tapInPanel(CARD_SORT_REVERSE_TEST_TAG)

        assertEquals(
            owned.sortedWith(CardSort.NUMBER.comparator(reversed = true)).map { it.id },
            cellsInReadingOrder(owned.map { it.id }),
        )
    }

    /** "Owned · 3 / 21" read back as its two numbers: what is owned of what the filters let by. */
    private fun ComposeUiTest.ownedAndShown(): Pair<Int, Int> {
        val text = onNodeWithTag(CARD_TOTAL_TEST_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }
        val (owned, shown) = text.substringAfterLast(DOT_SEPARATOR)
            .split(" / ")
            .map { it.trim().toInt() }
        return owned to shown
    }

    @Test
    fun twoSourcesLitTogetherAdmitTheCardsEitherTableOffers() = wideWindow {
        setContent { TestApp(store = unownedAs(UnownedCards.DIMMED)) }
        openCards()
        val kinds = sourceKindsByCard(opponents)
        fun offeredBy(vararg lit: SourceKind) =
            catalog.all.count { card -> kinds[card.id].orEmpty().any { it in lit } }

        tapInPanel(sourceFilterTestTag(SourceKind.SHOP))
        assertEquals(offeredBy(SourceKind.SHOP), ownedAndShown().second)

        tapInPanel(sourceFilterTestTag(SourceKind.BOOSTER))
        val either = offeredBy(SourceKind.SHOP, SourceKind.BOOSTER)
        assertTrue(either > offeredBy(SourceKind.SHOP), "the fixture assumes packs the shop lacks")
        assertEquals(either, ownedAndShown().second)
    }

    @Test
    fun aSideMinimumKeepsTheCardsReachingItOnThatSideAndStopsAtTheAce() = wideWindow {
        setContent { TestApp(store = unownedAs(UnownedCards.DIMMED)) }
        openCards()

        repeat(TOP_MINIMUM) { tapInPanel(sideRaiseTestTag(Side.TOP)) }

        onNodeWithTag(sideMinimumTestTag(Side.TOP)).assertTextEquals("$TOP_MINIMUM")
        assertEquals(catalog.all.count { it.top >= TOP_MINIMUM }, ownedAndShown().second)
        onNodeWithTag(CARD_FILTER_RESET_TEST_TAG).assertTextEquals("Reset (1)")

        repeat(ACE_POWER - TOP_MINIMUM) { tapInPanel(sideRaiseTestTag(Side.TOP)) }
        onNodeWithTag(sideMinimumTestTag(Side.TOP)).assertTextEquals("A")
        onNodeWithTag(sideRaiseTestTag(Side.TOP)).assertIsNotEnabled()
    }

    @Test
    fun underTheQuestionMarkASideMinimumLeavesOnlyCardsShowingTheirSides() = wideWindow {
        setContent { TestApp(store = unownedAs(UnownedCards.UNKNOWN)) }
        openCards()
        val (owned, shown) = ownedAndShown()
        assertTrue(shown > owned, "the fixture assumes cards drawn as a question mark")

        // One on the left: every side of every card reaches it, so all it takes away is the "?".
        tapInPanel(sideRaiseTestTag(Side.LEFT))

        assertEquals(owned to owned, ownedAndShown())
    }

    @Test
    fun aWindowNarrowedPastThePanelStillOffersToLiftWhatOnlyThePanelSets() = runComposeUiTest {
        val filters = CardFilters(
            blockGroups = emptyMap(),
            sets = emptyList(),
            types = emptyList(),
            rarities = emptyList(),
        )
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme { CardFilterMenus(filters) }
            }
        }
        assertFalse(exists(CARD_FILTER_RESET_TEST_TAG), "a reset with nothing to undo")

        filters.setMinimum(Side.RIGHT, ACE_POWER)
        waitForIdle()
        onNodeWithTag(CARD_FILTER_RESET_TEST_TAG).assertTextEquals("Reset (1)").performClick()
        waitForIdle()

        assertEquals(0, filters.narrowings)
        assertFalse(exists(CARD_FILTER_RESET_TEST_TAG), "the reset outlived what it undid")
    }

    private fun unownedAs(mode: UnownedCards): SettingsStore =
        InMemorySettingsStore("""{"language":"en_US","unowned_cards":"${mode.tag}"}""")

    private companion object {
        /** Copies of a card no deck holds: enough for the stepper to have a middle. */
        const val SPARES = 3

        // 153 FF14 + 110 FF8 before the FF14 set completed to 454 across two blocks, and 475
        // with the cards of patches 7.4-7.51. 584 of the 586: Mooba and Gilgamesh are secret, and
        // a secret card the fixture's profile does not own does not widen this total either —
        // the same filter that hides it from the grid hides it from the count under it. See
        // `SECRET_CARD_IDS` in `CardListBody.kt`.
        const val ALL_CARDS = 584

        /** Past `FilterPanelMinWidth` with the rail up — a 1600 × 1000 browser window. */
        const val WIDE_WINDOW_WIDTH = 1600f
        const val WIDE_WINDOW_HEIGHT = 1000f

        /** High enough that most of the table falls short of it, and short of an ace. */
        const val TOP_MINIMUM = 8

        /** Two fifths of the stage: a floor under a regression, not a measure of the layout. */
        const val GRID_TOP_CEILING = 0.4f

        /** `card_frame.png`'s authored size, and so the cell's. See `CardListBody`. */
        val CELL_SIDE = 44.dp

        val UNOWNED_CARD = Card.idFor(block = 1, number = 44)

        val FF14_ONLY_CARD = Card.idFor(block = 1, number = 138)

        /** `STR_FF14_CARD_1`, and the one card in the starter deck named "Dodo". */
        val DODO = Card.idFor(block = 1, number = 1)

        /**
         * `STR_FF14_CARD_13`, on the shop's shelf at 150 Gil and in two opponents' drop tables at
         * 20 % — the one card in the shipped data that exercises both halves of the index at once.
         * Authored data, so this moves with `cards.json` and `npcs.json`; when it does, pick
         * another card that is both bought and dropped.
         */
        const val CHOCOBO = 269

        /** FFVIII's secret card, won with the hidden Zantetsuken. */
        const val GILGAMESH = 0x0850

        const val TWO_COPIES = 2
        const val THREE_COPIES = 3
    }

    // ---- Filters -----------------------------------------------------------

    @Test
    fun filteringByTypeNarrowsTheGridAndItsTotal() = runComposeUiTest {
        // Dimmed, so every card's element is on show and the menu can answer with all of them.
        // Under the "?" it cannot — see the next test.
        setContent { TestApp(store = unownedAs(UnownedCards.DIMMED)) }
        openCards()

        onNodeWithTag(CARD_FILTERS_TEST_TAG).assertExists()
        pickFilter(CARD_TYPE_MENU_TEST_TAG, typeFilterTestTag(CardType.FIRE))

        val fire = catalog.all.count { it.type == CardType.FIRE }
        val held = STARTER_CARDS.count { catalog.byId[it]?.type == CardType.FIRE }
        assertTrue(fire in 1 until ALL_CARDS, "the fixture assumes some cards are fire")
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR" + "$held / $fire",
        )
    }

    @Test
    fun underTheQuestionMarkAnElementOnlyAnswersWithTheCardsThatShowIt() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        pickFilter(CARD_TYPE_MENU_TEST_TAG, typeFilterTestTag(CardType.FIRE))

        val fire = catalog.all.count { it.type == CardType.FIRE }
        val held = STARTER_CARDS.count { catalog.byId[it]?.type == CardType.FIRE }
        assertTrue(fire > held, "the fixture assumes some fire cards are not owned")
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR" + "$held / $held",
        )
    }

    @Test
    fun anElementIsPutBackFromTheSameMenuItWasChosenIn() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        pickFilter(CARD_TYPE_MENU_TEST_TAG, typeFilterTestTag(CardType.FIRE))
        pickFilter(CARD_TYPE_MENU_TEST_TAG, typeFilterTestTag(null))

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
    }

    /**
     * The row says what it is hiding without being opened.
     *
     * The one thing a row of chips did better than a menu, and the reason the closed menu is
     * titled by its answer rather than by its question — see [CardFilterMenus].
     */
    @Test
    fun aMenuIsTitledByTheFilterInForce() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        assertTrue(isVisible("Type ▾"), "the closed menu should name the question")

        pickFilter(CARD_TYPE_MENU_TEST_TAG, typeFilterTestTag(CardType.FIRE))

        assertTrue(isVisible("FIRE ▾"), "the closed menu should name the answer")
        assertFalse(isVisible("Type ▾"), "the question is still on the chip")
    }

    /**
     * What the whole rework is for: the grid used to start at roughly two thirds of the way down
     * a phone screen, under five bands of controls. Two bands put it back near the top.
     *
     * A fraction of the stage rather than a number of dp, because the stage is whatever size the
     * test window happens to be. Two fifths is loose on purpose — it is a floor under a
     * regression, not a measurement of the current layout.
     */
    @Test
    fun theGridStartsNearTheTopRatherThanUnderFiveBandsOfControls() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        val stage = onRoot().getBoundsInRoot().height
        val top = onNodeWithTag(CARD_GRID_TEST_TAG).getBoundsInRoot().top
        assertTrue(top < stage * GRID_TOP_CEILING, "the grid starts $top down a $stage stage")
    }

    @Test
    fun filteringBySetShowsOneTableAtATime() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        pickFilter(CARD_SET_MENU_TEST_TAG, setFilterTestTag(FF8_BLOCK))

        // Mooba and Gilgamesh are in this block but the fixture profile owns neither, so the list —
        // and the total beneath it — hides both. See `SECRET_CARD_IDS` in `CardListBody.kt`.
        val ff8 = catalog.block(FF8_BLOCK).size - SECRET_CARD_IDS.size
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

    /**
     * Three cards, three answers: three loose copies give two, two copies a deck fields one of
     * give one, and a single loose copy is not tickable at all — the one-card Sell button would
     * sell it, and the bar says one of each is kept.
     */
    @Test
    fun aBulkSaleSellsTheTickedDuplicatesAndKeepsOneOfEach() = runComposeUiTest {
        val loose = STARTER_CARDS.filter { it !in STARTER_DECK }
        val triple = loose[0]
        val single = loose[1]
        val decked = STARTER_DECK.first()
        val start = freshSave().copy(mgp = 0)
        val held = mapOf(triple to THREE_COPIES, decked to TWO_COPIES, single to 1)
        val documents = seeded(start.copy(cards = start.cards + held))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromBar("cards", CARD_GRID_TEST_TAG)

        onNodeWithTag(CARD_SELECT_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_BULK_BAR_TEST_TAG) }
        for (id in listOf(triple, decked)) {
            onNodeWithTag(CARD_GRID_TEST_TAG).performScrollToNode(hasTestTag(cardCellTestTag(id)))
            assertTrue(existsUnmerged(cardPickTestTag(id)), "card $id has copies to give")
            onNodeWithTag(cardCellTestTag(id)).performClick()
        }
        onNodeWithTag(CARD_GRID_TEST_TAG).performScrollToNode(hasTestTag(cardCellTestTag(single)))
        assertFalse(existsUnmerged(cardPickTestTag(single)), "the only copy was offered")
        assertFalse(exists(CARD_SHEET_TEST_TAG), "a tick opened the card as well")

        onNodeWithTag(CARD_BULK_COUNT_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("2 card(s) selected")
        onNodeWithTag(CARD_BULK_SELL_TEST_TAG).performClick()
        val paid = CardValue.resaleOf(triple, catalog.byId) * TWO_COPIES +
            CardValue.resaleOf(decked, catalog.byId)
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).mgp == paid }

        val save = storedSave(documents)
        assertEquals(1, save.copiesOf(triple))
        assertEquals(1, save.copiesOf(decked), "the deck's copy stayed")
        assertEquals(1, save.copiesOf(single))
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(CARD_BULK_BAR_TEST_TAG) }
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

    @Test
    fun theDuplicatesSegmentKeepsOnlyCardsHeldTwice() = runComposeUiTest {
        val twice = STARTER_CARDS.first { it !in STARTER_DECK }
        val once = STARTER_CARDS.last { it !in STARTER_DECK && it != twice }
        val profile = freshSave().let { it.copy(cards = it.cards + (twice to 2)) }
        setContent { Cards(profile) { IntentOutcome.APPLIED } }

        onNodeWithTag(CARD_DUPLICATES_FILTER_TEST_TAG).performClick()
        waitForIdle()

        assertTrue(exists(cardCellTestTag(twice)), "a card held twice is not listed")
        assertFalse(exists(cardCellTestTag(once)), "a card held once is listed")
        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals("Owned$DOT_SEPARATOR" + "1 / 1")
    }

    /**
     * Three copies of a deck card: the deck keeps one, two are spare. Two copies would read "1 in a
     * deck · 1 spare" whichever way round the line was built.
     */
    @Test
    fun theDetailSaysHowManyCopiesADeckKeeps() = runComposeUiTest {
        val inDeck = STARTER_DECK.first()
        val profile = freshSave().let { it.copy(cards = it.cards + (inDeck to 3)) }
        setContent { Cards(profile) { IntentOutcome.APPLIED } }

        openDetail(inDeck)

        onNodeWithTag(CARD_COPIES_LINE_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("3 owned · 1 in a deck · 2 spare")
        onNodeWithTag(CARD_SELL_COUNT_TEST_TAG, useUnmergedTree = true).assertTextEquals("1 / 2")
    }

    @Test
    fun aSingleSpareIsSoldWithoutAStepper() = runComposeUiTest {
        val inDeck = STARTER_DECK.first()
        val profile = freshSave().let { it.copy(cards = it.cards + (inDeck to 2)) }
        setContent { Cards(profile) { IntentOutcome.APPLIED } }

        openDetail(inDeck)

        onNodeWithTag(CARD_COPIES_LINE_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("2 owned · 1 in a deck · 1 spare")
        assertTrue(exists(CARD_SELL_TEST_TAG), "the spare copy is not offered")
        assertFalse(exists(CARD_SELL_COUNT_TEST_TAG), "a stepper is drawn for a single spare")
    }

    @Test
    fun theStepperSellsAsManyCopiesAsItCounts() = runComposeUiTest {
        val spare = STARTER_CARDS.first { it !in STARTER_DECK }
        val profile = freshSave().let { it.copy(cards = it.cards + (spare to SPARES)) }
        val sold = mutableListOf<Intent>()
        setContent {
            Cards(profile) {
                sold += it
                IntentOutcome.APPLIED
            }
        }

        openDetail(spare)
        onNodeWithTag(
            CARD_SELL_COUNT_TEST_TAG,
            useUnmergedTree = true,
        ).assertTextEquals("1 / $SPARES")
        onNodeWithTag(CARD_SELL_FEWER_TEST_TAG, useUnmergedTree = true).assertIsNotEnabled()
        repeat(2) { onNodeWithTag(CARD_SELL_MORE_TEST_TAG, useUnmergedTree = true).performClick() }
        onNodeWithTag(
            CARD_SELL_COUNT_TEST_TAG,
            useUnmergedTree = true,
        ).assertTextEquals("$SPARES / $SPARES")
        onNodeWithTag(CARD_SELL_MORE_TEST_TAG, useUnmergedTree = true).assertIsNotEnabled()

        onNodeWithTag(CARD_SELL_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { sold.size == SPARES }
        assertEquals(List(SPARES) { Intent.SellCard(spare) }, sold.toList())
    }

    @Test
    fun aRefusalEndsTheRunOfSales() = runComposeUiTest {
        val spare = STARTER_CARDS.first { it !in STARTER_DECK }
        val profile = freshSave().let { it.copy(cards = it.cards + (spare to 3)) }
        val asked = mutableListOf<Intent>()
        setContent {
            Cards(profile) {
                asked += it
                IntentOutcome.REFUSED
            }
        }

        openDetail(spare)
        repeat(2) { onNodeWithTag(CARD_SELL_MORE_TEST_TAG, useUnmergedTree = true).performClick() }
        onNodeWithTag(CARD_SELL_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(COLLECTION_NOTE_TEST_TAG) }

        assertEquals(1, asked.size, "the run went on after the server declined")
    }

    /**
     * The shortcut hands the house the card the panel is showing, and nothing else: which copy,
     * at what price, for how long are the desk's questions, not the list's.
     */
    @Test
    fun aSpareCardIsTakenToTheAuctionDeskFromItsPanel() = runComposeUiTest {
        val spare = STARTER_CARDS.first { it !in STARTER_DECK }
        val profile = freshSave().let { it.copy(cards = it.cards + (spare to 2)) }
        val sent = mutableListOf<Int>()
        setContent {
            Cards(profile, onAuction = { sent += it }, unlocks = Unlocks(auction = 0)) {
                IntentOutcome.APPLIED
            }
        }

        openDetail(spare)
        onNodeWithTag(CARD_AUCTION_TEST_TAG).performClick()

        assertEquals(listOf(spare), sent.toList())
    }

    /**
     * Below the house's level the auction tab is a locked door, so the shortcut is not drawn — the
     * counter still is. Same card, same spare, only the level differs from the test above.
     */
    @Test
    fun theAuctionShortcutWaitsForTheHousesLevel() = runComposeUiTest {
        val spare = STARTER_CARDS.first { it !in STARTER_DECK }
        val profile = freshSave().let { it.copy(cards = it.cards + (spare to 2)) }
        setContent {
            Cards(profile, onAuction = {}, unlocks = Unlocks(auction = profile.level + 1)) {
                IntentOutcome.APPLIED
            }
        }

        openDetail(spare)

        assertTrue(exists(CARD_SELL_TEST_TAG), "the counter went with the house")
        assertFalse(exists(CARD_AUCTION_TEST_TAG), "a shortcut to a locked door was drawn")
    }

    private fun ComposeUiTest.openDetail(cardId: Int) {
        onNodeWithTag(CARD_GRID_TEST_TAG).performScrollToNode(hasTestTag(cardCellTestTag(cardId)))
        onNodeWithTag(cardCellTestTag(cardId)).performClick()
        waitForIdle()
    }

    @Composable
    private fun Cards(
        profile: GameSave,
        onAuction: ((Int) -> Unit)? = null,
        unlocks: Unlocks = Unlocks(),
        onIntent: suspend (Intent) -> IntentOutcome,
    ) {
        CompositionLocalProvider(LocalStrings provides strings, LocalUnlocks provides unlocks) {
            TripleTriadTheme {
                CollectionScreen(
                    profile = profile,
                    catalog = catalog,
                    format = formats.default!!,
                    opponents = opponents,
                    initial = CollectionTab.CARDS,
                    onPersist = {},
                    onIntent = onIntent,
                    onBack = {},
                    onAuction = onAuction,
                )
            }
        }
    }
}
