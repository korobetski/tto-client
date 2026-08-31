package com.tripletriad.ui

import com.tripletriad.model.GameSave
import com.tripletriad.net.AccountResult
import com.tripletriad.net.AuctionClient
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.protocol.AuctionDuration
import com.tripletriad.protocol.AuctionLot
import com.tripletriad.protocol.AuctionOutcome
import com.tripletriad.protocol.AuctionPage
import com.tripletriad.protocol.AuctionRefusal
import com.tripletriad.protocol.AuctionStatus
import com.tripletriad.protocol.BidRequest
import com.tripletriad.protocol.PlayerState
import com.tripletriad.time.FixedClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The auction house as state, with the network stubbed underneath it.
 *
 * Four claims, and everything here is one of them:
 *
 * * **the countdown is the server's clock**, not this device's — `remaining` is the only place the
 *   correction happens, and a device with a wrong clock is the ordinary case rather than an exotic
 *   one;
 * * **a refusal and a failure are different fields**, because only one of them is worth a retry and
 *   only one of them arrives with a fresh profile beside it;
 * * **a write folds its lot back into the lists** rather than triggering a re-read, so the price
 *   the player just moved is on screen in the same frame the button comes back;
 * * **a failed read leaves the lists alone**. A poll that could not reach the house has not learnt
 *   that the board is empty.
 *
 * `watch()` is deliberately not exercised: it is a `while (true)` around `refresh`, and the loop
 * body is what every test here is about.
 */
class AuctionSessionTest {

    // ---- The clock is the server's ----------------------------------------

    /**
     * A phone half a minute fast still draws a full hour.
     *
     * This is the whole reason [AuctionPage.now] is on the wire. The paired assertion is the point:
     * before a read there is nothing to correct against and the countdown is thirty seconds short,
     * which is precisely the bug a player would report as the lot having closed early.
     */
    @Test
    fun theCountdownIsCorrectedByWhatTheHousesClockSaid() = runTest {
        val session = sessionOn(serving(page(lot())))

        assertEquals(AN_HOUR - SKEW, session.remaining(lot(), DEVICE_NOW))
        session.refreshBoard()
        assertEquals(AN_HOUR, session.remaining(lot(), DEVICE_NOW))
    }

    @Test
    fun aLotAlreadyPastIsZeroAndNeverANegativeCountdown() = runTest {
        val session = sessionOn(serving(page()))
        session.refreshBoard()

        assertEquals(0L, session.remaining(lot(endsAt = SERVER_NOW - AN_HOUR), DEVICE_NOW))
    }

    // ---- Reading ----------------------------------------------------------

    /**
     * The desk opens on something rather than on nothing.
     *
     * A wide screen shows the pane whether or not a lot is picked, and an empty pane beside a full
     * board is a screen asking the player to do something before it will tell them anything.
     */
    @Test
    fun theFirstReadPicksTheFirstLotAndLaterReadsLeaveThePickAlone() = runTest {
        val session = sessionOn(serving(page(lot(), lot(id = OTHER))))

        session.refreshBoard()
        assertEquals(LOT, session.selectedId)

        session.select(OTHER)
        session.refreshBoard()
        assertEquals(OTHER, session.selectedId, "a poll moved the pane under the player")
    }

    @Test
    fun bothListsAreReadAndEachRemembersItsOwnState() = runTest {
        val session = sessionOn(serving(page(lot())))

        session.refresh()

        assertEquals(listOf(LOT), session.board.map { it.id })
        assertEquals(listOf(LOT), session.mine.map { it.id })
        assertEquals(ListState.READY, session.boardState)
        assertEquals(ListState.READY, session.mineState)
    }

    /**
     * A read that failed is not a board that emptied.
     *
     * The failure lands on the list's own state rather than on [AuctionSession.failure] — this
     * polls, and a note per poll would bury the screen under the same sentence every five seconds.
     */
    @Test
    fun aFailedReadIsAStateAndNotAnEmptyBoard() = runTest {
        val session = sessionOn(sequence(page(lot()), page(lot()), "", ""))
        session.refresh()

        session.refresh()

        assertEquals(listOf(LOT), session.board.map { it.id }, "a dead poll emptied the board")
        assertEquals(listOf(LOT), session.mine.map { it.id })
        assertEquals(ListState.FAILED, session.boardState)
        assertEquals(ListState.FAILED, session.mineState)
        assertNull(session.failure, "a poll spoke over the screen")
    }

    /** No token is no request: nothing to say, and nothing to blame either. */
    @Test
    fun withoutATokenNothingIsAskedAndNothingIsBlamed() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val session = sessionOn(serving(page(lot()), record = { seen += it }), token = null)

        session.refresh()
        session.bid(LOT, 500)

        assertTrue(seen.isEmpty(), "a request went out unauthenticated")
        assertEquals(ListState.LOADING, session.boardState)
        assertNull(session.failure)
    }

    // ---- A refusal is not a failure ---------------------------------------

    @Test
    fun beingOutbidIsARefusalAndNotSomethingToRetry() = runTest {
        val session = sessionOn(serving(outcome(refusal = AuctionRefusal.BID_TOO_LOW)))

        session.bid(LOT, 1)

        assertEquals(AuctionRefusal.BID_TOO_LOW, session.refusal)
        assertNull(session.failure)
        assertFalse(session.isBusy, "the button never came back")
    }

    @Test
    fun aHouseThatCannotBeReachedIsAFailureAndNotARefusal() = runTest {
        val session = sessionOn(serving("", HttpStatusCode.BadGateway))

        session.bid(LOT, 500)

        assertNull(session.refusal)
        assertTrue(session.failure is AccountResult.Failed)
        assertFalse(session.isBusy)
    }

    /** A refusal answers the question that was asked; picking another lot asks a new one. */
    @Test
    fun changingLotClearsTheAnswerToTheOldOne() = runTest {
        val session = sessionOn(serving(outcome(refusal = AuctionRefusal.CANNOT_AFFORD)))
        session.bid(LOT, 500)

        session.select(OTHER)

        assertNull(session.refusal)
    }

    @Test
    fun aSecondAttemptStartsWithTheFirstOnesVerdictCleared() = runTest {
        val session = sessionOn(
            sequence(outcome(refusal = AuctionRefusal.BID_TOO_LOW), outcome(lot(topBid = 500))),
        )
        session.bid(LOT, 1)

        session.bid(LOT, 500)

        assertNull(session.refusal)
        assertEquals(500, session.selected?.topBid)
    }

    // ---- What a write does to the lists -----------------------------------

    @Test
    fun aBidThatStandsMovesThePriceInPlaceAndFollowsTheLot() = runTest {
        val session = sessionOn(
            sequence(page(lot(), lot(id = OTHER)), page(), outcome(lot(topBid = 525))),
        )
        session.refresh()
        session.select(OTHER)

        session.bid(LOT, 525)

        assertEquals(listOf(LOT, OTHER), session.board.map { it.id }, "the board was reordered")
        assertEquals(525, session.board.first().topBid)
        assertEquals(LOT, session.selectedId, "the pane did not follow the bid")
    }

    /**
     * A lot this player now leads joins the list they watch.
     *
     * `mine` is not "lots I opened", it is "lots I have something in" — which is what makes the tab
     * the place a bidder reads the last two minutes from.
     */
    @Test
    fun aLotBidOnJoinsTheListThePlayerWatches() = runTest {
        val session = sessionOn(sequence(page(lot()), page(), outcome(lot(topBid = 525))))
        session.refresh()
        assertTrue(session.mine.isEmpty())

        session.bid(LOT, 525)

        assertEquals(listOf(LOT), session.mine.map { it.id })
    }

    /**
     * A lot that finished leaves the room but stays where its owner can read it.
     *
     * Vanishing from both lists is how "my lot sold while I was looking at it" becomes a row that
     * disappears mid-glance with nothing said.
     */
    @Test
    fun aFinishedLotLeavesTheBoardAndStaysInTheOwnersList() = runTest {
        val session = sessionOn(
            sequence(
                page(lot(yours = true)),
                page(lot(yours = true)),
                outcome(lot(yours = true, status = AuctionStatus.CANCELLED)),
            ),
        )
        session.refresh()

        session.withdraw(LOT)

        assertTrue(session.board.isEmpty(), "a settled lot is still taking bids")
        assertEquals(AuctionStatus.CANCELLED, session.mine.single().status)
        assertEquals(LOT, session.selectedId)
    }

    /** The pane reads whichever list holds the lot: a finished one is only in the second. */
    @Test
    fun theOpenPaneReadsFromEitherList() = runTest {
        val session = sessionOn(
            sequence(page(), page(lot(yours = true, status = AuctionStatus.SOLD))),
        )
        session.refresh()

        session.select(LOT)

        assertEquals(AuctionStatus.SOLD, session.selected?.status)
    }

    @Test
    fun aLotWaitingOnThisSellersDecisionIsWhatBadgesTheTab() = runTest {
        val session = sessionOn(
            serving(
                page(
                    lot(yours = true, status = AuctionStatus.AWAITING_SELLER),
                    lot(id = OTHER, status = AuctionStatus.AWAITING_SELLER),
                    lot(id = THIRD, yours = true),
                ),
            ),
        )

        session.refreshMine()

        assertEquals(listOf(LOT), session.awaitingMe.map { it.id })
    }

    // ---- The profile and the operation id ---------------------------------

    /** The client is told what it now owns rather than working it out; see [AuctionOutcome]. */
    @Test
    fun theProfileTheHouseWroteIsHandedOnOnce() = runTest {
        val adopted = mutableListOf<PlayerState>()
        val session = sessionOn(serving(outcome(lot())), onProfile = { adopted += it })

        session.bid(LOT, 525)

        assertEquals(1, adopted.size)
        assertEquals("Kuplu", adopted.single().save.username)
    }

    @Test
    fun aFailedWriteAdoptsNoProfile() = runTest {
        val adopted = mutableListOf<PlayerState>()
        val session = sessionOn(
            serving("", HttpStatusCode.BadGateway),
            onProfile = { adopted += it },
        )

        session.bid(LOT, 525)

        assertTrue(adopted.isEmpty())
    }

    /**
     * Two presses are two decisions, and they must not be idempotent with each other.
     *
     * The id exists to make a request *the network* delivered twice settle once. A player who bids
     * twice on purpose has asked for two bids, and an id minted per lot instead of per press would
     * silently swallow the second.
     */
    @Test
    fun everyPressCarriesItsOwnOperationId() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val session = sessionOn(serving(outcome(lot()), record = { seen += it }))

        session.bid(LOT, 525)
        session.bid(LOT, 550)

        val ids = seen.map { operationIdOf(it) }
        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1], "two presses shared an operation id")
        assertTrue(ids.all { it.isNotEmpty() })
    }

    /** All four writes go through the one guard, so all four have to report the same wall. */
    @Test
    fun everyWriteReportsTheSameFailureTheSameWay() = runTest {
        val session = sessionOn(serving("", HttpStatusCode.BadGateway))

        session.listCard(1, 100, 100, AuctionDuration.SHORT)
        assertTrue(session.failure is AccountResult.Failed, "listing said nothing")

        session.withdraw(LOT)
        assertTrue(session.failure is AccountResult.Failed, "withdrawing said nothing")

        session.decide(LOT, accept = true)
        assertTrue(session.failure is AccountResult.Failed, "accepting said nothing")

        session.decide(LOT, accept = false)
        assertTrue(session.failure is AccountResult.Failed, "declining said nothing")
    }

    // ---- Fixtures ---------------------------------------------------------

    private suspend fun operationIdOf(request: HttpRequestData): String =
        matchProtocolJson.decodeFromString(
            BidRequest.serializer(),
            request.body.toByteArray().decodeToString(),
        ).operationId

    private fun sessionOn(
        engine: MockEngine,
        token: String? = TOKEN,
        onProfile: suspend (PlayerState) -> Unit = {},
    ) = AuctionSession(
        client = AuctionClient(
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json(matchProtocolJson) }
            },
            { "https://example.invalid" },
        ),
        tokenOf = { token },
        clock = FixedClock(DEVICE_NOW),
        onProfile = onProfile,
    )

    private fun serving(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        record: (HttpRequestData) -> Unit = {},
    ) = MockEngine { request ->
        record(request)
        respond(content = body, status = status, headers = jsonHeaders)
    }

    /** Answers each call from the list in turn, then repeats the last. An empty body is a wall. */
    private fun sequence(vararg bodies: String): MockEngine {
        var call = 0
        return MockEngine {
            val body = bodies[minOf(call++, bodies.lastIndex)]
            respond(
                content = body,
                status = if (body.isEmpty()) HttpStatusCode.BadGateway else HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
    }

    private fun lot(
        id: String = LOT,
        status: AuctionStatus = AuctionStatus.OPEN,
        yours: Boolean = false,
        endsAt: Long = SERVER_NOW + AN_HOUR,
        topBid: Int? = null,
    ) = AuctionLot(
        id = id,
        cardId = 1,
        startPrice = 100,
        endsAt = endsAt,
        status = status,
        topBid = topBid,
        yours = yours,
    )

    private fun page(vararg lots: AuctionLot) = matchProtocolJson.encodeToString(
        AuctionPage.serializer(),
        AuctionPage(lots.toList(), SERVER_NOW),
    )

    private fun outcome(
        lot: AuctionLot? = null,
        refusal: AuctionRefusal? = null,
    ) = matchProtocolJson.encodeToString(
        AuctionOutcome.serializer(),
        AuctionOutcome(
            player = PlayerState(save = GameSave.new(username = "Kuplu", createdAt = 0L)),
            lot = lot,
            refusal = refusal,
        ),
    )

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    private companion object {
        const val TOKEN = "a-token"
        const val LOT = "lot-1"
        const val OTHER = "lot-2"
        const val THIRD = "lot-3"

        const val AN_HOUR = 3_600_000L
        const val SERVER_NOW = 1_770_000_000_000L

        /** This device runs half a minute fast: the ordinary case, not an exotic one. */
        const val SKEW = 30_000L
        const val DEVICE_NOW = SERVER_NOW + SKEW
    }
}
