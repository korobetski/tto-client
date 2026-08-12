package com.tripletriad.net

import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.PvpChallenge
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpMove
import com.tripletriad.protocol.PvpQueueState
import com.tripletriad.protocol.PvpStake
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
     * Joins the quick queue, or takes whoever was already waiting in it.
     *
     * One call for both, because from the player's side it is one action — find me a match — and
     * the client cannot know which of the two it is doing without seeing a queue it has no business
     * seeing.
     */
    suspend fun queue(token: String): AccountResult<PvpQueueState> =
        call(HTTP_OK) {
            client.post("${baseUrl()}/pvp/queue") {
                protocolHeaders()
                bearer(token)
            }
        }

    /** Leaves the queue. */
    suspend fun leaveQueue(token: String): AccountResult<PvpQueueState> =
        call(HTTP_OK) {
            client.delete("${baseUrl()}/pvp/queue") {
                protocolHeaders()
                bearer(token)
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

    /** Invites a named player, optionally for a card. */
    suspend fun challenge(
        token: String,
        username: String,
        stake: PvpStake = PvpStake.None,
    ): AccountResult<PvpChallenge> =
        call(HTTP_CREATED) {
            client.post("${baseUrl()}/pvp/challenges") {
                protocolHeaders()
                bearer(token)
                setBody(ChallengeRequest(username, stake))
            }
        }

    /** Accepts an invitation, which opens the match. */
    suspend fun accept(token: String, challengeId: String): AccountResult<PvpQueueState> =
        call(HTTP_CREATED) {
            client.post("${baseUrl()}/pvp/challenges/$challengeId/accept") {
                protocolHeaders()
                bearer(token)
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
     * Unlike [AccountClient], the PvP routes answer with a plain reason rather than an
     * `AccountFailure` — they refuse things that are about a *match*, not about an account, and
     * `AccountError` has no member meaning "it is not your turn". So the status is what a caller
     * reads, and the reason is carried as detail for the log.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> HttpResponse.toFailure(): AccountResult<T> {
        if (status.value == HTTP_UPGRADE_REQUIRED) {
            return AccountResult.UpdateRequired(headers[VERSION_HEADER]?.let(AppVersion::parse))
        }
        return try {
            AccountResult.Failed(status.value, body<Refusal>().reason)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
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
        const val DETAIL_LIMIT = 500

        /** 409, and the two things it means. See [play]. */
        const val HTTP_CONFLICT = 409
    }
}

/** What the server is asked for when this client wants to challenge somebody. */
@Serializable
internal data class ChallengeRequest(val username: String, val stake: PvpStake = PvpStake.None)

/** A refusal a player can read — the shape every PvP 4xx answers with. */
@Serializable
internal data class Refusal(val reason: String)
