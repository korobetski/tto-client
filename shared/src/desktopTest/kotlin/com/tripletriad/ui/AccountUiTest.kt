package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.CLIENT_VERSION
import com.tripletriad.FF14_BLOCK
import com.tripletriad.FF8_BLOCK
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.net.AccountClient
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.PveClient
import com.tripletriad.net.PvpClient
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerProbe
import com.tripletriad.net.SessionStore
import com.tripletriad.net.StoredSession
import com.tripletriad.net.TicketStore
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.Session
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import androidx.compose.ui.autofill.ContentType as AutofillType

@OptIn(ExperimentalTestApi::class)
class AccountUiTest {

    @Test
    fun withNoServerPlayStillLeadsToTheLocalCharacterList() = runComposeUiTest {
        setContent { App(store = english()) }

        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NEW_TEST_TAG) }
    }

    @Test
    fun withAServerPlayLeadsToTheSignInFormAndItSaysWhichBuildThisIs() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openForm()

        onNodeWithTag(ACCOUNT_VERSION_TEST_TAG).assertTextEquals("v$CLIENT_VERSION")
    }

    @Test
    fun signingInLandsOnTheAccountsDashboard() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openForm()
        submitCredentials()

        awaitDashboard()
        assertVisible("kuplu", "the dashboard did not show the account's character")
        // And straight there: an account that already exists has a collection, so the step that
        // follows a registration must not appear again on every sign-in.
        check(!exists(STARTER_CONFIRM_TEST_TAG)) {
            "an existing account was asked to choose its collection again"
        }
    }

    @Test
    fun aStoredSessionSkipsTheFormOnTheNextLaunch() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        runBlocking {
            SessionStore(documents).save(
                home.id,
                StoredSession(token = TOKEN, expiresAt = LATER, username = "kuplu"),
            )
        }

        setContent { App(store = english(), server = connection(sessions = documents)) }

        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()

        // Straight past `Screen.ACCOUNT`: the profile was restored before the splash ended, so
        // Play is Continue.
        awaitDashboard()
    }

    @Test
    fun anExpiredSessionLeavesTheNameInTheForm() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        runBlocking {
            SessionStore(documents).save(
                home.id,
                StoredSession(token = TOKEN, expiresAt = EXPIRED, username = "kuplu"),
            )
        }

        setContent { App(store = english(), server = connection(sessions = documents)) }

        // The form, and not the dashboard: the token is dead, so this is a sign-in and not a
        // restore. Both halves matter — a passing assertion below with a *live* token would only
        // prove the screen was never reached.
        openForm()
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertTextContains("kuplu")
    }

    @Test
    fun aFirstRunLeavesTheFormBlank() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openForm()
        // The label, then the empty value — `assertTextEquals` reads both, and the label is stable
        // because these tests fix the locale.
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertTextEquals("Character Name", "")
    }

    @Test
    fun theFieldsTellThePasswordManagerWhatTheyAre() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openForm()
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertContentType(AutofillType.Username)
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).assertContentType(AutofillType.Password)
    }

    @Test
    fun creatingAnAccountAsksForANewPasswordInstead() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openForm()
        onNodeWithTag(ACCOUNT_TOGGLE_TEST_TAG).performClick()

        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertContentType(AutofillType.NewUsername)
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).assertContentType(AutofillType.NewPassword)
    }

    @Test
    fun aRefusedSignInStaysOnTheFormAndSaysWhy() = runComposeUiTest {
        val refusing = MockEngine {
            respondJson(
                HttpStatusCode.Unauthorized,
                """{"error":"INVALID_CREDENTIALS","detail":"no"}""",
            )
        }
        setContent { App(store = english(), server = connection(engine = refusing)) }

        openForm()
        submitCredentials()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_ERROR_TEST_TAG) }
        check(exists(ACCOUNT_SCREEN_TEST_TAG)) { "a refused sign-in left the form" }
    }

    @Test
    fun aRefusalIsWordedInThePlayersLanguage() = runComposeUiTest {
        val refusing = MockEngine {
            respondJson(
                HttpStatusCode.Unauthorized,
                """{"error":"INVALID_CREDENTIALS","detail":"no"}""",
            )
        }
        setContent {
            App(store = settingsFor(AppLocale.FR_FR), server = connection(engine = refusing))
        }

        openForm()
        submitCredentials()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_ERROR_TEST_TAG) }
        assertVisible(
            "Ce nom et ce mot de passe ne correspondent à aucun compte.",
            "the refusal should be in French",
        )
    }

    @Test
    fun theFormWillNotSubmitCredentialsTheServerWouldRefuse() = runComposeUiTest {
        var asked = false
        val engine = MockEngine { request ->
            // The probe is not the form. `GET /server` is made by the menu's indicator on every
            // launch and asks nothing about the player, so counting it here would make this test
            // fail for a request that carries no credentials at all.
            if (request.url.encodedPath != "/server") asked = true
            respondJson(HttpStatusCode.OK, encode(session))
        }
        setContent { App(store = english(), server = connection(engine = engine)) }

        openForm()
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).performTextInput("ku")
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).performTextInput("short")
        onNodeWithTag(ACCOUNT_SUBMIT_TEST_TAG).performClick()
        waitForIdle()

        check(exists(ACCOUNT_SCREEN_TEST_TAG)) { "the form navigated away" }
        check(!asked) { "the form sent credentials it had already judged invalid" }
    }

    @Test
    fun registeringAsksForAStarterAndSendsIt() = runComposeUiTest {
        val saved = mutableListOf<String>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/me/save") {
                saved += request.body.toByteArray().decodeToString()
            }
            respondJson(HttpStatusCode.Created, encode(session))
        }
        setContent { App(store = english(), server = connection(engine = engine)) }

        register()
        onNodeWithTag(starterChoiceTestTag(starterFor(FF8_BLOCK).id)).performClick()
        onNodeWithTag(STARTER_CONFIRM_TEST_TAG).performClick()
        awaitDashboard()

        val ff8 = starterFor(FF8_BLOCK).cards
        val body = saved.lastOrNull { body -> ff8.all { body.contains("\"$it\"") } }
        check(body != null) { "the chosen box never reached the server: $saved" }
        // And only that box: opening one is a replacement, not a top-up. See `StarterPack.opened`.
        for (id in starterFor(FF14_BLOCK).cards) {
            check(!body.contains("\"$id\"")) { "an FFXIV card survived the choice: $body" }
        }
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun SemanticsNodeInteraction.assertContentType(expected: AutofillType) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, expected))

    private fun ComposeUiTest.openForm() {
        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }
    }

    private fun ComposeUiTest.submitCredentials() {
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).performTextInput("kuplu")
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).performTextInput(PASSWORD)
        onNodeWithTag(ACCOUNT_SUBMIT_TEST_TAG).performClick()
    }

    private fun connection(
        sessions: InMemoryDocumentStore = InMemoryDocumentStore(),
        engine: MockEngine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/server" -> encode(serverInfo)
                "/me" -> encode(player)
                else -> encode(session)
            }
            respondJson(HttpStatusCode.OK, body)
        },
    ): ServerConnection {
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(matchProtocolJson) }
        }
        val directory = ServerDirectory(InMemoryDocumentStore(), listOf(home))
        return ServerConnection(
            directory = directory,
            accounts = AccountClient(http, baseUrl = { directory.selected.baseUrl }),
            pvp = PvpClient(http, baseUrl = { directory.selected.baseUrl }),
            pve = PveClient(http, baseUrl = { directory.selected.baseUrl }),
            session = SessionStore(sessions),
            tickets = TicketStore(InMemoryDocumentStore()),
            probe = ServerProbe(http) { 0L },
            reporter = MatchReporter.None,
        )
    }

    private fun english() = settingsFor(AppLocale.EN_US)

    private fun MockRequestHandleScope.respondJson(
        status: HttpStatusCode,
        body: String,
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private inline fun <reified T> encode(value: T) = matchProtocolJson.encodeToString(value)

    private val serverInfo = ServerInfo(
        name = "Test server",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    private val home = ServerEntry.of("http://127.0.0.1:8080", label = "Test server")

    private companion object {
        const val TOKEN = "test-session"
        const val NOW = 1_770_000_000_000L
        const val LATER = NOW + 86_400_000L

        const val EXPIRED = FixedClock.DEFAULT_MILLIS - 1

        const val PASSWORD = "not-a-real-password"

        val player = PlayerState(save = GameSave(username = "kuplu", mgp = 4200))
        val session = Session(token = TOKEN, expiresAt = LATER, player = player)
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.register() {
    awaitMenu()
    onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }
    onNodeWithTag(ACCOUNT_TOGGLE_TEST_TAG).performClick()
    onNodeWithTag(ACCOUNT_NAME_TEST_TAG).performTextInput("kuplu")
    onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).performTextInput("not-a-real-password")
    onNodeWithTag(ACCOUNT_SUBMIT_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(STARTER_CONFIRM_TEST_TAG) }
}
