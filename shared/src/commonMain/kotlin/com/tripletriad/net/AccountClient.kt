package com.tripletriad.net

import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.protocol.AccountError
import com.tripletriad.protocol.AccountFailure
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.BagItemRequest
import com.tripletriad.protocol.BuyRequest
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ClaimStarterRequest
import com.tripletriad.protocol.Credentials
import com.tripletriad.protocol.EnterCampaignRequest
import com.tripletriad.protocol.Idempotent
import com.tripletriad.protocol.ItemUsed
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.PvpRefusal
import com.tripletriad.protocol.SeedTickets
import com.tripletriad.protocol.SellCardRequest
import com.tripletriad.protocol.Session
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Registering, signing in, signing out, and asking the server who you are.
 *
 * ### What this type is for
 *
 * With the account replacing the local profile, this is where a player's whole existence comes
 * from: the save the game renders, the MGP the shop spends, the collection the deck builder shows.
 * The client no longer *has* a profile — it has a session, and the profile is what the server hands
 * back when it is used.
 *
 * ### Nothing here throws for a network failure
 *
 * The same contract as [KtorMatchSubmitter], for the same reason: a dead server is an ordinary
 * state of the world, and a sign-in form must be able to say "the server is unreachable" rather
 * than crash behind it. Every method returns an [AccountResult].
 *
 * @property baseUrl where the server is, without a trailing slash — read **per request** rather
 *   than held, because the player can change servers while the app is running and a client built
 *   with an address would keep talking to the one that was selected when the screen was composed.
 *   The same reasoning as [KtorMatchSubmitter]'s token.
 */
class AccountClient(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
    private val version: AppVersion = CURRENT_VERSION,
) {

    /** Creates an account and signs into it, in one round trip. */
    suspend fun register(credentials: Credentials): AccountResult<Session> =
        call(HTTP_CREATED) {
            client.post("${baseUrl()}/accounts") {
                protocolHeaders()
                setBody(credentials)
            }
        }

    /** Signs in to an existing account. */
    suspend fun signIn(credentials: Credentials): AccountResult<Session> =
        call(HTTP_OK) {
            client.post("${baseUrl()}/sessions") {
                protocolHeaders()
                setBody(credentials)
            }
        }

    /**
     * The profile and stats the server holds for [token] — what a returning player is shown.
     *
     * This is the call that makes a stored session worth storing. It is also the one that discovers
     * a session has stopped being honoured, which is why [AccountError.UNAUTHENTICATED] is a
     * modelled outcome rather than an error string: the caller's response is to clear the stored
     * token and show the sign-in form, and it should not have to parse prose to know that.
     */
    suspend fun me(token: String): AccountResult<PlayerState> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/me") {
                protocolHeaders()
                bearer(token)
            }
        }

    /**
     * Stores a profile the player changed outside a match — a purchase, a deck, an item used.
     *
     * Fire-and-hope is **not** acceptable here even though the result is usually ignored: a failed
     * write means the card the player just bought is not theirs yet, and the caller has to be able
     * to find that out. What it must not do is block the shop behind a spinner, which is why this
     * is a separate call from the one that renders the screen.
     */
    suspend fun saveProfile(token: String, save: GameSave): AccountResult<Unit> = guard {
        val response = client.put("${baseUrl()}/me/save") {
            protocolHeaders()
            bearer(token)
            setBody(save)
        }
        when (response.status.value) {
            HTTP_NO_CONTENT, HTTP_OK -> AccountResult.Ok(Unit)
            else -> response.toFailure()
        }
    }

    /**
     * Uses something from the bag, and lets the server roll it.
     *
     * The one call in this client that deliberately gives up a local computation. `Inventory.use`
     * is a pure `:core` function this client could run in a microsecond — and that is exactly the
     * problem, because running it locally means holding the dice for a booster. See
     * `BagItemRequest`.
     *
     * [operationId] is the caller's id for the *intent*, not for this attempt: retrying with the
     * same one returns the first answer rather than opening a second pack.
     */
    suspend fun useItem(
        token: String,
        item: Item,
        operationId: String,
    ): AccountResult<ItemUsed> =
        call(HTTP_OK) {
            client.post("${baseUrl()}/me/bag/use") {
                protocolHeaders()
                bearer(token)
                setBody(BagItemRequest(item, operationId))
            }
        }

    /**
     * The five intents whose whole answer is the profile the server wrote.
     *
     * One function rather than five, because they differ in exactly two things — the path and the
     * body — and the shape of a "here is what I want, tell me what happened" call is not worth
     * writing out five times. [useItem] stays separate because its answer is bigger: a pack has
     * contents to report as well as a profile to return.
     *
     * The **prices are absent from every one of these bodies**, deliberately. See `BuyRequest`.
     */
    suspend fun buy(
        token: String,
        item: Item,
        formatId: String,
        operationId: String,
    ): AccountResult<PlayerState> =
        intent(token, "/me/shop/buy", BuyRequest(item, formatId, operationId))

    /** Sells a bag item for what the card table says it is worth. */
    suspend fun sellItem(
        token: String,
        item: Item,
        operationId: String,
    ): AccountResult<PlayerState> =
        intent(token, "/me/bag/sell", BagItemRequest(item, operationId))

    /** Throws a bag item away. Nothing is paid. */
    suspend fun discardItem(
        token: String,
        item: Item,
        operationId: String,
    ): AccountResult<PlayerState> =
        intent(token, "/me/bag/discard", BagItemRequest(item, operationId))

    /** Sells a card out of the collection. */
    suspend fun sellCard(
        token: String,
        cardId: Int,
        operationId: String,
    ): AccountResult<PlayerState> =
        intent(token, "/me/cards/sell", SellCardRequest(cardId, operationId))

    /**
     * Tops this account's stock of unspent seeds up, and returns everything it now holds.
     *
     * A `GET` that writes, and safe as one: the server issues the *difference* between what the
     * account holds and the ceiling, so calling it twice in a row issues nothing the second time.
     * A client may therefore ask whenever it notices it is low, including after a response it never
     * saw, with no operation id and no consequence.
     */
    suspend fun tickets(token: String): AccountResult<SeedTickets> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/matches/tickets") {
                protocolHeaders()
                bearer(token)
            }
        }

    /** Claims the box a destitute profile is owed. See `ClaimStarterRequest`. */
    suspend fun claimStarter(token: String, operationId: String): AccountResult<PlayerState> =
        intent(token, "/me/starter", ClaimStarterRequest(operationId))

    /** Pays a ladder's entry fee. The amount is the server's — see `EnterCampaignRequest`. */
    suspend fun enterCampaign(
        token: String,
        campaignKey: String,
        operationId: String,
    ): AccountResult<PlayerState> =
        intent(token, "/me/campaign/enter", EnterCampaignRequest(campaignKey, operationId))

    private suspend inline fun <reified T : Idempotent> intent(
        token: String,
        path: String,
        request: T,
    ): AccountResult<PlayerState> =
        call(HTTP_OK) {
            client.post("${baseUrl()}$path") {
                protocolHeaders()
                bearer(token)
                setBody(request)
            }
        }

    /**
     * Ends this session on the server.
     *
     * The caller clears its own stored token **regardless** of what this returns. A sign-out that
     * failed because the server was unreachable still has to sign the player out of the app in
     * front of them; leaving them signed in until the network comes back would be a strange answer
     * to a button they pressed.
     */
    suspend fun signOut(token: String): AccountResult<Unit> = guard {
        val response = client.delete("${baseUrl()}/sessions") {
            protocolHeaders()
            bearer(token)
        }
        when (response.status.value) {
            HTTP_NO_CONTENT, HTTP_OK -> AccountResult.Ok(Unit)
            else -> response.toFailure()
        }
    }

    /**
     * Runs [request] and decodes [T] from it when the status is [expected].
     *
     * One place where a response becomes a result, so that "which status means success" is stated
     * per endpoint and everything else — the failures, the version gate, the unreadable body — is
     * handled identically for all of them.
     */
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

    /**
     * Turns anything the transport can raise into [AccountResult.Offline].
     *
     * As broad as [KtorMatchSubmitter]'s catch and for the same reason — the failures are the
     * platform's, not Ktor's, and a list of them goes stale without anything saying so. A body that
     * will not decode lands here too: a 200 that is not the shape this build expects is a captive
     * portal or a newer server, and neither is a crash.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <T> guard(
        block: () -> AccountResult<T>,
    ): AccountResult<T> = try {
        block()
    } catch (cancellation: CancellationException) {
        // Never swallowed: the screen went away, which is not the server being unreachable.
        throw cancellation
    } catch (failure: Exception) {
        AccountResult.Offline(failure.message ?: failure.toString())
    }

    /**
     * The failure this response describes.
     *
     * The server sends an [AccountFailure] for everything it refuses on purpose, so the common case
     * is a decode. When that decode fails the status is reported instead — which covers a proxy, a
     * 500 with a stack trace, and anything else that is not this server talking.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> HttpResponse.toFailure(): AccountResult<T> {
        if (status.value == HTTP_UPGRADE_REQUIRED) {
            return AccountResult.UpdateRequired(headers[VERSION_HEADER]?.let(AppVersion::parse))
        }
        // Before the body is read, because there is no body to read: Ktor's rate limiter answers
        // with the status and `Retry-After` and nothing else, so decoding would fail and this would
        // arrive as a nameless `Failed`.
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

    private fun io.ktor.client.request.HttpRequestBuilder.protocolHeaders() {
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

/** Adds the bearer token. One function, so there is one place the header's spelling is decided. */
internal fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
    header(HttpHeaders.Authorization, "Bearer $token")
}

/**
 * What came of an account request.
 *
 * Shaped like [com.tripletriad.protocol.SubmissionResult] deliberately: the same four things can
 * happen — it worked, the server said no, the server could not be reached, this build is too old —
 * and a caller that has learned one should not have to learn the other.
 */
sealed interface AccountResult<out T> {

    /** It worked. */
    data class Ok<T>(val value: T) : AccountResult<T>

    /**
     * The server understood and refused: a taken name, wrong credentials, a dead session.
     *
     * Carries [AccountFailure] rather than a message, so the UI decides the wording and the code
     * decides the behaviour. `INVALID_CREDENTIALS` and `USERNAME_TAKEN` need different forms
     * focused on different fields; `UNAUTHENTICATED` needs the stored token thrown away.
     */
    data class Refused(val failure: AccountFailure) : AccountResult<Nothing>

    /** The server could not be reached. Worth retrying, and worth saying so plainly. */
    data class Offline(val cause: String) : AccountResult<Nothing>

    /** The server is a newer major version. See [com.tripletriad.protocol.AppVersion]. */
    data class UpdateRequired(val serverVersion: AppVersion?) : AccountResult<Nothing>

    /**
     * Asked too often. Worth retrying, but **not yet** — which is the whole difference.
     *
     * Its own case rather than a [Failed] with status 429, because the two need opposite handling:
     * a failure is a bug to report and this is a wait to observe. Collapsed into `Failed` it would
     * render as "something went wrong", which is both untrue and an invitation to tap again
     * immediately — turning a throttle into the load it was installed to shed.
     *
     * @property retryAfterSeconds what the server said, or null when it did not say. Null means
     *   "wait a bit", not "retry now".
     */
    data class Throttled(val retryAfterSeconds: Long?) : AccountResult<Nothing>

    /** Reached, and something went wrong anyway. A bug or a bad deploy, not a player's mistake. */
    data class Failed(val status: Int, val detail: String) : AccountResult<Nothing>

    /**
     * A player-versus-player request the server understood and refused.
     *
     * [Refused] is the same idea for the account API and is a separate case rather than a shared
     * one, because the two carry different vocabularies: `AccountError` is about who you are, and
     * [PvpRefusal] is about what you asked to do with a match. One type spanning both would be an
     * enum where half the members are unreachable from either half of the API.
     */
    data class RefusedPvp(val code: PvpRefusal, val detail: String) : AccountResult<Nothing>
}

/** The value if this worked, or null. For call sites that only care about the happy path. */
fun <T> AccountResult<T>.valueOrNull(): T? = (this as? AccountResult.Ok)?.value

/** Whether this is the server saying the session is dead — the one failure that clears storage. */
fun AccountResult<*>.isUnauthenticated(): Boolean =
    this is AccountResult.Refused && failure.error == AccountError.UNAUTHENTICATED
