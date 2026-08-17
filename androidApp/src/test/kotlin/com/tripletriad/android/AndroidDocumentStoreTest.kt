package com.tripletriad.android

import com.tripletriad.storage.DocumentStore
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidDocumentStoreTest {
    private val root: File = File.createTempFile("tto-files", "").let { file ->
        file.delete()
        file.mkdirs()
        file
    }

    private fun store(subdirectory: String = "saves"): DocumentStore =
        AndroidDocumentStore(root = root, subdirectory = subdirectory)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun aWrittenDocumentReadsBack() = runTest {
        val store = store()

        store.write("kuplu - 42", CONTENT)

        assertEquals(CONTENT, store.read("kuplu - 42"))
    }

    @Test
    fun anAbsentDocumentIsNull() = runTest {
        assertNull(store().read("nobody"))
    }

    @Test
    fun writingTwiceReplacesRatherThanAppends() = runTest {
        val store = store()

        store.write("kuplu", CONTENT)
        store.write("kuplu", "second")

        assertEquals("second", store.read("kuplu"))
        assertEquals(listOf("kuplu"), store.keys())
    }

    @Test
    fun writingLeavesNoTemporaryFile() = runTest {
        val store = store()

        store.write("kuplu", CONTENT)
        store.write("kuplu", "again")

        val leftovers = File(root, "saves").listFiles().orEmpty().filterNot {
            it.name.endsWith(".sav")
        }
        assertTrue(leftovers.isEmpty(), "left behind: ${leftovers.map { it.name }}")
    }

    @Test
    fun keysListsEveryStoredDocumentAndNothingElse() = runTest {
        val store = store()
        store.write("one", CONTENT)
        store.write("two", CONTENT)
        File(root, "saves/notes.txt").writeText("ignore me")

        assertEquals(listOf("one", "two"), store.keys().sorted())
    }

    @Test
    fun keysOnAFreshInstallIsEmpty() = runTest {
        assertEquals(emptyList(), store().keys())
    }

    @Test
    fun deletingRemovesTheDocumentAndDeletingAgainIsHarmless() = runTest {
        val store = store()
        store.write("kuplu", CONTENT)

        store.delete("kuplu")
        store.delete("kuplu")

        assertNull(store.read("kuplu"))
        assertEquals(emptyList(), store.keys())
    }

    @Test
    fun twoSubdirectoriesDoNotSeeEachOther() = runTest {
        val saves = store("saves")
        val history = store("history")

        saves.write("kuplu", CONTENT)
        history.write("kuplu", "a match")

        assertEquals(listOf("kuplu"), saves.keys())
        assertEquals(CONTENT, saves.read("kuplu"))
        assertEquals("a match", history.read("kuplu"))
    }

    @Test
    fun aKeyThatWouldEscapeTheDirectoryIsRefused() = runTest {
        val store = store()

        for (key in listOf("../escape", "sub/dir", "back\\slash", "c:drive", "", " ", ".", "..")) {
            assertFailsWith<IllegalArgumentException>("key '$key' should be refused") {
                store.write(key, CONTENT)
            }
        }
    }

    @Test
    fun contentRoundTripsUnchanged() = runTest {
        val store = store()
        val awkward = "accents éàü, a quote \", a newline\n, a tab\t, and an emoji 🃏"

        store.write("awkward", awkward)

        assertEquals(awkward, store.read("awkward"))
    }

    private companion object {
        const val CONTENT = "a profile, obfuscated"
    }
}
