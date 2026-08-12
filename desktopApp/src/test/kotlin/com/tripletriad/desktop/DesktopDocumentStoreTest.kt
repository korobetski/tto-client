package com.tripletriad.desktop

import com.tripletriad.storage.DocumentStore
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The desktop [DocumentStore], against a real filesystem.
 *
 * ### Why this file exists
 *
 * Because until it did, **neither host module had a test source set at all**. The `DocumentStore`
 * *contract* is covered by `InMemoryDocumentStore` and the whole profile flow runs against it, but
 * the two implementations that touch a player's actual saves were exercised only by hand. Phase 4
 * recorded that as a known gap; this is half of closing it.
 *
 * The half that matters is the write path. `write` is not `File.writeText` — it writes a temporary
 * beside the target and renames, with two fallbacks for Windows, because "a save killed mid-write
 * is a lost profile, not a lost volume setting". None of that branching had ever been executed by
 * a test.
 *
 * A temporary directory per test, and [home] is a constructor parameter precisely so this is
 * possible without writing into the developer's real `~/My Games`.
 */
class DesktopDocumentStoreTest {
    private val home: File = File.createTempFile("tto-home", "").let { file ->
        file.delete()
        file.mkdirs()
        file
    }

    private fun store(subdirectory: String = "saves"): DocumentStore =
        DesktopDocumentStore(subdirectory = subdirectory, home = home)

    @AfterTest
    fun cleanUp() {
        home.deleteRecursively()
    }

    @Test
    fun aWrittenDocumentReadsBack() = runTest {
        val store = store()

        store.write("kuplu - 42", CONTENT)

        assertEquals(CONTENT, store.read("kuplu - 42"))
    }

    /** Reading what was never written is null, not an exception and not an empty string. */
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

    /**
     * The rename path leaves no `.tmp` behind.
     *
     * If it did, `keys()` would still not see it — it filters on the extension — but the
     * directory would accumulate one file per write forever, which is the kind of thing nobody
     * notices until a profile directory has ten thousand entries in it.
     */
    @Test
    fun writingLeavesNoTemporaryFile() = runTest {
        val store = store()

        store.write("kuplu", CONTENT)
        store.write("kuplu", "again")

        val leftovers = File(home, "My Games/Triple Triad Online/saves")
            .listFiles()
            .orEmpty()
            .filterNot { it.name.endsWith(".sav") }
        assertTrue(leftovers.isEmpty(), "left behind: ${leftovers.map { it.name }}")
    }

    @Test
    fun keysListsEveryStoredDocumentAndNothingElse() = runTest {
        val store = store()
        store.write("one", CONTENT)
        store.write("two", CONTENT)
        // A stray file of another kind, which the AS3 directory really can contain.
        File(home, "My Games/Triple Triad Online/saves/notes.txt").writeText("ignore me")

        assertEquals(listOf("one", "two"), store.keys().sorted())
    }

    /** An empty or missing directory lists nothing rather than throwing. */
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

    /**
     * Two collections are two directories, so a profile cannot appear in the match history.
     *
     * The KDoc gives this as the reason for the `subdirectory` parameter — "so a stray file in one
     * cannot show up in the other's `keys`" — and it is the sort of claim that is true until
     * somebody changes the path expression.
     */
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

    /**
     * A key that could escape the directory is refused, not sanitised into something else.
     *
     * `sanitizeKey` is called by every implementation rather than by the interface, so that the
     * in-memory store rejects exactly what this one does. This is the half of that claim the file
     * stores are responsible for — and the input is reachable: a profile key is built from a
     * player-typed name.
     */
    @Test
    fun aKeyThatWouldEscapeTheDirectoryIsRefused() = runTest {
        val store = store()

        for (key in listOf("../escape", "sub/dir", "back\\slash", "c:drive", "", " ", ".", "..")) {
            assertFailsWith<IllegalArgumentException>("key '$key' should be refused") {
                store.write(key, CONTENT)
            }
        }
        assertFalse(home.resolve("escape.sav").exists(), "a refused key wrote a file anyway")
    }

    /** The obfuscated save format is not text-safe by accident — bytes must round-trip exactly. */
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
