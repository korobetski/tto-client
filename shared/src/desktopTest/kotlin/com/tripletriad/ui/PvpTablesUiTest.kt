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
import com.tripletriad.protocol.PvpTable
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The lobby's first tab: the tables on offer, and the prize banner above them.
 *
 * ### Split from [PvpLobbyUiTest] rather than added to it
 *
 * The two tabs are two features sharing a scaffold. An invitation is a directed offer to one
 * person; a table is an open one to nobody in particular — and the difference shows in what each
 * row has to say, because a table carries **terms**, which is the whole reason it replaced a queue.
 *
 * ### The assertion that matters
 *
 * [yourOwnTableOffersCancelAndNoJoin]. It is the sibling of the invitation claim next door: joining
 * your own table is a match against yourself, which the server refuses with nothing on screen to
 * explain it, so the row that would produce that error does not draw the button.
 */
@OptIn(ExperimentalTestApi::class)
class PvpTablesUiTest {

    /** Before anything happens: a way to host, and an empty lobby that says so. */
    @Test
    fun anEmptyLobbyOffersToHostAndListsNothing() = lobby {
        onNodeWithTag(PVP_HOST_TEST_TAG).assertExists()
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertExists()
        onNodeWithTag(PVP_TABLES_TEST_TAG).assertDoesNotExist()
    }

    /** A table is listed with a way in. */
    @Test
    fun aTableIsListedAndCanBeJoined() = lobby(tables = listOf(tableJson())) {
        onNodeWithTag(tableRowTestTag(TABLE_ID)).assertExists()
        onNodeWithTag(tableJoinTestTag(TABLE_ID)).assertExists()
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertDoesNotExist()
    }

    /**
     * Your own table offers Withdraw and **no** Join.
     *
     * The sibling of [anInvitationYouSentOffersNoAcceptButton], and the same claim: joining your
     * own table is a match against yourself, which the server refuses with nothing on screen to
     * explain it. The row that would produce that error does not draw the button.
     */
    @Test
    fun yourOwnTableOffersCancelAndNoJoin() = lobby(tables = listOf(tableJson(host = ME))) {
        onNodeWithTag(tableRowTestTag(TABLE_ID)).assertExists()
        onNodeWithTag(tableJoinTestTag(TABLE_ID)).assertDoesNotExist()
        onNodeWithTag(PVP_CANCEL_TABLE_TEST_TAG).assertExists()
        onNodeWithTag(PVP_HOST_TEST_TAG).assertDoesNotExist()
    }

    /** A prize waiting is announced, because a deadline collects it otherwise. */
    @Test
    fun anUncollectedPrizeIsAnnounced() = lobby(claims = listOf(claimJson())) {
        onNodeWithTag(PVP_CLAIM_BANNER_TEST_TAG).assertExists()
    }

    /** And with nothing owed there is no banner to distract from the lobby. */
    @Test
    fun nothingOwedShowsNoBanner() = lobby {
        onNodeWithTag(PVP_CLAIM_BANNER_TEST_TAG).assertDoesNotExist()
    }

    /** Tapping Join posts to that table exactly once. */
    @Test
    fun joiningATablePostsToIt() {
        val paths = mutableListOf<String>()

        lobby(tables = listOf(tableJson()), record = paths::add) {
            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()
        }

        assertEquals(
            1,
            paths.count { it.endsWith("/pvp/tables/$TABLE_ID/join") },
            "joined via $paths",
        )
    }

    /**
     * A refused request says so on screen.
     *
     * The claim this file exists to make second-loudest. Every refusal the server can answer a
     * table request with reached the client and was read by nothing: the player tapped, the server
     * said no, and the screen sat there. A note is the difference between "it did not work" and
     * "nothing happened".
     */
    @Test
    fun aRefusalIsShown() = lobby(tables = listOf(tableJson()), refuse = true) {
        onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
        waitForIdle()

        onNodeWithTag(PVP_NOTE_TEST_TAG).assertExists()
    }

    /** And nothing is shown when nothing was refused. */
    @Test
    fun noNoteWithoutARefusal() = lobby(tables = listOf(tableJson())) {
        onNodeWithTag(PVP_NOTE_TEST_TAG).assertDoesNotExist()
    }

    /**
     * A table says how long it has left.
     *
     * It expires after five minutes — `PvpMatchRow.TABLE_MILLIS` — and until now did so in
     * silence: the host watched the lobby, their row disappeared, and the Host button came back
     * with nothing said about why.
     */
    @Test
    fun aTableSaysHowLongItHasLeft() {
        val table = PvpTable(
            id = TABLE_ID,
            hostName = "Kuplu",
            formatId = "free-play",
            openedAt = NOW,
            expiresAt = NOW + FIVE_MINUTES,
        )

        assertEquals(LIFETIME_MINUTES, minutesLeft(table, NOW))
        // Rounded up, so the last minute of a table's life reads "1 min" and not "0 min".
        assertEquals(1, minutesLeft(table, NOW + FIVE_MINUTES - 1))
        // And floored, because a lapsed table is still on screen until the next poll replaces it.
        assertEquals(0, minutesLeft(table, NOW + FIVE_MINUTES))
        assertEquals(0, minutesLeft(table, NOW + FIVE_MINUTES * 2))
    }

    // ---- Harness ----------------------------------------------------------

    /** Renders the lobby with [tables] on offer and [claims] waiting to be collected. */
    private fun lobby(
        tables: List<String> = emptyList(),
        claims: List<String> = emptyList(),
        refuse: Boolean = false,
        record: (String) -> Unit = {},
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine { request ->
            record(request.url.encodedPath)
            when {
                request.url.encodedPath.endsWith("/tables") ->
                    respondJson("[" + tables.joinToString(",") + "]")

                request.url.encodedPath.endsWith("/claims") ->
                    respondJson("[" + claims.joinToString(",") + "]")

                request.url.encodedPath.endsWith("/join") -> if (refuse) {
                    respond(
                        content = """{"code":"CANNOT_AFFORD","reason":"you cannot cover that"}""",
                        status = HttpStatusCode.Conflict,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respondJson("""{"waiting":false}""")
                }

                request.url.encodedPath.endsWith("/challenges") -> respondJson("[]")

                // No match, so the screen stays on the lobby rather than navigating away.
                else -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val session = PvpSession(
            client = PvpClient(http, { "http://server" }),
            tokenOf = { "token" },
            hostName = ME,
        )
        runBlocking {
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
                        onInvite = {},
                        onClaim = {},
                        onBack = {},
                    )
                }
            }
        }
        block()
    }

    /** One open table, with a wager on it so the row has terms to draw. */
    private fun tableJson(host: String = "Kuplu") = """
        {"id":"$TABLE_ID","hostName":"$host","formatId":"free-play",
         "rules":{},"roulette":true,
         "stake":{"mgp":50,"trade":"ONE"},"openedAt":0,"expiresAt":1}
    """.trimIndent()

    /** A finished match owing this player one card. */
    private fun claimJson() = """
        {"matchId":"m-1","side":"BLUE","opponentName":"Kuplu","rules":{},
         "formatId":"free-play","cells":[null,null,null,null,null,null,null,null,null],
         "elements":[null,null,null,null,null,null,null,null,null],
         "hand":[],"opponentHand":[],"first":"BLUE","placement":9,"status":"AWAITING_CLAIM",
         "outcome":{"result":"WIN","blue":6,"red":4,"picksOwed":1,"pickFrom":[257]}}
    """.trimIndent()

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private companion object {
        const val ME = "Sigfrid"

        /** Fixed, so a countdown reads the same on every run. */
        const val NOW = 0L

        /** What `PvpMatchRow.TABLE_MILLIS` is, in the unit this test reads. */
        const val FIVE_MINUTES = 300_000L
        const val LIFETIME_MINUTES = 5
        const val TABLE_ID = "t-1"
    }
}
