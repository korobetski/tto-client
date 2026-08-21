package com.tripletriad.net

import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.PveFailure
import com.tripletriad.protocol.PveMatchRequest
import com.tripletriad.protocol.PveMatchView
import com.tripletriad.protocol.PveMove
import com.tripletriad.protocol.PveRefusal
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four calls a refereed solo match is made of.
 *
 * Built on [AccountClientTest]'s shape, because the two clients are deliberately the same shape: a
 * status code that means success, a body decoded on it, and every other answer turned into an
 * [AccountResult] rather than an exception. What is tested here is the part that differs — which
 * code each call expects, the 204 that means "no match in progress", and the deck travelling as a
 * slot.
 */
class PveClientTest {

    // ---- Opening ----------------------------------------------------------

    @Test
    fun openingAcceptsOnlyTwoHundredAndOne() = runTest {
        assertIs<AccountResult.Ok<PveMatchView>>(
            clientAnswering(HttpStatusCode.Created, encode(view)).open(TOKEN, OPPONENT, FORMAT),
        )
        assertFalse(
            clientAnswering(HttpStatusCode.OK, encode(view)).open(TOKEN, OPPONENT, FORMAT)
                is AccountResult.Ok,
            "a match is created, so 200 is not the answer to opening one",
        )
    }

    /**
     * **The deck is a slot, and it is in the body.**
     *
     * The whole reason the protocol carries a number rather than five card ids: the server resolves
     * it against the profile it holds, so a client cannot name a card it does not own. A test that
     * only checked the field existed would pass on a client that sent a hand.
     */
    @Test
    fun theDeckTravelsAsASlotInTheRequestBody() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.Created, encode(view), record = { seen = it })

        client.open(TOKEN, OPPONENT, FORMAT, deck = 3)

        val body = seen?.body?.toByteArray()?.decodeToString().orEmpty()
        assertEquals(
            PveMatchRequest(OPPONENT, FORMAT, deck = 3),
            matchProtocolJson.decodeFromString(PveMatchRequest.serializer(), body),
        )
    }

    @Test
    fun noChoiceOfDeckIsItsOwnAnswerRatherThanAnAbsentField() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.Created, encode(view), record = { seen = it })

        client.open(TOKEN, OPPONENT, FORMAT)

        val body = seen?.body?.toByteArray()?.decodeToString().orEmpty()
        val sent = matchProtocolJson.decodeFromString(PveMatchRequest.serializer(), body)
        assertEquals(ANY_DECK, sent.deck)
    }

    // ---- Resuming ---------------------------------------------------------

    /**
     * **No content means no match, not a failure.**
     *
     * This is the whole of resuming: the client asks what it is in the middle of and is told
     * "nothing". Treating that as an error would put a reconnection panel in front of every player
     * who is not already playing.
     */
    @Test
    fun anEmptyAnswerToResumingIsSuccessWithNothingInIt() = runTest {
        val result = clientAnswering(HttpStatusCode.NoContent, "").current(TOKEN)

        assertNull(assertIs<AccountResult.Ok<PveMatchView?>>(result).value)
    }

    @Test
    fun aMatchInProgressComesBackFromResuming() = runTest {
        val result = clientAnswering(HttpStatusCode.OK, encode(view)).current(TOKEN)

        assertEquals(MATCH_ID, assertIs<AccountResult.Ok<PveMatchView?>>(result).value?.matchId)
    }

    @Test
    fun resumingTakesNoMatchIdBecauseTheServerKnowsWhichOne() = runTest {
        var seen: HttpRequestData? = null
        clientAnswering(HttpStatusCode.NoContent, "", record = { seen = it }).current(TOKEN)

        assertEquals("/pve/matches/active", seen?.url?.encodedPath)
        assertEquals(HttpMethod.Get, seen?.method)
    }

    // ---- Reading and playing ----------------------------------------------

    @Test
    fun readingOneMatchAsksForItById() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.OK, encode(view), record = { seen = it })

        assertEquals(MATCH_ID, client.match(TOKEN, MATCH_ID).valueOrNull()?.matchId)
        assertEquals("/pve/matches/$MATCH_ID", seen?.url?.encodedPath)
    }

    @Test
    fun aPlacementIsPostedUnderTheMatchItBelongsTo() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.OK, encode(view), record = { seen = it })

        client.play(TOKEN, MATCH_ID, PveMove(handIndex = 2, position = 4))

        assertEquals(HttpMethod.Post, seen?.method)
        assertEquals("/pve/matches/$MATCH_ID/moves", seen?.url?.encodedPath)
        assertEquals(
            PveMove(handIndex = 2, position = 4),
            matchProtocolJson.decodeFromString(
                PveMove.serializer(),
                seen?.body?.toByteArray()?.decodeToString().orEmpty(),
            ),
        )
    }

    // ---- When it goes wrong -----------------------------------------------

    /** A refusal keeps its code, because that is what a screen acts on. */
    @Test
    fun aRefusalComesBackAsItsCodeRatherThanAStatus() = runTest {
        val refusal = encode(PveFailure(PveRefusal.NOT_YOUR_TURN, "the board is waiting"))
        val client = clientAnswering(HttpStatusCode.Conflict, refusal)

        val refused = assertIs<AccountResult.RefusedPve>(
            client.play(TOKEN, MATCH_ID, PveMove(0, 0)),
        )
        assertEquals(PveRefusal.NOT_YOUR_TURN, refused.code)
        assertEquals("the board is waiting", refused.detail)
    }

    /**
     * A body this server did not write is reported as what it is.
     *
     * A proxy's error page is not a refusal, and guessing at one would tell the player something
     * about their move that nobody said.
     */
    @Test
    fun anUnparseableFailureIsReportedRatherThanGuessedAt() = runTest {
        val client = clientAnswering(
            HttpStatusCode.BadGateway,
            "<html>gateway</html>",
            contentType = ContentType.Text.Html,
        )

        val failed = assertIs<AccountResult.Failed>(client.match(TOKEN, MATCH_ID))
        assertEquals(HttpStatusCode.BadGateway.value, failed.status)
    }

    @Test
    fun anOutdatedClientIsToldTheServersVersion() = runTest {
        val client = clientAnswering(
            HttpStatusCode.UpgradeRequired,
            "",
            headers = headersOf(VERSION_HEADER, "9.0.0"),
        )

        val update = assertIs<AccountResult.UpdateRequired>(client.open(TOKEN, OPPONENT, FORMAT))
        assertEquals(AppVersion(9, 0, 0), update.serverVersion)
    }

    /**
     * **A dropped connection is an answer, not an exception.**
     *
     * Every call has to come back with something the screen can show, because losing the connection
     * must not lose the match — see `PveSession.trouble`, which is the one thing this turns into.
     */
    @Test
    fun aDeadConnectionIsAnAnswerAndNotAThrow() = runTest {
        val client = PveClient(httpClient(MockEngine { throw IOException("no route") }), address)

        assertIs<AccountResult.Offline>(client.current(TOKEN))
        assertIs<AccountResult.Offline>(client.open(TOKEN, OPPONENT, FORMAT))
        assertIs<AccountResult.Offline>(client.match(TOKEN, MATCH_ID))
        assertIs<AccountResult.Offline>(client.play(TOKEN, MATCH_ID, PveMove(0, 0)))
    }

    // ---- The token --------------------------------------------------------

    @Test
    fun everyCallCarriesTheTokenAsABearerHeader() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val client = clientAnswering(HttpStatusCode.OK, encode(view), record = { seen += it })

        client.current(TOKEN)
        client.match(TOKEN, MATCH_ID)
        client.play(TOKEN, MATCH_ID, PveMove(0, 0))

        assertEquals(3, seen.size)
        assertTrue(
            seen.all { it.headers["Authorization"] == "Bearer $TOKEN" },
            "a call went out unauthenticated",
        )
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun httpClient(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(matchProtocolJson) }
    }

    private fun clientAnswering(
        status: HttpStatusCode,
        body: String,
        contentType: ContentType = ContentType.Application.Json,
        headers: io.ktor.http.Headers = io.ktor.http.Headers.Empty,
        record: (HttpRequestData) -> Unit = {},
    ): PveClient {
        val engine = MockEngine { request ->
            record(request)
            respond(
                content = body,
                status = status,
                headers = io.ktor.http.HeadersBuilder().apply {
                    appendAll(headers)
                    append("Content-Type", contentType.toString())
                }.build(),
            )
        }
        return PveClient(httpClient(engine), address)
    }

    private fun encode(sent: PveMatchView) =
        matchProtocolJson.encodeToString(PveMatchView.serializer(), sent)

    private fun encode(sent: PveFailure) =
        matchProtocolJson.encodeToString(PveFailure.serializer(), sent)

    private val address: suspend () -> String = { "https://example.invalid" }

    private val view = PveMatchView(
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
