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

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.startMatch(
    iconId: String = TEST_OPPONENT,
    block: Int = FF14_BLOCK,
) {
    newCharacter(block)
    openOpponents()
    challenge(iconId)
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.challenge(iconId: String = TEST_OPPONENT) {
    scrollToOpponent(iconId)
    onNodeWithTag(opponentRowTestTag(iconId)).performClick()
    settleDeck()
    awaitPlayer()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.settleDeck() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
        exists(DECK_SELECT_CHOOSE_TEST_TAG) || exists(BOARD_TEST_TAG)
    }
    if (exists(DECK_SELECT_EMPTY_TEST_TAG)) {
        onNodeWithTag(DECK_SELECT_RANDOM_TEST_TAG).performClick()
    } else if (exists(DECK_SELECT_CHOOSE_TEST_TAG)) {
        onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).performClick()
    }
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
}

internal const val ANY_LEVEL: Int = 99

internal val STARTER_CARDS: List<Int> = starterFor(FF14_BLOCK).cards
