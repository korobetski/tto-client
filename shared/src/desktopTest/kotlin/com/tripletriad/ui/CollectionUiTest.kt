package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.FF14_BLOCK
import com.tripletriad.FF8_BLOCK
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The collection browser: the whole card table, owned and not.
 *
 * The point of the screen is the second half of that. `cardListScreen.as:101-106` walks the whole
 * table and dims what the profile does not have, and a browser that showed only what you own would
 * not tell you what there is to get.
 */
@OptIn(ExperimentalTestApi::class)
class CollectionUiTest {
    private fun ComposeUiTest.openCards(block: Int = FF14_BLOCK) {
        newCharacter(block)
        openFromBar("cards", CARD_GRID_TEST_TAG)
    }

    /** Counted over the table, so an id outside it cannot push the total past the table's size. */
    @Test
    fun theTotalCountsWhatIsOwnedAgainstTheWholeTable() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${STARTER_CARDS.size} / $ALL_CARDS",
        )
    }

    /** The detail panel says what to do rather than sitting blank, which is what the AS3 did. */
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

    /** Tapping the selected card again closes the detail. */
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

    /**
     * An unowned card is drawn and is tappable.
     *
     * `CardThumb.enabled = false` made unowned thumbs untouchable in the original, so the
     * description of the card you were hunting for was the one thing you could not read.
     */
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

    /**
     * Whichever box a character opened, it browses the whole table.
     *
     * The count is the starter's ten out of both tables: a card an FFVIII character does not own is
     * still a card that exists, and the browser's job is to show what there is to want.
     */
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

    /**
     * A second copy is a badge on the one cell, not a second cell — see `CardListBody`.
     *
     * Both halves matter: absent at one copy, because "x1" on every cell is noise, and the total
     * still counts distinct cards, because owning two of something is not owning two more cards.
     */
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

    private companion object {
        /**
         * Both tables, because the screen shows one.
         *
         * `CardBundleTest` pins the two halves — 153 and 110 — and this is their sum. It is the
         * whole table now: the browser lists what the *format* admits, and the format the app plays
         * is the widest one. It used to list what `MODE` named.
         */
        const val ALL_CARDS = 263

        /** An ff14 card the starter does not include. */
        val UNOWNED_CARD = Card.idFor(block = 1, number = 44)

        /** A card in the ff14 set only — number 138, which the 110-card ff8 set has not. */
        val FF14_ONLY_CARD = Card.idFor(block = 1, number = 138)
    }
}
