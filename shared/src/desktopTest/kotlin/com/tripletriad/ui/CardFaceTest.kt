package com.tripletriad.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.model.Card
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class CardFaceTest {
    private val art = runBlocking { loadCardArt() }
    private val cards = runBlocking { loadCardCatalog() }.all

    @Test
    fun theFaceFollowsTheCardWhenASlotIsReused() = runComposeUiTest {
        val first = cards.first()
        val second = cards.first { it.textureId != first.textureId }
        val card = mutableStateOf(first)
        var drawn: ImageBitmap? = null

        setContent { drawn = rememberCardFace(art, card.value) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { drawn != null }
        val firstFace = drawn
        assertSame(art.cachedFace(first), firstFace, "the first card's own artwork")

        // The slot is kept — only the card in it changes, exactly as when a hand closes up.
        card.value = second
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { drawn != null && drawn !== firstFace }

        assertSame(art.cachedFace(second), drawn, "the second card's own artwork")
        assertNotEquals(firstFace, drawn, "two different cards cannot share one bitmap")
    }

    @Test
    fun anAlreadyDecodedFaceIsReturnedWithoutABlankFrame() = runComposeUiTest {
        val card: Card = cards.last()
        art.face(card)
        val frames = mutableListOf<ImageBitmap?>()

        setContent { frames += rememberCardFace(art, card) }

        waitForIdle()
        assertSame(art.cachedFace(card), frames.first(), "the very first frame is already drawn")
    }

    @Test
    fun theFaceCacheDropsTheLeastRecentlyReadFaceFirst(): Unit = runBlocking {
        val art = loadCardArt()
        val faces = cards.take(FACE_CACHE_SIZE + 1)
        val (reread, oldest) = faces[0] to faces[1]
        faces.take(FACE_CACHE_SIZE).forEach { art.face(it) }

        art.face(reread)
        art.face(faces.last())

        assertNotNull(art.cachedFace(faces.last()), "the face just read")
        assertNotNull(art.cachedFace(reread), "a face read again is recent again, however old")
        assertNull(art.cachedFace(oldest), "one over the limit, and the stalest face is gone")
    }
}
