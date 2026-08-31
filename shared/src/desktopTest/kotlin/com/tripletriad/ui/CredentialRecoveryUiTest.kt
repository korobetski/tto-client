package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.tripletriad.protocol.AccountError
import com.tripletriad.protocol.AccountFailure
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.Session
import com.tripletriad.storage.InMemoryDocumentStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The two screens a player reaches when the account itself is the problem.
 *
 * Driven through the whole app rather than by rendering a screen in isolation, like every other
 * `*UiTest` here: what is worth checking is not that a form draws but that registering *arrives* at
 * confirmation, that *later* is a real way out, and that a reset lands back on a sign-in form —
 * three claims about navigation that a screen-level test cannot make.
 *
 * The server is a `MockEngine` answering by path. Nothing here proves a mail is sent; that is the
 * server's own `CredentialRecoveryTest`, which reads the code out of a recording mailer.
 */
@OptIn(ExperimentalTestApi::class)
class CredentialRecoveryUiTest {

    @Test
    fun registeringLandsOnTheConfirmationScreenAndNamesTheAddress() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }

        signUp()

        assertTrue(
            isVisible("kuplu@example.test"),
            "the confirmation screen did not say where the code went",
        )
    }

    /**
     * The submit button will not send six digits until there are six of them.
     *
     * Cheap on this side and worth having: a five-digit attempt spends one of the five the server
     * allows, and a player who is guessing at a code they mistyped has fewer left than they think.
     */
    @Test
    fun aPartialCodeCannotBeSubmitted() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }
        signUp()

        onNodeWithTag(CONFIRM_CODE_TEST_TAG).performTextInput("123")

        onNodeWithTag(CONFIRM_SUBMIT_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun theCodeIsSentToTheServerAndConfirmingContinuesToTheCollectionStep() = runComposeUiTest {
        val seen = mutableListOf<String>()
        setContent { TestApp(store = english(), server = connection(record = { seen += it })) }
        signUp()

        onNodeWithTag(CONFIRM_CODE_TEST_TAG).performTextInput("123456")
        onNodeWithTag(CONFIRM_SUBMIT_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(STARTER_CONFIRM_TEST_TAG) }
        assertTrue("/me/email/verify" in seen, "the code never reached the server: $seen")
    }

    /** And *later* reaches the same place, because confirming is not required to play. */
    @Test
    fun laterIsARealWayOut() = runComposeUiTest {
        setContent { TestApp(store = english(), server = connection()) }
        signUp()

        onNodeWithTag(CONFIRM_LATER_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(STARTER_CONFIRM_TEST_TAG) }
    }

    /**
     * A refused code says the server's own reason and leaves the player on the screen.
     *
     * Navigating away on a failure is the bug this guards: `onConfirmed` runs only when the call
     * answered `Ok`, and a screen that advanced on any answer would report an account confirmed
     * that is not.
     */
    @Test
    fun aRefusedCodeKeepsTheScreenAndSaysWhy() = runComposeUiTest {
        setContent {
            TestApp(
                store = english(),
                server = connection(
                    refusing = "/me/email/verify" to AccountError.INVALID_CODE,
                ),
            )
        }
        signUp()

        onNodeWithTag(CONFIRM_CODE_TEST_TAG).performTextInput("000000")
        onNodeWithTag(CONFIRM_SUBMIT_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("That code is not valid.") }
        assertTrue(exists(CONFIRM_SCREEN_TEST_TAG), "a refusal navigated away")
    }

    @Test
    fun resendingAsksTheServerForAnotherOne() = runComposeUiTest {
        val seen = mutableListOf<String>()
        setContent { TestApp(store = english(), server = connection(record = { seen += it })) }
        signUp()

        onNodeWithTag(CONFIRM_RESEND_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { "/me/email/resend" in seen }
    }

    // ---- Forgotten passwords -----------------------------------------------

    @Test
    fun theSignInFormOffersAWayBackInAndTheCodeFieldsFollowTheRequest() = runComposeUiTest {
        val seen = mutableListOf<String>()
        setContent { TestApp(store = english(), server = connection(record = { seen += it })) }

        openSignIn()
        onNodeWithTag(ACCOUNT_FORGOT_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(RESET_SCREEN_TEST_TAG) }

        // Nothing to type a code into until one has been asked for — see the screen's own note on
        // why showing the fields early makes a refusal read as the wrong thing.
        assertTrue(!exists(RESET_CODE_TEST_TAG), "the code field was showing before the request")

        onNodeWithTag(RESET_NAME_TEST_TAG).performTextInput("kuplu")
        onNodeWithTag(RESET_SEND_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(RESET_CODE_TEST_TAG) }
        assertTrue("/accounts/password/forgot" in seen, "nothing was asked of the server: $seen")
    }

    /**
     * Finishing lands back on the account form.
     *
     * Not on the lobby, and that is the whole point of the reset: it ends every session on the
     * account, so there is nothing to walk into. The new password has to be used once.
     */
    @Test
    fun aFinishedResetReturnsToTheFormToSignInWithTheNewPassword() = runComposeUiTest {
        val seen = mutableListOf<String>()
        setContent { TestApp(store = english(), server = connection(record = { seen += it })) }

        openSignIn()
        onNodeWithTag(ACCOUNT_FORGOT_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(RESET_SCREEN_TEST_TAG) }
        onNodeWithTag(RESET_NAME_TEST_TAG).performTextInput("kuplu")
        onNodeWithTag(RESET_SEND_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(RESET_CODE_TEST_TAG) }

        onNodeWithTag(RESET_CODE_TEST_TAG).performTextInput("123456")
        onNodeWithTag(RESET_PASSWORD_TEST_TAG).performTextInput("a-different-horse-entirely")
        onNodeWithTag(RESET_SUBMIT_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }
        assertTrue("/accounts/password/reset" in seen, "the reset never reached the server: $seen")
    }

    // ---- Harness -----------------------------------------------------------

    private fun ComposeUiTest.openSignIn() {
        awaitTitleChoice("signin")
        onNodeWithTag(titleChoiceTestTag("signin")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }
    }

    /** Fills the registration form and stops on the confirmation screen it now leads to. */
    private fun ComposeUiTest.signUp() {
        awaitTitleChoice("register")
        onNodeWithTag(titleChoiceTestTag("register")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).performTextInput("kuplu")
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).performTextInput("not-a-real-password")
        onNodeWithTag(ACCOUNT_EMAIL_TEST_TAG).performTextInput("kuplu@example.test")
        onNodeWithTag(ACCOUNT_SUBMIT_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CONFIRM_SCREEN_TEST_TAG) }
    }

    /**
     * A server that answers every path this flow touches, and records which were asked for.
     *
     * [refusing] names one path that answers a refusal instead — enough for the one test that is
     * about a bad code, and no more machinery than that test needs.
     */
    private fun connection(
        record: (String) -> Unit = {},
        refusing: Pair<String, AccountError>? = null,
    ): ServerConnection {
        val engine = MockEngine { request: HttpRequestData ->
            val path = request.url.encodedPath
            record(path)

            when {
                path == refusing?.first -> respondJson(
                    HttpStatusCode.BadRequest,
                    matchProtocolJson.encodeToString(
                        AccountFailure(refusing.second, "refused by the test server"),
                    ),
                )

                path == "/server" -> respondJson(
                    HttpStatusCode.OK,
                    matchProtocolJson.encodeToString(serverInfo),
                )

                // Every code endpoint: 204 for the two that confirm or reset, 202 for the two that
                // send. Answered together because the client distinguishes them and this engine
                // must not be the thing that makes a wrong status pass.
                path == "/me/email/verify" || path == "/accounts/password/reset" ->
                    respondJson(HttpStatusCode.NoContent, "")

                path == "/me/email/resend" || path == "/accounts/password/forgot" ->
                    respondJson(HttpStatusCode.Accepted, "")

                path == "/me" -> respondJson(
                    HttpStatusCode.OK,
                    matchProtocolJson.encodeToString(player),
                )

                path == "/accounts" -> respondJson(
                    HttpStatusCode.Created,
                    matchProtocolJson.encodeToString(session),
                )

                else -> respondJson(
                    HttpStatusCode.OK,
                    matchProtocolJson.encodeToString(session),
                )
            }
        }

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
            session = SessionStore(InMemoryDocumentStore()),
            tickets = TicketStore(InMemoryDocumentStore()),
            probe = ServerProbe(http) { 0L },
            reporter = MatchReporter.None,
        )
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        status: HttpStatusCode,
        body: String,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private fun english() = settingsFor(AppLocale.EN_US)

    private val serverInfo = ServerInfo(
        name = "Test server",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    private val home = ServerEntry.of("http://127.0.0.1:8080", label = "Test server")

    private val player = PlayerState(
        save = GameSave(username = "kuplu"),
        email = "kuplu@example.test",
        verified = false,
    )

    private val session = Session(
        token = "test-session",
        expiresAt = 1_770_086_400_000L,
        player = player,
    )
}
