package com.tripletriad.net

import com.tripletriad.protocol.AccountFailure
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.AuctionLotRequest
import com.tripletriad.protocol.AuctionOutcome
import com.tripletriad.protocol.AuctionPage
import com.tripletriad.protocol.BidRequest
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ListCardRequest
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
 * The auction house over HTTP.
 *
 * ### Why almost nothing here has a failure case
 *
 * Every write answers `200` with an [AuctionOutcome], and a *refusal* is a field inside it rather
 * than a status code — somebody outbidding you while you were typing is the normal case near the
 * end of a lot, not a fault. So the [AccountResult] this returns is `Ok` for both "your bid stands"
 * and "your bid was too low", and `AuctionSession` is what tells them apart. What is left in the
 * failure cases is what it says: no server, a stale build, a throttle, a gate.
 *
 * The one exception is the gate. Trading is refused with a `403` and an [AccountFailure] naming
 * the level or the unconfirmed address, the same shape every other gated door answers with, which
 * is why [toFailure] is [AccountClient]'s rather than [PvpClient]'s: those refusals are about the
 * *account*, and the auction's own refusals never come through here at all.
 *
 * ### Why there is no anonymous read
 *
 * Both reads carry the token even though looking is not gated on the level, because both answers
 * are *about* the caller: `yours`, `youLead` and `yourBid` are fields on every lot. A signed-out
 * board would be a different screen, and this game has no signed-out screens.
 */
class AuctionClient(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
    private val version: AppVersion = CURRENT_VERSION,
) {

    /**
     * The open lots, soonest to close first.
     *
     * @param cardId narrows to one card, for the "what else is a Ifrit going for" question a
     *   seller asks before naming a price. Null is the whole board.
     */
    suspend fun browse(token: String, cardId: Int? = null): AccountResult<AuctionPage> =
        call(HTTP_OK) {
            val board = "${baseUrl()}/auctions"
            client.get(if (cardId == null) board else "$board?card=$cardId") {
                protocolHeaders()
                bearer(token)
            }
        }

    /** Everything this account has a stake in, finished lots included. */
    suspend fun mine(token: String): AccountResult<AuctionPage> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/auctions/mine") {
                protocolHeaders()
                bearer(token)
            }
        }

    /** Opens a lot. The card leaves the collection and the listing fee leaves the purse. */
    suspend fun list(token: String, request: ListCardRequest): AccountResult<AuctionOutcome> =
        write(token, "", request)

    /** Bids. The amount and its fee leave the purse now and come back if somebody outbids. */
    suspend fun bid(token: String, request: BidRequest): AccountResult<AuctionOutcome> =
        write(token, "/bid", request)

    /** Withdraws a lot nobody has bid on. */
    suspend fun withdraw(token: String, request: AuctionLotRequest): AccountResult<AuctionOutcome> =
        write(token, "/cancel", request)

    /** Takes a bid that fell short of the reserve. */
    suspend fun accept(token: String, request: AuctionLotRequest): AccountResult<AuctionOutcome> =
        write(token, "/accept", request)

    /** Refuses one. The card comes back and the bidder is made whole. */
    suspend fun decline(token: String, request: AuctionLotRequest): AccountResult<AuctionOutcome> =
        write(token, "/decline", request)

    private suspend inline fun <reified T> write(
        token: String,
        path: String,
        body: T,
    ): AccountResult<AuctionOutcome> =
        call(HTTP_OK) {
            client.post("${baseUrl()}/auctions$path") {
                protocolHeaders()
                bearer(token)
                setBody(body)
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
        // Before the body is read, because there is no body to read — see `AccountClient`. It
        // matters more here than there: the auction bucket is thirty a minute across listing,
        // bidding and deciding, and a player refreshing a lot they are losing will find it.
        if (status.value == HTTP_TOO_MANY_REQUESTS) {
            return AccountResult.Throttled(headers[HttpHeaders.RetryAfter]?.toLongOrNull())
        }
        return try {
            AccountResult.Refused(body<AccountFailure>())
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
        const val HTTP_UPGRADE_REQUIRED = 426
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val DETAIL_LIMIT = 500
    }
}
