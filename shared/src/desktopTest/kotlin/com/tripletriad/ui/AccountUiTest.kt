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
import com.tripletriad.net.AuctionClient
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
    fun withNoServerTheTitleScreenOffersToMakeALocalCharacter() = runComposeUiTest {
        setContent { TestApp(store = english()) }

        awaitTitleChoice("new")
        onNodeWithTag(titleChoiceTestTag("new")).performClick()

        // Straight to creation rather than to a list with nothing in it.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_CREATE_TEST_TAG) }
    }

    @Test
    fun withAServerPlayLeadsToTheSignInFormAndItSaysWhichBuildThisIs() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }

        openForm()

        onNodeWithTag(ACCOUNT_VERSION_TEST_TAG).assertTextEquals("v$CLIENT_VERSION")
    }

    @Test
    fun signingInLandsOnTheAccountsDashboard() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }

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

        setContent { TestApp(store = english(), server = connection(sessions = documents)) }

        // Straight past `Screen.ACCOUNT`: the profile was restored before the splash ended,
        // so the title screen is already offering to continue rather than to sign in.
        openDashboard()
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

        setContent { TestApp(store = english(), server = connection(sessions = documents)) }

        // The form, and not the dashboard: the token is dead, so this is a sign-in and not a
        // restore. Both halves matter — a passing assertion below with a *live* token would only
        // prove the screen was never reached.
        //
        // And it is reached by tapping the screen rather than by picking Sign in: a device
        // that has been signed in on before is not asked which errand it is on, it is told
        // its session lapsed. See `titleEntry`.
        awaitTitle()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("Session expired") }
        onNodeWithTag(TITLE_CONTINUE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertTextContains("kuplu")
    }

    @Test
    fun aFirstRunLeavesTheFormBlank() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }

        openForm()
        // The label, then the empty value — `assertTextEquals` reads both, and the label is stable
        // because these tests fix the locale.
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertTextEquals("Character Name", "")
    }

    @Test
    fun theFieldsTellThePasswordManagerWhatTheyAre() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }

        openForm()
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertContentType(AutofillType.Username)
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).assertContentType(AutofillType.Password)
    }

    @Test
    fun creatingAnAccountAsksForANewPasswordInstead() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }

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
        setContent { TestApp(store = english(), server = connection(engine = refusing)) }

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
            TestApp(store = settingsFor(AppLocale.FR_FR), server = connection(engine = refusing))
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
        setContent { TestApp(store = english(), server = connection(engine = engine)) }

        openForm()
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).performTextInput("ku")
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).performTextInput("short")
        onNodeWithTag(ACCOUNT_SUBMIT_TEST_TAG).performClick()
        waitForIdle()

        check(exists(ACCOUNT_SCREEN_TEST_TAG)) { "the form navigated away" }
        check(!asked) { "the form sent credentials it had already judged invalid" }
    }

    /**
     * The choice reaches the server, as an **id**, on the endpoint that grants cards.
     *
     * It used to reach it as a profile on `PUT /me/save` — and did not arrive: that endpoint takes
     * `cards` from the stored document (`GameSave.withServerOwnedFrom`), so the box was discarded
     * and the character kept whatever registration had made it. This asserts the request that
     * replaced it, and asserts that no attempt is made to push the cards, because a client that
     * pushed them would look like it worked and would not.
     */
    @Test
    fun registeringAsksForAStarterAndSendsItsId() = runComposeUiTest {
        val claimed = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/me/starter" -> {
                    claimed += request.body.toByteArray().decodeToString()
                    // A profile that differs from the one registration answered with, because that
                    // is what "the box landed" looks like to the client — `AccountSession.perform`
                    // reads an unchanged profile as a refusal, which is the server's way of saying
                    // nothing was owed.
                    respondJson(HttpStatusCode.OK, encode(dealt))
                }

                "/me/save" -> {
                    saved += request.body.toByteArray().decodeToString()
                    respondJson(HttpStatusCode.Created, encode(session))
                }

                else -> respondJson(HttpStatusCode.Created, encode(session))
            }
        }
        setContent { TestApp(store = english(), server = connection(engine = engine)) }

        register()
        onNodeWithTag(starterChoiceTestTag(starterFor(FF8_BLOCK).id)).performClick()
        onNodeWithTag(STARTER_CONFIRM_TEST_TAG).performClick()
        awaitDashboard()

        val ff8 = starterFor(FF8_BLOCK)
        val body = claimed.lastOrNull { it.contains("\"starterId\":\"${ff8.id}\"") }
        check(body != null) { "the chosen box never reached the server: $claimed" }
        for (id in ff8.deck + starterFor(FF14_BLOCK).deck) {
            check(claimed.none { it.contains("\"$id\"") }) {
                "the client named a card id: $claimed"
            }
            check(saved.none { it.contains("\"$id\":") }) {
                "the client pushed cards the server would have discarded: $saved"
            }
        }
    }

    /**
     * A refused claim keeps the player on the choice screen, and says so.
     *
     * The grant is a round trip now, so it can fail where a local write could not. Walking on to a
     * dashboard would leave a character owning nothing and no way back to the box they picked — the
     * shop's repair offers the catalogue's first rather than a choice — so the screen stays put.
     */
    @Test
    fun aStarterTheServerRefusesLeavesThePlayerOnTheChoice() = runComposeUiTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/me/starter") {
                respondJson(HttpStatusCode.ServiceUnavailable, "{}")
            } else {
                respondJson(HttpStatusCode.Created, encode(session))
            }
        }
        setContent { TestApp(store = english(), server = connection(engine = engine)) }

        register()
        onNodeWithTag(starterChoiceTestTag(starterFor(FF8_BLOCK).id)).performClick()
        onNodeWithTag(STARTER_CONFIRM_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(STARTER_NOTE_TEST_TAG) }
        onNodeWithTag(STARTER_CONFIRM_TEST_TAG).assertExists()
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun SemanticsNodeInteraction.assertContentType(expected: AutofillType) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, expected))

    private fun ComposeUiTest.openForm() {
        awaitTitleChoice("signin")
        onNodeWithTag(titleChoiceTestTag("signin")).performClick()
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
            auctions = AuctionClient(http, baseUrl = { directory.selected.baseUrl }),
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

        /** The profile a granted box comes back as: the same character, now holding one. */
        val dealt = player.copy(
            save = player.save.copy(cards = starterFor(FF8_BLOCK).deck.associateWith { 1 }),
        )
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.register() {
    // No toggle: the title screen's own button says which half of the form it wants, and
    // the form opens on it. See `Choice.registering`.
    awaitTitleChoice("register")
    onNodeWithTag(titleChoiceTestTag("register")).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }
    onNodeWithTag(ACCOUNT_NAME_TEST_TAG).performTextInput("kuplu")
    onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).performTextInput("not-a-real-password")
    // Required now, and the submit button stays off without it — see `AccountScreen`, which asks
    // `Credentials.looksValidToRegister` rather than `looksValid` on this half of the form.
    onNodeWithTag(ACCOUNT_EMAIL_TEST_TAG).performTextInput("kuplu@example.test")
    onNodeWithTag(ACCOUNT_SUBMIT_TEST_TAG).performClick()

    // And registration now lands on the confirmation screen rather than on the collection step.
    // Dismissed here rather than answered: this helper's callers are about what happens *after*
    // signing up, and confirming an address is `ConfirmEmailUiTest`'s subject.
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CONFIRM_SCREEN_TEST_TAG) }
    onNodeWithTag(CONFIRM_LATER_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(STARTER_CONFIRM_TEST_TAG) }
}
