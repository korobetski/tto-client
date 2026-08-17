package com.tripletriad.log

import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CrashLogTest {

    @Test
    fun aWarningIsKept() = runTest {
        val store = InMemoryDocumentStore()
        val log = CrashLog(store, FixedClock(millis = NOW), scope = backgroundScope)

        log.write(LogLevel.WARN, "Pvp", "the table was refused", null)
        runCurrent()

        val kept = log.readAll()
        assertTrue("the table was refused" in kept, "the warning was not kept: $kept")
        assertTrue("Pvp" in kept, "the tag was not kept: $kept")
    }

    @Test
    fun anErrorKeepsItsException() = runTest {
        val store = InMemoryDocumentStore()
        val log = CrashLog(store, FixedClock(millis = NOW), scope = backgroundScope)

        log.write(LogLevel.ERROR, "Save", "could not write", IllegalStateException("disk full"))
        runCurrent()

        val kept = log.readAll()
        assertTrue("IllegalStateException" in kept, kept)
        assertTrue("disk full" in kept, kept)
    }

    @Test
    fun debugLinesAreNotKept() = runTest {
        val store = InMemoryDocumentStore()
        val log = CrashLog(store, FixedClock(millis = NOW), scope = backgroundScope)

        log.write(LogLevel.DEBUG, "Cards", "resolved 257", null)
        log.write(LogLevel.INFO, "Cards", "catalogue loaded", null)
        runCurrent()

        assertEquals("", log.readAll(), "an ordinary line was persisted")
    }

    @Test
    fun theRingDoesNotGrow() = runTest {
        val store = InMemoryDocumentStore()
        val log = CrashLog(store, FixedClock(millis = NOW), scope = backgroundScope, keep = 3)

        repeat(10) { log.write(LogLevel.WARN, "Pvp", "warning $it", null) }
        runCurrent()

        val kept = log.readAll()
        assertEquals(3, kept.lines().size, "the ring kept ${kept.lines().size} lines: $kept")
        assertTrue("warning 9" in kept, "the newest line was dropped")
        assertFalse("warning 6" in kept, "an old line survived the ring")
    }

    @Test
    fun itCanBeCleared() = runTest {
        val store = InMemoryDocumentStore()
        val log = CrashLog(store, FixedClock(millis = NOW), scope = backgroundScope)
        log.write(LogLevel.WARN, "Pvp", "something", null)
        runCurrent()

        log.clear()

        assertEquals("", log.readAll())
    }

    @Test
    fun itPassesEverythingOnToTheNextSink() = runTest {
        val store = InMemoryDocumentStore()
        val seen = mutableListOf<String>()
        val log = CrashLog(store, FixedClock(millis = NOW), scope = backgroundScope).apply {
            next = LogSink { _, tag, message, _ -> seen += "$tag: $message" }
        }

        log.write(LogLevel.DEBUG, "Cards", "resolved 257", null)
        log.write(LogLevel.WARN, "Pvp", "refused", null)

        assertEquals(listOf("Cards: resolved 257", "Pvp: refused"), seen)
    }

    private companion object {
        const val NOW = 1_770_000_000_000L
    }
}
