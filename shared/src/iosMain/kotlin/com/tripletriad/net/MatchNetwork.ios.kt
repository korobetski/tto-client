package com.tripletriad.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun defaultHttpEngineFactory(): HttpClientEngineFactory<*> = Darwin
