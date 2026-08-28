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

@OptIn(ExperimentalTestApi::class)
class PvpListStateUiTest {

    @Test
    fun anUnreadLobbyIsShownAsLoadingAndNotAsEmpty() = unread {
        onNodeWithTag(PVP_TABLES_LOADING_TEST_TAG).assertExists()
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun aLobbyThatCouldNotBeReadOffersToTryAgain() = unread(answering = false) {
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PVP_TABLES_FAILED_TEST_TAG) }

        onNodeWithTag(PVP_TABLES_FAILED_TEST_TAG).assertExists()
        onNodeWithTag("$PVP_TABLES_FAILED_TEST_TAG-retry").assertExists()
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(PVP_TABLES_LOADING_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun aLobbyThatHasBeenReadAndIsEmptySaysSo() = unread(refreshFirst = true) {
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertExists()
        onNodeWithTag(PVP_TABLES_LOADING_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(PVP_TABLES_FAILED_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun theInvitationsTabHasTheSameThreeStates() = unread {
        onNodeWithTag(screenTabTestTag("invites")).performClick()

        onNodeWithTag(PVP_CHALLENGES_LOADING_TEST_TAG).assertExists()
        onNodeWithTag(PVP_NO_CHALLENGE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun invitationsThatCouldNotBeReadOfferToTryAgain() = unread(answering = false) {
        onNodeWithTag(screenTabTestTag("invites")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PVP_CHALLENGES_FAILED_TEST_TAG) }

        onNodeWithTag("$PVP_CHALLENGES_FAILED_TEST_TAG-retry").assertExists()
        onNodeWithTag(PVP_NO_CHALLENGE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun theClaimScreenWaitsRatherThanSayingThereIsNothing() = claims {
        onNodeWithTag(PVP_CLAIM_LOADING_TEST_TAG).assertExists()
        onNodeWithTag(PVP_CLAIM_EMPTY_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun claimsThatCouldNotBeReadOfferToTryAgain() = claims(answering = false) {
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PVP_CLAIM_FAILED_TEST_TAG) }

        onNodeWithTag("$PVP_CLAIM_FAILED_TEST_TAG-retry").assertExists()
        onNodeWithTag(PVP_CLAIM_EMPTY_TEST_TAG).assertDoesNotExist()
    }

    // ---- Harness ----------------------------------------------------------

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
                        catalog = pvpCards,
                        formats = pvpFormats,
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
