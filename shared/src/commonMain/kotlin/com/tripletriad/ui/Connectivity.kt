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
import com.tripletriad.protocol.ServerInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * What is known about every configured server, and about this build's standing with them.
 *
 * ### Why one object for all of the servers and not one per server
 *
 * Because the two questions the UI asks are about the *set*: "is the one I am on usable" and "is
 * there a better one". Both need every entry probed, and probing them one screen at a time would
 * mean the menu's indicator and the server list disagreeing about the same host.
 *
 * ### Why probing is explicit and not a timer
 *
 * There is no poll loop here, deliberately. A game that pings three hosts every thirty seconds
 * forever is a game that drains a phone battery to keep a dot green on a screen nobody is looking
 * at. Probes happen when the answer is about to be used or acted on — at startup, on opening the
 * server list, on a pull to refresh, and after a switch — which is every moment the status is worth
 * anything and none of the ones it is not.
 */
class Connectivity internal constructor(
    private val server: ServerConnection,
) {
    private val states = mutableStateMapOf<String, ServerStatus>()

    /** True while any probe is in flight, so a list can show one spinner rather than n. */
    var isProbing: Boolean by mutableStateOf(false)
        private set

    /** The servers this build offers, in configured order. */
    val servers: List<ServerEntry> get() = server.directory.entries

    /** The one in play. */
    val selected: ServerEntry get() = server.directory.selected

    /** What is known about [entry]. [ServerStatus.Unknown] until it has been probed. */
    fun statusOf(entry: ServerEntry): ServerStatus = states[entry.id] ?: ServerStatus.Unknown

    /** What is known about the server in play — the one the indicator and the gate care about. */
    val status: ServerStatus get() = statusOf(selected)

    /**
     * What the releases page last said, or null if it has not been asked or had nothing to say.
     *
     * Held rather than re-fetched because [update] is read on every recomposition of three screens
     * and this is a network call to somebody else's rate limit. See [checkForRelease].
     */
    var release: ClientRelease? by mutableStateOf(null)
        private set

    /**
     * This build's standing, from whichever source has something to say.
     *
     * Null when there is nothing to say: it is fine, or nothing is known yet. Non-null is the whole
     * of the update story — either the server will not have this build, or a newer one is published
     * — and it is what the screens render.
     *
     * The **server wins** when it has an opinion, and the order is not arbitrary: only a deployment
     * can say "this build cannot be served at all", and letting a suggestion from the releases page
     * take that slot would replace a refusal the player has to act on with a note they can ignore.
     * The releases page is what answers when the server is content — or has simply not been told
     * about the release yet, which is the usual case for the first hours of one.
     */
    val update: UpdateAdvice? get() = adviceFor(status) ?: releaseAdvice()

    /** Probes the selected server only. What startup and a sign-in failure want. */
    suspend fun refreshSelected() {
        probe(listOf(selected))
    }

    /**
     * Asks the releases page what the newest published build is, at most once per launch.
     *
     * Once, and not on the probe timer the servers are on: this is an anonymous GitHub API call
     * against a limit of sixty an hour **per address**, which a household behind one NAT can
     * exhaust between them. The answer also changes a few times a year, so re-asking on every visit
     * to the menu would be spending a shared budget to learn the same thing.
     */
    suspend fun checkForRelease() {
        if (hasCheckedReleases) return
        hasCheckedReleases = true
        release = server.releases.latest()
        release?.let { Log.i(TAG) { "the newest published build is ${it.version}" } }
    }

    /** Guards [checkForRelease]. Not the null-ness of [release]: a failed check is also a check. */
    private var hasCheckedReleases = false

    /** Probes every configured server, for the screen that shows them side by side. */
    suspend fun refreshAll() {
        probe(servers)
    }

    /**
     * Probes [entries] concurrently.
     *
     * Concurrently because they are independent and a serial sweep costs the sum of every timeout —
     * with three servers and one black hole among them, a list that takes thirty seconds to draw.
     */
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

    /**
     * What the probed deployment has to say about this build.
     *
     * ### The comparison is against the app, not the protocol
     *
     * It used to be `published > CURRENT_VERSION`, and that was wrong in a way nobody could see
     * from here: `CURRENT_VERSION` is the **protocol** version — 1.0.0, moving only on a
     * replay-affecting break — while the app ships its own release number. A deployment setting
     * `TTO_CLIENT_VERSION` to the real app version therefore told every client it was out of date,
     * forever, including one already running that exact build. `tto-core`'s `docs/RELEASING.md`
     * § 7 parks it and prescribes the workaround: put the protocol version in that variable, which
     * makes the notice incapable of announcing an app release.
     *
     * Both sources now go through [isNewerThanRunning], so the deployment's advice and the
     * releases page's agree about what "newer" means. See [RunningVersion].
     */
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

    /**
     * The releases page's advice, or null.
     *
     * Filtered on [isNewerThanRunning] and not on the target alone: a build *ahead* of the newest
     * release is the ordinary state between tagging and publishing, and telling its owner to
     * downgrade would be the only genuinely wrong thing this feature could say.
     */
    private fun releaseAdvice(): UpdateAdvice? =
        release?.takeIf { it.isNewerThanRunning() }?.let(UpdateAdvice::fromRelease)

    private companion object {
        const val TAG = "Connectivity"
    }
}

/**
 * What to tell the player about their build.
 *
 * ### Why required and suggested are one type with a flag
 *
 * Because they carry exactly the same information and differ only in what the screen may do about
 * it: a required update replaces the sign-in form, a suggested one is a line above it. Two types
 * would be two renderings of the same three fields, and the day a third case appears — a server
 * that is *older* than this build, which is allowed — it would be a third.
 *
 * ### Why it no longer carries a [ServerInfo]
 *
 * It used to, and derived the other three fields from it, which quietly made "there is a newer
 * build" a thing only a server could say. There are two sources now — the selected deployment, and
 * the releases page ([com.tripletriad.net.GithubReleaseClient]) — and they agree on what a notice
 * *is* while having nothing else in common. So the advice is the three things a notice renders, and
 * each source builds one; see [fromServer] and [fromRelease].
 *
 * @property target the version being asked for: what to update *to*, not what refused us.
 * @property download where to get it **on this platform**, or null when the source publishes
 *   nothing for it. Resolved by the source rather than by the notice, because only the source knows
 *   whether its URL is an artifact or a page.
 * @property notes one line the source may want shown. Displayed as given and never parsed.
 * @property isRequired true when the server will not serve this build at all. False means it will,
 *   and would rather it did not have to. A release-page advice is never required: a published
 *   artifact says nothing about what any deployment will accept.
 */
data class UpdateAdvice(
    val target: AppVersion,
    val download: String?,
    val notes: String?,
    val isRequired: Boolean,
) {
    companion object {
        /** The advice a probed deployment gives. */
        fun fromServer(info: ServerInfo, isRequired: Boolean): UpdateAdvice = UpdateAdvice(
            target = info.release?.version ?: info.minimumClient,
            download = info.downloadForThisPlatform(),
            notes = info.release?.notes,
            isRequired = isRequired,
        )

        /** The advice the releases page gives. Never required — see [isRequired]. */
        fun fromRelease(release: ClientRelease): UpdateAdvice = UpdateAdvice(
            target = release.version,
            download = release.downloads[clientPlatform],
            notes = release.notes,
            isRequired = false,
        )
    }
}

/** Whether the selected server can be signed in to right now. */
val Connectivity.isServerUsable: Boolean get() = status.isUsable

@Composable
internal fun rememberConnectivity(server: ServerConnection): Connectivity =
    remember(server) { Connectivity(server) }
