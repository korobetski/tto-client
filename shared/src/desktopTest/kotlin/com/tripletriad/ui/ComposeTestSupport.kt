package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.tripletriad.FF14_BLOCK
import com.tripletriad.data.SaveRepository
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.runBlocking

internal const val UI_TIMEOUT_MS = 10_000L

internal const val TUTORIAL_TIMEOUT_MS = 30_000L

internal const val TEST_OPPONENT = "tt-master"

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.isVisible(text: String): Boolean =
    onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertVisible(text: String, message: String) {
    check(isVisible(text)) { "$message (no node containing \"$text\")" }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.exists(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.existsUnmerged(tag: String): Boolean =
    onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

internal fun settingsFor(locale: AppLocale, lessonsDone: Int = 0): SettingsStore =
    InMemorySettingsStore("""{"language":"${locale.tag}","lessons_done":$lessonsDone}""")

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitMenu() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(MENU_PLAY_TEST_TAG) }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.newCharacter(block: Int = FF14_BLOCK) {
    awaitMenu()
    onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NEW_TEST_TAG) }
    onNodeWithTag(PROFILE_NEW_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_CREATE_TEST_TAG) }
    onNodeWithTag(starterChoiceTestTag(starterFor(block).id)).performClick()
    onNodeWithTag(PROFILE_CREATE_TEST_TAG).performClick()
    awaitDashboard()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitDashboard() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DASHBOARD_PLAY_TEST_TAG) }
}

internal fun seeded(save: GameSave): InMemoryDocumentStore {
    val documents = InMemoryDocumentStore()
    runBlocking { SaveRepository(documents).save(save, at = 0L) }
    return documents
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.loadCharacter(documents: InMemoryDocumentStore) {
    awaitMenu()
    onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
    onNodeWithTag(profileRowTestTag(documents.stored.keys.single())).performClick()
    awaitDashboard()
}

internal fun storedSave(documents: InMemoryDocumentStore): GameSave =
    runBlocking { SaveRepository(documents).list().single().save }

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitOpponents() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPPONENT_LIST_TEST_TAG) }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openLessons() {
    onNodeWithTag(DASHBOARD_LESSONS_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(LESSONS_LIST_TEST_TAG) }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.scrollToLesson(lesson: Int) {
    onNodeWithTag(LESSONS_LIST_TEST_TAG).performScrollToNode(hasTestTag(lessonRowTestTag(lesson)))
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openOpponents() {
    onNodeWithTag(DASHBOARD_PLAY_TEST_TAG).performClick()
    awaitOpponents()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openFromDashboard(entry: String, landmark: String) {
    onNodeWithTag(entry).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(landmark) }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openFromBar(tab: String, landmark: String) {
    onNodeWithTag(navTestTag(tab)).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(landmark) }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.backToDashboard() {
    onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
    awaitDashboard()
}

/**
 * Straight to the dashboard of the profile the **server** holds.
 *
 * The counterpart of [newCharacter], and it exists because a refereed match has no local profile to
 * play it: the account is signed in before the first frame — see `PveStubServer.connection` — so
 * "choose a character" has already been answered and tapping Play lands on the dashboard.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openDashboard() {
    awaitMenu()
    onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
    awaitDashboard()
}

/**
 * A board, from a cold start, against [iconId].
 *
 * Needs an `App` given a `PveStubServer` connection. Without one there is no referee, and a screen
 * that renders what a referee sent renders nothing at all.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.startMatch(iconId: String = TEST_OPPONENT) {
    openDashboard()
    openOpponents()
    challenge(iconId)
}

/** Waits for a refereed board to arrive. There is nothing local to settle first. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitBoard() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.challenge(iconId: String = TEST_OPPONENT) {
    scrollToOpponent(iconId)
    onNodeWithTag(opponentRowTestTag(iconId)).performClick()
    awaitBoard()
    awaitPlayer()
}

internal const val ANY_LEVEL: Int = 99

internal val STARTER_CARDS: List<Int> = starterFor(FF14_BLOCK).cards
