package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.Format
import com.tripletriad.data.FormatCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameSave
import com.tripletriad.model.TradeRule
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opening a table: choosing the rules and what the match is played for.
 *
 * ### The two claims worth a screen test
 *
 * **A rule the format does not allow is not offered.** The server refuses one, and a chip that can
 * be ticked into a refusal is a chip that lies about what it does. The pool is the menu.
 *
 * **The Roulette box does not tick a rule.** `RULE_ROULETTE` is one of the sixteen keys and is
 * deliberately absent from the grid: `GameRules.roulette` is what the Wheel of Fortune achievements
 * count, so it means *a draw happened* — a claim only the server may make. The box sets a separate
 * flag on the request, which is what this asserts against the body actually sent.
 */
@OptIn(ExperimentalTestApi::class)
class PvpTableUiTest {

    /** Only the format's own rules are on the menu. */
    @Test
    fun aRuleOutsideTheFormatIsNotOffered() = editor {
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertExists()
        onNodeWithTag(ruleToggleTestTag("RULE_PLUS")).assertExists()
        // Not in this fixture's pool, so not a choice.
        onNodeWithTag(ruleToggleTestTag("RULE_ELEMENTAL")).assertDoesNotExist()
    }

    /** And Roulette is never a chip, even though it is a rule key. */
    @Test
    fun rouletteIsNotOneOfTheChips() = editor {
        onNodeWithTag(ruleToggleTestTag("RULE_ROULETTE")).assertDoesNotExist()
        onNodeWithTag(PVP_TABLE_ROULETTE_TEST_TAG).assertExists()
    }

    /** A chip ticks on and off again, which is what `withoutRuleKey` exists for. */
    @Test
    fun aRuleTicksBothWays() = editor {
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertIsNotSelected()

        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).performClick()
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertIsSelected()

        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).performClick()
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertIsNotSelected()
    }

    /**
     * The three enum slots are exclusive: ticking one member unticks its sibling.
     *
     * All Open and Three Open share one field, so a grid that treated them as independent flags
     * would let a player ask for a rule set the engine cannot represent.
     */
    @Test
    fun theEnumSlotsAreExclusive() = editor {
        onNodeWithTag(ruleToggleTestTag("RULE_ALL_OPEN")).performClick()
        onNodeWithTag(ruleToggleTestTag("RULE_ALL_OPEN")).assertIsSelected()

        onNodeWithTag(ruleToggleTestTag("RULE_THREE_OPEN")).performClick()

        onNodeWithTag(ruleToggleTestTag("RULE_THREE_OPEN")).assertIsSelected()
        onNodeWithTag(ruleToggleTestTag("RULE_ALL_OPEN")).assertIsNotSelected()
    }

    /** A trade rule replaces the last rather than adding to it: a match has one, or none. */
    @Test
    fun theTradeRulesAreExclusive() = editor {
        onNodeWithTag(tradeToggleTestTag(TradeRule.NONE)).assertIsSelected()

        onNodeWithTag(tradeToggleTestTag(TradeRule.ONE)).performClick()

        onNodeWithTag(tradeToggleTestTag(TradeRule.ONE)).assertIsSelected()
        onNodeWithTag(tradeToggleTestTag(TradeRule.NONE)).assertIsNotSelected()
    }

    /** What the host ticked is what is sent — rules in the set, roulette as its own flag. */
    @Test
    fun theRequestCarriesTheChosenTerms() {
        val bodies = mutableListOf<String>()

        editor(recordBody = bodies::add) {
            onNodeWithTag(ruleToggleTestTag("RULE_SAME")).performClick()
            onNodeWithTag(PVP_TABLE_ROULETTE_TEST_TAG).performClick()
            onNodeWithTag(tradeToggleTestTag(TradeRule.DIFF)).performClick()
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            waitForIdle()
        }

        val sent = bodies.first { it.contains("formatId") }
        assertTrue(""""same":true""" in sent, "the rule was not sent: $sent")
        assertTrue(""""roulette":true""" in sent, "the draw was not asked for: $sent")
        assertTrue(""""trade":"DIFF"""" in sent, "the trade rule was not sent: $sent")
        // And `GameRules.roulette` — the field the server owns — is not what was set: the request
        // carries its own flag, so the rule set inside it stays as the host left it.
        assertTrue(""""rules":{"same":true}""" in sent, "the rule set was not sent bare: $sent")
    }

    /**
     * The chosen format is what gets sent.
     *
     * Two of the three authored formats were unreachable before this picker existed: the server
     * has always taken a `formatId` and the client has always sent `FormatCatalog.default`.
     */
    @Test
    fun theChosenFormatIsSent() {
        val bodies = mutableListOf<String>()

        editor(recordBody = bodies::add) {
            onNodeWithTag(formatToggleTestTag("other-format")).performClick()
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            waitForIdle()
        }

        val sent = bodies.first { it.contains("formatId") }
        assertTrue(""""formatId":"other-format"""" in sent, "the format was not sent: $sent")
    }

    /**
     * Switching format drops a rule the new one does not allow.
     *
     * Otherwise the request carries a rule the server refuses, for a reason the screen had already
     * stopped showing — the chip disappears with the format, so the player cannot see what is
     * wrong with what they are sending.
     */
    @Test
    fun switchingFormatDropsRulesTheNewOneForbids() = editor {
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).performClick()
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertIsSelected()

        // `other-format` allows only Elemental, so Same has nowhere to go.
        onNodeWithTag(formatToggleTestTag("other-format")).performClick()
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertDoesNotExist()

        onNodeWithTag(formatToggleTestTag("test-format")).performClick()
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertIsNotSelected()
    }

    /** Opening the table posts once and leaves the screen. */
    @Test
    fun openingPostsTheTableAndLeaves() {
        val paths = mutableListOf<String>()
        var left = false

        editor(record = paths::add, onOpened = { left = true }) {
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            waitForIdle()
        }

        assertEquals(1, paths.count { it.endsWith("/pvp/tables") }, "opened via $paths")
        assertTrue(left, "the editor did not return to the lobby")
    }

    /**
     * Aimed at somebody, the same screen sends an invitation instead of opening a table.
     *
     * The two propose the same four things and are checked by the same function on the server, so
     * they are stated on the same screen. Naming your rules used to be something you could do for
     * strangers browsing the lobby and not for a friend you invited by name.
     */
    @Test
    fun namingAnInviteeSendsAChallengeOnTheSameTerms() {
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<String>()

        editor(invitee = "Kuplu", record = paths::add, recordBody = bodies::add) {
            onNodeWithTag(ruleToggleTestTag("RULE_SAME")).performClick()
            onNodeWithTag(tradeToggleTestTag(TradeRule.ALL)).performClick()
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            waitForIdle()
        }

        assertEquals(1, paths.count { it.endsWith("/pvp/challenges") }, "posted to $paths")
        assertTrue(paths.none { it.endsWith("/pvp/tables") }, "it opened a table too: $paths")

        val sent = bodies.first { it.contains("username") }
        assertTrue(""""username":"Kuplu"""" in sent, "the invitee was not named: $sent")
        assertTrue(""""same":true""" in sent, "the rules did not travel: $sent")
        assertTrue(""""trade":"ALL"""" in sent, "the wager did not travel: $sent")
    }

    // ---- Harness ----------------------------------------------------------

    @Suppress("LongParameterList")
    private fun editor(
        invitee: String? = null,
        record: (String) -> Unit = {},
        recordBody: (String) -> Unit = {},
        onOpened: () -> Unit = {},
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine { request ->
            record(request.url.encodedPath)
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
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    PvpTableScreen(
                        profile = GameSave.new(username = "Sigfrid", createdAt = 0L),
                        formats = FormatCatalog(listOf(format, other)),
                        session = session,
                        invitee = invitee,
                        onOpened = onOpened,
                        onBack = {},
                    )
                }
            }
        }
        block()
    }

    private fun bodyOf(request: HttpRequestData): String =
        (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    /**
     * A pool with two flags, both members of one enum slot, and Roulette.
     *
     * Elemental is left out on purpose: [aRuleOutsideTheFormatIsNotOffered] needs a rule that is
     * real and not in this pool, and one the shipped FFXIV format also excludes.
     */
    /** A second format, so the picker has something to offer and a rule pool to differ on. */
    private val other = Format(
        id = "other-format",
        nameKey = "STR_TEST_OTHER",
        blocks = listOf(2),
        rules = listOf("RULE_ELEMENTAL"),
    )

    private val format = Format(
        id = "test-format",
        nameKey = "STR_TEST",
        blocks = listOf(1),
        rules = listOf(
            "RULE_SAME",
            "RULE_PLUS",
            "RULE_ALL_OPEN",
            "RULE_THREE_OPEN",
            "RULE_ROULETTE",
        ),
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private companion object {
        val TABLE_JSON = """
            {"id":"t-1","hostName":"Sigfrid","formatId":"test-format","rules":{},
             "roulette":false,"stake":{"mgp":0,"trade":"NONE"},"openedAt":0,"expiresAt":1}
        """.trimIndent()
    }
}
