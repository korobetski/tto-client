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

/**
 * Playing against another person, over the server.
 *
 * ### Why there is no engine on this side
 *
 * Every other match this client plays is run locally: `MatchState` is here, the AI is here, and the
 * server only checks the transcript afterwards. A PvP match is the opposite — the server holds the
 * one state and this client holds a **view** of it. It asks what it may see, sends a slot and a
 * square, and is told what happened.
 *
 * That is not a preference about architecture. If this client held both hands, the only thing
 * protecting the opponent's cards would be this client choosing not to draw them, and a modified
 * build would see everything with nothing anywhere to show for it.
 *
 * ### The same contract as [AccountClient]
 *
 * Nothing here throws for a network failure, every method returns an [AccountResult], and the
 * base URL is read per request because the player can change servers mid-session. A reader who
 * knows one of these two clients knows both.
 */
class PvpClient(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
    private val version: AppVersion = CURRENT_VERSION,
) {

    /**
     * Every table currently on offer, this player's own included.
     *
     * Replaces a `POST /pvp/queue` that both joined a queue and took whoever was in it. That was
     * one call because it was one *action* — find me a match — and because the client could not
     * choose between the two halves without seeing a queue it had no business seeing. Neither
     * argument survives a match having terms: a player paired into a wager they never saw has not
     * agreed to it. So the lobby is readable, and choosing is the client's job again.
     */
    suspend fun tables(token: String): AccountResult<List<PvpTable>> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/pvp/tables") {
                protocolHeaders()
                bearer(token)
            }
        }

    /** Opens one, on the terms this player is offering. */
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

    /** Withdraws it. */
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

    /** Joins one, which opens the match. */
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

    /** The invitations standing in either direction. */
    suspend fun challenges(token: String): AccountResult<List<PvpChallenge>> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/pvp/challenges") {
                protocolHeaders()
                bearer(token)
            }
        }

    /** Invites a named player, on stated terms. */
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

    /** Accepts an invitation, which opens the match. */
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

    /** Declines an invitation, or withdraws one. */
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

    /**
     * The match in progress, or `Ok(null)` when there is none.
     *
     * **Null is an answer, not an absence**, which is why it is inside an `Ok` rather than being a
     * failure: "you are not in a match" is the normal state of a player who is not in a match, and
     * a caller that had to distinguish it from "the server is unreachable" by the shape of an error
     * would get it wrong the first time the network blinked.
     *
     * This is also the call that makes a match survive the app being killed — which mobile does
     * without asking. It takes no id: the client says who it is and the server says what it is
     * doing.
     */
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

    /**
     * Places a card.
     *
     * A refusal is not an error to hide: the server is the referee, so "it is not your turn" or
     * "that move is not allowed" mean this client's view was stale, and the caller's response is to
     * poll again rather than to complain. Both arrive as [AccountResult.Failed] with a 409.
     */
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

    /** Concedes: the same settlement running out of time produces, chosen rather than suffered. */
    suspend fun forfeit(token: String, matchId: String): AccountResult<PvpMatchView> =
        call(HTTP_OK) {
            client.post("${baseUrl()}/pvp/match/$matchId/forfeit") {
                protocolHeaders()
                bearer(token)
            }
        }

    /**
     * Matches won and not yet collected.
     *
     * Asked separately from [currentMatch] because that one answers with the *newest* match, so a
     * player who started another game would have an uncollected prize hidden behind it — and would
     * lose it when the server's deadline passed and picked for them.
     */
    suspend fun claims(token: String): AccountResult<List<PvpMatchView>> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/pvp/claims") {
                protocolHeaders()
                bearer(token)
            }
        }

    /** Names the cards taken, under the One and Diff trade rules. */
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

    /**
     * The failure this response describes.
     *
     * The PvP routes refuse things that are about a *match* rather than about an account —
     * `AccountError` has no member meaning "it is not your turn" — so they answer with their own
     * [PvpRefusal] vocabulary, and it comes back as [AccountResult.RefusedPvp].
     *
     * The **code** is what matters here, and it took a release to arrive: the routes used to send
     * only a reason, in English, which is a sentence no screen in a four-language game can show.
     * A refusal a client cannot render is a refusal that vanishes, which is what happened.
     */
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

        /** 409, and the two things it means. See [play]. */
        const val HTTP_CONFLICT = 409
    }
}

/** What the server is asked for when this client wants to challenge somebody. */
@Serializable
internal data class ChallengeRequest(val username: String, val terms: PvpTableRequest)

/**
 * A refusal, as the shape every PvP 4xx answers with.
 *
 * [code] is what the UI switches on; [reason] is the server's own English sentence, kept for logs.
 */
@Serializable
internal data class Refusal(val code: PvpRefusal, val reason: String)
