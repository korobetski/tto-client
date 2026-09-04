package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.net.PvpClient
import com.tripletriad.protocol.PvpTableRequest
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PvpLobbyUiTest {

    @Test
    fun anInvitationReceivedCanBeAccepted() = lobby(challenges = listOf(fromKuplu())) {
        onNodeWithTag(challengeRowTestTag(INVITE_ID)).assertExists()
        onNodeWithTag(challengeAcceptTestTag(INVITE_ID)).assertExists()
        onNodeWithTag(PVP_NO_CHALLENGE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun anInvitationYouSentOffersNoAcceptButton() = lobby(challenges = listOf(toKuplu())) {
        onNodeWithTag(challengeRowTestTag(INVITE_ID)).assertExists()
        onNodeWithTag(challengeAcceptTestTag(INVITE_ID)).assertDoesNotExist()
    }

    @Test
    fun acceptingPostsToTheServer() {
        val paths = mutableListOf<String>()

        lobby(challenges = listOf(fromKuplu()), record = paths::add) {
            onNodeWithTag(challengeAcceptTestTag(INVITE_ID)).performClick()
            waitForIdle()
            // Accepting names a seat; the deck screen is what sends it. See [PvpSeat].
            onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).performClick()
            waitForIdle()
        }

        assertTrue(
            paths.any { it.endsWith("/pvp/challenges/$INVITE_ID/accept") },
            "accepting sent $paths",
        )
    }

    @Test
    fun invitingNeedsAName() = lobby(onInvites = true) {
        onNodeWithTag(PVP_CHALLENGE_TEST_TAG).assertIsNotEnabled()

        onNodeWithTag(PVP_NAME_TEST_TAG).performTextInput("Kuplu")

        onNodeWithTag(PVP_CHALLENGE_TEST_TAG).assertExists()
    }

    @Test
    fun aTypedNameIsTrimmedBeforeItIsSent() {
        lobby(onInvites = true) {
            onNodeWithTag(PVP_NAME_TEST_TAG).performTextInput("  Kuplu  ")
            onNodeWithTag(PVP_CHALLENGE_TEST_TAG).performClick()
            waitForIdle()
        }

        assertEquals(listOf("Kuplu"), invited)
    }

    private val invited = mutableListOf<String>()

    @Suppress("LongParameterList")
    private fun lobby(
        tables: List<String> = emptyList(),
        challenges: List<String> = emptyList(),
        // The lobby opens on the tables. Anything about invitations has to say so, because the
        // other tab is not composed at all until it is selected.
        onInvites: Boolean = challenges.isNotEmpty(),
        claims: List<String> = emptyList(),
        record: (String) -> Unit = {},
        recordBody: (String) -> Unit = {},
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine { request ->
            record(request.url.encodedPath)
            recordBody(bodyOf(request))
            when {
                request.url.encodedPath.endsWith("/challenges") ->
                    respondJson("[${challenges.joinToString(",")}]")

                request.url.encodedPath.endsWith("/tables") ->
                    respondJson("[${tables.joinToString(",")}]")

                request.url.encodedPath.endsWith("/claims") ->
                    respondJson("[${claims.joinToString(",")}]")

                request.url.encodedPath.endsWith("/join") ->
                    respondJson("""{"waiting":false}""")

                // No match, so the screen stays on the lobby rather than navigating away.
                else -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val session = sessionOver(engine)
        runBlocking {
            session.refreshChallenges()
            session.refreshTables()
            session.refreshClaims()
        }

        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    PvpScreen(
                        profile = freshSave().copy(username = ME),
                        session = session,
                        catalog = pvpCards,
                        formats = pvpFormats,
                        now = NOW,
                        onMatch = {},
                        onHost = {},
                        onInvite = { invited += it },
                        onClaim = {},
                        onBack = {},
                    )
                }
            }
        }
        if (onInvites) {
            onNodeWithTag(screenTabTestTag("invites")).performClick()
            waitForIdle()
        }
        block()
    }

    private fun sessionOver(engine: MockEngine): PvpSession {
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        return PvpSession(
            client = PvpClient(http, { "http://server" }),
            tokenOf = { "token" },
            hostName = ME,
        )
    }

    private fun fromKuplu() = challengeJson(from = "Kuplu", to = ME)

    private fun toKuplu() = challengeJson(from = ME, to = "Kuplu")

    private fun challengeJson(from: String, to: String) = json.encodeToString(
        com.tripletriad.protocol.PvpChallenge.serializer(),
        com.tripletriad.protocol.PvpChallenge(
            id = INVITE_ID,
            fromName = from,
            toName = to,
            expiresAt = Long.MAX_VALUE,
            terms = PvpTableRequest(formatId = "free-play"),
        ),
    )

    private fun bodyOf(request: HttpRequestData): String =
        (request.body as? io.ktor.http.content.OutgoingContent.ByteArrayContent)
            ?.bytes()
            ?.decodeToString()
            .orEmpty()

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private companion object {
        const val ME = "Tester"

        const val NOW = 0L
        const val TABLE_ID = "t-1"
        const val INVITE_ID = "inv-1"
    }
}
