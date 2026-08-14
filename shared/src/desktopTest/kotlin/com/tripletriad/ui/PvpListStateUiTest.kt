package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameSave
import com.tripletriad.net.PvpClient
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * The three states a fetched list can be in, and the two the lobby used to conflate.
 *
 * ### What was wrong
 *
 * An empty list was rendered one way, and it means three different things: nothing is there,
 * nothing has arrived yet, or nothing could be fetched. Only the first is worth telling a player.
 * The lobby told them all three as "nobody is here" — an **answer**, which somebody reads and acts
 * on by leaving, half a second before four tables arrive.
 *
 * ### Why the failure state exists at all
 *
 * It was introduced by the fix. Distinguishing "loading" from "empty" leaves an unreachable server
 * spinning for ever, which is worse than the wrong answer it replaced — that at least ended. So a
 * failed read says so and offers something to press. See [ListState].
 *
 * Split from [PvpTablesUiTest] because these are about the *states around* a list rather than what
 * a listed table does, and because that class was at the twenty functions detekt allows.
 */
@OptIn(ExperimentalTestApi::class)
class PvpListStateUiTest {

    /**
     * Before the lobby has been read, it says so — it does not say it is empty.
     *
     * The distinction this whole pair of states exists for. "Nobody is here" is an **answer**, and
     * a player who reads it leaves; showing it half a second before four tables arrive is telling
     * them something false at the one moment they are deciding whether to stay.
     *
     * Deliberately not asserted through the harness below, which refreshes before it renders — that
     * is the *loaded* path, and it is the one every other test here wants.
     */
    @Test
    fun anUnreadLobbyIsShownAsLoadingAndNotAsEmpty() = unread {
        onNodeWithTag(PVP_TABLES_LOADING_TEST_TAG).assertExists()
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertDoesNotExist()
    }

    /**
     * A lobby that could not be read says so, and offers a way to try again.
     *
     * The state this pair of screens **gained** when loading was introduced, and the reason it had
     * to: without it an unreachable server leaves the spinner turning for ever, which is worse than
     * the empty list it used to show wrongly — that at least ended. A dead end with nothing to
     * press is a screen the player has to leave and re-enter, so the only question is whether the
     * app looks like it knows.
     */
    @Test
    fun aLobbyThatCouldNotBeReadOffersToTryAgain() = unread(answering = false) {
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PVP_TABLES_FAILED_TEST_TAG) }

        onNodeWithTag(PVP_TABLES_FAILED_TEST_TAG).assertExists()
        onNodeWithTag("$PVP_TABLES_FAILED_TEST_TAG-retry").assertExists()
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(PVP_TABLES_LOADING_TEST_TAG).assertDoesNotExist()
    }

    /**
     * And once it has been read and really is empty, it says that instead.
     *
     * The state every other lobby test runs in, asserted here explicitly so the trio is complete:
     * the empty note is not gone, it is *conditional*.
     */
    @Test
    fun aLobbyThatHasBeenReadAndIsEmptySaysSo() = unread(refreshFirst = true) {
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertExists()
        onNodeWithTag(PVP_TABLES_LOADING_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(PVP_TABLES_FAILED_TEST_TAG).assertDoesNotExist()
    }

    /** The invitations tab draws the same three states, from its own request. */
    @Test
    fun theInvitationsTabHasTheSameThreeStates() = unread {
        onNodeWithTag(screenTabTestTag("invites")).performClick()

        onNodeWithTag(PVP_CHALLENGES_LOADING_TEST_TAG).assertExists()
        onNodeWithTag(PVP_NO_CHALLENGE_TEST_TAG).assertDoesNotExist()
    }

    /** And says so, with a way back, when it could not be read. */
    @Test
    fun invitationsThatCouldNotBeReadOfferToTryAgain() = unread(answering = false) {
        onNodeWithTag(screenTabTestTag("invites")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PVP_CHALLENGES_FAILED_TEST_TAG) }

        onNodeWithTag("$PVP_CHALLENGES_FAILED_TEST_TAG-retry").assertExists()
        onNodeWithTag(PVP_NO_CHALLENGE_TEST_TAG).assertDoesNotExist()
    }

    /**
     * The prize screen too, and it is the one where the wrong answer contradicts itself.
     *
     * A player reaches it by tapping a banner that says a prize is waiting. Telling them there is
     * nothing to collect while the request is still out disagrees with what sent them there.
     */
    @Test
    fun theClaimScreenWaitsRatherThanSayingThereIsNothing() = claims {
        onNodeWithTag(PVP_CLAIM_LOADING_TEST_TAG).assertExists()
        onNodeWithTag(PVP_CLAIM_EMPTY_TEST_TAG).assertDoesNotExist()
    }

    /** And offers a retry when the prizes could not be read at all. */
    @Test
    fun claimsThatCouldNotBeReadOfferToTryAgain() = claims(answering = false) {
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PVP_CLAIM_FAILED_TEST_TAG) }

        onNodeWithTag("$PVP_CLAIM_FAILED_TEST_TAG-retry").assertExists()
        onNodeWithTag(PVP_CLAIM_EMPTY_TEST_TAG).assertDoesNotExist()
    }

    // ---- Harness ----------------------------------------------------------

    /** The prize screen, with its one request either hanging or refusing. */
    private fun claims(
        answering: Boolean = true,
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine {
            if (answering) awaitCancellation() else throw java.io.IOException("unreachable")
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val session = PvpSession(PvpClient(http, { "http://server" }), tokenOf = { "token" })

        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    PvpClaimScreen(
                        session = session,
                        cards = emptyMap(),
                        now = NOW,
                        onDone = {},
                    )
                }
            }
        }
        block()
    }

    /**
     * The lobby as it is the instant it opens: nothing fetched, nothing refused.
     *
     * A separate harness from [lobby] because the difference *is* the test — that one refreshes
     * before rendering, which is what makes every other assertion here about the loaded state. The
     * engine hangs rather than answering, so the screen stays in the state under test.
     */
    private fun unread(
        answering: Boolean = true,
        refreshFirst: Boolean = false,
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        // Hanging for the loading case, refusing for the failed one, and an empty list for the
        // ready one. The difference is the whole point: one has not answered, one has answered no,
        // and one has answered nothing.
        val engine = MockEngine {
            when {
                refreshFirst -> respond(
                    content = "[]",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                answering -> awaitCancellation()
                else -> throw java.io.IOException("unreachable")
            }
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val session = PvpSession(
            client = PvpClient(http, { "http://server" }),
            tokenOf = { "token" },
            hostName = ME,
        )

        if (refreshFirst) runBlocking { session.refreshTables() }

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

    private val json = Json { ignoreUnknownKeys = true }

    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private companion object {
        const val ME = "Kuplu"
        const val NOW = 1_770_000_000_000L
        const val UI_TIMEOUT_MS = 5_000L
    }
}
