package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DecksUiTest {
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    /** The labels the two moves carry — the arrows' own, kept when the arrows went. */
    private val moveLeft = english[StringKeys.MOVE_LEFT]
    private val moveRight = english[StringKeys.MOVE_RIGHT]

    /**
     * The list holds the decks the profile has, and one line for all the slots it has not used.
     *
     * It used to hold eight rows whatever the profile had — seven of them empty, each with three
     * controls that could do nothing. This is the assertion that keeps them from coming back:
     * *no* row for slot 1 on a character who has one deck.
     */
    @Test
    fun onlyTheDecksThatExistAreListedAndTheFreeSlotsAreOneLine() = runComposeUiTest {
        // A *stored* empty slot under the starter deck, which is the case that tells a list of
        // decks from a list of slots: it has a name, it is in the file, and it is not a deck.
        val documents = seeded(
            freshSave().let { save ->
                save.copy(decks = save.decks + Deck(name = SECOND_DECK, cards = emptyList()))
            },
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        onNodeWithTag(deckSlotTestTag(0)).assertExists()
        assertTrue(isVisible(GameSave.DEFAULT_DECK_NAME), "slot 0 holds the starter deck")
        for (slot in 1 until GameSave.MAX_DECKS) {
            onNodeWithTag(deckSlotTestTag(slot)).assertDoesNotExist()
        }
        assertFalse(isVisible(SECOND_DECK), "an empty slot is not a deck, named or not")
        onNodeWithTag(DECK_NEW_TEST_TAG).assertExists()
        val free = GameSave.MAX_DECKS - 1
        assertTrue(isVisible("$free slot(s) free"), "the empty slots are counted, not drawn")
    }

    /** And the line is gone once there is nowhere left for it to lead. */
    @Test
    fun theNewDeckLineDisappearsWhenEverySlotIsTaken() = runComposeUiTest {
        val documents = seeded(everySlotFilled())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        onNodeWithTag(deckSlotTestTag(GameSave.MAX_DECKS - 1)).assertExists()
        onNodeWithTag(DECK_NEW_TEST_TAG).assertDoesNotExist()
    }

    /**
     * Every row says whether it can be played, including the row that can.
     *
     * The warnings were the only thing the list ever said about a deck, so "nothing is wrong with
     * this one" was written as silence — which is also what a screen that has not finished loading
     * looks like. Three states, one pill, one of them good.
     */
    @Test
    fun everyDeckWearsItsState() = runComposeUiTest {
        val over = listOf(FIVE_STAR, OTHER_FIVE_STAR) + STARTER_DECK.take(HAND_SIZE - 2)
        val profile = withAces(deck = over).let { save ->
            save.copy(decks = save.decks + Deck(name = SECOND_DECK, cards = STARTER_DECK))
        }
        val documents = seeded(profile)
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        // Unmerged: the pill sits inside the row's own `ttoClickable`, which absorbs it.
        onNodeWithTag(deckStateTestTag(0), useUnmergedTree = true)
            .assertTextEquals("Out of limits")
        onNodeWithTag(deckStateTestTag(1), useUnmergedTree = true).assertTextEquals("Playable")
    }

    /** A deck short of a card it no longer owns is not offered as playable either. */
    @Test
    fun aDeckMissingACardIsMarkedIncomplete() = runComposeUiTest {
        val documents = seeded(freshSave().withoutCard(STARTER_DECK.first()))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        onNodeWithTag(deckStateTestTag(0), useUnmergedTree = true).assertTextEquals("Incomplete")
    }

    @Test
    fun openingASlotShowsItsCardsAndBackReturnsToTheSlots() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
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
        openDecks()

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
        openDecks()
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
        openDecks()

        // The empty slots have one line between them, and it opens the first of them.
        onNodeWithTag(DECK_NEW_TEST_TAG).performClick()
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
        openDecks()

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
        openDecks()

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
        openDecks()

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
     * The starter deck in slot 0 and a second named one under it.
     *
     * Two *named* slots, because a swap is only observable by what the names do — a fixture whose
     * second slot was the padding `withDeck` invents would let a swap that dropped a deck pass.
     * Both hold cards, because the list draws the decks a profile has and an empty slot is no
     * longer a row to swap with.
     */
    private fun twoDecks(): GameSave = freshSave().let { save ->
        save.copy(decks = save.decks + Deck(name = SECOND_DECK, cards = STARTER_DECK))
    }

    /** A profile with nothing left to fill: every slot holds a deck. */
    private fun everySlotFilled(): GameSave = freshSave().let { save ->
        save.copy(
            decks = List(GameSave.MAX_DECKS) { slot ->
                Deck(name = "Deck ${slot + 1}", cards = STARTER_DECK)
            },
        )
    }

    private companion object {
        const val SECOND_DECK = "Second"

        /** Enough of a drag to cross the row below, and not enough to cross the one after it. */
        const val DRAG_OVERSHOOT = 1.4f

        /** Past the touch slop, short of the next position: a drag that must change nothing. */
        const val DRAG_UNDERSHOOT = 0.6f

        const val DRAG_STEPS = 8

        /** A phone, and short enough that eight decks cannot all be on screen at once. */
        val SHORT_WINDOW_WIDTH = 380.dp

        val SHORT_WINDOW_HEIGHT = 520.dp

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
        openDecks()

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
     *
     * Through the ⋮ rather than through the grip: this is the path a keyboard and a screen reader
     * take, and `aDeckDraggedOverTheOneBelowSwapsWithIt` is the same swap by gesture.
     */
    @Test
    fun movingASlotDownSwapsItWithTheOneBelowAndWritesAtOnce() = runComposeUiTest {
        val documents = seeded(twoDecks())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        pickAction(deckMenuTestTag(0), deckMoveDownTestTag(0))
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
        openDecks()
        val before = storedSave(documents).decks

        pickAction(deckMenuTestTag(0), deckMoveDownTestTag(0))
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).decks.first().name == SECOND_DECK
        }
        pickAction(deckMenuTestTag(1), deckMoveUpTestTag(1))
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).decks.first().name == GameSave.DEFAULT_DECK_NAME
        }

        assertEquals(before, storedSave(documents).decks)
    }

    /**
     * The move a row has nothing to swap with is greyed rather than dropped from the menu.
     *
     * Both ends are read on a two-deck profile, because the ends are now the ends of the *list of
     * decks* and not of the eight slots: a deck in slot 0 with the next deck in slot 4 is the last
     * row on screen, and offering it a Move down would swap it with an empty slot — a move that
     * looks like nothing happening.
     */
    @Test
    fun theTopOfTheListCannotBeMovedUp() = runComposeUiTest {
        val documents = seeded(twoDecks())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        openMenu(deckMenuTestTag(0), deckMoveUpTestTag(0))

        onNodeWithTag(deckMoveUpTestTag(0)).assertIsNotEnabled()
        onNodeWithTag(deckMoveDownTestTag(0)).assertIsEnabled()
    }

    /**
     * And the bottom cannot be moved down.
     *
     * Its own test rather than two halves of one, because a menu is a window: the click that would
     * open the second one is spent dismissing the first, and a test that opened both in turn would
     * be asserting against a menu that never opened.
     */
    @Test
    fun theBottomOfTheListCannotBeMovedDown() = runComposeUiTest {
        val documents = seeded(twoDecks())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        openMenu(deckMenuTestTag(1), deckMoveDownTestTag(1))

        onNodeWithTag(deckMoveUpTestTag(1)).assertIsEnabled()
        onNodeWithTag(deckMoveDownTestTag(1)).assertIsNotEnabled()
    }

    /**
     * The same swap, by the grip.
     *
     * A drag is the reason the arrows could leave the row, so it is held by a test that fails if
     * the gesture stops working — the arrows in the menu would otherwise cover for it. The travel
     * is one row and a third: enough to cross the row below, short of crossing two.
     */
    @Test
    fun aDeckDraggedOverTheOneBelowSwapsWithIt() = runComposeUiTest {
        val documents = seeded(twoDecks())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        val row = onNodeWithTag(deckSlotTestTag(0)).fetchSemanticsNode().size.height
        val travel = row * DRAG_OVERSHOOT
        onNodeWithTag(deckDragTestTag(0)).performTouchInput {
            down(center)
            for (step in 1..DRAG_STEPS) {
                moveTo(center + Offset(0f, travel * step / DRAG_STEPS))
            }
            up()
        }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).decks.first().name == SECOND_DECK
        }
        assertEquals(
            GameSave.DEFAULT_DECK_NAME,
            storedSave(documents).decks[1].name,
            "the deck that was dragged should be under the one it passed",
        )
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
        openDecks()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        shiftBy(0, moveRight)
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        val expected = listOf(STARTER_DECK[1], STARTER_DECK[0]) + STARTER_DECK.drop(2)
        assertEquals(expected, storedSave(documents).decks.first().cards)
        assertEquals(HAND_SIZE, storedSave(documents).decks.first().cards.size, "nothing was lost")
    }

    /** Shifting left is the same move back, and the ends of the hand offer no such action. */
    @Test
    fun theEndsOfTheHandCannotBeShiftedPastThem() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        assertEquals(listOf(moveRight), shiftsAt(0), "the first position can only go right")
        assertEquals(
            listOf(moveLeft),
            shiftsAt(HAND_SIZE - 1),
            "the last position can only go left",
        )

        shiftBy(0, moveRight)
        shiftBy(1, moveLeft)
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        assertEquals(STARTER_DECK, storedSave(documents).decks.first().cards, "right then left")
    }

    /** An empty position has nothing to shift, so it offers neither move. */
    @Test
    fun anEmptyPositionOffersNoShift() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        onNodeWithTag(DECK_NEW_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        for (position in 0 until HAND_SIZE) {
            assertEquals(emptyList(), shiftsAt(position), "position $position offers a move")
        }
    }

    // ---- A card the deck names and the profile no longer holds ---------------

    @Test
    fun aDeckNamingACardNoLongerOwnedSaysSo() = runComposeUiTest {
        val lost = STARTER_DECK.first()
        val documents = seeded(freshSave().withoutCard(lost))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

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
        newCharacter()
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
        openDecks()

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
        openDecks()

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
        openDecks()

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

    @Test
    fun fillingAnEmptyDraftProducesAFullLegalDeck() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openDecks()

        // Slot 1 is empty on a fresh character, and the new-deck line is how it is reached.
        onNodeWithTag(DECK_NEW_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        for (position in 0 until HAND_SIZE) {
            onNodeWithTag(deckPositionTestTag(position)).assertExists()
        }

        onNodeWithTag(DECK_FILL_TEST_TAG).performClick()
        waitForIdle()

        // The count on the power line is the screen's own statement that the deck is complete.
        assertTrue(isVisible("$HAND_SIZE / $HAND_SIZE"), "the draft was not filled")
        assertFalse(exists(DECK_OVER_LIMIT_TEST_TAG), "the fill broke a rank cap")
        assertFalse(exists(DECK_MISSING_TEST_TAG), "the fill used cards the profile does not own")
    }

    @Test
    fun fillingIsRefusedOnceThereIsNothingLeftToAdd() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openDecks()

        // The starter slot is already five cards, so the control has nothing to do on it.
        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        onNodeWithTag(DECK_FILL_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun duplicatingASlotCopiesItsCardsIntoTheFirstEmptyOne() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openDecks()

        pickAction(deckMenuTestTag(0), deckCopyTestTag(0))
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(deckSlotTestTag(1)) }

        // The copy landed in slot 1, and it holds the same five cards — which the slot row states
        // as its own count.
        onNodeWithTag(deckSlotTestTag(1)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        assertTrue(isVisible("$HAND_SIZE / $HAND_SIZE"), "the copy is not a full deck")
        assertFalse(exists(DECK_MISSING_TEST_TAG), "the copy claims cards the profile lacks")
    }

    /** With every slot spoken for there is nowhere to copy to, and the item says so. */
    @Test
    fun aDuplicateWithNowhereToLandIsRefused() = runComposeUiTest {
        val documents = seeded(everySlotFilled())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        openMenu(deckMenuTestTag(0), deckCopyTestTag(0))
        // Greyed rather than dropped, so the menu is the same three lines on every row.
        onNodeWithTag(deckCopyTestTag(0)).assertExists().assertIsNotEnabled()
    }

    /**
     * **A list too long for the window scrolls; it does not squeeze its last deck.**
     *
     * The scaffold hands its content a column of a bounded height and no scrolling of its own, so
     * a `Column` of eight rows in a phone-height window measured the rows it had room for and gave
     * the ones past the fold what was left, which is nothing. The last deck was drawn flat.
     * Asserted as geometry, on the shortest window the app is meant to run in.
     */
    @Test
    fun theListScrollsRatherThanFlatteningItsLastDeck() = runComposeUiTest {
        val documents = seeded(everySlotFilled())
        setContent {
            Box(modifier = Modifier.size(SHORT_WINDOW_WIDTH, SHORT_WINDOW_HEIGHT)) {
                TestApp(store = settingsFor(AppLocale.EN_US), documents = documents)
            }
        }
        loadCharacter(documents)
        openDecks()

        val last = GameSave.MAX_DECKS - 1
        val first = onNodeWithTag(deckSlotTestTag(0)).getUnclippedBoundsInRoot().height
        assertEquals(
            first,
            onNodeWithTag(deckSlotTestTag(last)).getUnclippedBoundsInRoot().height,
            "the last deck is drawn shorter than the first",
        )

        // And it is reachable: a row kept at full height below the fold is no better than a flat
        // one if nothing scrolls it into view.
        onNodeWithTag(DECK_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(deckSlotTestTag(last)))
        onNodeWithTag(deckSlotTestTag(last)).assertIsDisplayed()
    }

    /**
     * An empty position holds the space a card would take, so the row keeps its shape.
     *
     * A gap drawn shorter than a sprite would pull its grip up level with nothing and leave the
     * hand stepping up and down as cards go in and come out. Asserted as geometry because that is
     * what it is.
     */
    @Test
    fun anEmptyPositionKeepsTheRowOnOneLine() = runComposeUiTest {
        val hand = elementalHand()
        val documents = seeded(handProfile(hand))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        // A hole in the middle: tapping a position takes its card out of the draft.
        onNodeWithTag(deckPositionTestTag(2)).performClick()
        waitForIdle()

        val first = onNodeWithTag(deckPositionDragTestTag(0)).getUnclippedBoundsInRoot()
        val last = onNodeWithTag(deckPositionDragTestTag(HAND_SIZE - 1))
            .getUnclippedBoundsInRoot()
        assertEquals(first.top, last.top, "the grips under the row are not on one line")
    }

    /**
     * **A position is the card as the board will draw it, not a portrait of it.**
     *
     * Asserted on the label [CardFace] gives every card it draws — name and the four powers in
     * board order. A thumbnail carries no such label, so this is the assertion that keeps the
     * hand from quietly going back to five 40 dp portraits.
     */
    @Test
    fun everyPositionDrawsTheCardTheWayTheBoardWill() = runComposeUiTest {
        val hand = elementalHand()
        val documents = seeded(handProfile(hand))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        for ((position, card) in hand.withIndex()) {
            val label = "${english[card.nameKey]}, " +
                "${card.top} ${card.right} ${card.bottom} ${card.left}"
            onNode(
                hasContentDescription(label) and
                    hasAnyAncestor(hasTestTag(deckPositionTestTag(position))),
                useUnmergedTree = true,
            ).assertExists()
        }
    }

    /**
     * **A card dragged onto its neighbour changes places with it.**
     *
     * The same swap the two arrows make, by the gesture a hand of five cards is actually
     * rearranged with — and the one the list of decks already used, see
     * `aDeckDraggedOverTheOneBelowSwapsWithIt`. Read on what is *saved*, because the editor holds
     * a draft: a reordering that never reaches the file is a reordering the player loses.
     */
    @Test
    fun aCardDraggedOntoItsNeighbourSwapsWithIt() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        // The step is the *column*, which is wider than the thumbnail the gesture goes down on —
        // the powers under the card set the width. Measured off two positions rather than
        // assumed, so this test does not have to know either number.
        val first = onNodeWithTag(deckPositionTestTag(0)).fetchSemanticsNode().positionInRoot
        val second = onNodeWithTag(deckPositionTestTag(1)).fetchSemanticsNode().positionInRoot
        val travel = (second.x - first.x) * DRAG_OVERSHOOT

        onNodeWithTag(deckPositionTestTag(0)).performTouchInput {
            down(center)
            for (step in 1..DRAG_STEPS) {
                moveTo(center + Offset(travel * step / DRAG_STEPS, 0f))
            }
            up()
        }
        waitForIdle()

        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        assertEquals(
            listOf(STARTER_DECK[1], STARTER_DECK[0]) + STARTER_DECK.drop(2),
            storedSave(documents).decks.first().cards,
            "the dragged card should be where the one it passed was",
        )
    }

    /**
     * **And the same drag taken by the grip, which is where the arrows used to be.**
     *
     * The card is draggable too, but the grip is the part that *says* the hand can be
     * rearranged — it is what replaced the two arrows, and a grip that only looked the part
     * would leave the screen with no visible way to reorder at all.
     */
    @Test
    fun aCardDraggedByItsGripSwapsWithItsNeighbour() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        val first = onNodeWithTag(deckPositionTestTag(0)).fetchSemanticsNode().positionInRoot
        val second = onNodeWithTag(deckPositionTestTag(1)).fetchSemanticsNode().positionInRoot
        val travel = (second.x - first.x) * DRAG_OVERSHOOT

        onNodeWithTag(deckPositionDragTestTag(0)).performTouchInput {
            down(center)
            for (step in 1..DRAG_STEPS) {
                moveTo(center + Offset(travel * step / DRAG_STEPS, 0f))
            }
            up()
        }
        waitForIdle()

        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        assertEquals(
            listOf(STARTER_DECK[1], STARTER_DECK[0]) + STARTER_DECK.drop(2),
            storedSave(documents).decks.first().cards,
            "the grip did not move the card it belongs to",
        )
    }

    /** And a drag short of the next position moves nothing — the hand is not a slider. */
    @Test
    fun aCardDraggedLessThanAWholePositionStaysWhereItIs() = runComposeUiTest {
        val documents = seeded(freshSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openDecks()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        val first = onNodeWithTag(deckPositionTestTag(0)).fetchSemanticsNode().positionInRoot
        val second = onNodeWithTag(deckPositionTestTag(1)).fetchSemanticsNode().positionInRoot
        val travel = (second.x - first.x) * DRAG_UNDERSHOOT

        onNodeWithTag(deckPositionTestTag(0)).performTouchInput {
            down(center)
            for (step in 1..DRAG_STEPS) {
                moveTo(center + Offset(travel * step / DRAG_STEPS, 0f))
            }
            up()
        }
        waitForIdle()

        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        assertEquals(
            STARTER_DECK,
            storedSave(documents).decks.first().cards,
            "a drag that stopped short still moved the card",
        )
    }

    /** Five cards that all carry an element, so the badge has something to draw. */
    private fun elementalHand(): List<Card> =
        runBlocking { loadCardCatalog() }.all
            .filter { it.type != null }
            .sortedBy { it.id }
            .take(HAND_SIZE)

    // ---- The two moves the arrows used to make -------------------------------

    /**
     * What a position offers a screen reader, in the order it offers them.
     *
     * The arrows are gone and the drag replacing them reaches a finger and a mouse only; these
     * actions are the whole of the keyboard and screen-reader path, so they are asserted the way
     * the arrows were.
     */
    private fun ComposeUiTest.shiftsAt(position: Int): List<String> =
        onNodeWithTag(deckPositionTestTag(position))
            .fetchSemanticsNode()
            .config
            .getOrElse(SemanticsActions.CustomActions) { emptyList() }
            .map { it.label }

    private fun ComposeUiTest.shiftBy(position: Int, label: String) {
        val action = onNodeWithTag(deckPositionTestTag(position))
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .first { it.label == label }
        runOnIdle { action.action() }
        waitForIdle()
    }

    private fun handProfile(hand: List<Card>): GameSave = freshSave().let { save ->
        save.copy(
            cards = save.cards + hand.associate { it.id to 1 },
            decks = listOf(Deck(name = "Elements", cards = hand.map { it.id })),
        )
    }

    // ---- Reaching the ⋮ ------------------------------------------------------

    /** Menu items are composed into their own window, so one is only there once it is open. */
    private fun ComposeUiTest.openMenu(menu: String, item: String) {
        onNodeWithTag(menu).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(item) }
    }

    private fun ComposeUiTest.pickAction(menu: String, item: String) {
        openMenu(menu, item)
        onNodeWithTag(item).performClick()
        waitForIdle()
    }
}
