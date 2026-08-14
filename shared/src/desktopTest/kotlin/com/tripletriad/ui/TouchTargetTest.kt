package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
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

        /** Material 3's minimum touch target, and Android's own accessibility guidance. */
        val MINIMUM = 48.dp

        val TABLE = """
            {"id":"$TABLE_ID","hostName":"Kuplu2","formatId":"free-play",
             "rules":{},"stake":{"mgp":50},"openedAt":0,"expiresAt":1}
        """.trimIndent()
    }
}
