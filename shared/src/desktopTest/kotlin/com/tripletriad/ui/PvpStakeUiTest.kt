package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.Format
import com.tripletriad.data.FormatCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameSave
import com.tripletriad.net.PvpClient
import com.tripletriad.protocol.PvpStakePolicy
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The wager: typing one, and being warned about somebody else's.
 *
 * ### What replaced what
 *
 * A `TtoSlider` running from nothing to the whole purse. It could not land on a round number
 * without luck, it offered the entire balance as an ordinary drag, and it had no way to draw a
 * ceiling at all — see `PvpTableScreen.StakeField` for the argument in full.
 *
 * ### None of this is the rule
 *
 * `PvpStakeLimitTest` in `tto-server` is. Everything here is the courtesy layer: the field does not
 * offer a wager the server would refuse, and the lobby says which tables deserve a second look
 * *for the player reading them* — a question the house cannot answer once for everybody, because
 * the answer depends on the reader's purse.
 */
@OptIn(ExperimentalTestApi::class)
class PvpStakeUiTest {

    // ---- Proposing one -----------------------------------------------------

    /** What the field says before anything is typed: the most this profile may put up. */
    @Test
    fun theLimitIsWrittenUnderTheField() = editor {
        onNodeWithText(strings.format(StringKeys.PVP_STAKE_LIMIT, "$STARTING_LIMIT"))
            .assertExists()
    }

    /**
     * The number in the field is the number that is sent.
     *
     * The slider's whole failure mode: a wager landing a tick off what the host meant, with nothing
     * on screen to say so until the match settled.
     */
    @Test
    fun theTypedWagerIsWhatTravels() {
        val bodies = mutableListOf<String>()

        editor(recordBody = bodies::add) {
            onNodeWithTag(PVP_TABLE_MGP_TEST_TAG).performTextReplacement("75")
            open()
        }

        val sent = bodies.first { it.contains("formatId") }
        assertTrue(""""mgp":75""" in sent, "the wager did not travel: $sent")
    }

    /** A rung fills the field, so the two controls are one value rather than two. */
    @Test
    fun aRungFillsTheField() = editor {
        onNodeWithTag(stakeChipTestTag(MIDDLE_RUNG)).performClick()

        onNodeWithTag(PVP_TABLE_MGP_TEST_TAG).assertTextContains("$MIDDLE_RUNG")
    }

    /**
     * The top rung is the ceiling itself, which is the one figure the player cannot derive.
     *
     * A fresh profile is level 1 holding the starting hundred, so both halves of the limit land on
     * the same number here; `PvpStakeFieldTest` is where they are separated.
     */
    @Test
    fun theMaxRungIsOfferedAndIsTheLimit() = editor {
        onNodeWithText(strings[StringKeys.PVP_STAKE_MAX]).assertExists()

        onNodeWithTag(stakeChipTestTag(STARTING_LIMIT)).performClick()

        onNodeWithTag(PVP_TABLE_MGP_TEST_TAG).assertTextContains("$STARTING_LIMIT")
    }

    /**
     * Over the level's ceiling: named as the level's, and the button closed.
     *
     * The profile is rich and low, so the purse cannot be what refuses — which is the point. The
     * server answers `STAKE_TOO_HIGH` and `CANNOT_AFFORD` separately because they are fixed by
     * opposite things, and a field saying only "too much" to both would undo that.
     */
    @Test
    fun aWagerOverTheLevelSaysSoAndCannotBeOpened() = editor(profile = rich(level = 2)) {
        val ceiling = PvpStakePolicy.DEFAULT_PER_LEVEL * 2
        onNodeWithTag(PVP_TABLE_MGP_TEST_TAG).performTextReplacement("${ceiling + 1}")

        onNodeWithText(strings.format(StringKeys.PVP_STAKE_OVER_LIMIT, "2", "$ceiling"))
            .assertExists()
        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).assertIsNotEnabled()
    }

    /** Over the purse: the other sentence, because it is the other wait. */
    @Test
    fun aWagerOverThePurseSaysThatInstead() = editor(profile = holding(level = 20, mgp = POCKET)) {
        onNodeWithTag(PVP_TABLE_MGP_TEST_TAG).performTextReplacement("${POCKET * 2}")

        onNodeWithText(strings.format(StringKeys.PVP_STAKE_OVER_PURSE, "$POCKET")).assertExists()
        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).assertIsNotEnabled()
    }

    /** Legal, large, and said out loud on the way in — but not blocked. It is the host's money. */
    @Test
    fun aHeavyWagerIsFlaggedAndStillAllowed() = editor(
        profile = holding(level = 20, mgp = POCKET),
    ) {
        onNodeWithTag(PVP_TABLE_MGP_TEST_TAG).performTextReplacement("$POCKET")

        onNodeWithText(strings[StringKeys.PVP_STAKE_HEAVY]).assertExists()
        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).assertIsEnabled()
    }

    /** An empty field is a free table, not a refusal: nothing typed is nothing wagered. */
    @Test
    fun anEmptyFieldOpensAFreeTable() {
        val bodies = mutableListOf<String>()

        editor(recordBody = bodies::add) {
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).assertIsEnabled()
            open()
        }

        // Not `"mgp":0` — an unwagered stake is left out of the request altogether, which is the
        // same thing said more quietly. What matters is that nothing was put up.
        val sent = bodies.first { it.contains("formatId") }
        assertTrue(""""mgp"""" !in sent, "a free table wagered something: $sent")
    }

    /**
     * The ceiling drawn is this deployment's, not `:core`'s default.
     *
     * Otherwise `ServerInfo.stakes` would be a number the client is told and ignores, and a host
     * would fill in a field bounded at one figure against a server refusing at another.
     */
    @Test
    fun theServersOwnCeilingIsTheOneDrawn() =
        editor(
            profile = rich(level = TIGHT_LEVEL),
            stakes = PvpStakePolicy(perLevel = TIGHT_PER_LEVEL),
        ) {
            // 400 here; 1,000 under the shipped policy, which offers no such rung.
            onNodeWithTag(stakeChipTestTag(TIGHT_CEILING)).assertExists()
            onNodeWithTag(stakeChipTestTag(SHIPPED_CEILING)).assertDoesNotExist()

            onNodeWithTag(PVP_TABLE_MGP_TEST_TAG)
                .performTextReplacement("${TIGHT_CEILING + 1}")

            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).assertIsNotEnabled()
        }

    // ---- Sitting at somebody else's ---------------------------------------

    /**
     * A table above the reader's own ceiling is shown, named, and not offered.
     *
     * Shown rather than filtered out: a lobby that silently drops tables reads as an empty lobby.
     * The row says what is missing, and what is missing is a level rather than money — the profile
     * here could pay this wager many times over.
     */
    @Test
    fun aTableAboveYourCeilingCannotBeJoined() =
        lobby(mgp = HIGH_WAGER, profile = rich(level = 2)) {
            onNodeWithTag(tableRowTestTag(TABLE_ID)).assertExists()
            onNodeWithTag(tableJoinTestTag(TABLE_ID)).assertIsNotEnabled()
            onNodeWithTag(tableCautionTestTag(TABLE_ID)).assertTextContains(
                strings.format(
                    StringKeys.PVP_TABLE_OVER_LIMIT,
                    "${PvpStakePolicy.DEFAULT_PER_LEVEL * 2}",
                ),
            )
        }

    /**
     * **The report this change came from:** a table costing a large part of what the reader holds
     * is not joined on one press.
     *
     * Legal on both sides, and still a match somebody can walk into by tapping a row while
     * scrolling. The second press is the whole mechanism — the wager is not refused, only slowed.
     */
    @Test
    fun aHeavyTableTakesTwoPressesToJoin() {
        val paths = mutableListOf<String>()

        lobby(mgp = HEAVY_WAGER, profile = holding(level = 20, mgp = THIN), record = paths::add) {
            onNodeWithTag(tableJoinTestTag(TABLE_ID)).assertIsEnabled()
            onNodeWithTag(tableCautionTestTag(TABLE_ID))
                .assertTextContains(strings[StringKeys.PVP_TABLE_HEAVY])

            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()
            assertTrue(paths.none { it.endsWith("/join") }, "one press sat down: $paths")
            onNodeWithText(strings[StringKeys.PVP_JOIN_CONFIRM]).assertExists()

            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()
        }

        assertTrue(paths.any { it.endsWith("/join") }, "the second press did not sit down: $paths")
    }

    /** An ordinary table is one press, or the confirmation is noise on every row in the lobby. */
    @Test
    fun anOrdinaryTableIsJoinedOnTheFirstPress() {
        val paths = mutableListOf<String>()

        lobby(mgp = 20, profile = rich(level = 20), record = paths::add) {
            onNodeWithTag(tableCautionTestTag(TABLE_ID)).assertDoesNotExist()

            onNodeWithTag(tableJoinTestTag(TABLE_ID)).performClick()
            waitForIdle()
        }

        assertTrue(paths.any { it.endsWith("/join") }, "the seat was not taken: $paths")
    }

    // ---- Harness -----------------------------------------------------------

    /** Levelled, and holding far more than any wager here, so the purse never binds. */
    private fun rich(level: Int) = holding(level, mgp = 500_000)

    private fun holding(level: Int, mgp: Int) =
        GameSave.new(username = ME, createdAt = 0L).copy(level = level, mgp = mgp)

    /** Opens the table, answering the deck question with Random. */
    private fun androidx.compose.ui.test.ComposeUiTest.open() {
        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(DECK_SELECT_RANDOM_TEST_TAG).performClick()
        waitForIdle()
    }

    private fun editor(
        profile: GameSave = GameSave.new(username = ME, createdAt = 0L),
        stakes: PvpStakePolicy = PvpStakePolicy(),
        recordBody: (String) -> Unit = {},
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine { request ->
            recordBody(bodyOf(request))
            respond(
                content = TABLE_JSON,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val session = PvpSession(PvpClient(http, { "http://server" }), tokenOf = { "token" })

        setContent {
            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalStakes provides stakes,
            ) {
                TripleTriadTheme {
                    PvpTableScreen(
                        profile = profile,
                        catalog = pvpCards,
                        formats = FormatCatalog(listOf(format)),
                        session = session,
                        onOpened = {},
                        onBack = {},
                    )
                }
            }
        }
        block()
    }

    private fun lobby(
        mgp: Int,
        profile: GameSave,
        record: (String) -> Unit = {},
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine { request ->
            record(request.url.encodedPath)
            when {
                request.url.encodedPath.endsWith("/tables") -> respondJson("[${tableJson(mgp)}]")
                request.url.encodedPath.endsWith("/join") -> respondJson("""{"waiting":false}""")
                request.url.encodedPath.endsWith("/challenges") -> respondJson("[]")
                request.url.encodedPath.endsWith("/claims") -> respondJson("[]")
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
                        now = 0L,
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

    /** Random, so taking the seat does not stop to ask which deck. */
    private fun tableJson(mgp: Int) = """
        {"id":"$TABLE_ID","hostName":"Kuplu","formatId":"free-play",
         "rules":{"random":true},"roulette":false,
         "stake":{"mgp":$mgp,"trade":"NONE"},"openedAt":0,"expiresAt":1}
    """.trimIndent()

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun bodyOf(request: HttpRequestData): String =
        (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    private val format = Format(
        id = "test-format",
        nameKey = "STR_TEST",
        blocks = listOf(1),
        rules = listOf("RULE_SAME", "RULE_RANDOM"),
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private companion object {
        const val ME = "Sigfrid"

        const val TABLE_ID = "t-1"

        /** Whatever the editor posts, it is answered with a table; these tests read the request. */
        val TABLE_JSON = """
            {"id":"t-1","hostName":"Sigfrid","formatId":"test-format","rules":{},
             "roulette":false,"stake":{"mgp":0,"trade":"NONE"},"openedAt":0,"expiresAt":1}
        """.trimIndent()

        /** A fresh profile is level 1 with `GameSave.STARTING_MGP`, so both halves cap at 100. */
        const val STARTING_LIMIT = 100

        /** Well under any ceiling used here, so a wager over it is the purse's refusal. */
        const val POCKET = 40

        /** A rung offered on every ceiling here except the smallest, so it is always a chip. */
        const val MIDDLE_RUNG = 50

        /** The level [theServersOwnCeilingIsTheOneDrawn] measures both policies at. */
        const val TIGHT_LEVEL = 10

        /** Under the shipped 100, so a pass proves the configured number was the one read. */
        const val TIGHT_PER_LEVEL = 40

        /** What this deployment allows at [TIGHT_LEVEL] — and so what the top rung must be. */
        const val TIGHT_CEILING = TIGHT_PER_LEVEL * TIGHT_LEVEL

        /** What the shipped policy would have allowed there — a rung that must not appear. */
        const val SHIPPED_CEILING = PvpStakePolicy.DEFAULT_PER_LEVEL * TIGHT_LEVEL

        /** Legal at level 20, and far above a level-2 ceiling of 200. */
        const val HIGH_WAGER = 1_500

        /** Legal at level 20 — the ceiling there is 2,200. */
        const val HEAVY_WAGER = 2_000

        /** Four times [HEAVY_WAGER], so that wager is exactly the quarter that counts as heavy. */
        const val THIN = HEAVY_WAGER * 4
    }
}
