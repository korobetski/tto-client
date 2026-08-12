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

/**
 * What the screens read: the state of every configured server, and this build's standing with the
 * one in play.
 *
 * The two things worth pinning down are that the servers are kept **apart** — one host being down
 * must not colour another's row — and that the update advice distinguishes "you cannot play here"
 * from "there is a newer build". The second decides whether the sign-in form is replaced or merely
 * annotated, which is the difference between a helpful notice and a locked-out player.
 */
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

    /**
     * One host being down does not colour another's row.
     *
     * This is the reason the states are a map and not a field: a player looking at the list is
     * choosing *between* servers, and a screen that showed them all as whatever the last answer was
     * would be a screen that makes the choice for them, wrongly.
     */
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

    /**
     * A server that will not serve this build produces *required* advice.
     *
     * Required is what replaces the sign-in form. It has to, because the form cannot work: the same
     * gate that refused the probe's build will refuse the sign-in, and leaving the fields there
     * would invite a password to be typed into something guaranteed to fail.
     */
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

    /** With nothing published, the version being asked for is the minimum the server demands. */
    @Test
    fun withNoPublishedBuildTheTargetIsWhatTheServerDemands() = runTest {
        val next = AppVersion(CURRENT_VERSION.major + 1, 0, 0)
        val connectivity = connectivityOver { respondInfo(healthy.copy(minimumClient = next)) }

        connectivity.refreshSelected()

        assertEquals(next, assertNotNullAdvice(connectivity.update).target)
    }

    /**
     * A newer build the server will still serve is a suggestion, never a wall.
     *
     * Standing in the way here would be locking a player out of a server that is perfectly happy to
     * have them, which is a self-inflicted outage.
     */
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

    /**
     * A deployment announcing **the build that is running** says nothing.
     *
     * The regression `tto-core`'s `docs/RELEASING.md` § 7 parks. The comparison used to be against
     * `CURRENT_VERSION` — the protocol version, 1.0.0 — so a deployment that set
     * `TTO_CLIENT_VERSION` to the app's own release number told every client it was out of date,
     * including one already running it. The documented workaround was to put the protocol version
     * in that variable instead, which left the notice unable to announce an app release at all.
     *
     * Pinned with [runningVersion] rather than a literal so it keeps testing the claim after the
     * next `clientVersion` bump — a hard-coded 1.0.3 would start passing for the wrong reason.
     */
    @Test
    fun aDeploymentAnnouncingThisVeryBuildAdvisesNothing() = runTest {
        val running = requireNotNull(runningVersion) { "this build must know its own version" }
        val connectivity = connectivityOver {
            respondInfo(healthy.copy(release = ClientRelease(version = running)))
        }

        connectivity.refreshSelected()

        assertNull(connectivity.update)
    }

    /** And one publishing a genuinely newer app *does* — which is the notice's whole purpose. */
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

    /** A deployment publishing an *older* build than this one is not a reason to say anything. */
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

    /** And a server that could not be reached has nothing to advise about. */
    @Test
    fun anUnreachableServerAdvisesNothing() = runTest {
        val connectivity = connectivityOver { throw IOException("Connection refused") }

        connectivity.refreshSelected()

        assertIs<ServerStatus.Unreachable>(connectivity.status)
        assertNull(connectivity.update)
    }

    /**
     * The releases page answers when the deployment has nothing to say.
     *
     * Which is the usual state for the first hours of a release: the APK is published and the
     * server's `TTO_CLIENT_VERSION` is still whatever it was, because that is a line somebody
     * copies out of a workflow summary by hand.
     */
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

    /**
     * A refusal is never replaced by a suggestion.
     *
     * Only a deployment can say "this build cannot be served", and letting the releases page take
     * that slot would turn a wall the player has to act on into a note they can dismiss.
     */
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

    /** A release at or below this build is not an update, whatever the page says. */
    @Test
    fun aPublishedReleaseNoNewerThanThisBuildAdvisesNothing() = runTest {
        val connectivity = connectivityOver(
            releases = releasing(ClientRelease(AppVersion(0, 0, 0))),
        ) { respondInfo(healthy) }

        connectivity.refreshSelected()
        connectivity.checkForRelease()

        assertNull(connectivity.update)
    }

    /** Asked once a launch: it is somebody else's rate limit, and the answer changes yearly. */
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
        /** Stands in for whatever the releases page offers this platform. */
        const val PAGE = "https://github.com/korobetski/tto-client/releases/tag/v99.0.0"
    }
}
