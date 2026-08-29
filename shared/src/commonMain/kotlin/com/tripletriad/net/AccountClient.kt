package com.tripletriad.net

import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.protocol.AccountCode
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
import com.tripletriad.protocol.PasswordReset
import com.tripletriad.protocol.PasswordResetRequest
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.PveRefusal
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

class AccountClient(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
    private val version: AppVersion = CURRENT_VERSION,
) {

    /**
     * Creates the account, and asks for the confirmation mail in [language].
     *
     * The language travels as `Accept-Language` rather than in the body: HTTP already carries it,
     * and adding a field to `Credentials` would have been a protocol change to say something
     * twice. It is only ever consulted by the endpoints that *send* a mail — see the server's
     * `MailTemplates`, which falls back to English for anything it does not recognise, so passing
     * null costs an English mail and never a refusal.
     */
    suspend fun register(
        credentials: Credentials,
        language: String? = null,
    ): AccountResult<Session> =
        call(HTTP_CREATED) {
            client.post("${baseUrl()}/accounts") {
                protocolHeaders(language)
                setBody(credentials)
            }
        }

    /** Answers the code that was mailed. 204 and the account is confirmed. */
    suspend fun confirmEmail(token: String, code: String): AccountResult<Unit> =
        accepted(HTTP_NO_CONTENT) {
            client.post("${baseUrl()}/me/email/verify") {
                protocolHeaders()
                bearer(token)
                setBody(AccountCode(code))
            }
        }

    /**
     * Asks for another one, and invalidates the last.
     *
     * 202 whatever the state of the account, including one with no address at all — so `Ok` here
     * means *the request was taken*, not *a mail was sent*. Nothing on this side can know the
     * second, and a screen that claimed it would be lying to a player whose provider is down.
     */
    suspend fun resendCode(token: String, language: String? = null): AccountResult<Unit> =
        accepted(HTTP_ACCEPTED) {
            client.post("${baseUrl()}/me/email/resend") {
                protocolHeaders(language)
                bearer(token)
            }
        }

    /**
     * *I have forgotten my password.* Unauthenticated, by definition of the problem.
     *
     * Always 202, for an account that exists and one that does not alike — the server refuses to
     * turn this form into a way of asking which usernames are registered. So this cannot report
     * "no such player", and no screen above it should try to.
     */
    suspend fun forgotPassword(
        username: String,
        language: String? = null,
    ): AccountResult<Unit> =
        accepted(HTTP_ACCEPTED) {
            client.post("${baseUrl()}/accounts/password/forgot") {
                protocolHeaders(language)
                setBody(PasswordResetRequest(username))
            }
        }

    /** The code and the new password. Every session on the account ends with it, this one too. */
    suspend fun resetPassword(request: PasswordReset): AccountResult<Unit> =
        accepted(HTTP_NO_CONTENT) {
            client.post("${baseUrl()}/accounts/password/reset") {
                protocolHeaders()
                setBody(request)
            }
        }

    suspend fun signIn(credentials: Credentials): AccountResult<Session> =
        call(HTTP_OK) {
            client.post("${baseUrl()}/sessions") {
                protocolHeaders()
                setBody(credentials)
            }
        }

    suspend fun me(token: String): AccountResult<PlayerState> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/me") {
                protocolHeaders()
                bearer(token)
            }
        }

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

    suspend fun buy(
        token: String,
        item: Item,
        formatId: String,
        operationId: String,
    ): AccountResult<PlayerState> =
        intent(token, "/me/shop/buy", BuyRequest(item, formatId, operationId))

    suspend fun sellItem(
        token: String,
        item: Item,
        operationId: String,
    ): AccountResult<PlayerState> =
        intent(token, "/me/bag/sell", BagItemRequest(item, operationId))

    suspend fun sellAllItems(
        token: String,
        item: Item,
        operationId: String,
    ): AccountResult<PlayerState> =
        intent(token, "/me/bag/sell-all", BagItemRequest(item, operationId))

    suspend fun discardItem(
        token: String,
        item: Item,
        operationId: String,
    ): AccountResult<PlayerState> =
        intent(token, "/me/bag/discard", BagItemRequest(item, operationId))

    suspend fun sellCard(
        token: String,
        cardId: Int,
        operationId: String,
    ): AccountResult<PlayerState> =
        intent(token, "/me/cards/sell", SellCardRequest(cardId, operationId))

    suspend fun tickets(token: String): AccountResult<SeedTickets> =
        call(HTTP_OK) {
            client.get("${baseUrl()}/matches/tickets") {
                protocolHeaders()
                bearer(token)
            }
        }

    suspend fun claimStarter(token: String, operationId: String): AccountResult<PlayerState> =
        intent(token, "/me/starter", ClaimStarterRequest(operationId))

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

    suspend fun deleteAccount(
        token: String,
        credentials: Credentials,
    ): AccountResult<Unit> = guard {
        val response = client.delete("${baseUrl()}/accounts/me") {
            protocolHeaders()
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }
        when (response.status.value) {
            HTTP_NO_CONTENT, HTTP_OK -> AccountResult.Ok(Unit)
            else -> response.toFailure()
        }
    }

    /**
     * For the endpoints whose success has no body: the status *is* the answer.
     *
     * Separate from [call] rather than `call<Unit>`, because that would ask the content negotiator
     * to decode a `Unit` out of an empty body — which works by accident on some configurations and
     * throws on others, and either way asks a question nobody wanted answered.
     */
    private suspend inline fun accepted(
        expected: Int,
        crossinline request: suspend () -> HttpResponse,
    ): AccountResult<Unit> = guard {
        val response = request()
        if (response.status.value == expected) AccountResult.Ok(Unit) else response.toFailure()
    }

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
        // Never swallowed: the screen went away, which is not the server being unreachable.
        throw cancellation
    } catch (failure: Exception) {
        AccountResult.Offline(failure.message ?: failure.toString())
    }

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

    private fun io.ktor.client.request.HttpRequestBuilder.protocolHeaders(
        language: String? = null,
    ) {
        contentType(ContentType.Application.Json)
        header(VERSION_HEADER, version.toString())
        // `en_US` rather than `en-US`, which is what `AppLocale.tag` holds and is not quite the
        // header's own grammar. Left alone deliberately: the server reads the first two letters
        // and nothing else, so translating it here would be inventing a shape neither end wants.
        language?.let { header(HttpHeaders.AcceptLanguage, it) }
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val HTTP_ACCEPTED = 202
        const val HTTP_NO_CONTENT = 204
        const val HTTP_UPGRADE_REQUIRED = 426
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val DETAIL_LIMIT = 500
    }
}

internal fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
    header(HttpHeaders.Authorization, "Bearer $token")
}

sealed interface AccountResult<out T> {

    data class Ok<T>(val value: T) : AccountResult<T>

    data class Refused(val failure: AccountFailure) : AccountResult<Nothing>

    data class Offline(val cause: String) : AccountResult<Nothing>

    data class UpdateRequired(val serverVersion: AppVersion?) : AccountResult<Nothing>

    data class Throttled(val retryAfterSeconds: Long?) : AccountResult<Nothing>

    data class Failed(val status: Int, val detail: String) : AccountResult<Nothing>

    data class RefusedPvp(val code: PvpRefusal, val detail: String) : AccountResult<Nothing>

    /**
     * A refusal about a match against an opponent, named by the referee.
     *
     * Beside [RefusedPvp] rather than folded into it, though the two look alike. Their codes do not
     * overlap — there is no forfeit or claim against a program, and no undealable hand against a
     * person — so one member would carry an enum that half its readers cannot interpret, and the
     * compiler would stop pointing out the `when`s that forgot one of them.
     */
    data class RefusedPve(val code: PveRefusal, val detail: String) : AccountResult<Nothing>
}

fun <T> AccountResult<T>.valueOrNull(): T? = (this as? AccountResult.Ok)?.value

fun AccountResult<*>.isUnauthenticated(): Boolean =
    this is AccountResult.Refused && failure.error == AccountError.UNAUTHENTICATED
