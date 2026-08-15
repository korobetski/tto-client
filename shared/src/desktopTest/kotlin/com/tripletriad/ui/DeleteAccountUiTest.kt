package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
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
import com.tripletriad.net.AccountClient
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.PvpClient
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerProbe
import com.tripletriad.net.SessionStore
import com.tripletriad.net.StoredSession
import com.tripletriad.net.TicketStore
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.protocol.PlayerState
import com.tripletriad.settings.UserSettings
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deleting an account, from the settings screen.
 *
 * ### Why this is the most carefully tested screen in the app
 *
 * Because it is the only one whose mistake cannot be corrected. Every other destructive control in
 * this port deletes something the player can make again — a local save, a bought item — and this
 * one ends the account, its cards, its history, and its rows in other players' histories. There is
 * no undo, no grace period and nothing to restore from.
 *
 * So what is asserted is not only that it works. It is the **shape of the gate**: that a single tap
 * does nothing, that a wrong password takes nothing away, and that backing out leaves no password
 * behind on a device somebody else may pick up.
 */
@OptIn(ExperimentalTestApi::class)
class DeleteAccountUiTest {

    /** Signed out, there is no account group at all — not a disabled one. */
    @Test
    fun withNoAccountThereIsNothingToDelete() = runComposeUiTest {
        setContent { Fixture(account = null) }

        assertFalse(
            exists(OPTIONS_ACCOUNT_GROUP_TEST_TAG),
            "a local-profile build offered to delete an account it does not have",
        )
    }

    /**
     * The first tap reveals the explanation and the field. It **sends nothing**.
     *
     * The property that separates this from the `armed` two-tap pattern used elsewhere: there is no
     * state in which one more tap in the same place ends the account.
     */
    @Test
    fun theFirstTapAsksRatherThanActs() = runComposeUiTest {
        val calls = mutableListOf<String>()
        // Built before `setContent`, not inside it: a session created during composition is
        // created again on every recomposition, which is a different object each time and a
        // `runBlocking` in the middle of a frame.
        val account = session(calls = calls)
        setContent { Fixture(account = account) }

        onNodeWithTag(OPTIONS_DELETE_ACCOUNT_TEST_TAG).performClick()
        waitForIdle()

        assertTrue(exists(OPTIONS_DELETE_PASSWORD_TEST_TAG), "no password was asked for")
        assertTrue(calls.isEmpty(), "the first tap sent $calls")
        onNodeWithTag(OPTIONS_DELETE_CONFIRM_TEST_TAG).assertIsNotEnabled()
    }

    /**
     * Tapping again closes it and **clears what was typed**.
     *
     * Backing out of this is something a player does on a device they are about to hand over, and a
     * password left sitting in a field is the one thing that must not survive the retreat. Asserted
     * by reopening: the field is composed fresh, so its contents are whatever the state holds.
     */
    @Test
    fun backingOutForgetsTheTypedPassword() = runComposeUiTest {
        setContent { Fixture(account = session()) }

        onNodeWithTag(OPTIONS_DELETE_ACCOUNT_TEST_TAG).performClick()
        onNodeWithTag(OPTIONS_DELETE_PASSWORD_TEST_TAG).performTextInput(PASSWORD)
        onNodeWithTag(OPTIONS_DELETE_ACCOUNT_TEST_TAG).performClick()
        onNodeWithTag(OPTIONS_DELETE_ACCOUNT_TEST_TAG).performClick()
        waitForIdle()

        // Nothing typed means nothing to confirm with, which is the observable form of "cleared".
        onNodeWithTag(OPTIONS_DELETE_CONFIRM_TEST_TAG).assertIsNotEnabled()
    }

    /** With a password typed and the server agreeing, the account goes and the caller is told. */
    @Test
    fun aConfirmedDeletionEndsTheSessionAndLeavesTheScreen() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val account = session(calls = calls)
        var left = false
        setContent { Fixture(account = account, onDeleted = { left = true }) }

        deleteWith(PASSWORD)

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { left }
        assertEquals(listOf("DELETE /accounts/me"), calls)
        assertNull(account.player, "the session survived the account")
        assertNull(account.lastUsername, "the deleted name was still offered back to the form")
        assertTrue(
            sessions.stored.values.none { TOKEN in it },
            "the token outlived the account it authenticated",
        )
    }

    /**
     * A **wrong password takes nothing away** — the case that decides the whole design.
     *
     * `signOut` clears the token first and ignores the server, because a sign-out a dead network
     * could refuse would be a button that does nothing. Doing that here would mean mistyping your
     * password signs you out of an account that still exists: answering "that is not your password"
     * by taking away the session. So the player stays signed in, stays on the screen, and is told.
     */
    @Test
    fun aWrongPasswordChangesNothing() = runComposeUiTest {
        val account = session(engine = refusing())
        var left = false
        setContent { Fixture(account = account, onDeleted = { left = true }) }

        deleteWith("not-the-password")

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { account.failure != null }
        assertFalse(left, "a refused deletion navigated away")
        assertEquals(
            NAME,
            account.player?.save?.username,
            "a refused deletion signed the player out",
        )
        assertTrue(exists(OPTIONS_ACCOUNT_GROUP_TEST_TAG), "a refused deletion left the screen")
    }

    /** And it says so, in the player's own language rather than as a status code. */
    @Test
    fun theRefusalIsWordedForTheReader() = runComposeUiTest {
        val account = session(engine = refusing())
        setContent { Fixture(account = account) }

        deleteWith("not-the-password")

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPTIONS_DELETE_NOTE_TEST_TAG) }
        assertVisible(
            "That name and password do not match an account.",
            "the refusal should be a sentence",
        )
    }

    // ---- Harness -----------------------------------------------------------

    private fun ComposeUiTest.deleteWith(password: String) {
        onNodeWithTag(OPTIONS_DELETE_ACCOUNT_TEST_TAG).performClick()
        onNodeWithTag(OPTIONS_DELETE_PASSWORD_TEST_TAG).performTextInput(password)
        onNodeWithTag(OPTIONS_DELETE_CONFIRM_TEST_TAG).performClick()
    }

    /**
     * [OptionsScreen] alone, with the real session behind it.
     *
     * Not the whole `App`: reaching this screen through the shell means signing in first, which is
     * `AccountUiTest`'s subject, and would put a sign-in between every assertion here and the thing
     * it is about. The session is real either way — what is faked is the server, which is the only
     * part a test cannot have.
     */
    @Composable
    private fun Fixture(account: AccountSession?, onDeleted: () -> Unit = {}) {
        CompositionLocalProvider(LocalStrings provides strings) {
            TripleTriadTheme {
                OptionsScreen(
                    settings = SettingsHolder(UserSettings(language = AppLocale.EN_US.tag)) {},
                    onBack = {},
                    account = account,
                    onDeleted = onDeleted,
                )
            }
        }
    }

    /** A session already signed in, as it is by the time anybody reaches Settings. */
    private fun session(
        engine: MockEngine = accepting(),
        calls: MutableList<String> = mutableListOf(),
    ): AccountSession {
        recorded = calls
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(matchProtocolJson) }
        }
        val directory = ServerDirectory(InMemoryDocumentStore(), listOf(home))
        val connection = ServerConnection(
            directory = directory,
            accounts = AccountClient(http, baseUrl = { directory.selected.baseUrl }),
            pvp = PvpClient(http, baseUrl = { directory.selected.baseUrl }),
            session = SessionStore(sessions),
            tickets = TicketStore(InMemoryDocumentStore()),
            probe = ServerProbe(http) { 0L },
            reporter = MatchReporter.None,
        )
        return AccountSession(connection, FixedClock(0L)).also {
            // `restore()` is how a session is normally populated, and it is what runs here: a token
            // is stored, then read back through the same code the app uses on launch. Setting the
            // fields directly would test a state the app cannot actually be in.
            runBlocking {
                connection.session.save(
                    home.id,
                    StoredSession(token = TOKEN, expiresAt = Long.MAX_VALUE, username = NAME),
                )
                it.restore()
            }
        }
    }

    /** Everything succeeds, and the delete is recorded so a test can say it was not sent. */
    private fun accepting() = MockEngine { request ->
        if (request.method == HttpMethod.Delete) {
            recorded += "DELETE ${request.url.encodedPath}"
            return@MockEngine respond("", HttpStatusCode.NoContent)
        }
        respondJson(HttpStatusCode.OK, matchProtocolJson.encodeToString(player))
    }

    /** The server refuses the password — the ordinary failure, not an outage. */
    private fun refusing() = MockEngine { request ->
        if (request.method == HttpMethod.Delete) {
            recorded += "DELETE ${request.url.encodedPath}"
            return@MockEngine respondJson(
                HttpStatusCode.Unauthorized,
                """{"error":"INVALID_CREDENTIALS","detail":"no"}""",
            )
        }
        respondJson(HttpStatusCode.OK, matchProtocolJson.encodeToString(player))
    }

    private fun MockRequestHandleScope.respondJson(
        status: HttpStatusCode,
        body: String,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private var recorded: MutableList<String> = mutableListOf()
    private val sessions = InMemoryDocumentStore()
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }
    private val home = ServerEntry(id = "home", label = "Home", baseUrl = "https://example.invalid")
    private val player = PlayerState(save = GameSave.new(createdAt = 0L).copy(username = NAME))

    private companion object {
        const val NAME = "deleter"
        const val PASSWORD = "correct-horse-battery"
        const val TOKEN = "a-token"
    }
}
