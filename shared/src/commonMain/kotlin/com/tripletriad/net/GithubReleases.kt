package com.tripletriad.net

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

/** The repository this client is built from, and the only place its releases are published. */
const val PROJECT_REPOSITORY: String = "korobetski/tto-client"

/**
 * Where a player goes for a newer build when nothing more specific is on offer.
 *
 * ### Why there is always something
 *
 * A deployment fills [ClientRelease.downloads] when it knows the answer, and the answer really is
 * per-platform — a store listing on the phones, a file on the desktop. But the map can be empty,
 * the running platform can be missing from it, and `ServerStatus.Outdated` can arrive with no
 * [ClientRelease] at all. That last one is the case that matters: the notice is *blocking*, the
 * player is told this build cannot sign in, and the screen used to answer "so go and find it".
 *
 * The releases page is worse than a direct link and far better than nothing — it lists every
 * platform's artifact, and it stays correct without anybody remembering to update it. So
 * [UpdateAdvice.download] is not nullable: a notice that says a newer build exists always says
 * where.
 *
 * `/releases/latest` and not `/releases`: the page a player wants is the newest one, and GitHub
 * redirects it to whatever that currently is.
 */
val RELEASES_PAGE: String = "https://github.com/$PROJECT_REPOSITORY/releases/latest"

interface ReleaseSource {
    suspend fun latest(): ClientRelease?

    object None : ReleaseSource {
        override suspend fun latest(): ClientRelease? = null
    }
}

class GithubReleaseClient(
    private val client: HttpClient,
    private val repository: String = PROJECT_REPOSITORY,
) : ReleaseSource {
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
        val Format = Json { ignoreUnknownKeys = true }

        const val TAG = "Releases"
        const val API_BASE = "https://api.github.com"
        const val API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val API_VERSION = "2022-11-28"
    }
}

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String? = null,
    val name: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
) {
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

private const val TAG_PREFIX = "v"

private const val APK_SUFFIX = ".apk"

@Serializable
internal data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
