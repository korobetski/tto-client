package com.tripletriad.net

import com.tripletriad.storage.DocumentStore
import com.tripletriad.time.Clock
import io.ktor.client.engine.HttpClientEngineFactory

internal expect fun defaultHttpEngineFactory(): HttpClientEngineFactory<*>

// Eight collaborators, and they are not eight decisions: this is *everything the app talks to over
// HTTP*, assembled once by the host. Splitting it to satisfy a counter would give the two host
// modules two bundles to wire identically, which is the mistake the type exists to prevent.
@Suppress("LongParameterList")
class ServerConnection internal constructor(
    val directory: ServerDirectory,
    val accounts: AccountClient,
    val pvp: PvpClient,
    val pve: PveClient,
    val session: SessionStore,
    val tickets: TicketStore,
    val probe: ServerProbe,
    val reporter: MatchReporter,
    val releases: ReleaseSource = ReleaseSource.None,
) {
    val server: ServerEntry get() = directory.selected
}

data class ServerStores(
    val queue: DocumentStore,
    val session: DocumentStore,
    val directory: DocumentStore,
    val tickets: DocumentStore,
)

fun serverConnection(
    stores: ServerStores,
    servers: List<ServerEntry>,
    clock: Clock,
): ServerConnection {
    val http = matchSubmitterHttpClient(defaultHttpEngineFactory())
    val directory = ServerDirectory(stores.directory, servers)
    val sessions = SessionStore(stores.session)

    // Read per request rather than captured. Both of these change while the app is running — the
    // player switches servers, the player signs in — and a value read once would keep sending the
    // address or the token that was current when this was built.
    val address: suspend () -> String = { directory.selected.baseUrl }
    val token: suspend () -> String? = {
        sessions.load(directory.selected.id, clock.nowMillis())?.token
    }

    return ServerConnection(
        directory = directory,
        accounts = AccountClient(http, address),
        tickets = TicketStore(stores.tickets),
        pvp = PvpClient(http, address),
        pve = PveClient(http, address),
        session = sessions,
        probe = ServerProbe(http, elapsed = clock::nowMillis),
        reporter = QueuedMatchReporter(
            queue = TranscriptQueue(stores.queue),
            submitter = KtorMatchSubmitter(client = http, baseUrl = address, token = token),
        ),
        releases = GithubReleaseClient(http),
    )
}
