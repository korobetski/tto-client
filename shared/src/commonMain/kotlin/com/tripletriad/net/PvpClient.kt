package com.tripletriad.net

import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.PvpChallenge
import com.tripletriad.protocol.PvpClaim
import com.tripletriad.protocol.PvpJoinRequest
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpMove
import com.tripletriad.protocol.PvpQueueState
import com.tripletriad.protocol.PvpRefusal
import com.tripletriad.protocol.PvpTable
import com.tripletriad.protocol.PvpTableRequest
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

class PvpClient(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
    private val version: AppVersion = CURRENT_VERSION,
) {

    suspend fun tables(token: String): AccountResult<List<PvpTable>> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/pvp/tables") {
                protocolHeaders()
                bearer(token)
            }
        }

    suspend fun openTable(
        token: String,
        request: PvpTableRequest,
    ): AccountResult<PvpTable> =
        call(HTTP_CREATED) {
            client.post("${baseUrl()}/pvp/tables") {
                protocolHeaders()
                bearer(token)
                setBody(request)
            }
        }

    suspend fun cancelTable(token: String, tableId: String): AccountResult<Unit> = guard {
        val response = client.delete("${baseUrl()}/pvp/tables/$tableId") {
            protocolHeaders()
            bearer(token)
        }
        if (response.status.value in setOf(HTTP_OK, HTTP_NO_CONTENT)) {
            AccountResult.Ok(Unit)
        } else {
            response.toFailure()
        }
    }

    suspend fun joinTable(
        token: String,
        tableId: String,
        deck: Int = ANY_DECK,
    ): AccountResult<PvpQueueState> =
        call(HTTP_CREATED) {
            client.post("${baseUrl()}/pvp/tables/$tableId/join") {
                protocolHeaders()
                bearer(token)
                setBody(PvpJoinRequest(deck))
            }
        }

    suspend fun challenges(token: String): AccountResult<List<PvpChallenge>> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/pvp/challenges") {
                protocolHeaders()
                bearer(token)
            }
        }

    suspend fun challenge(
        token: String,
        username: String,
        terms: PvpTableRequest,
    ): AccountResult<PvpChallenge> =
        call(HTTP_CREATED) {
            client.post("${baseUrl()}/pvp/challenges") {
                protocolHeaders()
                bearer(token)
                setBody(ChallengeRequest(username, terms))
            }
        }

    suspend fun accept(
        token: String,
        challengeId: String,
        deck: Int = ANY_DECK,
    ): AccountResult<PvpQueueState> =
        call(HTTP_CREATED) {
            client.post("${baseUrl()}/pvp/challenges/$challengeId/accept") {
                protocolHeaders()
                bearer(token)
                setBody(PvpJoinRequest(deck))
            }
        }

    suspend fun dropChallenge(token: String, challengeId: String): AccountResult<Unit> = guard {
        val response = client.delete("${baseUrl()}/pvp/challenges/$challengeId") {
            protocolHeaders()
            bearer(token)
        }
        if (response.status.value in setOf(HTTP_OK, HTTP_NO_CONTENT)) {
            AccountResult.Ok(Unit)
        } else {
            response.toFailure()
        }
    }

    suspend fun currentMatch(token: String): AccountResult<PvpMatchView?> = guard {
        val response = client.get("${baseUrl()}/pvp/match") {
            protocolHeaders()
            bearer(token)
        }
        when (response.status.value) {
            HTTP_NO_CONTENT -> AccountResult.Ok(null)
            HTTP_OK -> AccountResult.Ok(response.body<PvpMatchView>())
            else -> response.toFailure()
        }
    }

    suspend fun play(
        token: String,
        matchId: String,
        move: PvpMove,
    ): AccountResult<PvpMatchView> =
        call(HTTP_OK) {
            client.post("${baseUrl()}/pvp/match/$matchId/move") {
                protocolHeaders()
                bearer(token)
                setBody(move)
            }
        }

    suspend fun forfeit(token: String, matchId: String): AccountResult<PvpMatchView> =
        call(HTTP_OK) {
            client.post("${baseUrl()}/pvp/match/$matchId/forfeit") {
                protocolHeaders()
                bearer(token)
            }
        }

    suspend fun claims(token: String): AccountResult<List<PvpMatchView>> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/pvp/claims") {
                protocolHeaders()
                bearer(token)
            }
        }

    suspend fun claim(
        token: String,
        matchId: String,
        cardIds: List<Int>,
    ): AccountResult<PvpMatchView> =
        call(HTTP_OK) {
            client.post("${baseUrl()}/pvp/match/$matchId/claim") {
                protocolHeaders()
                bearer(token)
                setBody(PvpClaim(cardIds))
            }
        }

    // ---- The same three helpers [AccountClient] has, and deliberately identical ----

    private suspend inline fun <reified T> call(
        expected: Int,
        crossinline request: suspend () -> HttpResponse,
    ): AccountResult<T> = guard {
        val response = request()
        if (response.status.value == expected) {
            AccountResult.Ok(response.body<T>())
        } else {
            response.toFailure()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <T> guard(
        block: () -> AccountResult<T>,
    ): AccountResult<T> = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        AccountResult.Offline(failure.message ?: failure.toString())
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> HttpResponse.toFailure(): AccountResult<T> {
        if (status.value == HTTP_UPGRADE_REQUIRED) {
            return AccountResult.UpdateRequired(headers[VERSION_HEADER]?.let(AppVersion::parse))
        }
        // The lobby is throttled too — opening tables is cheap for the host and visible to everyone
        // else. Same reading as the account client's: a wait, not a fault. No `Refusal` body comes
        // with it, so this has to come before the decode.
        if (status.value == HTTP_TOO_MANY_REQUESTS) {
            return AccountResult.Throttled(headers[HttpHeaders.RetryAfter]?.toLongOrNull())
        }
        return try {
            val refusal = body<Refusal>()
            AccountResult.RefusedPvp(refusal.code, refusal.reason)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Not a refusal this server knows how to name: a proxy's error page, a 500, a body
            // that did not parse. Reported as what it is rather than guessed at.
            AccountResult.Failed(status.value, bodyAsText().take(DETAIL_LIMIT))
        }
    }

    private fun HttpRequestBuilder.protocolHeaders() {
        contentType(ContentType.Application.Json)
        header(VERSION_HEADER, version.toString())
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val HTTP_NO_CONTENT = 204
        const val HTTP_UPGRADE_REQUIRED = 426
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val DETAIL_LIMIT = 500

        const val HTTP_CONFLICT = 409
    }
}

@Serializable
internal data class ChallengeRequest(val username: String, val terms: PvpTableRequest)

@Serializable
internal data class Refusal(val code: PvpRefusal, val reason: String)
