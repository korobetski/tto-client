package com.tripletriad.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun defaultHttpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
