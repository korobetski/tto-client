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

/**
 * The Android [DocumentStore], on the host JVM.
 *
 * ### Why this runs without Robolectric
 *
 * Because the store no longer takes a `Context`. The only thing it ever wanted from Android was
 * `filesDir`, which is a `File`, so the primary constructor takes the directory and the `Context`
 * overload delegates to it. A stubbed `android.jar` would have thrown on `context.filesDir`, and
 * the alternative — adding Robolectric — is a whole test framework for one property, in a project
 * that dropped `kotlinx-datetime` rather than carry a dependency it disagreed with.
 *
 * What this cannot cover is what only a device can: that `filesDir` is where the app may write,
 * that it survives an update and goes on uninstall. Those are Android's guarantees, not this
 * class's, and an instrumented test asserting them would be testing the platform.
 *
 * The sibling `DesktopDocumentStoreTest` covers the same contract against the other host. The two
 * are near-identical implementations of one interface and the duplication is deliberate: they are
 * the two places a player's saves really live, and a shared fixture would prove only that one of
 * them works.
 */
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

    /**
     * The write-then-rename path leaves no `.tmp` behind.
     *
     * The comment on it says `File.renameTo` is atomic within a directory on every Android
     * filesystem, which is the reason the fallback below it should never run. Nothing had ever
     * checked that the happy path cleans up after itself.
     */
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

    /** Two collections are two directories — a profile cannot surface in the match history. */
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

    /** A profile key is built from a player-typed name, so this input is reachable. */
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
