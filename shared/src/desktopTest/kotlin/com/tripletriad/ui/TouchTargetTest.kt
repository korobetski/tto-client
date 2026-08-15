package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameSave
import com.tripletriad.net.PvpClient
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the things a player taps are big enough to tap.
 *
 * ### Why the custom rows and not the buttons
 *
 * Material 3 enforces a minimum touch target on its own interactive components — `TextButton`,
 * `IconButton`, `FilterChip` all reserve 48dp of touch area whether or not they *look* that tall.
 * Nothing enforces it on `Modifier.clickable`, and this app has twenty-eight of those: list rows
 * built from a surface, a padding and a line of text. A row like that measures whatever its
 * contents happen to measure, and a `bodyMedium` line inside 10dp of padding lands near 40.
 *
 * ### Measured rather than reasoned about
 *
 * The arithmetic above is a guess about line heights, which vary by font, by platform and by the
 * player's own text scale. `assertHeightIsAtLeast` is the only version of this claim that is not a
 * guess, which is why this file renders a real screen instead of inspecting a modifier chain.
 *
 * A spot check and not an audit: one representative custom row, plus the button beside it. The
 * thing it is really guarding is the *pattern* — if `rowSurface` rows are tall enough here they are
 * tall enough everywhere they are built the same way.
 */
@OptIn(ExperimentalTestApi::class)
class TouchTargetTest {
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }
    private val json = Json { ignoreUnknownKeys = true }

    /** A lobby row is a custom clickable, so nothing gives it a minimum but this. */
    @Test
    fun aLobbyRowIsBigEnoughToTap() = lobby {
        onNodeWithTag(tableRowTestTag(TABLE_ID)).assertHeightIsAtLeast(MINIMUM)
    }

    /** And the button inside it, which Material does enforce — asserted so the pair is complete. */
    @Test
    fun theJoinButtonIsBigEnoughToTap() = lobby {
        onNodeWithTag(tableJoinTestTag(TABLE_ID)).assertHeightIsAtLeast(MINIMUM)
    }

    /** The host button is the widest target on the screen and the one a new player looks for. */
    @Test
    fun theHostButtonIsBigEnoughToTap() = lobby {
        onNodeWithTag(PVP_HOST_TEST_TAG).assertHeightIsAtLeast(MINIMUM)
    }

    /**
     * The rows the shared modifier is responsible for, across four screens.
     *
     * ### Why this stopped being one spot check
     *
     * It used to test a single lobby row, on the grounds that "if `rowSurface` rows are tall enough
     * here they are tall enough everywhere they are built the same way" — which was a claim about a
     * *drawing* helper that had nothing to do with touch. `Modifier.ttoClickable` is the one that
     * does: it calls `minimumInteractiveComponentSize`, so a row's tappable area no longer depends
     * on what its contents happen to measure. That makes a sweep worth having, because now a row
     * failing means the modifier was **not called** rather than that somebody's padding was thin.
     *
     * ### It measures the **touch** bounds, and the tests above do not
     *
     * That distinction turned out to matter, and it is why this test is written differently from
     * its three neighbours. `assertHeightIsAtLeast` reads a node's *layout* bounds, and Compose
     * extends a clickable's pointer bounds outwards to the 48 dp minimum without moving a pixel of
     * what is drawn. Measured here: the collection's element filter draws **32 dp** and takes
     * **48 dp** of touch; the help screen's rule row draws 38 and takes 48; the `×` in the profile
     * list draws 34 and takes 48. An assertion on the layout would fail all three, and all three
     * are perfectly reachable — as this test discovered on its first run, before it was pointed at
     * `SemanticsNode.touchBoundsInRoot` instead.
     *
     * The three tests above are left measuring layout because that is the honest claim for them: a
     * lobby row and a button are as tall as they look.
     *
     * ### What this can and cannot catch
     *
     * It catches a control that has become genuinely unreachable — one whose pointer bounds shrink,
     * or that stops being clickable at all. It does **not** guard any particular call inside
     * `ttoClickable`: removing the `minimumInteractiveComponentSize` that used to be there changed
     * none of the numbers above, which is how that call was found to be doing nothing and removed.
     * Worth knowing before reading a green run as proof of more than it says.
     */
    @Test
    fun everyOrdinaryRowIsBigEnoughToTap() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }

        newCharacter()

        openFromBar("cards", CARD_GRID_TEST_TAG)
        assertTouchTarget(typeFilterTestTag(null))
        assertTouchTarget(cardCellTestTag(STARTER_CARDS.first()))

        onNodeWithTag(screenTabTestTag("decks")).performClick()
        waitForIdle()
        assertTouchTarget(deckSlotTestTag(0))

        openFromBar("play", OPPONENT_LIST_TEST_TAG)
        scrollToOpponent(TEST_OPPONENT)
        assertTouchTarget(opponentRowTestTag(TEST_OPPONENT))

        openFromBar("home", DASHBOARD_PLAY_TEST_TAG)
        openFromDashboard(DASHBOARD_HELP_TEST_TAG, HELP_LIST_TEST_TAG)
        assertTouchTarget(helpRuleTestTag(FIRST_HELP_RULE))
    }

    /**
     * The smallest thing in the app: the `×` beside a character in the profile list.
     *
     * One glyph in 8 dp of padding, drawing 34 dp tall. Every other row the sweep visits is a list
     * row already taller than the minimum, so this is the one whose touch bounds are doing real
     * work — and the one that would notice first if a future Compose release stopped extending
     * them.
     */
    @Test
    fun theSmallestSharedTargetIsBigEnoughToTap() = runComposeUiTest {
        val documents = seeded(GameSave.new(username = "Kuplu", createdAt = 0L))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }

        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        assertTouchTarget(profileDeleteTestTag(documents.stored.keys.single()))
    }

    /** The tappable area of [tag], which is not the same as how tall it draws. */
    private fun ComposeUiTest.assertTouchTarget(tag: String) {
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        val height = with(node.layoutInfo.density) { node.touchBoundsInRoot.height.toDp() }
        assertTrue(
            height >= MINIMUM - TOLERANCE,
            "$tag has a ${height.value.toInt()} dp touch target, under $MINIMUM",
        )
    }

    private fun lobby(block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) = runComposeUiTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/tables") -> respondJson("[$TABLE]")
                else -> respondJson("[]")
            }
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val session = PvpSession(
            client = PvpClient(http, { "http://server" }),
            tokenOf = { "token" },
            hostName = ME,
        )
        runBlocking { session.refreshTables() }

        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    PvpScreen(
                        profile = GameSave.new(username = ME, createdAt = 0L),
                        session = session,
                        now = NOW,
                        onMatch = {},
                        onHost = {},
                        onInvite = {},
                        onClaim = {},
                        onBack = {},
                    )
                }
            }
        }
        block()
    }

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private companion object {
        const val ME = "Kuplu"
        const val NOW = 1_770_000_000_000L
        const val TABLE_ID = "t-1"

        /** The first rule the help screen lists, which every format admits. */
        const val FIRST_HELP_RULE = "RULE_SAME"

        /** Material 3's minimum touch target, and Android's own accessibility guidance. */
        val MINIMUM = 48.dp

        /** The same slack `assertHeightIsAtLeast` allows, so the two agree about rounding. */
        val TOLERANCE = 0.5.dp

        val TABLE = """
            {"id":"$TABLE_ID","hostName":"Kuplu2","formatId":"free-play",
             "rules":{},"stake":{"mgp":50},"openedAt":0,"expiresAt":1}
        """.trimIndent()
    }
}
