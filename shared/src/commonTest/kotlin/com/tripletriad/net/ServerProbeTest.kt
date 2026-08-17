package com.tripletriad.net

import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ClientRelease
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerProbeTest {

    @Test
    fun aHealthyServerIsOnline() = runTest {
        val status = probeAnswering(HttpStatusCode.OK, encode(healthy))

        val online = assertIs<ServerStatus.Online>(status)
        assertEquals("Test server", online.info.name)
        assertTrue(online.isUsable)
    }

    @Test
    fun theRoundTripIsMeasured() = runTest {
        var reading = 0L
        val probe = ServerProbe(
            client = clientAnswering { respondJson(HttpStatusCode.OK, encode(healthy)) },
            elapsed = { reading.also { reading += ELAPSED } },
        )

        assertEquals(ELAPSED, probe.probe(BASE_URL).latency)
    }

    @Test
    fun aServerWithNoDatabaseIsDegradedAndNotUnreachable() = runTest {
        val status = probeAnswering(HttpStatusCode.OK, encode(healthy.copy(ready = false)))

        val degraded = assertIs<ServerStatus.Degraded>(status)
        assertEquals("Test server", degraded.info.name)
        assertFalse(degraded.isUsable)
    }

    @Test
    fun aServerThatWillNotServeThisBuildIsOutdated() = runTest {
        val demanding = healthy.copy(minimumClient = AppVersion(CURRENT_VERSION.major + 1, 0, 0))

        val status = probeAnswering(HttpStatusCode.OK, encode(demanding))

        val outdated = assertIs<ServerStatus.Outdated>(status)
        assertEquals(demanding.minimumClient, outdated.info.minimumClient)
        assertFalse(outdated.isUsable)
    }

    @Test
    fun aServerThatIsNotThereIsUnreachable() = runTest {
        val probe = ServerProbe(
            client = clientAnswering { throw IOException("Connection refused") },
            elapsed = { 0L },
        )

        val unreachable = assertIs<ServerStatus.Unreachable>(probe.probe(BASE_URL))
        assertTrue(unreachable.cause.isNotBlank())
        assertNull(unreachable.latency)
    }

    @Test
    fun anErrorStatusIsNotAServerDescription() = runTest {
        val status = probeAnswering(HttpStatusCode.BadGateway, "<html>502</html>")

        assertIs<ServerStatus.Unusable>(status)
    }

    @Test
    fun aTwoHundredThatIsNotAServerInfoIsUnusable() = runTest {
        val status = probeAnswering(HttpStatusCode.OK, """{"welcome":"free airport wifi"}""")

        assertIs<ServerStatus.Unusable>(status)
    }

    @Test
    fun aServerTooNewToDecodeIsStillKnownToBeTooNew() = runTest {
        val theirs = AppVersion(CURRENT_VERSION.major + 1, 0, 0)
        val probe = ServerProbe(
            client = clientAnswering {
                respond(
                    content = """{"shape":"from the future"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        "Content-Type" to listOf(ContentType.Application.Json.toString()),
                        VERSION_HEADER to listOf(theirs.toString()),
                    ),
                )
            },
            elapsed = { 0L },
        )

        val outdated = assertIs<ServerStatus.Outdated>(probe.probe(BASE_URL))
        assertEquals(theirs, outdated.info.version)
    }

    @Test
    fun anUnreadableBodyWithNoHeaderIsMerelyUnusable() = runTest {
        assertIs<ServerStatus.Unusable>(probeAnswering(HttpStatusCode.OK, "not json at all"))
    }

    // ---- The request itself ------------------------------------------------

    @Test
    fun theProbeAsksTheOneRouteThatIsNeverGated() = runTest {
        var asked: HttpRequestData? = null
        val probe = ServerProbe(
            client = clientAnswering { request ->
                asked = request
                respondJson(HttpStatusCode.OK, encode(healthy))
            },
            elapsed = { 0L },
        )

        probe.probe("$BASE_URL/")

        assertEquals("/server", asked?.url?.encodedPath)
        // Sent even though this route refuses nobody, so a deployment's logs can see which builds
        // are asking — and so this request looks like every other one.
        assertEquals(CURRENT_VERSION.toString(), asked?.headers?.get(VERSION_HEADER))
    }

    // ---- The download offered ----------------------------------------------

    @Test
    fun onlyThisPlatformsDownloadIsOffered() {
        val published = healthy.copy(
            release = ClientRelease(
                version = AppVersion(9, 0, 0),
                downloads = mapOf(clientPlatform to "https://example.org/here"),
            ),
        )

        assertEquals("https://example.org/here", published.downloadForThisPlatform())
    }

    @Test
    fun aDeploymentThatPublishesNothingOffersNothing() {
        assertNull(healthy.downloadForThisPlatform())
    }

    // ---- Fixtures ----------------------------------------------------------

    private suspend fun probeAnswering(status: HttpStatusCode, body: String): ServerStatus =
        ServerProbe(
            client = clientAnswering { respondJson(status, body) },
            elapsed = { 0L },
        ).probe(BASE_URL)

    private fun clientAnswering(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = HttpClient(MockEngine(handler)) {
        expectSuccess = false
        install(ContentNegotiation) { json(matchProtocolJson) }
    }

    private fun MockRequestHandleScope.respondJson(
        status: HttpStatusCode,
        body: String,
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private inline fun <reified T> encode(value: T) = matchProtocolJson.encodeToString(value)

    private val healthy = ServerInfo(
        name = "Test server",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8080"

        const val ELAPSED = 42L
    }
}
