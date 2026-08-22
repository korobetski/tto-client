package com.tripletriad.net

import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.PveFailure
import com.tripletriad.protocol.PveMatchRequest
import com.tripletriad.protocol.PveMatchView
import com.tripletriad.protocol.PveMove
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
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

/**
 * The four calls a match against an opponent is made of.
 *
 * ### Why this exists where [KtorMatchSubmitter] used to be enough
 *
 * A solo match used to be played entirely here and *reported* afterwards: one call, at the end,
 * carrying a transcript the server replayed. That worked, and it is being retired because replaying
 * required this client to be running the same AI from the same seed — so it held the opponent's
 * five cards and knew every move they would make from the first placement. Nothing in the
 * transcript showed it, because the match really did happen as claimed.
 *
 * Now the server holds the match and this asks it questions. The client no longer runs the engine
 * for a solo game, no longer credits itself, and is never sent a card it is not entitled to see.
 *
 * ### One round trip per placement
 *
 * [play] answers with the opponent's reply already made — see [PveMatchView.plays], which carries
 * both placements. There is no polling loop here and there should not be one: against a program the
 * answer exists the moment the question is asked, and a client that came back for it would put a
 * round trip in front of every turn.
 *
 * ### Losing the connection is not losing the match
 *
 * Every call answers with [AccountResult.Offline] rather than throwing, and none of them needs a
 * retry queue behind it. The match is on the server; a failed request means the screen has nothing
 * new to draw, not that anything was lost. [current] is what picks it back up, and it is the only
 * recovery there is.
 */
class PveClient(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
    private val version: AppVersion = CURRENT_VERSION,
) {

    /**
     * Sits down against an opponent.
     *
     * @param deck a **slot** in the player's own decks, not five card ids. The server resolves it
     *   against the profile it holds, so a client can choose which deck to bring and still cannot
     *   name a card it does not own. [ANY_DECK] means no choice, which lands on the first complete
     *   and affordable one.
     */
    suspend fun open(
        token: String,
        opponentIconId: String,
        formatId: String,
        deck: Int = ANY_DECK,
        campaignKey: String? = null,
    ): AccountResult<PveMatchView> =
        call(HTTP_CREATED) {
            client.post("${baseUrl()}/pve/matches") {
                protocolHeaders()
                bearer(token)
                setBody(PveMatchRequest(opponentIconId, formatId, deck, campaignKey))
            }
        }

    /**
     * The match in progress, or null when there is none. **This is resuming.**
     *
     * Called on launch and after any reconnection. It takes no id because the client does not need
     * to have kept one: the question is "what am I doing", and the server is the only thing that
     * knows. A killed application, a tunnel and a flat battery are the same event, and none of them
     * is an abandon.
     */
    suspend fun current(token: String): AccountResult<PveMatchView?> = guard {
        val response = client.get("${baseUrl()}/pve/matches/active") {
            protocolHeaders()
            bearer(token)
        }
        when (response.status.value) {
            HTTP_NO_CONTENT -> AccountResult.Ok(null)
            HTTP_OK -> AccountResult.Ok(response.body<PveMatchView>())
            else -> response.toFailure()
        }
    }

    /** One match by id, for a screen that already knows which one it is holding. */
    suspend fun match(token: String, matchId: String): AccountResult<PveMatchView> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/pve/matches/$matchId") {
                protocolHeaders()
                bearer(token)
            }
        }

    /** Places a card. The answer already contains the opponent's reply. */
    suspend fun play(
        token: String,
        matchId: String,
        move: PveMove,
    ): AccountResult<PveMatchView> =
        call(HTTP_OK) {
            client.post("${baseUrl()}/pve/matches/$matchId/moves") {
                protocolHeaders()
                bearer(token)
                setBody(move)
            }
        }

    // ---- The same three helpers [PvpClient] has, and deliberately identical ----

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
        if (status.value == HTTP_TOO_MANY_REQUESTS) {
            return AccountResult.Throttled(headers[HttpHeaders.RetryAfter]?.toLongOrNull())
        }
        return try {
            val refusal = body<PveFailure>()
            AccountResult.RefusedPve(refusal.code, refusal.detail)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Not a refusal this server knows how to name: a proxy's error page, a 500, a body that
            // did not parse. Reported as what it is rather than guessed at.
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
    }
}
