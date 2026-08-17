package com.tripletriad.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentStoreTest {
    @Test
    fun readsBackWhatWasWritten() = runTest {
        val store = InMemoryDocumentStore()

        store.write("kuplu kopo - 1700000000000", "blob")

        assertEquals("blob", store.read("kuplu kopo - 1700000000000"))
        assertEquals(1, store.writes)
    }

    @Test
    fun anUnwrittenKeyReadsAsNullRatherThanThrowing() = runTest {
        assertNull(InMemoryDocumentStore().read("nothing here"))
    }

    @Test
    fun anEmptyDocumentIsDistinctFromAMissingOne() = runTest {
        val store = InMemoryDocumentStore()

        store.write("empty", "")

        assertEquals("", store.read("empty"))
        assertNull(store.read("absent"))
    }

    @Test
    fun writingTwiceReplaces() = runTest {
        val store = InMemoryDocumentStore()

        store.write("k", "first")
        store.write("k", "second")

        assertEquals("second", store.read("k"))
        assertEquals(listOf("k"), store.keys())
    }

    @Test
    fun listsAndDeletes() = runTest {
        val store = InMemoryDocumentStore(mapOf("a" to "1", "b" to "2"))

        assertEquals(listOf("a", "b"), store.keys().sorted())

        store.delete("a")

        assertEquals(listOf("b"), store.keys())
    }

    @Test
    fun deletingSomethingAbsentSucceedsSilently() = runTest {
        val store = InMemoryDocumentStore()

        store.delete("never existed")

        assertTrue(store.keys().isEmpty())
    }

    @Test
    fun theFailureModeIsReachable() = runTest {
        val store = InMemoryDocumentStore(failure = IllegalStateException("disk full"))

        assertFailsWith<IllegalStateException> { store.read("k") }
        assertFailsWith<IllegalStateException> { store.write("k", "v") }
        assertFailsWith<IllegalStateException> { store.keys() }
        assertFailsWith<IllegalStateException> { store.delete("k") }
    }

    @Test
    fun rejectsKeysThatWouldEscapeTheDirectory() = runTest {
        val store = InMemoryDocumentStore()

        for (bad in listOf("../etc/passwd", "a/b", "a\\b", "C:name", "", "   ", ".", "..")) {
            assertFailsWith<IllegalArgumentException>("'$bad' must be refused") {
                store.write(bad, "v")
            }
        }
    }

    @Test
    fun acceptsTheKeysTheSaveRepositoryProduces() {
        // `Save.as:85` names files "<username> - <creationDate>"; spaces, dashes and digits only.
        assertEquals("kuplu kopo - 1700000000000", sanitizeKey("kuplu kopo - 1700000000000"))
        assertEquals("profile_1", sanitizeKey("profile_1"))
    }
}
