package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.log.Log
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerStatus
import com.tripletriad.net.clientPlatform
import com.tripletriad.net.downloadForThisPlatform
import com.tripletriad.net.isNewerThanRunning
import com.tripletriad.net.isUsable
import com.tripletriad.net.serverInfo
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.ClientRelease
import com.tripletriad.protocol.PvpStakePolicy
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.Unlocks
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class Connectivity internal constructor(
    private val server: ServerConnection,
) {
    private val states = mutableStateMapOf<String, ServerStatus>()

    var isProbing: Boolean by mutableStateOf(false)
        private set

    val servers: List<ServerEntry> get() = server.directory.entries

    val selected: ServerEntry get() = server.directory.selected

    fun statusOf(entry: ServerEntry): ServerStatus = states[entry.id] ?: ServerStatus.Unknown

    val status: ServerStatus get() = statusOf(selected)

    /**
     * What the selected server says the two gated doors open at.
     *
     * `:core`'s defaults until a probe has answered, which is the same thing the server
     * would have said had it been asked — see [LocalUnlocks]. A deployment that has raised
     * a threshold is therefore shown correctly one probe later rather than never, and a
     * deployment that has not is shown correctly from the start.
     */
    val unlocks: Unlocks get() = status.serverInfo?.unlocks ?: Unlocks()

    /** How large a wager it allows, on the same terms as [unlocks] and for the same reason. */
    val stakes: PvpStakePolicy get() = status.serverInfo?.stakes ?: PvpStakePolicy()

    var release: ClientRelease? by mutableStateOf(null)
        private set

    val update: UpdateAdvice? get() = adviceFor(status) ?: releaseAdvice()

    suspend fun refreshSelected() {
        probe(listOf(selected))
    }

    suspend fun checkForRelease() {
        if (hasCheckedReleases) return
        hasCheckedReleases = true
        release = server.releases.latest()
        release?.let { Log.i(TAG) { "the newest published build is ${it.version}" } }
    }

    private var hasCheckedReleases = false

    suspend fun refreshAll() {
        probe(servers)
    }

    private suspend fun probe(entries: List<ServerEntry>) {
        if (entries.isEmpty()) return

        isProbing = true
        entries.forEach { states[it.id] = ServerStatus.Checking }
        try {
            coroutineScope {
                entries
                    .map { entry -> async { entry to server.probe.probe(entry.baseUrl) } }
                    .awaitAll()
                    .forEach { (entry, status) ->
                        states[entry.id] = status
                        Log.i(TAG) { "${entry.label}: $status" }
                    }
            }
        } finally {
            // In `finally` because a cancelled scope — the player left the screen — must not leave
            // a spinner running for the rest of the session.
            isProbing = false
        }
    }

    private fun adviceFor(status: ServerStatus): UpdateAdvice? {
        val info = status.serverInfo ?: return null

        return when {
            // Blocking: this build cannot be served, and the only remedy is a new one. Still the
            // protocol's business — `ServerStatus.Outdated` is what `minimumClient` refused, and
            // that gate is about the wire format rather than about which app is installed.
            status is ServerStatus.Outdated -> UpdateAdvice.fromServer(info, isRequired = true)

            // A suggestion. The deployment publishes a newer build than this one and will still
            // talk to us, so this is worth saying once and never worth standing in the way.
            info.release?.isNewerThanRunning() == true ->
                UpdateAdvice.fromServer(info, isRequired = false)

            else -> null
        }
    }

    private fun releaseAdvice(): UpdateAdvice? =
        release?.takeIf { it.isNewerThanRunning() }?.let(UpdateAdvice::fromRelease)

    private companion object {
        const val TAG = "Connectivity"
    }
}

data class UpdateAdvice(
    val target: AppVersion,
    val download: String?,
    val notes: String?,
    val isRequired: Boolean,
) {
    companion object {
        fun fromServer(info: ServerInfo, isRequired: Boolean): UpdateAdvice = UpdateAdvice(
            target = info.release?.version ?: info.minimumClient,
            download = info.downloadForThisPlatform(),
            notes = info.release?.notes,
            isRequired = isRequired,
        )

        fun fromRelease(release: ClientRelease): UpdateAdvice = UpdateAdvice(
            target = release.version,
            download = release.downloads[clientPlatform],
            notes = release.notes,
            isRequired = false,
        )
    }
}

val Connectivity.isServerUsable: Boolean get() = status.isUsable

@Composable
internal fun rememberConnectivity(server: ServerConnection): Connectivity =
    remember(server) { Connectivity(server) }
