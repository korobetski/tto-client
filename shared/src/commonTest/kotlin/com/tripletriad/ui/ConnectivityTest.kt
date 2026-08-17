package com.tripletriad.ui

import com.tripletriad.net.AccountClient
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.PvpClient
import com.tripletriad.net.ReleaseSource
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerProbe
import com.tripletriad.net.ServerStatus
import com.tripletriad.net.SessionStore
import com.tripletriad.net.TicketStore
import com.tripletriad.net.clientPlatform
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.net.runningVersion
import com.tripletriad.net.serverEntries
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ClientRelease
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.storage.InMemoryDocumentStore
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

class ConnectivityTest {

    @Test
    fun everythingIsUnknownUntilItHasBeenProbed() {
        val connectivity = connectivityOver { respondInfo(healthy) }

        assertEquals(ServerStatus.Unknown, connectivity.status)
        assertFalse(connectivity.isProbing)
        assertNull(connectivity.update)
    }

    @Test
    fun probingTheSelectedServerLeavesTheOthersAlone() = runTest {
        val connectivity = connectivityOver { respondInfo(healthy) }

        connectivity.refreshSelected()

        assertIs<ServerStatus.Online>(connectivity.statusOf(connectivity.servers.first()))
        assertEquals(ServerStatus.Unknown, connectivity.statusOf(connectivity.servers[1]))
        assertFalse(connectivity.isProbing)
    }

    @Test
    fun eachServerKeepsItsOwnState() = runTest {
        val connectivity = connectivityOver { request ->
            if (request.url.host == "a.example.org") {
                respondInfo(healthy)
            } else {
                throw IOException("Connection refused")
            }
        }

        connectivity.refreshAll()

        assertIs<ServerStatus.Online>(connectivity.statusOf(connectivity.servers.first()))
        assertIs<ServerStatus.Unreachable>(connectivity.statusOf(connectivity.servers[1]))
    }

    @Test
    fun theSelectedServerIsTheOneTheIndicatorReads() = runTest {
        val connectivity = connectivityOver { request ->
            if (request.url.host == "a.example.org") {
                respondInfo(healthy)
            } else {
                throw IOException("Connection refused")
            }
        }
        connectivity.refreshAll()

        assertTrue(connectivity.isServerUsable)
    }

    // ---- What to say about this build --------------------------------------

    @Test
    fun aServerThatIsHappyWithThisBuildHasNothingToSay() = runTest {
        val connectivity = connectivityOver { respondInfo(healthy) }

        connectivity.refreshSelected()

        assertNull(connectivity.update)
    }

    @Test
    fun aServerThisBuildIsTooOldForRequiresAnUpdate() = runTest {
        val next = AppVersion(CURRENT_VERSION.major + 1, 0, 0)
        val connectivity = connectivityOver {
            respondInfo(healthy.copy(minimumClient = next, release = ClientRelease(version = next)))
        }

        connectivity.refreshSelected()

        val advice = assertNotNullAdvice(connectivity.update)
        assertTrue(advice.isRequired)
        assertEquals(next, advice.target)
    }

    @Test
    fun withNoPublishedBuildTheTargetIsWhatTheServerDemands() = runTest {
        val next = AppVersion(CURRENT_VERSION.major + 1, 0, 0)
        val connectivity = connectivityOver { respondInfo(healthy.copy(minimumClient = next)) }

        connectivity.refreshSelected()

        assertEquals(next, assertNotNullAdvice(connectivity.update).target)
    }

    @Test
    fun aNewerPublishedBuildIsOnlySuggested() = runTest {
        val newer = CURRENT_VERSION.copy(minor = CURRENT_VERSION.minor + 1)
        val connectivity = connectivityOver {
            respondInfo(healthy.copy(release = ClientRelease(version = newer)))
        }

        connectivity.refreshSelected()

        val advice = assertNotNullAdvice(connectivity.update)
        assertFalse(advice.isRequired)
        assertEquals(newer, advice.target)
    }

    @Test
    fun aDeploymentAnnouncingThisVeryBuildAdvisesNothing() = runTest {
        val running = requireNotNull(runningVersion) { "this build must know its own version" }
        val connectivity = connectivityOver {
            respondInfo(healthy.copy(release = ClientRelease(version = running)))
        }

        connectivity.refreshSelected()

        assertNull(connectivity.update)
    }

    @Test
    fun aDeploymentAnnouncingANewerAppSuggestsIt() = runTest {
        val running = requireNotNull(runningVersion)
        val next = running.copy(patch = running.patch + 1)
        val connectivity = connectivityOver {
            respondInfo(healthy.copy(release = ClientRelease(version = next)))
        }

        connectivity.refreshSelected()

        val advice = assertNotNullAdvice(connectivity.update)
        assertFalse(advice.isRequired)
        assertEquals(next, advice.target)
    }

    @Test
    fun anOlderPublishedBuildIsNotAnUpdate() = runTest {
        // The lowest version that can be expressed, so it is at or below this build whatever this
        // build is — at, in which case there is still nothing to say.
        val older = AppVersion(0, 0, 0)
        val connectivity = connectivityOver {
            respondInfo(healthy.copy(release = ClientRelease(version = older)))
        }

        connectivity.refreshSelected()

        assertNull(connectivity.update)
    }

    @Test
    fun anUnreachableServerAdvisesNothing() = runTest {
        val connectivity = connectivityOver { throw IOException("Connection refused") }

        connectivity.refreshSelected()

        assertIs<ServerStatus.Unreachable>(connectivity.status)
        assertNull(connectivity.update)
    }

    @Test
    fun theReleasesPageIsAskedWhenTheServerIsContent() = runTest {
        val newer = AppVersion(99, 0, 0)
        val connectivity = connectivityOver(
            releases = releasing(ClientRelease(newer, mapOf(clientPlatform to PAGE))),
        ) { respondInfo(healthy) }

        connectivity.refreshSelected()
        assertNull(connectivity.update, "the server alone advises nothing")

        connectivity.checkForRelease()

        val advice = assertNotNullAdvice(connectivity.update)
        assertEquals(newer, advice.target)
        assertEquals(PAGE, advice.download)
        assertFalse(advice.isRequired, "a published artifact cannot refuse anybody")
    }

    @Test
    fun theServerWinsWhenItRefusesThisBuild() = runTest {
        val next = AppVersion(CURRENT_VERSION.major + 1, 0, 0)
        val connectivity = connectivityOver(
            releases = releasing(ClientRelease(AppVersion(99, 0, 0))),
        ) {
            respondInfo(
                healthy.copy(minimumClient = next, release = ClientRelease(version = next)),
            )
        }

        connectivity.refreshSelected()
        connectivity.checkForRelease()

        val advice = assertNotNullAdvice(connectivity.update)
        assertTrue(advice.isRequired)
        assertEquals(next, advice.target, "the server's target, not the releases page's")
    }

    @Test
    fun aPublishedReleaseNoNewerThanThisBuildAdvisesNothing() = runTest {
        val connectivity = connectivityOver(
            releases = releasing(ClientRelease(AppVersion(0, 0, 0))),
        ) { respondInfo(healthy) }

        connectivity.refreshSelected()
        connectivity.checkForRelease()

        assertNull(connectivity.update)
    }

    @Test
    fun theReleasesPageIsAskedOnlyOnce() = runTest {
        var asked = 0
        val connectivity = connectivityOver(
            releases = object : ReleaseSource {
                override suspend fun latest(): ClientRelease? {
                    asked++
                    return null
                }
            },
        ) { respondInfo(healthy) }

        repeat(3) { connectivity.checkForRelease() }

        assertEquals(1, asked)
    }

    // ---- Fixtures ----------------------------------------------------------

    private fun assertNotNullAdvice(advice: UpdateAdvice?): UpdateAdvice =
        requireNotNull(advice) { "expected advice about this build" }

    private fun releasing(release: ClientRelease) = object : ReleaseSource {
        override suspend fun latest(): ClientRelease = release
    }

    private fun connectivityOver(
        releases: ReleaseSource = ReleaseSource.None,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Connectivity {
        val http = HttpClient(MockEngine(handler)) {
            expectSuccess = false
            install(ContentNegotiation) { json(matchProtocolJson) }
        }
        val directory = ServerDirectory(InMemoryDocumentStore(), entries)
        return Connectivity(
            ServerConnection(
                directory = directory,
                accounts = AccountClient(http, baseUrl = { directory.selected.baseUrl }),
                pvp = PvpClient(http, baseUrl = { directory.selected.baseUrl }),
                session = SessionStore(InMemoryDocumentStore()),
                tickets = TicketStore(InMemoryDocumentStore()),
                probe = ServerProbe(http) { 0L },
                reporter = MatchReporter.None,
                releases = releases,
            ),
        )
    }

    private fun MockRequestHandleScope.respondInfo(info: ServerInfo) =
        respond(
            content = matchProtocolJson.encodeToString(info),
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private val healthy = ServerInfo(
        name = "Test server",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    private val entries: List<ServerEntry> =
        serverEntries("A=https://a.example.org, B=https://b.example.org")

    private companion object {
        const val PAGE = "https://github.com/korobetski/tto-client/releases/tag/v99.0.0"
    }
}
