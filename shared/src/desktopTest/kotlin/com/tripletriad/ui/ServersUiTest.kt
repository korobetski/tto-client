package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.net.AccountClient
import com.tripletriad.net.AuctionClient
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.PveClient
import com.tripletriad.net.PvpClient
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerProbe
import com.tripletriad.net.SessionStore
import com.tripletriad.net.TicketStore
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.net.serverEntries
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ClientPlatform
import com.tripletriad.protocol.ClientRelease
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.Session
import com.tripletriad.storage.InMemoryDocumentStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ServersUiTest {

    @Test
    fun theMenuNamesTheServerInPlay() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }

        awaitTitle()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("online") }
        assertVisible("Alpha", "the menu did not name the server it is on")
    }

    @Test
    fun theIndicatorOpensTheList() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }

        awaitTitle()
        onNodeWithTag(TITLE_SERVER_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SERVERS_SCREEN_TEST_TAG) }
        assertVisible("Beta", "the list did not offer the other configured server")
    }

    @Test
    fun theListShowsEachServersOwnState() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection(secondIsDown = true)) }

        openServers()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("unreachable") }
        assertVisible("online", "the healthy server stopped being shown as healthy")
    }

    @Test
    fun choosingAnotherServerMovesTheAppOntoIt() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }

        openServers()
        onNodeWithTag(serverRowTestTag(entries[1])).performClick()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()

        awaitTitle()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("Beta") }
    }

    @Test
    fun theSignInFormIsReachedOnWhicheverServerIsChosen() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }

        openServers()
        onNodeWithTag(serverRowTestTag(entries[1])).performClick()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitTitleChoice("signin")
        onNodeWithTag(titleChoiceTestTag("signin")).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }
    }

    // ---- Updates ------------------------------------------------------------

    @Test
    fun aBuildTheServerWillNotServeIsToldToUpdateInsteadOfSigningIn() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection(info = tooNewForThisBuild)) }

        awaitTitleChoice("signin")
        onNodeWithTag(titleChoiceTestTag("signin")).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(UPDATE_NOTICE_TEST_TAG) }
        check(!exists(ACCOUNT_SUBMIT_TEST_TAG)) { "the form that cannot work was left on screen" }
    }

    // `aPublishedDownloadForThisPlatformIsOfferedAsAButton` was here, asserting the button appears
    // when the deployment names a link. The button is now unconditional, so that test could no
    // longer fail — and the test below asserts the same thing on the harder fixture. Which URL the
    // button opens is not observable from here (`rememberUrlOpener` is an `expect` with no seam);
    // `ConnectivityTest` holds that, and holds that a deployment's own link beats the fallback.

    /**
     * **A deployment that published nothing still sends the player somewhere.**
     *
     * This used to assert the opposite — no link, on the grounds that the client should not invent
     * one. But the notice under test here is the *blocking* one: the build cannot sign in, and the
     * screen said so and then stopped. A dead end is not a more honest answer than the releases
     * page, it is the same answer with the useful half removed. See `RELEASES_PAGE`.
     */
    @Test
    fun aDeploymentThatPublishesNothingStillOffersTheReleasesPage() = runComposeUiTest {
        val bare = tooNewForThisBuild.copy(release = null)
        setContent { TestApp(store = english(), server = connection(info = bare)) }

        awaitTitleChoice("signin")
        onNodeWithTag(titleChoiceTestTag("signin")).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(UPDATE_DOWNLOAD_TEST_TAG) }
    }

    // ---- Fixtures ------------------------------------------------------------

    private fun ComposeUiTest.openServers() {
        awaitTitle()
        // The status dot in the corner is the whole of what the old menu card did.
        onNodeWithTag(TITLE_SERVER_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SERVERS_SCREEN_TEST_TAG) }
    }

    private fun english() = settingsFor(AppLocale.EN_US)

    private fun connection(
        info: ServerInfo = healthy,
        secondIsDown: Boolean = false,
    ): ServerConnection {
        val engine = MockEngine { request: HttpRequestData ->
            val isSecond = request.url.port == entries[1].baseUrl.substringAfterLast(':').toInt()
            when {
                isSecond && secondIsDown -> throw IOException("Connection refused")
                request.url.encodedPath == "/server" -> respondJson(encode(info))
                request.url.encodedPath == "/me" -> respondJson(encode(player))
                else -> respondJson(encode(session))
            }
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(matchProtocolJson) }
        }
        val directory = ServerDirectory(InMemoryDocumentStore(), entries)
        return ServerConnection(
            directory = directory,
            accounts = AccountClient(http, baseUrl = { directory.selected.baseUrl }),
            pvp = PvpClient(http, baseUrl = { directory.selected.baseUrl }),
            pve = PveClient(http, baseUrl = { directory.selected.baseUrl }),
            auctions = AuctionClient(http, baseUrl = { directory.selected.baseUrl }),
            session = SessionStore(InMemoryDocumentStore()),
            tickets = TicketStore(InMemoryDocumentStore()),
            probe = ServerProbe(http) { 0L },
            reporter = MatchReporter.None,
        )
    }

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private inline fun <reified T> encode(value: T) = matchProtocolJson.encodeToString(value)

    private val healthy = ServerInfo(
        name = "Alpha",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    private val tooNewForThisBuild: ServerInfo
        get() {
            val next = AppVersion(CURRENT_VERSION.major + 1, 0, 0)
            return healthy.copy(
                minimumClient = next,
                release = ClientRelease(
                    version = next,
                    downloads = ClientPlatform.entries.associateWith { "https://example.org/get" },
                ),
            )
        }

    private val entries: List<ServerEntry> =
        serverEntries("Alpha=http://127.0.0.1:8080, Beta=http://127.0.0.1:9090")

    private companion object {
        const val TOKEN = "test-session"
        const val LATER = 1_770_086_400_000L

        val player = PlayerState(save = GameSave(username = "kuplu", mgp = 4200))
        val session = Session(token = TOKEN, expiresAt = LATER, player = player)
    }
}
