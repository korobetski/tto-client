package com.tripletriad.ui

import com.tripletriad.i18n.AppLocale
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BannerArtTest {

    @Test
    fun everyCaptionDecodesInEveryLanguage() = runTest {
        AppLocale.entries.forEach { locale ->
            val art = BannerArt(locale)
            MatchBanner.entries.forEach { banner ->
                val image = art.caption(banner)
                assertNotNull(image, "${banner.name} is missing for ${locale.tag}")
                assertTrue(
                    image.width > 0 && image.height > 0,
                    "${banner.name} for ${locale.tag} decoded to nothing",
                )
            }
        }
    }

    @Test
    fun everyCaptionIsTheSameSize() = runTest {
        val art = BannerArt(AppLocale.EN_US)
        val sizes = MatchBanner.entries.mapNotNull { art.caption(it) }
            .map { it.width to it.height }
            .toSet()

        assertEquals(setOf(BANNER_WIDTH to BANNER_HEIGHT), sizes)
    }

    @Test
    fun aCaptionIsDecodedOnceAndCached() = runTest {
        val art = BannerArt(AppLocale.EN_US)

        val first = assertNotNull(art.caption(MatchBanner.SAME))

        assertSame(first, art.cached(MatchBanner.SAME))
        assertSame(first, art.caption(MatchBanner.SAME))
    }

    @Test
    fun nothingIsLoadedUntilItIsNeeded() {
        val art = BannerArt(AppLocale.EN_US)

        MatchBanner.entries.forEach {
            assertEquals(null, art.cached(it), "${it.name} was loaded eagerly")
        }
    }

    @Test
    fun eachLanguageDecodesItsOwnCaption() = runTest {
        val english = assertNotNull(BannerArt(AppLocale.EN_US).caption(MatchBanner.SAME))
        val french = assertNotNull(BannerArt(AppLocale.FR_FR).caption(MatchBanner.SAME))

        assertTrue(
            english.readPixels() != french.readPixels(),
            "the English and French captions are the same picture",
        )
    }

    private companion object {
        const val BANNER_WIDTH = 600
        const val BANNER_HEIGHT = 90
    }
}

private fun androidx.compose.ui.graphics.ImageBitmap.readPixels(): List<Int> {
    val buffer = IntArray(width * height)
    readPixels(buffer)
    return buffer.toList()
}
