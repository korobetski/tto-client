package com.tripletriad.net

import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ServerDirectoryTest {

    // ---- Reading a configured list ----------------------------------------

    @Test
    fun aBareAddressIsAServer() {
        val entries = serverEntries("https://eu.example.org")

        assertEquals(1, entries.size)
        assertEquals("https://eu.example.org", entries.first().baseUrl)
    }

    @Test
    fun anEntryMayBeLabelled() {
        val entry = serverEntries("Europe=https://eu.example.org").single()

        assertEquals("Europe", entry.label)
        assertEquals("https://eu.example.org", entry.baseUrl)
    }

    @Test
    fun anUnlabelledEntryIsNamedAfterItsHost() {
        assertEquals("eu.example.org", serverEntries("https://eu.example.org").single().label)
    }

    @Test
    fun severalAreCommaSeparatedAndKeepTheirOrder() {
        val entries = serverEntries("A=https://a.example.org, B=https://b.example.org")

        assertEquals(listOf("A", "B"), entries.map { it.label })
    }

    @Test
    fun blanksAndStraySpacingAreIgnored() {
        val entries = serverEntries("  ,, A = https://a.example.org ,  ")

        assertEquals(1, entries.size)
        assertEquals("A", entries.single().label)
        assertEquals("https://a.example.org", entries.single().baseUrl)
    }

    @Test
    fun noConfigurationIsNoServers() {
        assertTrue(serverEntries("").isEmpty())
        assertTrue(serverEntries("   ").isEmpty())
    }

    @Test
    fun oneAddressUnderTwoLabelsIsStillOneServer() {
        val entries = serverEntries("A=https://a.example.org, Also A=https://a.example.org/")

        assertEquals(1, entries.size)
    }

    // ---- The id is the address --------------------------------------------

    @Test
    fun theIdSurvivesARelabelling() {
        assertEquals(
            ServerEntry.of("https://a.example.org", label = "Europe").id,
            ServerEntry.of("https://a.example.org", label = "EU").id,
        )
    }

    @Test
    fun theIdIgnoresATrailingSlash() {
        assertEquals(
            ServerEntry.of("https://a.example.org").id,
            ServerEntry.of("https://a.example.org/").id,
        )
    }

    @Test
    fun twoAddressesAreTwoServers() {
        assertNotEquals(
            ServerEntry.of("https://a.example.org", label = "Home").id,
            ServerEntry.of("https://b.example.org", label = "Home").id,
        )
    }

    @Test
    fun theIdIsSafeToUseAsADocumentKey() {
        val id = ServerEntry.of("https://a.example.org:8443/path?x=1").id

        assertTrue(id.all { it.isLetterOrDigit() || it == '_' || it == '-' }, "was $id")
    }

    // ---- Choosing, and remembering the choice -----------------------------

    @Test
    fun theFirstEntryIsTheDefault() {
        val directory = ServerDirectory(InMemoryDocumentStore(), entries)

        assertEquals(entries.first(), directory.selected)
    }

    @Test
    fun aChoiceSurvivesARelaunch() = runTest {
        val store = InMemoryDocumentStore()
        ServerDirectory(store, entries).select(entries[1])

        // A fresh instance over the same storage: the process the player was in has ended.
        val restored = ServerDirectory(store, entries).also { it.restore() }

        assertEquals(entries[1], restored.selected)
    }

    @Test
    fun choosingTheCurrentServerIsNotAChange() = runTest {
        val directory = ServerDirectory(InMemoryDocumentStore(), entries)

        assertFalse(directory.select(entries.first()))
        assertTrue(directory.select(entries[1]))
    }

    @Test
    fun aStoredServerThatIsNoLongerConfiguredFallsBack() = runTest {
        val store = InMemoryDocumentStore()
        ServerDirectory(store, entries).select(entries[1])

        val shorter = listOf(entries.first())
        val directory = ServerDirectory(store, shorter).also { it.restore() }

        assertEquals(entries.first(), directory.selected)
    }

    @Test
    fun nothingStoredLeavesTheDefaultInPlace() = runTest {
        val directory = ServerDirectory(InMemoryDocumentStore(), entries).also { it.restore() }

        assertEquals(entries.first(), directory.selected)
    }

    @Test
    fun anUnreadableStoreLeavesAWorkingDefault() = runTest {
        val store = InMemoryDocumentStore(failure = IllegalStateException("permission denied"))

        val directory = ServerDirectory(store, entries)
        directory.restore()
        directory.select(entries[1])

        assertEquals(entries[1], directory.selected)
    }

    @Test
    fun anUnconfiguredServerCannotBeSelected() = runTest {
        val directory = ServerDirectory(InMemoryDocumentStore(), listOf(entries.first()))

        assertFailsWith<IllegalArgumentException> { directory.select(entries[1]) }
    }

    @Test
    fun aDirectoryWithNoServersIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            ServerDirectory(InMemoryDocumentStore(), emptyList())
        }
    }

    private val entries = serverEntries("A=https://a.example.org, B=https://b.example.org")
}
