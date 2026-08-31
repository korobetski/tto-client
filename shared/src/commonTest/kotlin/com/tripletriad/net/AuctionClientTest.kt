package com.tripletriad.net

import com.tripletriad.model.GameSave
import com.tripletriad.protocol.AccountError
import com.tripletriad.protocol.AccountFailure
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.AuctionDuration
import com.tripletriad.protocol.AuctionLot
import com.tripletriad.protocol.AuctionLotRequest
import com.tripletriad.protocol.AuctionOutcome
import com.tripletriad.protocol.AuctionPage
import com.tripletriad.protocol.AuctionRefusal
import com.tripletriad.protocol.BidRequest
import com.tripletriad.protocol.ListCardRequest
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The seven calls the house is made of.
 *
 * Built on [PveClientTest]'s shape, because the clients are deliberately the same shape. What is
 * particular here — and what most of this file is about — is that **a refusal is a `200`**. Being
 * outbid while typing is the normal ending of a bid, not a fault, so it arrives as a field inside
 * [AuctionOutcome] beside a fresh profile. A client that read it as a status code would show a
 * player an error where the house showed them a price.
 */
class AuctionClientTest {

    // ---- Reading ----------------------------------------------------------

    @Test
    fun theBoardIsReadWholeAndNarrowedToOneCard() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val client = answering(HttpStatusCode.OK, encode(page), record = { seen += it })

        assertEquals(LOT, client.browse(TOKEN).valueOrNull()?.lots?.single()?.id)
        client.browse(TOKEN, cardId = 42)

        assertEquals("/auctions", seen[0].url.encodedPath)
        assertEquals(null, seen[0].url.parameters["card"])
        assertEquals("42", seen[1].url.parameters["card"])
        assertTrue(seen.all { it.method == HttpMethod.Get }, "reading is not a write")
    }

    @Test
    fun aPlayersOwnLotsAreADifferentAddress() = runTest {
        var seen: HttpRequestData? = null
        answering(HttpStatusCode.OK, encode(page), record = { seen = it }).mine(TOKEN)

        assertEquals("/auctions/mine", seen?.url?.encodedPath)
    }

    /**
     * The server's clock travels with the lots.
     *
     * Every lot is a deadline, and a deadline read against this device's clock is a countdown that
     * disagrees with the house — see `AuctionSession.remaining`, which is the only place it is
     * corrected and which has nothing to correct against if this field is dropped.
     */
    @Test
    fun aPageCarriesTheHousesOwnTimeAlongWithTheLots() = runTest {
        val read = answering(HttpStatusCode.OK, encode(page)).browse(TOKEN)

        assertEquals(NOW, assertIs<AccountResult.Ok<AuctionPage>>(read).value.now)
    }

    // ---- Writing ----------------------------------------------------------

    @Test
    fun eachWriteGoesToItsOwnAddressAsAPost() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val client = answering(HttpStatusCode.OK, encode(outcome()), record = { seen += it })

        client.list(TOKEN, ListCardRequest(1, 100, 100, AuctionDuration.SHORT, "op"))
        client.bid(TOKEN, BidRequest(LOT, 500, "op"))
        client.withdraw(TOKEN, AuctionLotRequest(LOT, "op"))
        client.accept(TOKEN, AuctionLotRequest(LOT, "op"))
        client.decline(TOKEN, AuctionLotRequest(LOT, "op"))

        assertEquals(
            listOf(
                "/auctions",
                "/auctions/bid",
                "/auctions/cancel",
                "/auctions/accept",
                "/auctions/decline",
            ),
            seen.map { it.url.encodedPath },
        )
        assertTrue(seen.all { it.method == HttpMethod.Post }, "a write went out as a read")
    }

    /**
     * **The operation id is on the wire, not just in the client's head.**
     *
     * It is what makes a request the network delivered twice settle once — the server's
     * `applied_operations` is the other half. A client that minted one and did not send it would
     * pass every test about greyed-out buttons and still double-charge a player on a flaky link.
     */
    @Test
    fun aBidCarriesTheOperationItIsIdempotentUnder() = runTest {
        var seen: HttpRequestData? = null
        val client = answering(HttpStatusCode.OK, encode(outcome()), record = { seen = it })

        client.bid(TOKEN, BidRequest(LOT, 525, "a-press"))

        val body = seen?.body?.toByteArray()?.decodeToString().orEmpty()
        assertEquals(
            BidRequest(LOT, 525, "a-press"),
            json.decodeFromString(BidRequest.serializer(), body),
        )
    }

    // ---- A refusal is not a failure ---------------------------------------

    @Test
    fun beingOutbidComesBackAsSuccessCarryingTheReason() = runTest {
        val refused = encode(outcome(refusal = AuctionRefusal.BID_TOO_LOW))
        val client = answering(HttpStatusCode.OK, refused)

        val result = assertIs<AccountResult.Ok<AuctionOutcome>>(
            client.bid(TOKEN, BidRequest(LOT, 1, "op")),
        )
        assertEquals(AuctionRefusal.BID_TOO_LOW, result.value.refusal)
        // The profile always comes back with it, so the screen redraws to the truth in the same
        // frame it reports the refusal.
        assertNotNull(result.value.player)
    }

    @Test
    fun aBidThatStandsComesBackWithTheLotItChanged() = runTest {
        val client = answering(HttpStatusCode.OK, encode(outcome(lot = lot.copy(topBid = 525))))

        val result = assertIs<AccountResult.Ok<AuctionOutcome>>(
            client.bid(TOKEN, BidRequest(LOT, 525, "op")),
        )
        assertEquals(525, result.value.lot?.topBid)
        assertEquals(null, result.value.refusal)
    }

    // ---- When it goes wrong -----------------------------------------------

    /** The one thing that is a status code: the door, not the price. */
    @Test
    fun aShutDoorIsTheOneRefusalThatIsAStatusCode() = runTest {
        val body = encode(AccountFailure(AccountError.NOT_UNLOCKED, "level 12"))
        val client = answering(HttpStatusCode.Forbidden, body)

        val refused = assertIs<AccountResult.Refused>(client.browse(TOKEN))
        assertEquals(AccountError.NOT_UNLOCKED, refused.failure.error)
    }

    @Test
    fun aThrottleIsReadBeforeTheBodyIsAndKeepsItsDelay() = runTest {
        val client = answering(
            HttpStatusCode.TooManyRequests,
            "",
            headers = headersOf("Retry-After", "30"),
        )

        assertEquals(
            30L,
            assertIs<AccountResult.Throttled>(
                client.bid(TOKEN, BidRequest(LOT, 1, "op")),
            ).retryAfterSeconds,
        )
    }

    @Test
    fun anOutdatedClientIsToldTheServersVersion() = runTest {
        val client = answering(
            HttpStatusCode.UpgradeRequired,
            "",
            headers = headersOf(VERSION_HEADER, "9.0.0"),
        )

        val update = assertIs<AccountResult.UpdateRequired>(client.mine(TOKEN))
        assertEquals(AppVersion(9, 0, 0), update.serverVersion)
    }

    @Test
    fun aBodyThisServerDidNotWriteIsReportedRatherThanGuessedAt() = runTest {
        val client = answering(
            HttpStatusCode.BadGateway,
            "<html>gateway</html>",
            contentType = ContentType.Text.Html,
        )

        assertEquals(
            HttpStatusCode.BadGateway.value,
            assertIs<AccountResult.Failed>(client.browse(TOKEN)).status,
        )
    }

    @Test
    fun aDeadConnectionIsAnAnswerAndNotAThrow() = runTest {
        val client = AuctionClient(
            httpClient(MockEngine { throw IOException("no route") }),
            address,
        )

        assertIs<AccountResult.Offline>(client.browse(TOKEN))
        assertIs<AccountResult.Offline>(client.mine(TOKEN))
        assertIs<AccountResult.Offline>(client.bid(TOKEN, BidRequest(LOT, 1, "op")))
        assertIs<AccountResult.Offline>(client.withdraw(TOKEN, AuctionLotRequest(LOT, "op")))
    }

    // ---- The token --------------------------------------------------------

    /**
     * Looking is signed in too, and that is not an oversight.
     *
     * `yours`, `youLead` and `yourBid` are fields on every lot, so the board is *about* the caller.
     * A signed-out board would be a different screen, and this game has none.
     */
    @Test
    fun everyCallCarriesTheTokenIncludingTheTwoReads() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val client = answering(HttpStatusCode.OK, encode(page), record = { seen += it })

        client.browse(TOKEN)
        client.mine(TOKEN)

        assertEquals(2, seen.size)
        assertTrue(
            seen.all { it.headers["Authorization"] == "Bearer $TOKEN" },
            "a call went out unauthenticated",
        )
        assertTrue(seen.all { it.headers[VERSION_HEADER] != null }, "a call went out unversioned")
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun httpClient(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
    }

    private fun answering(
        status: HttpStatusCode,
        body: String,
        contentType: ContentType = ContentType.Application.Json,
        headers: Headers = Headers.Empty,
        record: (HttpRequestData) -> Unit = {},
    ): AuctionClient {
        val engine = MockEngine { request ->
            record(request)
            respond(
                content = body,
                status = status,
                headers = HeadersBuilder().apply {
                    appendAll(headers)
                    append("Content-Type", contentType.toString())
                }.build(),
            )
        }
        return AuctionClient(httpClient(engine), address)
    }

    private fun outcome(
        lot: AuctionLot? = this.lot,
        refusal: AuctionRefusal? = null,
    ) = AuctionOutcome(
        player = PlayerState(save = GameSave.new(username = "Kuplu", createdAt = 0L)),
        lot = lot,
        refusal = refusal,
    )

    private fun encode(sent: AuctionPage) = json.encodeToString(AuctionPage.serializer(), sent)

    private fun encode(sent: AuctionOutcome) =
        json.encodeToString(AuctionOutcome.serializer(), sent)

    private fun encode(sent: AccountFailure) =
        json.encodeToString(AccountFailure.serializer(), sent)

    private val json = matchProtocolJson

    private val address: suspend () -> String = { "https://example.invalid" }

    private val lot = AuctionLot(id = LOT, cardId = 1, startPrice = 100, endsAt = NOW + 1_000L)

    private val page = AuctionPage(lots = listOf(lot), now = NOW)

    private companion object {
        const val TOKEN = "a-token"
        const val LOT = "a-lot"
        const val NOW = 1_770_000_000_000L
    }
}
