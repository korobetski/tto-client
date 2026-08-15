package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tripletriad.data.Format
import com.tripletriad.data.FormatCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameSave
import com.tripletriad.net.PvpClient
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * That the densest screen still works for somebody who needs bigger text.
 *
 * ### Why 200% and why this screen
 *
 * 200% is what Android's font-size slider reaches at its largest, and it is the figure WCAG 1.4.4
 * names. `PvpTableScreen` is the worst case in this app by some distance: a format picker, twelve
 * rule chips, a checkbox with two lines of explanation, a slider, five trade options and a button,
 * all in one column. If anything is going to push a control off the bottom, it is this.
 *
 * ### What is asserted, and what deliberately is not
 *
 * That the **button still works** — `assertIsDisplayed` fails for a node scrolled out of the
 * viewport or clipped to nothing. That is the failure that matters: a player who cannot reach Open
 * cannot host a match at all, whereas a truncated label is a nuisance they can work around.
 *
 * Truncation is *not* asserted, because much of it is intended: names are `maxLines = 1` with an
 * ellipsis on purpose, and a test that forbade it would forbid a decision already taken.
 *
 * ### The assertion has teeth, which was checked rather than assumed
 *
 * A passing layout test is worth nothing if the assertion could not have failed. Raising the scale
 * to 6× does fail it — the button leaves the viewport — so 200% passing is a fact about the screen
 * and not about `assertIsDisplayed` being lenient. Worth knowing before trusting the green.
 *
 * ### A small window on purpose
 *
 * The default test surface is generous enough that a phone's problems do not appear on it. This one
 * is sized to a small handset so that "it fits" means something.
 */
@OptIn(ExperimentalTestApi::class)
class TextScalingTest {
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }
    private val json = Json { ignoreUnknownKeys = true }

    /** The busiest screen in the app, at the largest text a player can ask for. */
    @Test
    fun theTableEditorStaysUsableAtDoubleTextSize() = tableEditor(fontScale = 2f) {
        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).assertIsDisplayed()
    }

    /** And the controls above it, which is what makes the button worth reaching. */
    @Test
    fun itsControlsAreStillReachableAtDoubleTextSize() = tableEditor(fontScale = 2f) {
        onNodeWithTag(PVP_TABLE_ROULETTE_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(PVP_TABLE_MGP_TEST_TAG).assertIsDisplayed()
    }

    /**
     * The same assertions at ordinary size.
     *
     * So that a failure above is about the *scale* rather than about the screen being broken for
     * everybody — a distinction the report would not otherwise make.
     */
    @Test
    fun theSameControlsAreReachableAtOrdinarySize() = tableEditor(fontScale = 1f) {
        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(PVP_TABLE_ROULETTE_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(PVP_TABLE_MGP_TEST_TAG).assertIsDisplayed()
    }

    /**
     * The settings screen at 200%, which is where the refresh spent the most vertical space.
     *
     * `SettingsGroup` is a card holding a label, a row of locale chips and two labelled sliders,
     * and every one of those grew: the chips became real Material chips with a 32 dp body, and the
     * type scale moved the labels from 14 sp to 16. The screen scrolls, so the failure to watch for
     * is not overflow but a control that scrolling cannot reach.
     */
    @Test
    fun theSettingsScreenStaysUsableAtDoubleTextSize() = app(fontScale = 2f) {
        awaitMenu()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPTIONS_BACKGROUND_VOLUME_TEST_TAG) }

        onNodeWithTag(optionsLanguageTestTag(AppLocale.EN_US)).assertIsDisplayed()
        onNodeWithTag(OPTIONS_BACKGROUND_VOLUME_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(OPTIONS_NOISE_VOLUME_TEST_TAG).assertIsDisplayed()
    }

    /**
     * The dashboard at 200%: the hero action and the navigation bar are both still reachable.
     *
     * Those two because they are the pair scrolling cannot rescue. The grid between them scrolls,
     * so a card growing is absorbed; the bar is pinned to the bottom and Play is the first thing in
     * the list, and a layout that pushed either out of the window would leave a player who needs
     * large text unable to start a match or leave the screen.
     *
     * **It does not catch truncation, which is checked by eye.** `HomeCard` was `height(72.dp)`
     * and clipped `Quêtes journalières` to `Quêtes journalièr…` once the type scale grew; putting
     * that fixed height back leaves this test green, because a clipped label is still a displayed
     * one. Verified by doing exactly that. Asserting on truncation means reaching into
     * `TextLayoutResult` for every label on the screen, which would be a test about Compose's text
     * engine rather than about this layout — see the note at the top of this file about what is
     * deliberately not asserted.
     */
    @Test
    fun theDashboardStaysUsableAtDoubleTextSize() = app(fontScale = 2f) {
        newCharacter()

        onNodeWithTag(DASHBOARD_PLAY_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(navTestTag("home")).assertIsDisplayed()
    }

    /** The whole app on a small handset at a chosen text scale. */
    private fun app(
        fontScale: Float,
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(modifier = Modifier.size(PHONE_WIDTH, PHONE_HEIGHT)) {
                    App(store = settingsFor(AppLocale.EN_US))
                }
            }
        }
        block()
    }

    private fun tableEditor(
        fontScale: Float,
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val session = PvpSession(PvpClient(http, { "http://server" }), tokenOf = { "token" })

        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalStrings provides strings,
                // The player's text size, and nothing else: the density stays put so this is a
                // test about type scaling rather than about a differently sized screen.
                LocalDensity provides Density(base.density, fontScale),
            ) {
                TripleTriadTheme {
                    // A small handset. The default test surface is roomy enough to hide exactly
                    // the problem this file is looking for.
                    Box(modifier = Modifier.size(PHONE_WIDTH, PHONE_HEIGHT)) {
                        PvpTableScreen(
                            profile = GameSave.new(username = "Sigfrid", createdAt = 0L),
                            formats = FormatCatalog(listOf(format)),
                            session = session,
                            onOpened = {},
                            onBack = {},
                        )
                    }
                }
            }
        }
        block()
    }

    /** One format with a full pool, so every chip the editor can draw is drawn. */
    private val format = Format(
        id = "test-format",
        nameKey = "STR_TEST",
        blocks = listOf(1),
        rules = listOf(
            "RULE_SAME",
            "RULE_PLUS",
            "RULE_ALL_OPEN",
            "RULE_THREE_OPEN",
            "RULE_ROULETTE",
        ),
    )

    private companion object {
        /** Roughly a small modern handset in dp, which is the hardest case that ships. */
        val PHONE_WIDTH = 360.dp
        val PHONE_HEIGHT = 640.dp
    }
}
