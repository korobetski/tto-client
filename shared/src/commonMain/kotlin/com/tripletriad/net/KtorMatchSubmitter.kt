package com.tripletriad.net

import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.MatchReceipt
import com.tripletriad.protocol.MatchSubmitter
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.SubmissionResult
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class KtorMatchSubmitter(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
    private val token: suspend () -> String?,
    private val version: AppVersion = CURRENT_VERSION,
) : MatchSubmitter {

    // The broad catch is the feature, not an oversight: no network failure may lose a match the
    // player really played. Enumerating Ktor's exception types would be a list that goes stale
    // without anything failing to tell us. `CancellationException` is rethrown first, which is the
    // one case a blanket catch must never swallow.
    // The early returns are the point too: "nobody is signed in" and "the transport failed" are
    // answers, and threading them through a single exit would mean a nullable holding the result
    // of a call that was never made.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override suspend fun submit(transcript: MatchTranscript): SubmissionResult {
        // Not a round trip to be told what is already known. The transcript stays queued — see
        // `TranscriptQueue.drain` — so signing in later still collects it.
        val bearer = token() ?: return SubmissionResult.Unauthenticated

        val response = try {
            client.post("${baseUrl()}/matches/submit") {
                contentType(ContentType.Application.Json)
                header(VERSION_HEADER, version.toString())
                bearer(bearer)
                setBody(transcript)
            }
        } catch (cancellation: CancellationException) {
            // Never swallowed: the caller navigated away or the scope died, and reporting that as
            // "the server is offline" would queue a transcript nobody asked to submit.
            throw cancellation
        } catch (failure: Exception) {
            // Everything else the transport can raise — DNS, refused connection, timeout, TLS.
            // Deliberately broad: the point is that *no* network failure loses the match, and
            // enumerating Ktor's exception types would be a list that goes stale silently.
            return SubmissionResult.Offline(failure.message ?: failure.toString())
        }

        return response.toResult()
    }

    private suspend fun HttpResponse.toResult(): SubmissionResult = when (status.value) {
        HTTP_OK -> readReceipt()

        // The session is gone: expired, signed out elsewhere, or swept by a redeploy. Not a verdict
        // about the match, so the transcript survives it and a sign-in makes it creditable again.
        HTTP_UNAUTHORIZED -> SubmissionResult.Unauthenticated

        // The server said this build is too old. Its own version comes back in the same header it
        // rejected ours by, so the log can say which two numbers disagreed rather than only that
        // they did.
        HTTP_UPGRADE_REQUIRED -> SubmissionResult.UpdateRequired(
            headers[VERSION_HEADER]?.let(AppVersion::parse),
        )

        else -> SubmissionResult.Failed(status.value, bodyAsText().take(DETAIL_LIMIT))
    }

    // Same reasoning as `submit`: a 200 that will not decode is a captive portal or a server this
    // build does not understand, and both are results rather than crashes.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun HttpResponse.readReceipt(): SubmissionResult = try {
        SubmissionResult.Judged(body<MatchReceipt>())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        SubmissionResult.Failed(status.value, "unreadable receipt: ${failure.message}")
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_UPGRADE_REQUIRED = 426

        const val DETAIL_LIMIT = 500
    }
}

val matchProtocolJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun matchSubmitterHttpClient(engineFactory: HttpClientEngineFactory<*>): HttpClient =
    HttpClient(engineFactory) {
        // Not `expectSuccess = true`: a non-2xx here is an answer to translate, not an exception
        // to throw. 426 in particular is a documented outcome of a healthy exchange.
        expectSuccess = false
        install(ContentNegotiation) {
            json(matchProtocolJson)
        }
    }
