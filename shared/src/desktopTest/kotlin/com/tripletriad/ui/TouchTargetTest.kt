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

@OptIn(ExperimentalTestApi::class)
class TouchTargetTest {
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aLobbyRowIsBigEnoughToTap() = lobby {
        onNodeWithTag(tableRowTestTag(TABLE_ID)).assertHeightIsAtLeast(MINIMUM)
    }

    @Test
    fun theJoinButtonIsBigEnoughToTap() = lobby {
        onNodeWithTag(tableJoinTestTag(TABLE_ID)).assertHeightIsAtLeast(MINIMUM)
    }

    @Test
    fun theHostButtonIsBigEnoughToTap() = lobby {
        onNodeWithTag(PVP_HOST_TEST_TAG).assertHeightIsAtLeast(MINIMUM)
    }

    @Test
    fun everyOrdinaryRowIsBigEnoughToTap() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }

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

    @Test
    fun theSmallestSharedTargetIsBigEnoughToTap() = runComposeUiTest {
        val documents = seeded(GameSave.new(username = "Kuplu", createdAt = 0L))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }

        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        assertTouchTarget(profileDeleteTestTag(documents.stored.keys.single()))
    }

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

        const val FIRST_HELP_RULE = "RULE_SAME"

        val MINIMUM = 48.dp

        val TOLERANCE = 0.5.dp

        val TABLE = """
            {"id":"$TABLE_ID","hostName":"Kuplu2","formatId":"free-play",
             "rules":{},"stake":{"mgp":50},"openedAt":0,"expiresAt":1}
        """.trimIndent()
    }
}
