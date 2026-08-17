package com.tripletriad.net

import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.MatchVerdict
import com.tripletriad.protocol.RejectionReason
import com.tripletriad.protocol.SubmissionResult
import com.tripletriad.protocol.TranscriptMove
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorMatchSubmitterTest {

    // ---- The ordinary outcomes --------------------------------------------

    @Test
    fun anAcceptedVerdictComesBackAsJudged() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = receipt("""{"type":"accepted","blue":3,"red":7,"winner":"RED"}"""),
        )

        val result = submitter.submit(transcript)

        val judged = assertIs<SubmissionResult.Judged>(result, "was $result")
        assertEquals(MatchVerdict.Accepted(blue = 3, red = 7, winner = "RED"), judged.verdict)
    }

    @Test
    fun aRejectedVerdictIsAlsoJudgedRatherThanAFailure() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = receipt(
                """{"type":"rejected","reason":"TRUNCATED","detail":"ran out of moves"}""",
            ),
        )

        val judged = assertIs<SubmissionResult.Judged>(submitter.submit(transcript))
        val rejected = assertIs<MatchVerdict.Rejected>(judged.verdict)
        assertEquals(RejectionReason.TRUNCATED, rejected.reason)
    }

    @Test
    fun aDrawnVerdictDecodesWithNoWinnerField() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = receipt("""{"type":"accepted","blue":5,"red":5}"""),
        )

        val judged = assertIs<SubmissionResult.Judged>(submitter.submit(transcript))
        assertEquals(MatchVerdict.Accepted(blue = 5, red = 5, winner = null), judged.verdict)
    }

    // ---- The version gate -------------------------------------------------

    @Test
    fun theClientAnnouncesItsVersionOnEveryRequest() = runTest {
        var seen: HttpRequestData? = null
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = receipt("""{"type":"accepted","blue":5,"red":5}"""),
            record = { seen = it },
        )

        submitter.submit(transcript)

        assertEquals(CURRENT_VERSION.toString(), seen?.headers?.get(VERSION_HEADER))
    }

    @Test
    fun a426BecomesUpdateRequiredCarryingTheServersVersion() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.UpgradeRequired,
            body = """{"error":"upgrade_required","server":"2.0.0","client":"1.4.2"}""",
            headers = headersOf(VERSION_HEADER, "2.0.0"),
        )

        val result = submitter.submit(transcript)

        val update = assertIs<SubmissionResult.UpdateRequired>(result, "was $result")
        assertEquals(AppVersion(2, 0, 0), update.serverVersion)
    }

    @Test
    fun a426WithNoUsableHeaderIsStillUpdateRequired() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.UpgradeRequired,
            body = "{}",
            headers = headersOf(VERSION_HEADER, "not-a-version"),
        )

        val update = assertIs<SubmissionResult.UpdateRequired>(submitter.submit(transcript))
        assertNull(update.serverVersion)
    }

    // ---- The cases that must not lose the match ---------------------------

    @Test
    fun anUnreachableServerIsOfflineAndNotAnException() = runTest {
        val submitter = submitterThrowing(IOException("Connection refused"))

        val result = submitter.submit(transcript)

        val offline = assertIs<SubmissionResult.Offline>(result, "was $result")
        assertTrue(offline.cause.isNotBlank())
    }

    @Test
    fun aTwoHundredThatIsNotAVerdictFailsInsteadOfThrowing() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = "<html>Sign in to the network</html>",
            contentType = ContentType.Text.Html,
        )

        val failed = assertIs<SubmissionResult.Failed>(submitter.submit(transcript))
        assertTrue(failed.detail.isNotBlank())
    }

    @Test
    fun aServerErrorIsReportedWithItsStatus() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError, "boom") }
        val submitter = KtorMatchSubmitter(httpClient(engine), address, token = { TOKEN })

        val failed = assertIs<SubmissionResult.Failed>(submitter.submit(transcript))
        assertEquals(HttpStatusCode.InternalServerError.value, failed.status)
    }

    @Test
    fun anExpiredSessionIsUnauthenticatedRatherThanAFailure() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.Unauthorized,
            body = """{"error":"unauthorized"}""",
        )

        assertEquals(SubmissionResult.Unauthenticated, submitter.submit(transcript))
    }

    @Test
    fun noTokenIsUnauthenticatedWithoutAskingTheServer() = runTest {
        var asked = false
        val engine = MockEngine {
            asked = true
            respondError(HttpStatusCode.InternalServerError, "should not have been called")
        }
        val submitter = KtorMatchSubmitter(httpClient(engine), address, token = { null })

        assertEquals(SubmissionResult.Unauthenticated, submitter.submit(transcript))
        assertTrue(!asked, "the submitter contacted the server with no session")
    }

    @Test
    fun theSessionTokenTravelsAsABearerHeader() = runTest {
        var seen: HttpRequestData? = null
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = receipt("""{"type":"accepted","blue":5,"red":5}"""),
            record = { seen = it },
        )

        submitter.submit(transcript)

        assertEquals("Bearer $TOKEN", seen?.headers?.get("Authorization"))
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun receipt(verdict: String) = """{"verdict":$verdict}"""

    private fun httpClient(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(matchProtocolJson) }
    }

    private fun submitterAnswering(
        status: HttpStatusCode,
        body: String,
        contentType: ContentType = ContentType.Application.Json,
        headers: io.ktor.http.Headers = io.ktor.http.Headers.Empty,
        record: (HttpRequestData) -> Unit = {},
    ): KtorMatchSubmitter {
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
        return KtorMatchSubmitter(httpClient(engine), address, token = { TOKEN })
    }

    private fun submitterThrowing(failure: Throwable): KtorMatchSubmitter {
        val engine = MockEngine { throw failure }
        return KtorMatchSubmitter(httpClient(engine), address, token = { TOKEN })
    }

    private val transcript = MatchTranscript(
        seed = 20260807,
        formatId = "ff14",
        opponentIconId = "tt-master",
        deck = listOf(1, 2, 3, 4, 5),
        ownedCards = (1..5).associateWith { 1 },
        moves = listOf(TranscriptMove(cardId = 1, position = 0)),
    )

    private val address: suspend () -> String = { BASE_URL }

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8080"

        const val TOKEN = "test-session"
    }
}
