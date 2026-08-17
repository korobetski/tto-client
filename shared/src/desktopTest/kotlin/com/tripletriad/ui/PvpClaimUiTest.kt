package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
import com.tripletriad.net.PvpClient
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PvpClaimUiTest {

    @Test
    fun everyOfferedCardIsDrawn() = claim {
        for (id in PRIZES) {
            onNodeWithTag(prizeTestTag(id)).assertExists()
        }
        onNodeWithTag(PVP_CLAIM_TEST_TAG).assertExists()
        onNodeWithTag(PVP_CLAIM_EMPTY_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun confirmingIsDeadUntilExactlyEnoughArePicked() = claim(owed = 2) {
        onNodeWithTag(PVP_CLAIM_CONFIRM_TEST_TAG).assertIsNotEnabled()

        onNodeWithTag(prizeTestTag(PRIZES[0])).performClick()
        onNodeWithTag(PVP_CLAIM_CONFIRM_TEST_TAG).assertIsNotEnabled()

        onNodeWithTag(prizeTestTag(PRIZES[1])).performClick()
        onNodeWithTag(PVP_CLAIM_CONFIRM_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun aPickCanBeUndone() = claim {
        onNodeWithTag(prizeTestTag(PRIZES[0])).performClick()
        onNodeWithTag(PVP_CLAIM_CONFIRM_TEST_TAG).assertIsEnabled()

        onNodeWithTag(prizeTestTag(PRIZES[0])).performClick()

        onNodeWithTag(PVP_CLAIM_CONFIRM_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun anExtraPickIsIgnoredOnceEnoughAreChosen() = claim {
        onNodeWithTag(prizeTestTag(PRIZES[0])).performClick()

        onNodeWithTag(prizeTestTag(PRIZES[1])).performClick()

        // Still exactly one chosen, so the button is still live rather than over-full.
        onNodeWithTag(PVP_CLAIM_CONFIRM_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun confirmingPostsTheChoice() {
        val bodies = mutableListOf<String>()
        var left = false

        claim(recordBody = bodies::add, onDone = { left = true }) {
            onNodeWithTag(prizeTestTag(PRIZES[0])).performClick()
            onNodeWithTag(PVP_CLAIM_CONFIRM_TEST_TAG).performClick()
            waitForIdle()
        }

        val sent = bodies.first { it.contains("cardIds") }
        assertTrue("${PRIZES[0]}" in sent, "the chosen card was not sent: $sent")
        assertTrue(left, "the screen did not return to the lobby")
    }

    @Test
    fun nothingOwedSaysSo() = claim(claims = emptyList()) {
        onNodeWithTag(PVP_CLAIM_EMPTY_TEST_TAG).assertExists()
        onNodeWithTag(PVP_CLAIM_TEST_TAG).assertDoesNotExist()
    }

    // ---- Harness ----------------------------------------------------------

    private fun claim(
        owed: Int = 1,
        claims: List<String>? = null,
        recordBody: (String) -> Unit = {},
        onDone: () -> Unit = {},
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val listed = claims ?: listOf(claimJson(owed))
        val engine = MockEngine { request ->
            recordBody(bodyOf(request))
            respondJson(
                if (request.url.encodedPath.endsWith("/claims")) {
                    "[${listed.joinToString(",")}]"
                } else {
                    listed.firstOrNull() ?: "{}"
                },
            )
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val session = PvpSession(PvpClient(http, { "http://server" }), tokenOf = { "token" })
        runBlocking { session.refreshClaims() }

        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    PvpClaimScreen(
                        session = session,
                        cards = catalogue,
                        now = NOW,
                        onDone = onDone,
                    )
                }
            }
        }
        block()
    }

    private fun claimJson(owed: Int): String {
        val offered = PRIZES.joinToString(",")
        val deadline = NOW + DEADLINE_MILLIS
        return """
        {"matchId":"m-1","side":"BLUE","opponentName":"Kuplu","rules":{},
         "formatId":"free-play","cells":[null,null,null,null,null,null,null,null,null],
         "elements":[null,null,null,null,null,null,null,null,null],
         "hand":[],"opponentHand":[],"first":"BLUE","placement":9,"status":"AWAITING_CLAIM",
         "outcome":{"result":"WIN","blue":6,"red":4,"picksOwed":$owed,
                    "pickFrom":[$offered],"claimDeadline":$deadline}}
        """.trimIndent()
    }

    private fun bodyOf(request: HttpRequestData): String =
        (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(body: String) =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private val json = Json { ignoreUnknownKeys = true }

    private val catalogue: Map<Int, Card> =
        runBlocking { loadCardCatalog() }.all.associateBy { it.id }

    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private companion object {
        const val NOW = 1_767_268_800_000L

        const val DEADLINE_MILLIS = 60_000L

        val PRIZES: List<Int> = (1..5).map { Card.idFor(block = 1, number = it) }
    }
}
