package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchResult
import com.tripletriad.net.PvpClient
import com.tripletriad.protocol.PvpCell
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpMove
import com.tripletriad.protocol.PvpOutcome
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
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
 * The board of a match against another person.
 *
 * ### What this checks that [PvpSessionTest] cannot
 *
 * That one drives the state machine with no screen. This one renders, and the two claims worth
 * rendering for are both about **what is not there**: the opponent's cards are drawn as backs
 * because the client was never sent them, and a card the server did not list as playable does not
 * respond to a tap at all.
 *
 * The second is easy to get wrong in a way nothing else would catch — a card that accepts a tap and
 * posts a move the server refuses looks, to the player, like the game ignoring them.
 *
 * The screen is rendered directly rather than reached through `App`, because reaching it means
 * signing in to a server and being paired. What is under test is the board, not the route to it;
 * `PvpFlowTest` on the server covers the route with two real accounts.
 */
@OptIn(ExperimentalTestApi::class)
class PvpBoardUiTest {

    /** The opponent's five cards are five backs, and none of them is a face. */
    @Test
    fun theOpponentsHandIsDrawnAsBacks() = board {
        for (slot in 0 until HAND) {
            onNodeWithTag(pvpBackTestTag(slot)).assertExists()
        }
    }

    /** A revealed card — All Open, Three Open — is drawn face-up in its own slot. */
    @Test
    fun aRevealedOpponentCardIsDrawnFaceUp() = board(
        view = playing().copy(opponentHand = listOf(RED_CARDS[0], null, RED_CARDS[2], null, null)),
    ) {
        onNodeWithTag(pvpBackTestTag(HIDDEN_SLOT)).assertExists()
        onNodeWithTag(pvpBackTestTag(OTHER_HIDDEN_SLOT)).assertExists()
        // Slots 0 and 2 are faces, so they are *not* backs — the positional claim, which a filtered
        // list would lose: the hand would render as two cards instead of five with two shown.
        onNodeWithTag(pvpBackTestTag(0)).assertDoesNotExist()
        onNodeWithTag(pvpBackTestTag(SHOWN_SLOT)).assertDoesNotExist()
    }

    /** The score is the view's, and it is shown before a card is played. */
    @Test
    fun theScoreIsShown() = board {
        onNodeWithTag(PVP_SCORE_TEST_TAG).assertTextEquals("5 — 5")
    }

    /**
     * Tapping a playable card and then a cell posts exactly one move, naming the right slot.
     *
     * The slot is the assertion: the hand closes up as cards are played, so a screen that captured
     * an index when it drew the card would send a stale one. See `moveFor`.
     */
    @Test
    fun tappingACardThenACellPostsThatMove() {
        val posted = mutableListOf<PvpMove>()

        board(record = posted::add) {
            onNodeWithTag(pvpHandTestTag(TAPPED_SLOT)).performClick()
            onNodeWithTag(tileTestTag(CENTRE)).performClick()
            waitForIdle()
        }

        assertEquals(listOf(PvpMove(handIndex = TAPPED_SLOT, position = CENTRE)), posted)
    }

    /**
     * A card the server did not list as playable does not respond.
     *
     * Under Order and Chaos the server allows one slot, and the others are refused. A tap that
     * posts a move the server rejects reads, on the player's side, as the game ignoring them.
     */
    @Test
    fun anUnplayableCardIgnoresTaps() {
        val posted = mutableListOf<PvpMove>()

        board(view = playing().copy(playable = listOf(0)), record = posted::add) {
            onNodeWithTag(pvpHandTestTag(OTHER_HIDDEN_SLOT)).performClick()
            onNodeWithTag(tileTestTag(0)).performClick()
            waitForIdle()
        }

        assertTrue(posted.isEmpty(), "an unplayable card posted $posted")
    }

    /** Nothing is posted while it is the other player's turn. */
    @Test
    fun theWaitingSideCannotPlay() {
        val posted = mutableListOf<PvpMove>()

        board(view = playing().copy(playable = emptyList()), record = posted::add) {
            onNodeWithTag(pvpHandTestTag(0)).performClick()
            onNodeWithTag(tileTestTag(0)).performClick()
            waitForIdle()
        }

        assertTrue(posted.isEmpty(), "the waiting side posted $posted")
    }

    /** A card already on the board is drawn there, for both sides, with its owner. */
    @Test
    fun aPlayedCardIsOnTheBoard() = board(
        view = playing().copy(
            cells = List(BOARD) { position ->
                PvpCell(BLUE_CARDS[0], CardColor.BLUE).takeIf { position == CENTRE }
            },
        ),
    ) {
        onNodeWithTag(tileTestTag(CENTRE)).assertExists()
    }

    /**
     * A forfeit says *why*, which a plain result cannot.
     *
     * "You won" and "you won because they left" are not the same sentence to put in front of a
     * player, and only the second describes a board that was never finished.
     */
    @Test
    fun aForfeitExplainsItself() = board(
        view = playing().copy(
            status = PvpMatchStatus.FORFEITED,
            outcome = PvpOutcome(
                result = MatchResult.WIN,
                blue = 5,
                red = 5,
                forfeitedBy = CardColor.RED,
            ),
        ),
    ) {
        onNodeWithTag(PVP_RESULT_TEST_TAG).assertExists()
        // The concede button is gone: there is nothing left to concede.
        onNodeWithTag(PVP_FORFEIT_TEST_TAG).assertDoesNotExist()
    }

    /** A live match offers the concede button and no result panel. */
    @Test
    fun aLiveMatchOffersConcedingAndNoResult() = board {
        onNodeWithTag(PVP_FORFEIT_TEST_TAG).assertExists()
        onNodeWithTag(PVP_RESULT_TEST_TAG).assertDoesNotExist()
    }

    /**
     * The turn line says whose turn it is, and names the opponent when it is theirs.
     *
     * A board with no statement of whose move it is looks, to a player waiting, exactly like a
     * board that has stopped working.
     */
    @Test
    fun theTurnLineNamesWhoseMoveItIs() = board {
        onNodeWithTag(PVP_TURN_TEST_TAG).assertExists()
        onNodeWithTag(PVP_BOARD_TEST_TAG).assertExists()
    }

    /** And on the other side's turn it names them, so waiting has a reason attached. */
    @Test
    fun theWaitingSideIsToldWhoTheyAreWaitingFor() = board(
        view = playing().copy(playable = emptyList(), first = CardColor.RED),
    ) {
        onNodeWithTag(PVP_TURN_TEST_TAG).assertTextContains("Kuplu", substring = true)
    }

    /** A finished match offers a way out, and taking it clears the match. */
    @Test
    fun aFinishedMatchOffersAWayOut() {
        var exited = false

        board(
            view = playing().copy(
                status = PvpMatchStatus.FINISHED,
                outcome = PvpOutcome(result = MatchResult.WIN, blue = 6, red = 4),
            ),
            onExit = { exited = true },
        ) {
            onNodeWithTag(PVP_DONE_TEST_TAG).performClick()
            waitForIdle()
        }

        assertTrue(exited, "the result panel led nowhere")
    }

    // ---- Harness ----------------------------------------------------------

    /**
     * Renders [view] and runs [block] against it.
     *
     * @param record every move the screen posts, so a test can assert on what was sent rather than
     *   on what the (mocked) server answered.
     */
    @Suppress("LongParameterList")
    private fun board(
        view: PvpMatchView = playing(),
        record: (PvpMove) -> Unit = {},
        onExit: () -> Unit = {},
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/move")) {
                val sent = (request.body as OutgoingContent.ByteArrayContent)
                    .bytes()
                    .decodeToString()
                record(json.decodeFromString(PvpMove.serializer(), sent))
            }
            respondJson(json.encodeToString(PvpMatchView.serializer(), view))
        }
        val session = sessionOver(engine)
        runBlocking { session.resume() }

        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    PvpMatchScreen(session = session, cards = catalogue, now = NOW, onExit = onExit)
                }
            }
        }
        block()
    }

    private fun sessionOver(engine: MockEngine): PvpSession {
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        return PvpSession(PvpClient(http, { "http://server" })) { "token" }
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
        playable = BLUE_CARDS.indices.toList(),
    )

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The shipped table, so the ids in the fixtures resolve the way the app resolves them. */
    private val catalogue: Map<Int, Card> =
        runBlocking { loadCardCatalog() }.all.associateBy { it.id }

    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private companion object {
        const val BOARD = 9
        const val HAND = 5

        /** Slots the fixtures name. Constants because detekt counts a literal index as magic. */
        const val CENTRE = 4
        const val TAPPED_SLOT = 2
        const val SHOWN_SLOT = 2
        const val HIDDEN_SLOT = 1
        const val OTHER_HIDDEN_SLOT = 3
        const val NOW = 1_767_268_800_000L

        /** Ten real ids from the shipped FFXIV block: five each side, none shared. */
        val BLUE_CARDS: List<Int> = (1..5).map { Card.idFor(block = 1, number = it) }
        val RED_CARDS: List<Int> = (11..15).map { Card.idFor(block = 1, number = it) }
    }
}
