package com.tripletriad.ui

import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchResult
import com.tripletriad.net.AccountResult
import com.tripletriad.net.PveClient
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.PveFailure
import com.tripletriad.protocol.PveMatchStatus
import com.tripletriad.protocol.PveMatchView
import com.tripletriad.protocol.PveMove
import com.tripletriad.protocol.PveOutcome
import com.tripletriad.protocol.PveRefusal
import com.tripletriad.protocol.RewardSummary
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The small state machine a refereed solo match leaves on this side.
 *
 * Three claims are worth having and the rest follows from them:
 *
 * * a request that fails **changes nothing** — the match stays where the server has it, which is
 *   what "a dropped connection is not an abandon" means from here;
 * * a *refused* move re-reads the board, because a refusal says this client's idea of the position
 *   is wrong and the server's is the one worth having;
 * * a settled match hands the credited profile on **once**, and it is adopted rather than added up.
 */
class PveSessionTest {

    // ---- Opening and resuming ---------------------------------------------

    @Test
    fun openingTakesTheServersBoardAsItStands() = runTest {
        val session = sessionAnswering(HttpStatusCode.Created, encode(board))

        session.open(OPPONENT, FORMAT)

        assertEquals(MATCH_ID, session.match?.matchId)
        assertNull(session.trouble)
    }

    /**
     * A refusal to open is trouble to report, not a board to draw.
     *
     * The screen shows the reconnection panel on [PveSession.trouble] and a board on
     * [PveSession.match]; a failure that set neither would leave the player on a spinner with no
     * way forward, which is the state this whole class exists to make impossible.
     */
    @Test
    fun anOpeningTheServerRefusesLeavesTroubleAndNoBoard() = runTest {
        val session = sessionAnswering(HttpStatusCode.InternalServerError, "")

        session.open(OPPONENT, FORMAT)

        assertNull(session.match, "there is no board to show")
        assertNotNull(session.trouble, "and the panel needs something to offer a retry for")
    }

    /** Nothing in progress is an answer, and it must not look like a failure. */
    @Test
    fun resumingWithNothingInProgressLeavesNoTroubleBehind() = runTest {
        val session = sessionAnswering(HttpStatusCode.NoContent, "")

        session.resume()

        assertNull(session.match)
        assertNull(session.trouble, "having no match in progress is not something to reconnect for")
    }

    @Test
    fun resumingPicksUpTheMatchTheServerStillHas() = runTest {
        val session = sessionAnswering(HttpStatusCode.OK, encode(board.copy(placement = 4)))

        session.resume()

        assertEquals(4, session.match?.placement, "the board came back where it was left")
    }

    /**
     * **The board the player just won is not the board they are asking for.**
     *
     * `GET /pve/matches/active` keeps a settled match findable for a couple of minutes on purpose,
     * so a player killed between placing the ninth card and reading the answer is still shown the
     * result they were credited for (`PveStore.recentFor`). A board asking "am I in the middle of a
     * match against *this* opponent" must not take that for a yes — the reported symptom was
     * finishing a match, walking back to the roster, challenging somebody else, and being handed an
     * instant win on the previous opponent's full board.
     */
    @Test
    fun aMatchAlreadySettledIsNotResumedOntoTheNextOpponentsBoard() = runTest {
        val settled = board.copy(placement = 9, status = PveMatchStatus.FINISHED)
        val session = sessionAnswering(HttpStatusCode.OK, encode(settled))

        session.resume(against = OPPONENT)

        assertNull(session.match, "a finished match is not a match in progress")
        assertNull(session.trouble, "and it is not something to reconnect for either")
    }

    /** One live match at a time, but the live one may belong to a board the player has left. */
    @Test
    fun aLiveMatchAgainstSomebodyElseIsNotResumedEither() = runTest {
        val session = sessionAnswering(HttpStatusCode.OK, encode(board.copy(placement = 3)))

        session.resume(against = "another-opponent")

        assertNull(session.match, "that board belongs to the opponent it was opened against")
    }

    /** And the case the narrowing must not break: the match this board actually asked about. */
    @Test
    fun aLiveMatchAgainstThisOpponentIsResumedAsBefore() = runTest {
        val session = sessionAnswering(HttpStatusCode.OK, encode(board.copy(placement = 3)))

        session.resume(against = OPPONENT)

        assertEquals(3, session.match?.placement, "the board came back where it was left")
    }

    @Test
    fun aResumeThatFailsIsReportedRatherThanReadAsHavingNoMatch() = runTest {
        val session = sessionAnswering(HttpStatusCode.InternalServerError, "")

        session.resume()

        assertNull(session.match)
        assertNotNull(
            session.trouble,
            "a failed read is not the same answer as `you are in no match`",
        )
    }

    // ---- Placing a card ---------------------------------------------------

    @Test
    fun aPlacementAdoptsTheExchangeThatComesBack() = runTest {
        val session = sessionAnswering(HttpStatusCode.OK, encode(board.copy(placement = 2)))
        session.resume()

        session.play(PveMove(handIndex = 0, position = 0))

        assertEquals(2, session.match?.placement)
    }

    /**
     * **Losing the connection on a move leaves the match exactly where it was.**
     *
     * And it is the one case that does *not* re-read: the request reached the server or it did not,
     * and nobody here knows which. The board is left alone and the player is offered a way back —
     * `PveMatchScreen` shows the reconnection panel on [PveSession.trouble] and nothing else.
     */
    @Test
    fun aLostConnectionOnAMoveKeepsTheBoardAndOffersAWayBack() = runTest {
        // Reads answer, placements do not: the connection goes while the match is in progress.
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/moves")) throw IOException("no route")
            respondJson(encode(board.copy(placement = 4)), HttpStatusCode.OK)
        }
        val session = PveSession(PveClient(httpClient(engine), address), tokenOf = { TOKEN })
        session.resume()
        val before = session.match

        session.play(PveMove(handIndex = 0, position = 0))

        assertEquals(before, session.match, "the board must not be discarded on a failed request")
        assertIs<AccountResult.Offline>(session.trouble)
    }

    /**
     * A refusal is not argued with: the board is re-read.
     *
     * The refusal itself is not kept as trouble, because there is nothing for the player to do
     * about it — the answer is a board that says what the position really is.
     */
    @Test
    fun aRefusedMoveReReadsTheBoardInsteadOfReportingIt() = runTest {
        var reads = 0
        val refusal = matchProtocolJson.encodeToString(
            PveFailure.serializer(),
            PveFailure(PveRefusal.NOT_YOUR_TURN, "the board is waiting"),
        )
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/moves")) {
                respondJson(refusal, HttpStatusCode.Conflict)
            } else {
                reads++
                respondJson(encode(board.copy(placement = 6)), HttpStatusCode.OK)
            }
        }
        val session = PveSession(PveClient(httpClient(engine), address), tokenOf = { TOKEN })
        session.resume()
        val readsBeforeTheMove = reads

        session.play(PveMove(handIndex = 0, position = 0))

        assertEquals(readsBeforeTheMove + 1, reads, "a refusal should re-read the match")
        assertEquals(6, session.match?.placement, "and take what the re-read says")
    }

    /**
     * A move needs a match to be a move *in*.
     *
     * The screen cannot normally place without a board — it returns early and draws [PveWaiting]
     * instead — but a queued tap arriving after [clear] can, and posting a placement with no match
     * id would be a request the server can only refuse.
     */
    @Test
    fun placingWithNoBoardAsksNothing() = runTest {
        val counted = countingEngine()
        val session =
            PveSession(PveClient(httpClient(counted.engine), address), tokenOf = { TOKEN })

        session.play(PveMove(handIndex = 0, position = 0))

        assertEquals(0, counted.calls, "there is no match to place into")
        assertNull(session.match)
    }

    @Test
    fun placingWhileSignedOutAsksNothing() = runTest {
        val counted = countingEngine()
        val session = PveSession(PveClient(httpClient(counted.engine), address), tokenOf = { null })

        session.play(PveMove(handIndex = 0, position = 0))

        assertEquals(0, counted.calls, "a signed-out session must not talk to the server")
    }

    // ---- Re-reading the board ---------------------------------------------

    /**
     * [PveSession.refresh] is the recovery from anything that went wrong, and it asks **by id**.
     *
     * That is what makes it the right call on a board and [PveSession.resume] the wrong one: it
     * cannot come back with a different match. With no id there is nothing it could ask.
     */
    @Test
    fun reReadingWithNoBoardAsksNothing() = runTest {
        val counted = countingEngine()
        val session =
            PveSession(PveClient(httpClient(counted.engine), address), tokenOf = { TOKEN })

        session.refresh()

        assertEquals(0, counted.calls, "there is no match id to ask about")
    }

    @Test
    fun reReadingWhileSignedOutAsksNothing() = runTest {
        val counted = countingEngine()
        val session = PveSession(PveClient(httpClient(counted.engine), address), tokenOf = { null })

        session.refresh()

        assertEquals(0, counted.calls)
    }

    /** The claim the reconnection panel rests on: a failed re-read is not a lost match. */
    @Test
    fun aReReadThatFailsKeepsTheBoardItAlreadyHad() = runTest {
        val session = PveSession(
            client = PveClient(
                httpClient(answeringThenFailing(encode(board.copy(placement = 5)))),
                address,
            ),
            tokenOf = { TOKEN },
        )
        session.resume()
        val before = session.match
        assertNotNull(before, "the fixture should have put a board up first")

        session.refresh()

        assertEquals(before, session.match, "a failed re-read must not discard the position")
        assertNotNull(session.trouble, "and the player is offered the way back")
    }

    // ---- Settlement -------------------------------------------------------

    /**
     * The credited profile is handed on, once, and only when the match settles.
     *
     * The client replaces what it holds with it rather than adding anything up — two copies of a
     * profile and a window in which they disagree is what an item that never reaches the bag looks
     * like from the inside.
     */
    @Test
    fun aSettledMatchHandsOnTheProfileTheServerWrote() = runTest {
        val credited = mutableListOf<PlayerState>()
        val settled = board.copy(
            status = PveMatchStatus.FINISHED,
            outcome = PveOutcome(
                result = MatchResult.WIN,
                blue = 6,
                red = 4,
                reward = RewardSummary(result = MatchResult.WIN, mgp = 120, xp = 30),
                player = PlayerState(save = GameSave(username = "winner")),
            ),
        )
        val session = PveSession(
            client = clientAnswering(HttpStatusCode.OK, encode(settled)),
            tokenOf = { TOKEN },
            onCredited = { credited += it },
        )
        session.resume()

        session.play(PveMove(handIndex = 0, position = 0))

        assertEquals(1, credited.size, "the profile should be adopted exactly once")
        assertEquals("winner", credited.single().save.username)
        assertTrue(session.isOver)
    }

    @Test
    fun anUnfinishedMatchCreditsNobody() = runTest {
        val credited = mutableListOf<PlayerState>()
        val session = PveSession(
            client = clientAnswering(HttpStatusCode.OK, encode(board)),
            tokenOf = { TOKEN },
            onCredited = { credited += it },
        )

        session.resume()
        session.play(PveMove(handIndex = 0, position = 0))

        assertTrue(credited.isEmpty())
        assertTrue(!session.isOver)
    }

    /**
     * Nothing has begun, so nothing has ended.
     *
     * [PveSession.isOver] gates the way off the board. Reading "no match" as over would end a
     * screen that has not started one — the state every launch passes through.
     */
    @Test
    fun aSessionWithNoBoardIsNotOver() = runTest {
        val session = sessionAnswering(HttpStatusCode.OK, encode(board))

        assertFalse(session.isOver, "there is no match, so there is no match to be over")
    }

    /** A match swept without being played is over, and is not a result. */
    @Test
    fun anAbandonedMatchIsOverWithoutBeingSettled() = runTest {
        val abandoned = board.copy(status = PveMatchStatus.ABANDONED)
        val session = sessionAnswering(HttpStatusCode.OK, encode(abandoned))

        session.resume()

        assertTrue(session.isOver, "it is no longer live")
        assertNull(session.match?.outcome, "and nothing was credited for it")
    }

    // ---- Signing out ------------------------------------------------------

    /** Without a token there is nothing to ask, and nothing is asked. */
    @Test
    fun noTokenMeansNoRequestRatherThanAnUnauthenticatedOne() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respondJson(encode(board), HttpStatusCode.OK)
        }
        val session = PveSession(PveClient(httpClient(engine), address), tokenOf = { null })

        session.resume()
        session.open(OPPONENT, FORMAT)

        assertEquals(0, calls, "a signed-out session must not talk to the server")
        assertNull(session.match)
    }

    @Test
    fun clearingForgetsTheBoardWithoutTouchingTheServer() = runTest {
        val session = sessionAnswering(HttpStatusCode.OK, encode(board))
        session.resume()
        assertNotNull(session.match)

        session.clear()

        assertNull(session.match)
        assertNull(session.trouble)
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun httpClient(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(matchProtocolJson) }
    }

    /** An engine that counts what it was asked, for the cases where the answer is "nothing". */
    private class Counted(val engine: MockEngine) {
        var calls: Int = 0
    }

    private fun countingEngine(): Counted {
        lateinit var counted: Counted
        counted = Counted(
            MockEngine {
                counted.calls++
                respondJson(encode(board), HttpStatusCode.OK)
            },
        )
        return counted
    }

    /**
     * Answers once and fails afterwards — "it was working, and then the connection went".
     *
     * The first answer is what puts a board up; everything after it is the failure under test, so
     * the assertion can be about what survives rather than about what was never there.
     */
    private fun answeringThenFailing(first: String): MockEngine {
        var answered = false
        return MockEngine {
            if (answered) {
                respondJson("", HttpStatusCode.InternalServerError)
            } else {
                answered = true
                respondJson(first, HttpStatusCode.OK)
            }
        }
    }

    private fun clientAnswering(status: HttpStatusCode, body: String): PveClient =
        PveClient(httpClient(MockEngine { respondJson(body, status) }), address)

    private fun sessionAnswering(status: HttpStatusCode, body: String): PveSession =
        PveSession(clientAnswering(status, body), tokenOf = { TOKEN })

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private fun encode(sent: PveMatchView) =
        matchProtocolJson.encodeToString(PveMatchView.serializer(), sent)

    private val address: suspend () -> String = { "https://example.invalid" }

    private val board = PveMatchView(
        matchId = MATCH_ID,
        opponentIconId = OPPONENT,
        rules = GameRules(),
        formatId = FORMAT,
        cells = List(BOARD_CELLS) { null },
        elements = List(BOARD_CELLS) { null },
        hand = emptyList(),
        opponentHand = emptyList(),
        first = CardColor.BLUE,
        placement = 0,
    )

    private companion object {
        const val TOKEN = "a-token"
        const val MATCH_ID = "a-match"
        const val OPPONENT = "an-opponent"
        const val FORMAT = "ff14"
        const val BOARD_CELLS = 9
    }
}
