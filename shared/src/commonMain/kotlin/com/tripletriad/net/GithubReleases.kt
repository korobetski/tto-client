package com.tripletriad.net

import com.tripletriad.CLIENT_VERSION
import com.tripletriad.log.Log
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.ClientPlatform
import com.tripletriad.protocol.ClientRelease
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where the newest published build is looked up.
 *
 * An interface for the same reason [MatchReporter] is one: the only implementation that matters
 * talks to the network, and almost everything that composes an app does not want it to. [None] is
 * what a test gets by default and what a fork with no releases page would keep — see
 * [ServerConnection], where it is the default.
 */
interface ReleaseSource {
    /** The newest published release, or null when there is none or it could not be read. */
    suspend fun latest(): ClientRelease?

    /** Checks nothing and reports nothing. */
    object None : ReleaseSource {
        override suspend fun latest(): ClientRelease? = null
    }
}

/**
 * What build is published on GitHub, and where to get it.
 *
 * ### Why the client asks GitHub rather than only the server
 *
 * Because the server's answer is a **configuration**, not a fact. `ClientRelease` is filled from
 * `TTO_CLIENT_VERSION` and `TTO_CLIENT_DOWNLOAD_ANDROID` in a deployment's `.env`, which the
 * release workflow prints into its job summary for a human to copy across. Every release therefore
 * has a window in which the APK exists and no server knows it does, and a self-hosted deployment
 * may never be updated at all. The releases page is where the artifact actually is, so it is the
 * one source that cannot be stale.
 *
 * The two are not in competition: a server that says "this build is too old to serve" is still the
 * only thing that can say so, and it keeps saying it. This adds the case the server cannot cover —
 * *there is a newer build than yours* — which is a suggestion and never a refusal.
 *
 * ### Still not an updater
 *
 * `ClientRelease`'s own KDoc makes the argument and it is unchanged here: nothing downloads or runs
 * anything. This resolves a **URL**, the notice offers it, and the player decides. Fetching and
 * executing a binary would need signed artifacts and a per-platform installer handoff, and is
 * forbidden outright by the stores that own updates on two of the three targets.
 *
 * ### An anonymous request, and what that costs
 *
 * `/releases/latest` on a public repository needs no token, which is the property that makes this
 * possible at all — see `settings.gradle.kts` for the opposite case, where GitHub Packages answers
 * an anonymous read with a 401. Unauthenticated API requests are rate-limited per source address
 * to sixty an hour, so this is asked **once per launch** and never on a timer. A player behind a
 * shared address who is refused simply does not see the notice, which is why every failure here is
 * a null rather than anything a screen has to render.
 *
 * @property repository `owner/name`. A parameter rather than a constant so a fork checks its own
 *   releases instead of reporting this one's as an update to itself.
 */
class GithubReleaseClient(
    private val client: HttpClient,
    private val repository: String = DEFAULT_REPOSITORY,
) : ReleaseSource {
    /**
     * The newest published release, or null if there is none this build can make sense of.
     *
     * Null covers every failure and they are all ordinary: no network, a rate limit, a repository
     * with no releases yet, a tag that is not a version. None of them is worth a message — the
     * player did not ask, and "we could not check for updates" is a notification about nothing.
     */
    // TooGenericExceptionCaught: the same contract as `AccountClient.guard`, and the same reasons.
    // What a dead network throws is the platform's business, not Ktor's, and a body that will not
    // decode is a captive portal rather than a crash.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun latest(): ClientRelease? = try {
        val response = client.get("$API_BASE/repos/$repository/releases/latest") {
            accept(ContentType.Application.Json)
            // GitHub asks callers to pin the API version, and answers an unpinned request with
            // whatever is current — which is how a client starts failing on a schema change it was
            // never told about.
            header(API_VERSION_HEADER, API_VERSION)
        }
        if (!response.status.isSuccess()) {
            error("${response.status} from $repository")
        }
        Format.decodeFromString(GithubRelease.serializer(), response.bodyAsText())
            .toClientRelease()
    } catch (cancellation: CancellationException) {
        // Never swallowed: the app is going away, which is not GitHub being unreachable.
        throw cancellation
    } catch (failure: Exception) {
        Log.i(TAG) { "could not read the latest release of $repository: $failure" }
        null
    }

    private companion object {
        /**
         * Read here rather than through content negotiation, and decoded through the generated
         * serializer rather than a reified `body<T>()`.
         *
         * Two reasons, and both are load-bearing. The response carries some forty fields this build
         * does not read, so `ignoreUnknownKeys` is not optional — and the client passed in is the
         * app's, configured for **this protocol**, whose settings are not this endpoint's to
         * borrow. And [GithubRelease] is `internal`: a reified `body<T>()` resolves its serializer
         * reflectively, which fails on an internal class with an `IllegalAccessException` reaching
         * its companion. Naming the serializer makes it a direct call and the question moot.
         */
        val Format = Json { ignoreUnknownKeys = true }

        const val TAG = "Releases"
        const val API_BASE = "https://api.github.com"
        const val API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val API_VERSION = "2022-11-28"

        /** Where this build's own APKs are published — see `.github/workflows/release.yml`. */
        const val DEFAULT_REPOSITORY = "korobetski/tto-client"
    }
}

/**
 * The half of GitHub's release object this build reads.
 *
 * `ignoreUnknownKeys` is already on the shared Ktor JSON configuration, which matters more here
 * than anywhere else in the app: the response carries some forty fields and every one of them is
 * somebody else's to change.
 */
@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String? = null,
    val name: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
) {
    /**
     * This release as a [ClientRelease], or null when the tag is not a version.
     *
     * Drafts and pre-releases are refused rather than offered. `/releases/latest` already excludes
     * both, so this is belt and braces — and it is the belt that matters: being told to install a
     * pre-release is being told to install something that was published precisely because nobody
     * had tried it yet.
     */
    fun toClientRelease(): ClientRelease? {
        // `v1.0.2` is the tag the release workflow pushes; the `v` is not part of the number.
        val version = AppVersion.parse(tagName.removePrefix(TAG_PREFIX))
        if (draft || prerelease || version == null) return null
        val apk = assets.firstOrNull { it.name.endsWith(APK_SUFFIX) }

        return ClientRelease(
            version = version,
            // Android gets the APK itself; anything else gets the release page, which is where a
            // desktop build will be attached when there is one to attach. Never the APK for a
            // platform that cannot install it — `downloadForThisPlatform` is what a Download
            // button opens, and offering a phone package to a desktop is offering a dead end.
            downloads = buildMap {
                apk?.let { put(ClientPlatform.ANDROID, it.browserDownloadUrl) }
                htmlUrl?.let {
                    put(ClientPlatform.DESKTOP, it)
                    put(ClientPlatform.IOS, it)
                }
            },
            notes = name?.takeIf { it.isNotBlank() && it != tagName },
        )
    }
}

/** The `v` of `v1.0.2`, which is the tag's and not the version's. */
private const val TAG_PREFIX = "v"

/** How the Android artifact is recognised among a release's assets. */
private const val APK_SUFFIX = ".apk"

@Serializable
internal data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

/**
 * The release number this build was compiled with, or null if it is not a version.
 *
 * Null is unreachable while `:androidApp` validates the same property at configuration time, and
 * is handled rather than asserted because the alternative is an app that refuses to start over a
 * string it only wanted to print.
 */
val runningVersion: AppVersion? by lazy { AppVersion.parse(CLIENT_VERSION) }

/**
 * Whether [this] is worth telling the player about.
 *
 * A *newer* release only. Equal is the ordinary case and older happens during a rollout — an APK
 * built from a tag that has not been published yet, or a hand-built one — and neither is an update.
 * Unknown ([runningVersion] null) is treated as up to date: a build that cannot say what it is has
 * no business claiming it is behind.
 */
fun ClientRelease.isNewerThanRunning(): Boolean {
    val running = runningVersion ?: return false
    return version > running
}
