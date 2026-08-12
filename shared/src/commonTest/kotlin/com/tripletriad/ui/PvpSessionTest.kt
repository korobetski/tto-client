package com.tripletriad.ui

import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.net.PvpClient
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpMove
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PvpSession] — the client half of a server-refereed match.
 *
 * ### The behaviour worth pinning
 *
 * Two things, and neither is about the happy path.
 *
 * **A refused move polls instead of complaining.** The server is the referee, so a 409 means this
 * client was working from a view that has since moved on. Reporting it would blame the player for a
 * tap that was legal when they made it, and leave the screen showing a state the server has already
 * left behind.
 *
 * **A match survives the application dying.** [PvpSession.resume] is the first question this client
 * asks, because mobile kills applications without asking and the player did not choose to leave.
 */
class PvpSessionTest {

    /** Resuming finds the match the server is holding, and says it has looked. */
    @Test
    fun resumingFindsTheMatchTheServerIsHolding() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(playing())))

        session.resume()

        assertTrue(session.isResumed)
        assertEquals(MATCH_ID, session.match?.matchId)
        assertFalse(session.isOver)
    }

    /** And a player in no match is told so without it looking like a failure. */
    @Test
    fun noMatchIsAnAnswerAndNotAFailure() = runTest {
        val session = sessionOver(answering(HttpStatusCode.NoContent, ""))

        session.resume()

        assertTrue(session.isResumed)
        assertNull(session.match)
        assertNull(session.failure, "not being in a match is not a failure")
    }

    /**
     * A refused move re-reads the match rather than publishing an error.
     *
     * The assertion is on both halves: nothing is reported to the player, **and** the view is the
     * one the server has now. Only the first would pass just as well if the client silently did
     * nothing at all.
     */
    @Test
    fun aRefusedMoveRereadsTheMatchAndReportsNothing() = runTest {
        val moved = playing(placement = 3)
        var polled = false
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/move")) {
                respondJson(HttpStatusCode.Conflict, """{"reason":"it is not your turn"}""")
            } else {
                polled = true
                respondJson(HttpStatusCode.OK, encode(moved))
            }
        }
        val session = sessionOver(engine)
        session.resume()

        session.play(PvpMove(handIndex = 0, position = 0))

        assertNull(session.failure, "a stale view was reported to the player as an error")
        assertTrue(polled, "the refusal was swallowed without finding out what happened")
        assertEquals(3, session.match?.placement)
    }

    /** An accepted move takes the returned view, with no second round trip to fetch it. */
    @Test
    fun anAcceptedMoveTakesTheViewItIsGiven() = runTest {
        var reads = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/move")) {
                respondJson(HttpStatusCode.OK, encode(playing(placement = 1)))
            } else {
                reads++
                respondJson(HttpStatusCode.OK, encode(playing()))
            }
        }
        val session = sessionOver(engine)
        session.resume()

        session.play(PvpMove(handIndex = 0, position = 4))

        assertEquals(1, session.match?.placement)
        assertEquals(1, reads, "the accepted move was followed by a needless read")
    }

    /** Being paired ends the queue, whichever of the two routes put the player in a match. */
    @Test
    fun beingPairedEndsTheQueue() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/queue") ->
                    respondJson(HttpStatusCode.OK, """{"waiting":false,"matchId":"$MATCH_ID"}""")

                else -> respondJson(HttpStatusCode.OK, encode(playing()))
            }
        }
        val session = sessionOver(engine)

        session.findMatch()

        assertFalse(session.isQueued)
        assertEquals(MATCH_ID, session.match?.matchId)
    }

    /** Waiting leaves the player queued and in no match. */
    @Test
    fun waitingLeavesThePlayerQueued() = runTest {
        val session = sessionOver(
            answering(HttpStatusCode.OK, """{"waiting":true,"since":1}"""),
        )

        session.findMatch()

        assertTrue(session.isQueued)
        assertNull(session.match)
    }

    /**
     * Leaving the queue takes effect locally even when the server cannot be reached.
     *
     * The same reasoning sign-out uses: the player pressed a button, and leaving them waiting until
     * the network comes back would be a strange answer to it.
     */
    @Test
    fun leavingTheQueueTakesEffectEvenOffline() = runTest {
        val session = sessionOver(
            answering(HttpStatusCode.OK, """{"waiting":true,"since":1}"""),
        )
        session.findMatch()

        val offline = sessionOver(MockEngine { error("connection refused") })
        offline.leaveQueue()

        assertTrue(session.isQueued, "the first session was not queued, so the test proves nothing")
        assertFalse(offline.isQueued)
    }

    /** A finished match reads as over, which is what stops the board accepting taps. */
    @Test
    fun aFinishedMatchReadsAsOver() = runTest {
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(playing(status = PvpMatchStatus.FINISHED))),
        )

        session.resume()

        assertTrue(session.isOver)
    }

    /** With no token, nothing is sent at all — an offline profile has no PvP to poll for. */
    @Test
    fun withNoTokenNothingIsSent() = runTest {
        var asked = false
        val session = sessionOver(
            MockEngine {
                asked = true
                respondJson(HttpStatusCode.OK, encode(playing()))
            },
            token = null,
        )

        session.resume()

        assertFalse(asked, "a request went out with no session behind it")
        assertNull(session.match)
        assertTrue(session.isResumed)
    }

    /** A card id the catalogue does not know refuses the whole view rather than drawing a hole. */
    @Test
    fun anUnknownCardRefusesTheRenderedView() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(playing())))
        session.resume()

        assertNull(session.view(emptyMap()), "a view was rendered from cards nobody has")
    }

    // ---- Helpers ----------------------------------------------------------

    private fun playing(
        placement: Int = 0,
        status: PvpMatchStatus = PvpMatchStatus.PLAYING,
    ) = PvpMatchView(
        matchId = MATCH_ID,
        side = CardColor.BLUE,
        opponentName = "Kuplu",
        rules = GameRules(),
        collection = CardCollection.FF14,
        cells = List(BOARD) { null },
        elements = List(BOARD) { null },
        hand = listOf(257, 258, 259, 260, 261),
        opponentHand = List(HAND) { null },
        first = CardColor.BLUE,
        placement = placement,
        status = status,
    )

    private fun sessionOver(engine: MockEngine, token: String? = "token"): PvpSession {
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        return PvpSession(PvpClient(http, { "http://server" })) { token }
    }

    private fun answering(status: HttpStatusCode, body: String) =
        MockEngine { respondJson(status, body) }

    private fun MockRequestHandleScope.respondJson(
        status: HttpStatusCode,
        body: String,
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun encode(view: PvpMatchView): String =
        json.encodeToString(PvpMatchView.serializer(), view)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private companion object {
        const val MATCH_ID = "m-1"
        const val BOARD = 9
        const val HAND = 5
    }
}
