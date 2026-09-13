package com.tripletriad.net

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js
import io.ktor.client.engine.js.JsClientEngineConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI
import kotlinx.io.IOException

internal actual fun defaultHttpEngineFactory(): HttpClientEngineFactory<*> = FetchEngine

/**
 * Ktor's `Js` engine, with one thing corrected: what a failed `fetch` throws.
 *
 * A request that never gets an answer — the network is down, the server is not listening, the
 * page's CSP refuses the address — reaches Kotlin as `kotlin.Error("Fail to fetch")`. Every other
 * engine throws an `IOException` there, and every caller in `:shared` catches `Exception`, which
 * `Error` is not. So in the browser, and only there, a lost connection was not a failure the app
 * handled but an uncaught throwable that ended the coroutine that met it: the first one to hit it
 * was the release check at start-up, and the game stopped on its splash screen.
 *
 * Fixed here rather than at the twenty-odd `catch` sites, because the engine is the one place that
 * knows the throwable means "no answer" — and a `catch (Throwable)` at each of those would also
 * swallow the errors that do mean a bug.
 */
private object FetchEngine : HttpClientEngineFactory<JsClientEngineConfig> {
    override fun create(block: JsClientEngineConfig.() -> Unit): HttpClientEngine =
        FetchFailureAsIoException(Js.create(block))
}

@OptIn(InternalAPI::class)
private class FetchFailureAsIoException(private val fetch: HttpClientEngine) : HttpClientEngine {
    override val dispatcher get() = fetch.dispatcher
    override val config get() = fetch.config
    override val supportedCapabilities get() = fetch.supportedCapabilities
    override val coroutineContext get() = fetch.coroutineContext

    // `install` is deliberately not forwarded. Its default body intercepts the client's pipeline
    // and calls `execute` on *this* engine, which is the call that has to go through the catch.
    override suspend fun execute(data: HttpRequestData): HttpResponseData =
        runCatching { fetch.execute(data) }.getOrElse { failure ->
            // The exact class, not a subclass: Ktor throws a plain `Error`, while an
            // `AssertionError`, a `NotImplementedError` or a cancellation is rethrown untouched.
            if (failure::class != Error::class) throw failure
            throw IOException("${data.method.value} ${data.url}: ${failure.message}", failure)
        }

    override fun close() = fetch.close()
}
