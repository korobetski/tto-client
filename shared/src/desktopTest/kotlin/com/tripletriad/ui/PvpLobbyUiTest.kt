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
import com.tripletriad.model.GameSave
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

/**
 * The lobby: finding an opponent, and answering an invitation.
 *
 * ### The state is seeded, not polled
 *
 * `PvpScreen` runs a poll loop for as long as it is on screen. A test that waited for it would be
 * asserting on a timer; every case below instead drives the [PvpSession] to the state under test
 * **before** the screen is composed, and then asserts what is drawn. What the loop does is
 * [PvpSessionTest]'s business, and it is tested there without a screen at all.
 *
 * ### The assertion that matters
 *
 * [anInvitationYouSentOffersNoAcceptButton]. Both directions are listed in one place, and an
 * invitation this player *sent* has nothing to accept — offering the button would be offering them
 * a match against themselves, which the server would refuse with nothing on screen to explain it.
 */
@OptIn(ExperimentalTestApi::class)
class PvpLobbyUiTest {

    /** An invitation received offers both Accept and Decline. */
    @Test
    fun anInvitationReceivedCanBeAccepted() = lobby(challenges = listOf(fromKuplu())) {
        onNodeWithTag(challengeRowTestTag(INVITE_ID)).assertExists()
        onNodeWithTag(challengeAcceptTestTag(INVITE_ID)).assertExists()
        onNodeWithTag(PVP_NO_CHALLENGE_TEST_TAG).assertDoesNotExist()
    }

    /**
     * An invitation this player sent has no Accept.
     *
     * See the class KDoc: accepting your own invitation is a match against yourself.
     */
    @Test
    fun anInvitationYouSentOffersNoAcceptButton() = lobby(challenges = listOf(toKuplu())) {
        onNodeWithTag(challengeRowTestTag(INVITE_ID)).assertExists()
        onNodeWithTag(challengeAcceptTestTag(INVITE_ID)).assertDoesNotExist()
    }

    /** Accepting posts to the server, naming that invitation. */
    @Test
    fun acceptingPostsToTheServer() {
        val paths = mutableListOf<String>()

        lobby(challenges = listOf(fromKuplu()), record = paths::add) {
            onNodeWithTag(challengeAcceptTestTag(INVITE_ID)).performClick()
            waitForIdle()
        }

        assertTrue(
            paths.any { it.endsWith("/pvp/challenges/$INVITE_ID/accept") },
            "accepting sent $paths",
        )
    }

    /** The Invite button is dead until a name is typed — an empty challenge names nobody. */
    @Test
    fun invitingNeedsAName() = lobby(onInvites = true) {
        onNodeWithTag(PVP_CHALLENGE_TEST_TAG).assertIsNotEnabled()

        onNodeWithTag(PVP_NAME_TEST_TAG).performTextInput("Kuplu")

        onNodeWithTag(PVP_CHALLENGE_TEST_TAG).assertExists()
    }

    /**
     * A typed name is trimmed before it is sent.
     *
     * The server trims too. This is here so a trailing space costs nothing rather than a round trip
     * and a "no such player" — the same argument `Credentials.looksValid` makes.
     */
    @Test
    fun aTypedNameIsTrimmedBeforeItIsSent() {
        lobby(onInvites = true) {
            onNodeWithTag(PVP_NAME_TEST_TAG).performTextInput("  Kuplu  ")
            onNodeWithTag(PVP_CHALLENGE_TEST_TAG).performClick()
            waitForIdle()
        }

        assertEquals(listOf("Kuplu"), invited)
    }

    /**
     * Renders the lobby with [challenges] listed and the queue in state [queued].
     *
     * The session is driven into that state before composition — see the class KDoc — by answering
     * the two requests it makes and then letting the screen render what it holds.
     */
    /** Names handed on to the terms screen, in order. */
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
                        profile = GameSave.new(username = ME, createdAt = 0L),
                        session = session,
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

    /** An invitation somebody sent to this player. */
    private fun fromKuplu() = challengeJson(from = "Kuplu", to = ME)

    /** One this player sent. `fromName` is theirs, which is how the row tells the two apart. */
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

        /** Fixed, so a countdown reads the same on every run. */
        const val NOW = 0L
        const val TABLE_ID = "t-1"
        const val INVITE_ID = "inv-1"
    }
}
