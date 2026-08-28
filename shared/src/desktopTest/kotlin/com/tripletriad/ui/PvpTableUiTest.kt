package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.Format
import com.tripletriad.data.FormatCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PvpTableUiTest {

    @Test
    fun aRuleOutsideTheFormatIsNotOffered() = editor {
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertExists()
        onNodeWithTag(ruleToggleTestTag("RULE_PLUS")).assertExists()
        // Not in this fixture's pool, so not a choice.
        onNodeWithTag(ruleToggleTestTag("RULE_ELEMENTAL")).assertDoesNotExist()
    }

    @Test
    fun rouletteIsNotOneOfTheChips() = editor {
        onNodeWithTag(ruleToggleTestTag("RULE_ROULETTE")).assertDoesNotExist()
        onNodeWithTag(PVP_TABLE_ROULETTE_TEST_TAG).assertExists()
    }

    @Test
    fun aRuleTicksBothWays() = editor {
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertIsNotSelected()

        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).performClick()
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertIsSelected()

        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).performClick()
        onNodeWithTag(ruleToggleTestTag("RULE_SAME")).assertIsNotSelected()
    }

    @Test
    fun theEnumSlotsAreExclusive() = editor {
        onNodeWithTag(ruleToggleTestTag("RULE_ALL_OPEN")).performClick()
        onNodeWithTag(ruleToggleTestTag("RULE_ALL_OPEN")).assertIsSelected()

        onNodeWithTag(ruleToggleTestTag("RULE_THREE_OPEN")).performClick()

        onNodeWithTag(ruleToggleTestTag("RULE_THREE_OPEN")).assertIsSelected()
        onNodeWithTag(ruleToggleTestTag("RULE_ALL_OPEN")).assertIsNotSelected()
    }

    @Test
    fun theTradeRulesAreExclusive() = editor {
        onNodeWithTag(tradeToggleTestTag(TradeRule.NONE)).assertIsSelected()

        onNodeWithTag(tradeToggleTestTag(TradeRule.ONE)).performClick()

        onNodeWithTag(tradeToggleTestTag(TradeRule.ONE)).assertIsSelected()
        onNodeWithTag(tradeToggleTestTag(TradeRule.NONE)).assertIsNotSelected()
    }

/**
     * **The deck is asked after the terms, not among them.** See `PvpTableScreen.HostDeck`.
     *
     * The form is what is being offered to somebody else; the deck is the one line the host answers
     * for themselves, so it is the step that follows rather than a chip row sitting between the
     * roulette and the wager.
     */
    @Test
    fun theDeckIsAskedOnceTheTermsAreSettled() = editor {
        onNodeWithTag(DECK_SELECT_TEST_TAG).assertDoesNotExist()

        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(deckChoiceTestTag(0)).assertExists()
    }

    /**
     * A question that has not been answered has sent nothing.
     *
     * Which is the whole of why it is asked before the request and not after:
     * `pvp_tables.host_deck` is written when the table is opened, so an answer arriving later
     * would arrive too late.
     */
    @Test
    fun nothingIsPostedUntilTheDeckIsAnswered() {
        val paths = mutableListOf<String>()
        var left = false

        editor(record = paths::add, onOpened = { left = true }) {
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            waitForIdle()
        }

        assertTrue(paths.isEmpty(), "the table was opened before the deck was chosen: $paths")
        assertFalse(left, "the editor left with the question still on screen")
    }

    /**
     * **The host's deck is offered against the format the host has just picked.**
     *
     * This question used to be a chip row above the lobby's tabs, where the only thing it could
     * test was whether a deck was complete — no format had been chosen yet, so none could be
     * applied. An FFXIV deck brought to an FFVIII table is five cards its pool does not admit, and
     * the referee answers `UNDEALABLE` rather than dealing them.
     *
     * The two fixture formats draw from different blocks, and the starting profile's deck is in the
     * first, so switching format is what empties the list.
     */
    @Test
    fun onlyDecksTheChosenFormatAdmitsAreOffered() = editor {
        onNodeWithTag(formatToggleTestTag(other.id)).performClick()
        waitForIdle()

        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(deckChoiceTestTag(0)).assertDoesNotExist()
        onNodeWithTag(DECK_SELECT_EMPTY_TEST_TAG).assertExists()
        // Random survives: it is the absence of a choice, and the referee still has to deal
        // something. What it can deal is the server's problem, not this screen's.
        onNodeWithTag(DECK_SELECT_RANDOM_TEST_TAG).assertExists()
    }

    /**
     * What a host about to wager is choosing cards against — the line a solo match has no
     * equivalent of, and the reason this is worth a screen rather than a chip.
     */
    @Test
    fun theDeckQuestionShowsWhatIsBeingWagered() = editor {
        onNodeWithTag(tradeToggleTestTag(TradeRule.ALL)).performClick()
        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(DECK_SELECT_STAKE_TEST_TAG)
            .assertTextContains(strings[StringKeys.PVP_TRADE_ALL])
    }

    @Test
    fun theRequestCarriesTheChosenDeck() {
        val bodies = mutableListOf<String>()

        editor(recordBody = bodies::add) {
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            waitForIdle()
            onNodeWithTag(deckChoiceTestTag(0)).performClick()
            onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).performClick()
            waitForIdle()
        }

        val sent = bodies.first { it.contains("formatId") }
        assertTrue(""""deck":0""" in sent, "the host's deck was not sent: $sent")
    }

    /**
     * Under Random there is nothing to ask: the referee splices the hand from the whole collection
     * and the deck the host would name is ignored. The same skip `PvpScreen.sit` makes for a seat.
     */
    @Test
    fun randomOpensTheTableWithoutAskingForADeck() {
        val paths = mutableListOf<String>()

        editor(record = paths::add) {
            onNodeWithTag(ruleToggleTestTag("RULE_RANDOM")).performClick()
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            waitForIdle()

            onNodeWithTag(DECK_SELECT_TEST_TAG).assertDoesNotExist()
        }

        assertEquals(1, paths.count { it.endsWith("/pvp/tables") }, "opened via $paths")
    }

    @Test
    fun theRequestCarriesTheChosenTerms() {
        val bodies = mutableListOf<String>()

        editor(recordBody = bodies::add) {
            onNodeWithTag(ruleToggleTestTag("RULE_SAME")).performClick()
            onNodeWithTag(PVP_TABLE_ROULETTE_TEST_TAG).performClick()
            onNodeWithTag(tradeToggleTestTag(TradeRule.DIFF)).performClick()
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            bringAnyDeck()
        }

        val sent = bodies.first { it.contains("formatId") }
        assertTrue(""""same":true""" in sent, "the rule was not sent: $sent")
        assertTrue(""""roulette":true""" in sent, "the draw was not asked for: $sent")
        assertTrue(""""trade":"DIFF"""" in sent, "the trade rule was not sent: $sent")
        // And `GameRules.roulette` — the field the server owns — is not what was set: the request
        // carries its own flag, so the rule set inside it stays as the host left it.
        assertTrue(""""rules":{"same":true}""" in sent, "the rule set was not sent bare: $sent")
    }

    @Test
    fun theChosenFormatIsSent() {
        val bodies = mutableListOf<String>()

        editor(recordBody = bodies::add) {
            onNodeWithTag(formatToggleTestTag("other-format")).performClick()
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            bringAnyDeck()
        }

        val sent = bodies.first { it.contains("formatId") }
        assertTrue(""""formatId":"other-format"""" in sent, "the format was not sent: $sent")
    }

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

    @Test
    fun openingPostsTheTableAndLeaves() {
        val paths = mutableListOf<String>()
        var left = false

        editor(record = paths::add, onOpened = { left = true }) {
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            bringAnyDeck()
        }

        assertEquals(1, paths.count { it.endsWith("/pvp/tables") }, "opened via $paths")
        assertTrue(left, "the editor did not return to the lobby")
    }

    @Test
    fun namingAnInviteeSendsAChallengeOnTheSameTerms() {
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<String>()

        editor(invitee = "Kuplu", record = paths::add, recordBody = bodies::add) {
            onNodeWithTag(ruleToggleTestTag("RULE_SAME")).performClick()
            onNodeWithTag(tradeToggleTestTag(TradeRule.ALL)).performClick()
            onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).performClick()
            bringAnyDeck()
        }

        assertEquals(1, paths.count { it.endsWith("/pvp/challenges") }, "posted to $paths")
        assertTrue(paths.none { it.endsWith("/pvp/tables") }, "it opened a table too: $paths")

        val sent = bodies.first { it.contains("username") }
        assertTrue(""""username":"Kuplu"""" in sent, "the invitee was not named: $sent")
        assertTrue(""""same":true""" in sent, "the rules did not travel: $sent")
        assertTrue(""""trade":"ALL"""" in sent, "the wager did not travel: $sent")
    }

    // ---- Harness ----------------------------------------------------------

    /**
     * Answers the deck question with Random and lets the request go.
     *
     * Random rather than a row, because these tests are about what the *form* sent and a deck row
     * is not offered under every format they pick — see [onlyDecksTheChosenFormatAdmitsAreOffered].
     */
    private fun androidx.compose.ui.test.ComposeUiTest.bringAnyDeck() {
        waitForIdle()
        onNodeWithTag(DECK_SELECT_RANDOM_TEST_TAG).performClick()
        waitForIdle()
    }

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
                        catalog = pvpCards,
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
            "RULE_RANDOM",
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
