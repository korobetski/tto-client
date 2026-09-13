package com.tripletriad.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FetchEngineTest {
    // Port 1 on the loopback: nothing listens there, so `fetch` rejects locally.
    // Without `FetchEngine` this is a `kotlin.Error`, which `catch (Exception)` misses.
    @Test
    fun aRequestThatGetsNoAnswerFailsAsAnIoException() = runTest {
        val client = HttpClient(defaultHttpEngineFactory())
        try {
            assertFailsWith<IOException> { client.get("http://127.0.0.1:1/server") }
        } finally {
            client.close()
        }
    }
}
