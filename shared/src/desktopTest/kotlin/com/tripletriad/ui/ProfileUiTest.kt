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
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter(FF8_BLOCK)

        val save = stored(documents).single()
        val starter = starterFor(FF8_BLOCK)

        assertEquals(starter.cards.associateWith { 1 }, save.cards)
        assertEquals(listOf(starter.deck), save.decks.map { it.cards })
    }

    @Test
    fun theCreationScreenShowsTheStarterItWouldGrant() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NEW_TEST_TAG) }
        onNodeWithTag(PROFILE_NEW_TEST_TAG).performClick()
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
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        awaitMenu()
        onNodeWithTag(MENU_PROFILES_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_EMPTY_TEST_TAG) }

        onNodeWithTag(PROFILE_EMPTY_TEST_TAG).assertExists()
        assertFalse(exists(PROFILE_LIST_TEST_TAG), "an empty list should not draw a list")
    }

    @Test
    fun creatingACharacterWritesItAndOpensItsDashboard() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }

        newCharacter()

        assertEquals(1, documents.writes, "creating should have written exactly one profile")
        val saved = stored(documents).single()
        assertEquals(GameSave.DEFAULT_USERNAME, saved.username)
        assertEquals(starterFor(FF14_BLOCK).cards.associateWith { 1 }, saved.cards)
    }

    @Test
    fun theTypedNameIsTheCharactersName() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        awaitMenu()
        onNodeWithTag(MENU_PROFILES_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NEW_TEST_TAG) }
        onNodeWithTag(PROFILE_NEW_TEST_TAG).performClick()
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
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }

        newCharacter(FF8_BLOCK)
        openOpponents()

        val saved = stored(documents).single()
        assertEquals(starterFor(FF8_BLOCK).cards.associateWith { 1 }, saved.cards)
        // `chocoboy` shipped as ff8-only and `tt-master` as ff14-only. Both are on the list.
        scrollToOpponent("chocoboy")
        onNodeWithTag(opponentRowTestTag("chocoboy")).assertExists()
        scrollToOpponent(TEST_OPPONENT)
        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).assertExists()
    }

    @Test
    fun aCreatedCharacterIsListedAndNamedOnTheMenu() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        // Back out to the menu: dashboard → characters → menu.
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
        assertTrue(isVisible(GameSave.DEFAULT_USERNAME), "the character should be in the list")

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitMenu()

        assertTrue(
            isVisible(GameSave.DEFAULT_USERNAME),
            "the menu should name the loaded character",
        )
    }

    @Test
    fun deletingTakesTwoTapsAndRemovesTheFile() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
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
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        val key = documents.stored.keys.single()
        onNodeWithTag(profileDeleteTestTag(key)).performClick()
        onNodeWithTag(profileDeleteTestTag(key)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_EMPTY_TEST_TAG) }
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitMenu()

        onNodeWithTag(MENU_PROFILE_TEST_TAG).assertTextEquals(NO_CHARACTER)
    }

    @Test
    fun twoCharactersCanCoexist() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter(FF14_BLOCK)
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
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
            setOf(starterFor(FF14_BLOCK).cards.toSet(), starterFor(FF8_BLOCK).cards.toSet()),
            saved.map { it.cards.keys.toSet() }.toSet(),
            "the two should keep the boxes they were opened with",
        )
    }

    @Test
    fun choosingAListedCharacterLoadsIt() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter(FF8_BLOCK)
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
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
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()
        val written = documents.stored.toMap()

        // A second `App` over the same store is what "launching again" is, short of a new process.
        setContent {
            App(
                store = settingsFor(AppLocale.EN_US),
                documents = InMemoryDocumentStore(written),
                clock = FixedClock(),
            )
        }
        awaitMenu()
        onNodeWithTag(MENU_PROFILES_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        assertTrue(isVisible(GameSave.DEFAULT_USERNAME), "the stored character should be listed")
    }

    private companion object {
        const val NAME = "Sigfrid"

        const val NO_CHARACTER = "No character yet — create one to play."
    }
}
