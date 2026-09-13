package com.tripletriad.storage

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The [DocumentStoreTest] contract, against a real browser's `localStorage`.
 *
 * Each test gets a collection name of its own and removes what it wrote, because the storage
 * outlives the test: Karma runs every test in one page, and a key left behind would be found by
 * the next test's [DocumentStore.keys].
 */
class BrowserDocumentStoreTest {
    private val collection = "test-${Random.nextLong().toULong()}"
    private val store = BrowserDocumentStore(collection)

    @AfterTest
    fun forgetEverything() = runTest {
        for (key in store.keys()) store.delete(key)
    }

    @Test
    fun readsBackWhatWasWritten() = runTest {
        store.write("kuplu kopo - 1700000000000", "blob")

        assertEquals("blob", store.read("kuplu kopo - 1700000000000"))
    }

    @Test
    fun anUnwrittenKeyReadsAsNullRatherThanThrowing() = runTest {
        assertNull(store.read("nothing here"))
    }

    @Test
    fun anEmptyDocumentIsDistinctFromAMissingOne() = runTest {
        store.write("empty", "")

        assertEquals("", store.read("empty"))
        assertNull(store.read("absent"))
    }

    @Test
    fun writingTwiceReplaces() = runTest {
        store.write("k", "first")
        store.write("k", "second")

        assertEquals("second", store.read("k"))
        assertEquals(listOf("k"), store.keys())
    }

    @Test
    fun listsAndDeletes() = runTest {
        store.write("a", "1")
        store.write("b", "2")

        assertEquals(listOf("a", "b"), store.keys().sorted())

        store.delete("a")

        assertEquals(listOf("b"), store.keys())
    }

    @Test
    fun deletingSomethingAbsentSucceedsSilently() = runTest {
        store.delete("never existed")

        assertTrue(store.keys().isEmpty())
    }

    /** The reason the prefix exists: the origin has one map, and the hosts' collections are not. */
    @Test
    fun collectionsDoNotSeeEachOther() = runTest {
        val other = BrowserDocumentStore("$collection-other")
        try {
            store.write("shared name", "mine")
            other.write("shared name", "theirs")

            assertEquals("mine", store.read("shared name"))
            assertEquals(listOf("shared name"), store.keys())
            assertEquals(listOf("shared name"), other.keys())
        } finally {
            other.delete("shared name")
        }
    }

    @Test
    fun whatSurvivesIsTheStorageAndNotTheObject() = runTest {
        store.write("k", "v")

        assertEquals("v", BrowserDocumentStore(collection).read("k"))
    }

    @Test
    fun rejectsKeysThatWouldEscapeTheCollection() = runTest {
        for (bad in listOf("../etc/passwd", "a/b", "a\\b", "C:name", "", "   ", ".", "..")) {
            assertFailsWith<IllegalArgumentException>("'$bad' must be refused") {
                store.write(bad, "v")
            }
        }
        assertFailsWith<IllegalArgumentException> { BrowserDocumentStore("saves/other") }
    }
}
