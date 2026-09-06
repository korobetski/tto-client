package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
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
 * Whether there is anybody to play, said before the door that opens a table.
 *
 * The question this screen could not answer until now: an empty lobby looks identical whether the
 * server has nobody on it or simply nobody hosting, and the player's only way to tell them apart
 * was to open a table and wait at it. So the count is asserted in words rather than as a number —
 * "nobody else is online" and "0 other players online" are the same integer and not the same
 * sentence, and it is the sentence that decides whether the player waits.
 *
 * Read through the whole screen and not through `presenceLine` alone, because the line being
 * *computed* was never the thing at risk; the line being drawn where the decision is taken was.
 */
@OptIn(ExperimentalTestApi::class)
class PvpPresenceUiTest {

    @Test
    fun anEmptyRoomSaysSoBeforeOfferingToOpenATable() = lobby(others = 0) {
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertExists()
        onNodeWithTag(PVP_PRESENCE_TEST_TAG)
            .assertTextEquals("Nobody else is online right now.")
    }

    @Test
    fun oneOtherPlayerIsNotWrittenAsAPlural() = lobby(others = 1) {
        onNodeWithTag(PVP_PRESENCE_TEST_TAG).assertTextEquals("1 other player online")
    }

    @Test
    fun aBusyRoomSaysHowManyOthersAreAbout() = lobby(others = OTHERS) {
        onNodeWithTag(PVP_PRESENCE_TEST_TAG).assertTextEquals("$OTHERS other players online")
    }

    /**
     * A room whose census has not come back says nothing, rather than saying it is empty.
     *
     * The presence route answers 500 here, which is what an old server without it does too — this
     * screen must survive one, and the line it must not draw in that case is the one that would
     * tell the player there is nobody to play.
     */
    @Test
    fun aCensusThatFailedIsNotDrawnAsAnEmptyRoom() = lobby(others = 0, answers = false) {
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertExists()
        onNodeWithTag(PVP_PRESENCE_TEST_TAG).assertDoesNotExist()
    }

    /** The same line again on the lobby that has something in it, above the same door. */
    @Test
    fun thePopulatedLobbySaysItToo() = lobby(others = OTHERS, tables = listOf(tableJson())) {
        onNodeWithTag(PVP_LOBBY_TEST_TAG).assertExists()
        onNodeWithTag(PVP_PRESENCE_TEST_TAG).assertTextEquals("$OTHERS other players online")
    }

    private fun lobby(
        others: Int,
        answers: Boolean = true,
        tables: List<String> = emptyList(),
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/presence") ->
                    if (answers) {
                        respondJson("""{"others":$others,"tables":0}""")
                    } else {
                        respond(
                            content = "",
                            status = HttpStatusCode.InternalServerError,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                path.endsWith("/tables") -> respondJson("[" + tables.joinToString(",") + "]")
                path.endsWith("/claims") -> respondJson("[]")
                path.endsWith("/challenges") -> respondJson("[]")
                else -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val session = PvpSession(
            client = PvpClient(http, { "http://server" }),
            tokenOf = { "token" },
            hostName = ME,
        )
        runBlocking {
            session.refreshTables()
            session.refreshClaims()
            session.refreshPresence()
        }

        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    PvpScreen(
                        profile = freshSave().copy(username = ME),
                        session = session,
                        catalog = pvpCards,
                        formats = pvpFormats,
                        now = 0L,
                        onMatch = {},
                        onHost = {},
                        onInvite = {},
                        onClaim = {},
                        onTab = {},
                        onBack = {},
                    )
                }
            }
        }
        block()
    }

    private fun tableJson() = """
        {"id":"t-1","hostName":"Kuplu","formatId":"free-play",
         "rules":{},"roulette":true,
         "stake":{"mgp":10,"trade":"ONE"},"openedAt":0,"expiresAt":1}
    """.trimIndent()

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private companion object {
        const val ME = "Sigfrid"

        /** More than one, so the plural line is not the singular one by accident. */
        const val OTHERS = 3
    }
}
