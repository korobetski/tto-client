package com.tripletriad.ui

import com.tripletriad.model.Capture
import com.tripletriad.model.CaptureKind
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.TradeRule
import com.tripletriad.net.AccountResult
import com.tripletriad.net.PvpClient
import com.tripletriad.protocol.PvpCell
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpMove
import com.tripletriad.protocol.PvpPlay
import com.tripletriad.protocol.PvpRefusal
import com.tripletriad.protocol.PvpStake
import com.tripletriad.protocol.PvpTable
import com.tripletriad.protocol.PvpTableRequest
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PvpSessionTest {

    @Test
    fun resumingFindsTheMatchTheServerIsHolding() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(playing())))

        session.resume()

        assertTrue(session.isResumed)
        assertEquals(MATCH_ID, session.match?.matchId)
        assertFalse(session.isOver)
    }

    @Test
    fun noMatchIsAnAnswerAndNotAFailure() = runTest {
        val session = sessionOver(answering(HttpStatusCode.NoContent, ""))

        session.resume()

        assertTrue(session.isResumed)
        assertNull(session.match)
        assertNull(session.failure, "not being in a match is not a failure")
    }

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

    @Test
    fun anAcceptedMoveTakesTheViewItIsGiven() = runTest {
        var reads = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/move") ->
                    respondJson(HttpStatusCode.OK, encode(playing(placement = 1)))

                // Counted narrowly: `resume` also asks for the claims, and lumping that in would
                // make this assert "two requests happened" rather than "the match was re-read".
                request.url.encodedPath.endsWith("/pvp/match") -> {
                    reads++
                    respondJson(HttpStatusCode.OK, encode(playing()))
                }

                else -> respondJson(HttpStatusCode.OK, "[]")
            }
        }
        val session = sessionOver(engine)
        session.resume()

        session.play(PvpMove(handIndex = 0, position = 4))

        assertEquals(1, session.match?.placement)
        assertEquals(1, reads, "the accepted move was followed by a needless read")
    }

    @Test
    fun joiningATableEntersTheMatch() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/join") -> respondJson(
                    HttpStatusCode.Created,
                    """{"waiting":false,"matchId":"$MATCH_ID"}""",
                )

                else -> respondJson(HttpStatusCode.OK, encode(playing()))
            }
        }
        val session = sessionOver(engine)

        session.join("t-1")

        assertEquals(MATCH_ID, session.match?.matchId)
    }

    @Test
    fun theLobbyIsReadAsAList() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(listOf(table()))))

        session.refreshTables()

        assertEquals(1, session.tables.size)
        assertEquals(WAGER, session.tables.first().stake.mgp)
        assertEquals(TradeRule.ONE, session.tables.first().stake.trade)
    }

    @Test
    fun yourOwnTableIsRecognised() = runTest {
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(listOf(table(host = "Me"), table(id = "t-2")))),
            hostName = "Me",
        )

        session.refreshTables()

        assertEquals("t-1", session.myTable?.id)
    }

    @Test
    fun withdrawingATableTakesEffectEvenOffline() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(listOf(table()))))
        session.refreshTables()
        assertEquals(1, session.tables.size, "the lobby was empty, so the test proves nothing")

        val offline = sessionOver(MockEngine { error("connection refused") })
        offline.cancelTable("t-1")

        assertTrue(offline.tables.isEmpty())
    }

    @Test
    fun aRefusedWithdrawalIsReported() = runTest {
        val offline = sessionOver(MockEngine { error("connection refused") })

        offline.cancelTable("t-1")

        assertTrue(offline.failure != null, "the refusal was swallowed")
    }

    @Test
    fun aFinishedMatchReadsAsOver() = runTest {
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(playing(status = PvpMatchStatus.FINISHED))),
        )

        session.resume()

        assertTrue(session.isOver)
    }

    @Test
    fun aMatchAwaitingAClaimIsOverButNotSettled() = runTest {
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(playing(status = PvpMatchStatus.AWAITING_CLAIM))),
        )

        session.resume()

        assertTrue(session.isOver, "the board is finished")
        assertTrue(session.isAwaitingClaim, "and a choice is owed on it")
        assertFalse(session.isSettled, "so the poll must keep running")
    }

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

    @Test
    fun aSettlementRefreshesTheProfileOnce() = runTest {
        var refreshes = 0
        var over = false
        val engine = MockEngine {
            val view = if (over) playing(status = PvpMatchStatus.FINISHED) else playing()
            respondJson(HttpStatusCode.OK, encode(view))
        }
        val session = sessionOver(engine, onSettled = { refreshes++ })

        session.poll()
        assertEquals(0, refreshes, "a live match refreshed the profile")

        over = true
        session.poll()
        session.poll()

        assertEquals(1, refreshes, "the settlement refreshed the profile $refreshes times")
    }

    @Test
    fun claimingRefreshesTheProfile() = runTest {
        var refreshes = 0
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(playing(status = PvpMatchStatus.FINISHED))),
            onSettled = { refreshes++ },
        )

        session.claim(MATCH_ID, listOf(257))

        assertEquals(1, refreshes)
    }

    @Test
    fun aRefusalArrivesAsACode() = runTest {
        val session = sessionOver(
            answering(
                HttpStatusCode.Conflict,
                """{"code":"CANNOT_AFFORD","reason":"you cannot cover that stake"}""",
            ),
        )

        session.host(PvpTableRequest(formatId = "free-play"))

        val failure = session.failure
        assertTrue(failure is AccountResult.RefusedPvp, "the refusal was lost: $failure")
        assertEquals(PvpRefusal.CANNOT_AFFORD, failure.code)
    }

    @Test
    fun resumingAlsoAsksWhatIsOwed() = runTest {
        val asked = mutableListOf<String>()
        val engine = MockEngine { request ->
            asked += request.url.encodedPath
            respondJson(
                HttpStatusCode.OK,
                if (request.url.encodedPath.endsWith("/claims")) {
                    "[]"
                } else {
                    encode(playing())
                },
            )
        }
        val session = sessionOver(engine)

        session.resume()

        assertTrue(asked.any { it.endsWith("/pvp/match") }, "asked $asked")
        assertTrue(asked.any { it.endsWith("/pvp/claims") }, "asked $asked")
    }

    @Test
    fun aDismissedMatchIsNotPickedUpAgain() = runTest {
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(playing(status = PvpMatchStatus.FINISHED))),
        )
        session.poll()
        assertEquals(MATCH_ID, session.match?.matchId, "there was no match, so this proves nothing")

        session.clear()
        session.poll()

        assertNull(session.match, "the finished match was handed back after being dismissed")
    }

    @Test
    fun aNewMatchArrivesAfterOneWasDismissed() = runTest {
        var id = MATCH_ID
        val session = sessionOver(
            MockEngine { respondJson(HttpStatusCode.OK, encode(playing().copy(matchId = id))) },
        )
        session.poll()
        session.clear()

        id = "m-2"
        session.poll()

        assertEquals("m-2", session.match?.matchId)
    }

    @Test
    fun anUnknownCardRefusesTheRenderedView() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(playing())))
        session.resume()

        assertNull(session.view(emptyMap()), "a view was rendered from cards nobody has")
    }

    @Test
    fun theViewIsAlwaysBlueWhicheverSideTheServerDealt() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(dealtRed())))
        session.resume()

        val view = session.view(catalogue) ?: error("the view did not render")

        assertEquals(CardColor.BLUE, view.side)
        assertTrue(view.ownHand.all { it.owner == CardColor.BLUE }, "own cards were not blue")
        assertTrue(
            view.opponentHand.filterNotNull().all { it.owner == CardColor.RED },
            "the opponent's revealed cards were not red",
        )
        // The cell the server says red — this player — holds comes back blue. `PlacedCard.owner`
        // is the one asserted because it is the one that decides the colour: `BoardCard` overrides
        // the `Card` inside with it before drawing.
        val mine = view.board.cells[0] ?: error("the placed card was dropped")
        assertEquals(CardColor.BLUE, mine.owner)
        val theirs = view.board.cells[1] ?: error("the placed card was dropped")
        assertEquals(CardColor.RED, theirs.owner)
    }

    @Test
    fun mirroringKeepsTheTurnAndTheScoreThisPlayersOwn() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(dealtRed())))
        session.resume()

        val view = session.view(catalogue) ?: error("the view did not render")

        assertTrue(view.isMyTurn, "the mirrored view handed this player's turn to the opponent")
        // Two cards on the board, one each, and four left in each hand: 5 — 5, told from this
        // player's side because `side` is now blue.
        assertEquals(5, view.score.blue)
        assertEquals(5, view.score.red)
    }

    @Test
    fun aBlueViewIsNotMirrored() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(playing())))
        session.resume()

        val view = session.view(catalogue) ?: error("the view did not render")

        assertEquals(CardColor.BLUE, view.side)
        assertEquals(CardColor.BLUE, view.order.first)
    }

    // ---- Helpers ----------------------------------------------------------

    @Test
    fun theLastPlacementIsMirroredWithTheRestOfTheView() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(dealtRed())))
        session.resume()

        val view = session.view(catalogue) ?: error("the view did not render")
        val play = view.lastPlay ?: error("the last play was dropped")

        // The server said RED played it, and red is this player — so they see themselves as blue.
        assertEquals(CardColor.BLUE, play.player)
        assertEquals(CardColor.BLUE, play.card.owner)
        assertEquals(listOf(CaptureKind.SAME), play.captures.map { it.kind })
    }

    private fun dealtRed() = PvpMatchView(
        matchId = MATCH_ID,
        side = CardColor.RED,
        opponentName = "Kuplu",
        rules = GameRules(),
        formatId = "ff14",
        cells = List(BOARD) { index ->
            when (index) {
                0 -> PvpCell(cardId = 257, owner = CardColor.RED)
                1 -> PvpCell(cardId = 258, owner = CardColor.BLUE)
                else -> null
            }
        },
        elements = List(BOARD) { null },
        hand = listOf(259, 260, 261, 262),
        // One revealed, as Three Open would leave it, so the opponent's colour is assertable too.
        opponentHand = listOf(263, null, null, null),
        first = CardColor.RED,
        placement = 2,
        lastPlay = PvpPlay(
            player = CardColor.RED,
            cardId = 257,
            position = 0,
            captures = listOf(Capture(position = 1, kind = CaptureKind.SAME, wave = 0)),
        ),
    )

    private val catalogue: Map<Int, Card> = (257..263).associateWith { id ->
        Card(id = id, nameKey = "STR_FF14_CARD_$id", name = "Card $id", 1, 2, 3, 4, rarity = 1)
    }

    private fun playing(
        placement: Int = 0,
        status: PvpMatchStatus = PvpMatchStatus.PLAYING,
    ) = PvpMatchView(
        matchId = MATCH_ID,
        side = CardColor.BLUE,
        opponentName = "Kuplu",
        rules = GameRules(),
        formatId = "ff14",
        cells = List(BOARD) { null },
        elements = List(BOARD) { null },
        hand = listOf(257, 258, 259, 260, 261),
        opponentHand = List(HAND) { null },
        first = CardColor.BLUE,
        placement = placement,
        status = status,
    )

    private fun sessionOver(
        engine: MockEngine,
        token: String? = "token",
        hostName: String = "",
        onSettled: suspend () -> Unit = {},
    ): PvpSession {
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        return PvpSession(
            client = PvpClient(http, { "http://server" }),
            tokenOf = { token },
            hostName = hostName,
            onSettled = onSettled,
        )
    }

    private fun table(id: String = "t-1", host: String = "Kuplu") = PvpTable(
        id = id,
        hostName = host,
        formatId = "free-play",
        stake = PvpStake(mgp = WAGER, trade = TradeRule.ONE),
        openedAt = 0,
        expiresAt = 1,
    )

    private fun encode(tables: List<PvpTable>): String =
        json.encodeToString(ListSerializer(PvpTable.serializer()), tables)

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
        const val WAGER = 50
        const val BOARD = 9
        const val HAND = 5
    }
}
