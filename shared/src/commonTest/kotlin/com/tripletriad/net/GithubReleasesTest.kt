package com.tripletriad.net

import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.ClientPlatform
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GithubReleasesTest {

    @Test
    fun theTagBecomesAVersionAndTheApkBecomesTheAndroidDownload() = runTest {
        val release = clientOver(body = RELEASE).latest()

        assertEquals(AppVersion(1, 0, 2), release?.version)
        assertEquals(APK, release?.downloads?.get(ClientPlatform.ANDROID))
    }

    @Test
    fun theOtherPlatformsGetThePageRatherThanTheAndroidPackage() = runTest {
        val downloads = clientOver(body = RELEASE).latest()?.downloads.orEmpty()

        for (platform in listOf(ClientPlatform.DESKTOP, ClientPlatform.IOS)) {
            assertEquals(PAGE, downloads[platform], platform.name)
        }
    }

    @Test
    fun theRequestGoesToTheConfiguredRepository() = runTest {
        val asked = mutableListOf<String>()
        clientOver(body = RELEASE, record = { asked += it.url.encodedPath })
            .latest()

        assertEquals(listOf("/repos/korobetski/tto-client/releases/latest"), asked)
    }

    @Test
    fun aForkAsksAboutItself() = runTest {
        val asked = mutableListOf<String>()
        clientOver(
            body = RELEASE,
            repository = "someone/fork",
            record = { asked += it.url.encodedPath },
        ).latest()

        assertEquals(listOf("/repos/someone/fork/releases/latest"), asked)
    }

    @Test
    fun aPrereleaseIsNotOffered() = runTest {
        val body = RELEASE.replace("\"prerelease\": false", "\"prerelease\": true")

        assertNull(clientOver(body = body).latest())
    }

    @Test
    fun aDraftIsNotOffered() = runTest {
        val body = RELEASE.replace("\"draft\": false", "\"draft\": true")

        assertNull(clientOver(body = body).latest())
    }

    @Test
    fun aTagThatIsNotAVersionIsIgnored() = runTest {
        assertNull(clientOver(body = RELEASE.replace("v1.0.2", "nightly")).latest())
    }

    @Test
    fun aReleaseWithNoApkStillNamesItsVersionAndItsPage() = runTest {
        val release = clientOver(body = RELEASE_WITHOUT_ASSETS).latest()

        assertEquals(AppVersion(1, 0, 2), release?.version)
        assertNull(release?.downloads?.get(ClientPlatform.ANDROID))
        assertTrue(release?.downloads?.containsKey(ClientPlatform.DESKTOP) == true)
    }

    @Test
    fun aRateLimitOrAnOutageIsSilent() = runTest {
        assertNull(clientOver(status = HttpStatusCode.Forbidden, body = "rate limited").latest())
        assertNull(clientOver(status = HttpStatusCode.NotFound, body = "no releases").latest())
        assertNull(clientOver(body = "this is not json").latest())
    }

    @Test
    fun theNoneSourceChecksNothing() = runTest {
        assertNull(ReleaseSource.None.latest())
    }

    private fun clientOver(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        repository: String = "korobetski/tto-client",
        record: (HttpRequestData) -> Unit = {},
    ): GithubReleaseClient {
        val engine = MockEngine { request ->
            record(request)
            if (status == HttpStatusCode.OK) respondJson(body) else respondError(status, body)
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return GithubReleaseClient(http, repository)
    }

    private fun MockRequestHandleScope.respondJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private companion object {
        const val PAGE = "https://github.com/korobetski/tto-client/releases/tag/v1.0.2"

        const val APK = "https://github.com/korobetski/tto-client/releases/" +
            "download/v1.0.2/tto-1.0.2.apk"

        const val ASSETS = """"assets": [
                { "name": "tto-1.0.2.apk", "browser_download_url": "$APK" }
              ],"""

        val RELEASE = """
            {
              "id": 12345,
              "node_id": "RE_kwDO",
              "tag_name": "v1.0.2",
              "name": "v1.0.2",
              "draft": false,
              "prerelease": false,
              "html_url": "$PAGE",
              $ASSETS
              "body": "generated notes"
            }
        """.trimIndent()

        val RELEASE_WITHOUT_ASSETS = """
            {
              "tag_name": "v1.0.2",
              "name": "v1.0.2",
              "draft": false,
              "prerelease": false,
              "html_url": "$PAGE",
              "assets": []
            }
        """.trimIndent()
    }
}
