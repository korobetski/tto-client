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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class PvpTablesUiTest {

    @Test
    fun anEmptyLobbyOffersToHostAndListsNothing() = lobby {
        onNodeWithTag(PVP_HOST_TEST_TAG).assertExists()
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertExists()
        onNodeWithTag(PVP_TABLES_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun aTableIsListedAndCanBeJoined() = lobby(tables = listOf(tableJson())) {
        onNodeWithTag(tableRowTestTag(TABLE_ID)).assertExists()
        onNodeWithTag(tableJoinTestTag(TABLE_ID)).assertExists()
        onNodeWithTag(PVP_NO_TABLE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun yourOwnTableOffersCancelAndNoJoin() = lobby(tables = listOf(tableJson(host = ME))) {
        onNodeWithTag(tableRowTestTag(TABLE_ID)).assertExists()
        onNodeWithTag(tableJoinTestTag(TABLE_ID)).assertDoesNotExist()
        onNodeWithTag(PVP_CANCEL_TABLE_TEST_TAG).assertExists()
        onNodeWithTag(PVP_HOST_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun anUncollectedPrizeIsAnnounced() = lobby(claims = listOf(claimJson())) {
        onNodeWithTag(PVP_CLAIM_BANNER_TEST_TAG).assertExists()
    }

    @Test
    fun nothingOwedShowsNoBanner() = lobby {
        onNodeWithTag(PVP_CLAIM_BANNER_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun joiningATablePostsToIt() {
        val paths = mutableListOf<String>()

        lobby(tables = listOf(tableJson()), record = paths::add) {
            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()
            onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).performClick()
            waitForIdle()
        }

        assertEquals(
            1,
            paths.count { it.endsWith("/pvp/tables/$TABLE_ID/join") },
            "joined via $paths",
        )
    }

    @Test
    fun aRefusalIsShown() = lobby(tables = listOf(tableJson()), refuse = true) {
        onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
        waitForIdle()
        onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).performClick()
        waitForIdle()

        // Back in the lobby, which is where the note lives: the deck screen closes on the answer
        // being given, and the refusal arrives after it.
        onNodeWithTag(PVP_NOTE_TEST_TAG).assertExists()
    }

    @Test
    fun noNoteWithoutARefusal() = lobby(tables = listOf(tableJson())) {
        onNodeWithTag(PVP_NOTE_TEST_TAG).assertDoesNotExist()
    }

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

    /**
     * **Joining asks which deck before it sends anything.**
     *
     * The lobby used to carry a row of deck chips above the tabs and Join sent whatever it was
     * holding, which asked the question before either of the things it depends on was known —
     * which table, and so which rules and what stake. Now Join names a seat and the deck screen the
     * rest of the game uses takes it from there. See [PvpSeat].
     */
    @Test
    fun joiningAsksForADeckBeforeSendingAnything() {
        val bodies = mutableListOf<String>()

        lobby(profile = twoDecks(), tables = listOf(tableJson()), body = bodies::add) {
            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()

            onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).assertExists()
            assertEquals(emptyList(), bodies, "the join was sent before the deck was chosen")
        }
    }

    /** And the table's own terms are on that screen, because they are what the answer turns on. */
    @Test
    fun theDeckIsChosenAgainstTheTablesTerms() {
        lobby(profile = twoDecks(), tables = listOf(tableJson())) {
            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()

            // The host, the wager, and — this table sets `roulette` — the note that the referee
            // will draw more rules on top of the ones listed.
            assertVisible("Kuplu", "the deck screen should name who is being played")
            onNodeWithTag(DECK_SELECT_STAKE_TEST_TAG).assertExists()
            assertVisible("Roulette", "a pending draw should be shown before a deck is chosen")
        }
    }

    @Test
    fun theChosenDeckIsSentWhenJoining() {
        val bodies = mutableListOf<String>()

        lobby(profile = twoDecks(), tables = listOf(tableJson()), body = bodies::add) {
            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()
            onNodeWithTag(deckChoiceTestTag(1)).performClick()
            onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).performClick()
            waitForIdle()
        }

        assertEquals(listOf("""{"deck":1}"""), bodies, "the join carried $bodies")
    }

    @Test
    fun joiningWithRandomLeavesTheDeckToTheServer() {
        val bodies = mutableListOf<String>()

        lobby(profile = twoDecks(), tables = listOf(tableJson()), body = bodies::add) {
            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()
            onNodeWithTag(DECK_SELECT_RANDOM_TEST_TAG).performClick()
            waitForIdle()
        }

        assertEquals(listOf("{}"), bodies, "the join carried $bodies")
    }

    /**
     * **Under Random the deck is not asked, because the referee ignores it.**
     *
     * `RULE_RANDOM` splices the hand from the player's whole collection and the deck selector never
     * opens — that is how a solo match behaves, and now how the referee deals a multiplayer one.
     * Asking a question whose answer is thrown away is worse than not asking it.
     */
    @Test
    fun joiningARandomTableSkipsTheDeckQuestion() {
        val bodies = mutableListOf<String>()

        lobby(profile = twoDecks(), tables = listOf(randomTableJson()), body = bodies::add) {
            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()

            assertFalse(exists(DECK_SELECT_CHOOSE_TEST_TAG), "Random still asked for a deck")
        }

        assertEquals(listOf("{}"), bodies, "the join carried $bodies")
    }

    /** Backing out of the deck question leaves the lobby as it was, and sends nothing. */
    @Test
    fun leavingTheDeckQuestionJoinsNothing() {
        val bodies = mutableListOf<String>()

        lobby(profile = twoDecks(), tables = listOf(tableJson()), body = bodies::add) {
            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()
            onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
            waitForIdle()

            onNodeWithTag(tableJoinTestTag(TABLE_ID)).assertExists()
            assertEquals(emptyList(), bodies, "backing out still joined")
        }
    }

    // ---- Harness ----------------------------------------------------------

    private fun twoDecks(): GameSave {
        val profile = GameSave.new(username = ME, createdAt = 0L)
        val first = profile.decks.first()
        return profile.copy(decks = listOf(first, first.copy(name = "Second")))
    }

    @Suppress("LongParameterList")
    private fun lobby(
        tables: List<String> = emptyList(),
        claims: List<String> = emptyList(),
        refuse: Boolean = false,
        record: (String) -> Unit = {},
        body: (String) -> Unit = {},
        profile: GameSave = GameSave.new(username = ME, createdAt = 0L),
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine { request ->
            record(request.url.encodedPath)
            when {
                request.url.encodedPath.endsWith("/tables") ->
                    respondJson("[" + tables.joinToString(",") + "]")

                request.url.encodedPath.endsWith("/claims") ->
                    respondJson("[" + claims.joinToString(",") + "]")

                request.url.encodedPath.endsWith("/join") -> {
                    body((request.body as TextContent).text)
                    if (refuse) {
                        respond(
                            content = REFUSAL,
                            status = HttpStatusCode.Conflict,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respondJson("""{"waiting":false}""")
                    }
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
                        profile = profile,
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

    /** A table whose declared rules include Random, so the deck question has nothing to decide. */
    private fun randomTableJson() = """
        {"id":"$TABLE_ID","hostName":"Kuplu","formatId":"free-play",
         "rules":{"random":true},"roulette":false,
         "stake":{"mgp":0,"trade":"NONE"},"openedAt":0,"expiresAt":1}
    """.trimIndent()

    /**
     * An ordinary table: wagered, and not for enough to be worth a warning.
     *
     * Ten and not the fifty this used to be, because these tests are about joining and the profile
     * they join with holds `GameSave.STARTING_MGP`. Half a starting purse is a *heavy* table, and a
     * heavy one takes two presses — see `PvpStakeUiTest.aHeavyTableTakesTwoPressesToJoin`. Leaving
     * it at fifty would have made every test in this file exercise that confirmation instead.
     */
    private fun tableJson(host: String = "Kuplu") = """
        {"id":"$TABLE_ID","hostName":"$host","formatId":"free-play",
         "rules":{},"roulette":true,
         "stake":{"mgp":10,"trade":"ONE"},"openedAt":0,"expiresAt":1}
    """.trimIndent()

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
        const val REFUSAL = """{"code":"CANNOT_AFFORD","reason":"you cannot cover that"}"""

        const val ME = "Sigfrid"

        const val NOW = 0L

        const val FIVE_MINUTES = 300_000L
        const val LIFETIME_MINUTES = 5
        const val TABLE_ID = "t-1"
    }
}
