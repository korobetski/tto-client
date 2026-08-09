package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.storage.InMemoryDocumentStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Choosing the portrait, and the file that has to remember it.
 *
 * Driven through the real `App` and asserted against the decoded `.sav`, for the reason
 * `ProfileUiTest` gives: a screen that shows the new avatar without writing it looks identical
 * until the next launch.
 */
@OptIn(ExperimentalTestApi::class)
class AvatarUiTest {

    @Test
    fun theRecordOpensThePickerAndTheChoiceReachesTheFile() = runComposeUiTest {
        val documents = seeded(GameSave(username = "kuplu"))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }

        loadCharacter(documents)
        openPicker()
        pick(documents, OTHER_AVATAR)

        assertEquals(OTHER_AVATAR, storedSave(documents).avatarId)
    }

    /** And the picker draws every portrait the bundle ships, not a subset somebody typed twice. */
    @Test
    fun everyImportedPortraitIsOffered() = runComposeUiTest {
        val documents = seeded(GameSave(username = "kuplu"))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }

        loadCharacter(documents)
        openPicker()

        assertEquals(AVATAR_NAMES.size, AVATAR_NAMES.distinct().size, "duplicated avatar names")
        for (id in AVATAR_NAMES) {
            onNodeWithTag(AVATAR_GRID_TEST_TAG)
                .performScrollToNode(hasTestTag(avatarChoiceTestTag(id)))
        }
    }

    /** Choosing twice keeps the last one — the choice is cosmetic and has no commit step. */
    @Test
    fun aSecondChoiceReplacesTheFirst() = runComposeUiTest {
        val documents = seeded(GameSave(username = "kuplu"))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }

        loadCharacter(documents)
        openPicker()
        pick(documents, OTHER_AVATAR)
        pick(documents, GameSave.DEFAULT_AVATAR)

        assertEquals(GameSave.DEFAULT_AVATAR, storedSave(documents).avatarId)
    }

    private fun ComposeUiTest.openPicker() {
        openFromDashboard(DASHBOARD_STATS_TEST_TAG, STATS_LEVEL_TEST_TAG)
        onNodeWithTag(AVATAR_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AVATAR_GRID_TEST_TAG) }
    }

    private fun ComposeUiTest.pick(documents: InMemoryDocumentStore, avatarId: String) {
        onNodeWithTag(AVATAR_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(avatarChoiceTestTag(avatarId)))
        onNodeWithTag(avatarChoiceTestTag(avatarId)).performClick()
        // The write is what the test is about, so it waits for the file rather than for idleness.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).avatarId == avatarId }
    }

    private companion object {
        /** Any portrait that is not the one every new character starts with. */
        const val OTHER_AVATAR = "ffxiv_twi01001"
    }
}
