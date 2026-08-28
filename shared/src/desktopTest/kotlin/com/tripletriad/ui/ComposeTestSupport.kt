package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.tripletriad.FF14_BLOCK
import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.SilentAudioPlayer
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.FormatCatalog
import com.tripletriad.data.SaveRepository
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadFormatCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.net.ServerConnection
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.Clock
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.runBlocking

/**
 * The app under test, at a pace no one has to sit through.
 *
 * Everything else about it is [App] — same composition, same defaults, same code paths. The one
 * difference is [TEST_PACING], and it is the difference between a suite that takes nine minutes
 * and one that does not: a Compose UI test waits out the game's scripted pauses in real time, and
 * `TutorialUiTest` alone was 247s of a 526s run because `TalkBubble` holds every line for five
 * seconds.
 *
 * A wrapper rather than an argument at each of the 235 call sites, so that the pace is written
 * down once and a test written tomorrow gets it without knowing it exists. A test that needs the
 * real thing — because what it is checking *is* the pacing — passes `pacing = Pacing.Default`.
 *
 * [ScreenshotCapture] deliberately still calls [App]: the README's pictures should be of the app
 * as it ships.
 */
@Composable
@Suppress("LongParameterList")
internal fun TestApp(
    store: SettingsStore = InMemorySettingsStore(),
    documents: DocumentStore = InMemoryDocumentStore(),
    clock: Clock = FixedClock(),
    audio: AudioPlayer = SilentAudioPlayer,
    onQuit: () -> Unit = {},
    server: ServerConnection? = null,
    pacing: Pacing = TEST_PACING,
) = App(
    store = store,
    documents = documents,
    clock = clock,
    audio = audio,
    onQuit = onQuit,
    server = server,
    pacing = pacing,
)

/**
 * Fast, but not instant, and **a tenth rather than a fiftieth** — which was measured, not chosen.
 *
 * Two floors. The first is zero: `withTimeoutOrNull(0)` in [TalkBubble] never lets a tap win its
 * race and `tween(0)` stops being an animation, so the tests would exercise paths the player never
 * takes.
 *
 * The second is the one that actually bit. Uniform scaling preserves the app's own orderings — the
 * tutor still speaks before it plays, whatever the factor — but it does not scale the *test
 * harness*: a `waitUntil` poll, a `performClick`, a frame. At a fiftieth the app's windows come
 * down to about a hundred milliseconds, which is the same order as that latency, and the two start
 * racing. It showed up as `TutorialUiTest.theTutorSpeaksBeforePlaying` reading the second line
 * where it asserts the first, and as `theCourseEndsAtTheRuleBook` needing the shipped pace and then
 * overrunning `runTest`'s one-minute limit at it. At a tenth every window is back above half a
 * second, both pass, and the suite gives up a few seconds of the win to stop being flaky.
 *
 * Three tests still take [Pacing.Default], and each says why where it stands: they measure the
 * pacing itself rather than something the pacing merely delays.
 */
internal val TEST_PACING = Pacing(0.1)

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
    // A row's tap now opens the detail sheet rather than starting the match outright — see
    // `OpponentDetailSheet` — so the sheet's own challenge button is what actually registers it.
    onNodeWithTag(opponentRowTestTag(iconId)).performClick()
    onNodeWithTag(OPPONENT_CHALLENGE_TEST_TAG).performClick()
    settleDeck()
    awaitPlayer()
}

/**
 * Answers the deck question, if it is asked, and waits for the board.
 *
 * It comes **before** the board now rather than on top of one: the referee deals from the request,
 * so which deck to bring has to be settled before there is a match at all. Under the Random rule it
 * is not asked, which is why this waits on either.
 */
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
    awaitBoard()
}

/**
 * The shipped catalogues, loaded once for the whole suite.
 *
 * `PvpScreen` needs both since sitting down at a table asks which deck to bring, and every fixture
 * that mounts the lobby directly needs them for that reason alone — the tests here are about the
 * lobby, not about which cards exist. `by lazy` rather than a field per test class: reading and
 * parsing the bundles four times over is four times the cost for one answer.
 */
internal val pvpCards: CardCatalog by lazy { runBlocking { loadCardCatalog() } }

internal val pvpFormats: FormatCatalog by lazy { runBlocking { loadFormatCatalog() } }

internal const val ANY_LEVEL: Int = 99

internal val STARTER_CARDS: List<Int> = starterFor(FF14_BLOCK).cards
