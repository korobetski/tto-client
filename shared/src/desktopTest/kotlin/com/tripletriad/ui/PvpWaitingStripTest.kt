package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.net.PvpClient
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.time.FixedClock
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The strip that tells a player mid-board that a multiplayer opponent has arrived.
 *
 * Written because nothing told them. A table is polled from every screen, so hosting one and
 * playing a campaign board while it fills is the obvious thing to do — and the only sign the table
 * had filled was an operating-system notification, which desktop does not have and a phone in the
 * player's own hand does not show. The board they were looking at said nothing at all.
 */
@OptIn(ExperimentalTestApi::class)
class PvpWaitingStripTest {

    // ---- when it is offered ----------------------------------------------

    @Test
    fun aPairedMatchIsAnnouncedOverAGameInProgress() {
        assertTrue(shouldOfferWaitingMatch(sessionWith(playing()), Screen.MATCH))
        assertTrue(shouldOfferWaitingMatch(sessionWith(playing()), Screen.CAMPAIGN_MATCH))
    }

    /** The one screen it must never appear on is the board it is pointing at. */
    @Test
    fun itIsNotOfferedOnTheBoardItWouldSendThePlayerTo() {
        assertFalse(shouldOfferWaitingMatch(sessionWith(playing()), Screen.PVP_MATCH))
    }

    /**
     * Nor anywhere that is not a game.
     *
     * The lobby already draws the match itself, and a strip over a menu would be a second, worse
     * copy of a card that is right there. The strip is for the screens that have taken the player's
     * attention away — which is what [PLAYING_SCREENS] names.
     */
    @Test
    fun itIsNotOfferedOnAScreenThePlayerCanSimplyLeave() {
        assertFalse(shouldOfferWaitingMatch(sessionWith(playing()), Screen.PVP))
        assertFalse(shouldOfferWaitingMatch(sessionWith(playing()), Screen.DASHBOARD))
    }

    /** A match that is over is not somewhere to hurry to. */
    @Test
    fun aSettledMatchIsNotAnnounced() {
        val over = playing().copy(status = PvpMatchStatus.FINISHED)
        assertFalse(shouldOfferWaitingMatch(sessionWith(over), Screen.MATCH))
    }

    @Test
    fun nothingIsAnnouncedWithNoMatchAndNoServer() {
        assertFalse(shouldOfferWaitingMatch(null, Screen.MATCH))
        assertFalse(shouldOfferWaitingMatch(sessionWith(null), Screen.MATCH))
    }

    // ---- the countdown ----------------------------------------------------

    /**
     * Minutes and seconds, because the decision the number is read for is "can I finish this
     * board". Rounded **up**, so a strip reading `0:01` is never already too late.
     */
    @Test
    fun theCountdownIsMinutesAndSeconds() {
        for ((millis, expected) in READINGS) {
            assertEquals(expected, waitingCountdown(millis), "$millis ms")
        }
    }

    @Test
    fun aLapsedCountdownStopsAtZeroRatherThanGoingNegative() {
        for (millis in LAPSED) {
            assertEquals("0:00", waitingCountdown(millis), "$millis ms")
        }
    }

    // ---- what it draws ----------------------------------------------------

    @Test
    fun theStripSaysWhoIsWaitingAndForHowLong() = strip(playing()) {
        onNodeWithTag(PVP_WAITING_STRIP_TAG).assertExists()
        onNodeWithText(strings[StringKeys.PVP_WAITING_NOW], useUnmergedTree = true).assertExists()
        onNodeWithText("5:00", useUnmergedTree = true).assertExists()
    }

    /**
     * One sentence to a screen reader, not four fields.
     *
     * The two lines and the number are separate `Text`s because they are drawn at three different
     * sizes; read out one by one they would be an announcement interrupted by a bare number that
     * changes every second. See the `clearAndSetSemantics` in [PvpWaitingStrip].
     */
    @Test
    fun theStripIsReadOutAsOneSentence() = strip(playing()) {
        val spoken = listOf(
            strings[StringKeys.PVP_WAITING_NOW],
            strings[StringKeys.PVP_WAITING_KEPT],
            "5:00",
        ).joinToString(" · ")

        onNodeWithContentDescription(spoken).assertExists()
    }

    /**
     * The second line, and the reason the first one is actionable.
     *
     * A player will not walk off a campaign board on "an opponent is waiting" alone — and they do
     * not have to, because the game they are in is kept. Dropping this line would leave a strip
     * that reads as a threat.
     */
    @Test
    fun theStripSaysTheGameInProgressIsKept() = strip(playing()) {
        onNodeWithText(strings[StringKeys.PVP_WAITING_KEPT], useUnmergedTree = true).assertExists()
    }

    @Test
    fun aMatchWithNoDeadlineIsStillAnnouncedWithoutACountdown() =
        strip(playing().copy(deadline = null)) {
            onNodeWithTag(PVP_WAITING_STRIP_TAG).assertExists()
            onNodeWithText("5:00", useUnmergedTree = true).assertDoesNotExist()
        }

    @Test
    fun theButtonIsTheWayToTheMatch() {
        var joined = 0
        strip(playing(), onJoin = { joined++ }) {
            onNodeWithTag(PVP_WAITING_JOIN_TAG).assertTextEquals(strings[StringKeys.PVP_JOIN])
            onNodeWithTag(PVP_WAITING_JOIN_TAG).performClick()
        }
        assertEquals(1, joined, "the button did not lead anywhere")
    }

    // ---- fixtures ---------------------------------------------------------

    private fun strip(
        view: PvpMatchView?,
        onJoin: () -> Unit = {},
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val session = sessionWith(view)
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    PvpWaitingStrip(
                        pvp = session,
                        clock = FixedClock(NOW),
                        strings = strings,
                        onJoin = onJoin,
                    )
                }
            }
        }
        block()
    }

    /** A session whose next poll answers [view], resumed so that [PvpSession.match] holds it. */
    private fun sessionWith(view: PvpMatchView?): PvpSession {
        val engine = MockEngine { _ ->
            if (view == null) {
                respond("", HttpStatusCode.NoContent)
            } else {
                respond(
                    content = json.encodeToString(PvpMatchView.serializer(), view),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val session = PvpSession(PvpClient(http, { "http://server" }), tokenOf = { "token" })
        runBlocking { session.poll() }
        return session
    }

    private fun playing() = PvpMatchView(
        matchId = "m-1",
        side = CardColor.BLUE,
        opponentName = "Kuplu",
        rules = GameRules(),
        formatId = "ff14",
        cells = List(BOARD) { null },
        elements = List(BOARD) { null },
        hand = BLUE_CARDS,
        opponentHand = List(HAND) { null },
        first = CardColor.BLUE,
        placement = 0,
        // The pairing wait, which is what the server sends both sides of a match nobody has opened.
        deadline = NOW + WAIT_MS,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val strings: Strings = runBlocking { loadStrings(AppLocale.Default) }

    private companion object {
        const val BOARD = 9
        const val HAND = 5
        const val NOW = 1_767_268_800_000L

        /** Five minutes: `PvpMatchRow.PAIRING_MILLIS` on the server. */
        const val WAIT_MS = 300_000L

        /**
         * What the strip reads at four points, the third of which is the rounding.
         *
         * 8.4 seconds is `0:09` and not `0:08`: the number is rounded **up**, so a strip saying
         * `0:01` is never already too late.
         */
        val READINGS = mapOf(
            134_000L to "2:14",
            WAIT_MS to "5:00",
            8_400L to "0:09",
            60_000L to "1:00",
        )

        /** Zero, and past it — the sweep has not run yet but the wait is over. */
        val LAPSED = listOf(0L, -90_000L)

        val BLUE_CARDS: List<Int> = (1..5).map { Card.idFor(block = 1, number = it) }
    }
}
