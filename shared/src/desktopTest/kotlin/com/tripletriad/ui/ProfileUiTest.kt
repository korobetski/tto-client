package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.FF14_BLOCK
import com.tripletriad.FF8_BLOCK
import com.tripletriad.data.SaveRepository
import com.tripletriad.data.StarterCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileUiTest {
    private fun store() = InMemoryDocumentStore()

    private fun stored(documents: InMemoryDocumentStore): List<GameSave> =
        runBlocking { SaveRepository(documents).list().map { it.save } }

    @Test
    fun creatingACharacterGrantsTheAuthoredStarter() = runComposeUiTest {
        val documents = store()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter(FF8_BLOCK)

        assertOpenedWith(stored(documents).single(), FF8_BLOCK)
    }

    /**
     * [save] holds the box authored for [block], and nothing else.
     *
     * Only five of the nine are authored — the other four are drawn from the block's commons by
     * `StarterPack.drawn`, with the app's own generator — so what can be asserted is the shape: the
     * count, the deck, and that every card came out of the right block. Pinning the four would mean
     * pinning a seed the UI does not take.
     */
    private fun assertOpenedWith(save: GameSave, block: Int) {
        val starter = starterFor(block)

        assertEquals(listOf(starter.deck), save.decks.map { it.cards }, "the authored deck")
        assertEquals(StarterCatalog.SIZE, save.cards.size, "the box is ${StarterCatalog.SIZE}")
        assertEquals(setOf(1), save.cards.values.toSet(), "one copy of each")
        assertTrue(save.cards.keys.containsAll(starter.deck), "the deck is owned")
        assertTrue(
            save.cards.keys.all { pvpCards.byId.getValue(it).block == block },
            "a card from another block was dealt: ${save.cards.keys}",
        )
    }

    @Test
    fun theCreationScreenShowsTheStarterItWouldGrant() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        awaitTitleChoice("new")
        onNodeWithTag(titleChoiceTestTag("new")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_CREATE_TEST_TAG) }

        val ff14 = starterFor(FF14_BLOCK)
        assertTrue(
            exists(starterPreviewTestTag(ff14.id)),
            "the default collection's starter should be previewed on arrival",
        )

        // And it follows the choice, which is the half that makes the preview worth having.
        val ff8 = starterFor(FF8_BLOCK)
        onNodeWithTag(starterChoiceTestTag(ff8.id)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(starterPreviewTestTag(ff8.id)) }
        assertFalse(exists(starterPreviewTestTag(ff14.id)), "one starter at a time")
    }

    @Test
    fun aFreshInstallHasNoCharacters() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        // The list is reached by stepping back out of creation: the title screen sends a
        // device with nothing on it straight to the form, since there is nothing to list.
        awaitTitleChoice("new")
        onNodeWithTag(titleChoiceTestTag("new")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_CREATE_TEST_TAG) }
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_EMPTY_TEST_TAG) }

        onNodeWithTag(PROFILE_EMPTY_TEST_TAG).assertExists()
        assertFalse(exists(PROFILE_LIST_TEST_TAG), "an empty list should not draw a list")
    }

    @Test
    fun creatingACharacterWritesItAndOpensItsDashboard() = runComposeUiTest {
        val documents = store()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }

        newCharacter()

        assertEquals(1, documents.writes, "creating should have written exactly one profile")
        val saved = stored(documents).single()
        assertEquals(GameSave.DEFAULT_USERNAME, saved.username)
        assertOpenedWith(saved, FF14_BLOCK)
    }

    @Test
    fun theTypedNameIsTheCharactersName() = runComposeUiTest {
        val documents = store()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        awaitTitleChoice("new")
        onNodeWithTag(titleChoiceTestTag("new")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NAME_TEST_TAG) }

        onNodeWithTag(PROFILE_NAME_TEST_TAG).performTextClearance()
        onNodeWithTag(PROFILE_NAME_TEST_TAG).performTextInput(NAME)
        onNodeWithTag(PROFILE_CREATE_TEST_TAG).performClick()
        awaitDashboard()

        assertEquals(NAME, stored(documents).single().username)
        assertTrue(
            documents.stored.keys.single().startsWith(NAME.lowercase()),
            "the file should be named after the character: ${documents.stored.keys}",
        )
    }

    @Test
    fun theChosenBoxDealsItsCardsAndLeavesTheRosterWhole() = runComposeUiTest {
        val documents = store()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }

        newCharacter(FF8_BLOCK)
        openOpponents()

        assertOpenedWith(stored(documents).single(), FF8_BLOCK)
        // `chocoboy` shipped as ff8-only and `tt-master` as ff14-only. Both are on the list.
        scrollToOpponent("chocoboy")
        onNodeWithTag(opponentRowTestTag("chocoboy")).assertExists()
        scrollToOpponent(TEST_OPPONENT)
        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).assertExists()
    }

    @Test
    fun aCreatedCharacterIsListedAndCanBeLoadedFromTheTitleScreen() = runComposeUiTest {
        val documents = store()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()

        // Out of the session, then out of the list: lobby → characters → title.
        signOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
        assertTrue(isVisible(GameSave.DEFAULT_USERNAME), "the character should be in the list")

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitTitle()

        // "New Game" was the only offer on the way in; there is somebody to load now.
        loadCharacter(documents)
    }

    @Test
    fun deletingTakesTwoTapsAndRemovesTheFile() = runComposeUiTest {
        val documents = store()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()
        signOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        val key = documents.stored.keys.single()
        onNodeWithTag(profileDeleteTestTag(key)).performClick()
        waitForIdle()

        assertEquals(1, stored(documents).size, "the first tap must only arm the control")
        // Armed, the × becomes the word — so the second tap is unambiguous.
        onNodeWithTag(profileDeleteTestTag(key)).assertTextEquals("Delete")

        onNodeWithTag(profileDeleteTestTag(key)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_EMPTY_TEST_TAG) }

        assertTrue(stored(documents).isEmpty(), "the profile should be gone")
    }

    @Test
    fun deletingTheLoadedCharacterUnloadsIt() = runComposeUiTest {
        val documents = store()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()
        signOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        val key = documents.stored.keys.single()
        onNodeWithTag(profileDeleteTestTag(key)).performClick()
        onNodeWithTag(profileDeleteTestTag(key)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_EMPTY_TEST_TAG) }
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitTitle()

        // The title screen's prompt is where "there is nobody to play as" is now said.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            exists(titleChoiceTestTag("new"))
        }
        onNodeWithTag(TITLE_PROMPT_TEST_TAG).assertTextEquals(NO_CHARACTER)
    }

    @Test
    fun twoCharactersCanCoexist() = runComposeUiTest {
        val documents = store()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter(FF14_BLOCK)
        signOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        onNodeWithTag(PROFILE_NEW_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NAME_TEST_TAG) }
        onNodeWithTag(PROFILE_NAME_TEST_TAG).performTextClearance()
        onNodeWithTag(PROFILE_NAME_TEST_TAG).performTextInput(NAME)
        onNodeWithTag(starterChoiceTestTag(starterFor(FF8_BLOCK).id)).performClick()
        onNodeWithTag(PROFILE_CREATE_TEST_TAG).performClick()
        awaitDashboard()

        val saved = stored(documents)
        assertEquals(2, saved.size, "both characters should be on disk")
        assertEquals(
            setOf(listOf(starterFor(FF14_BLOCK).deck), listOf(starterFor(FF8_BLOCK).deck)),
            saved.map { profile -> profile.decks.map { it.cards } }.toSet(),
            "the two should keep the boxes they were opened with",
        )
        for (profile in saved) {
            val first = profile.decks.first().cards.first()
            assertOpenedWith(profile, pvpCards.byId.getValue(first).block)
        }
    }

    @Test
    fun choosingAListedCharacterLoadsIt() = runComposeUiTest {
        val documents = store()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter(FF8_BLOCK)
        signOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        onNodeWithTag(profileRowTestTag(documents.stored.keys.single())).performClick()
        awaitDashboard()
        openOpponents()

        scrollToOpponent("chocoboy")
        onNodeWithTag(opponentRowTestTag("chocoboy")).assertExists()
    }

    @Test
    fun aStoredCharacterSurvivesARelaunch() = runComposeUiTest {
        val documents = store()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()
        val written = documents.stored.toMap()

        // A second `App` over the same store is what "launching again" is, short of a new process.
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                documents = InMemoryDocumentStore(written),
                clock = FixedClock(),
            )
        }
        awaitTitleChoice("profiles")
        onNodeWithTag(titleChoiceTestTag("profiles")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        assertTrue(isVisible(GameSave.DEFAULT_USERNAME), "the stored character should be listed")
    }

    private companion object {
        const val NAME = "Sigfrid"

        const val NO_CHARACTER = "No character yet — create one to play."
    }
}
