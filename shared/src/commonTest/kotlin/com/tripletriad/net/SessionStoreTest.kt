package com.tripletriad.net

import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStoreTest {

    // ---- Surviving a launch -----------------------------------------------

    @Test
    fun aStoredSessionComesBackAfterARelaunch() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(SERVER, session(expiresAt = LATER))

        // A fresh instance over the same storage: the process the player was in has ended.
        val restored = SessionStore(documents).load(SERVER, NOW)

        assertEquals(session(expiresAt = LATER), restored)
    }

    @Test
    fun nothingStoredIsNoSession() = runTest {
        assertNull(SessionStore(InMemoryDocumentStore()).load(SERVER, NOW))
    }

    @Test
    fun clearForgetsIt() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = LATER))

        store.clear(SERVER)

        assertNull(store.load(SERVER, NOW))
    }

    // ---- Refusing to hand back something unusable -------------------------

    @Test
    fun anExpiredSessionIsNotReturned() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = NOW - 1))

        assertNull(store.load(SERVER, NOW))
    }

    @Test
    fun aSessionExpiringExactlyNowIsNotReturned() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = NOW))

        assertNull(store.load(SERVER, NOW))
    }

    @Test
    fun anEmptyTokenIsNotASession() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = LATER).copy(token = ""))

        assertNull(store.load(SERVER, NOW))
    }

    // ---- Never breaking the launch ----------------------------------------

    @Test
    fun anUnreadableDocumentDegradesToNoSession() = runTest {
        val store = InMemoryDocumentStore(mapOf(SERVER to "not json at all"))

        assertNull(SessionStore(store).load(SERVER, NOW))
    }

    @Test
    fun anUnreadableStoreDegradesToNoSession() = runTest {
        val store = InMemoryDocumentStore(failure = IllegalStateException("permission denied"))

        assertNull(SessionStore(store).load(SERVER, NOW))
    }

    @Test
    fun clearingAFailingStoreDoesNotThrow() = runTest {
        SessionStore(
            InMemoryDocumentStore(failure = IllegalStateException("read-only")),
        ).clear(SERVER)
    }

    // ---- The name outlives the token ---------------------------------------

    @Test
    fun theNameSurvivesTheTokenItWasStoredWith() = runTest {
        val store = InMemoryDocumentStore()
        SessionStore(store).save(SERVER, session(expiresAt = NOW - 1))

        assertNull(SessionStore(store).load(SERVER, NOW), "the token has expired")
        assertEquals("kuplu", SessionStore(store).lastUsername(SERVER))
    }

    @Test
    fun thereIsNoNameWhereThereIsNoSession() = runTest {
        assertNull(SessionStore(InMemoryDocumentStore()).lastUsername(SERVER))
    }

    @Test
    fun clearingForgetsTheNameAsWell() = runTest {
        val store = InMemoryDocumentStore()
        SessionStore(store).save(SERVER, session(expiresAt = LATER))

        SessionStore(store).clear(SERVER)

        assertNull(SessionStore(store).lastUsername(SERVER))
    }

    @Test
    fun eachServerRemembersItsOwnName() = runTest {
        val store = InMemoryDocumentStore()
        SessionStore(store).save(SERVER, session(expiresAt = LATER))

        assertNull(SessionStore(store).lastUsername(OTHER_SERVER))
    }

    @Test
    fun anUnreadableDocumentHasNoNameToOffer() = runTest {
        val store = InMemoryDocumentStore(mapOf(SERVER to "not json at all"))

        assertNull(SessionStore(store).lastUsername(SERVER))
    }

    // ---- The token is not printable ---------------------------------------

    @Test
    fun theTokenIsNotInTheStringForm() = runTest {
        val printed = session(expiresAt = LATER).toString()

        assertFalse(printed.contains(TOKEN), "the token was printed")
        assertTrue(printed.contains("kuplu"), "and the useful part was lost with it")
    }

    @Test
    fun validityIsTheSameQuestionLoadAsks() {
        val stored = session(expiresAt = LATER)

        assertTrue(stored.isValidAt(NOW))
        assertFalse(stored.isValidAt(LATER))
        assertFalse(stored.copy(token = "").isValidAt(NOW))
    }

    // ---- The queue key ----------------------------------------------------

    @Test
    fun theQueueKeyIgnoresCase() {
        assertEquals(accountQueueKey(SERVER, "Kuplu"), accountQueueKey(SERVER, "kuplu"))
    }

    @Test
    fun namesThatSanitizeToTheSameStringStillGetTheirOwnQueue() {
        assertNotEquals(accountQueueKey(SERVER, "a/b"), accountQueueKey(SERVER, "a_b"))
    }

    @Test
    fun theQueueKeyIsSafeToUseAsADocumentKey() {
        val key = accountQueueKey(SERVER, "../../etc/passwd")

        // The dot is the one other character a key may contain: it is the separator this function
        // puts there, between a server id that is already safe and a sanitized username.
        assertTrue(
            key.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' },
            "was $key",
        )
    }

    @Test
    fun theQueueKeyIsStable() {
        assertEquals(accountQueueKey(SERVER, "kuplu"), accountQueueKey(SERVER, "kuplu"))
    }

    @Test
    fun oneNameOnTwoServersIsTwoQueues() {
        assertNotEquals(accountQueueKey(SERVER, "kuplu"), accountQueueKey(OTHER_SERVER, "kuplu"))
    }

    // ---- One session per server -------------------------------------------

    @Test
    fun aSessionOnOneServerIsNotASessionOnAnother() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = LATER))

        assertNull(store.load(OTHER_SERVER, NOW))
    }

    @Test
    fun switchingAwayLeavesTheSessionWhereItWas() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = LATER))
        store.save(OTHER_SERVER, session(expiresAt = LATER).copy(username = "elsewhere"))

        store.clear(OTHER_SERVER)

        assertEquals(session(expiresAt = LATER), store.load(SERVER, NOW))
        assertNull(store.load(OTHER_SERVER, NOW))
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun session(expiresAt: Long) =
        StoredSession(token = TOKEN, expiresAt = expiresAt, username = "kuplu")

    private companion object {
        const val NOW = 1_770_000_000_000L
        const val LATER = NOW + 86_400_000L

        const val TOKEN = "opaque-session-value"

        const val SERVER = "127_0_0_1_8080-abc"
        const val OTHER_SERVER = "example_org-def"
    }
}
