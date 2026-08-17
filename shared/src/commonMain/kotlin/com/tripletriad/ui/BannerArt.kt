package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.tripletriad.i18n.AppLocale
import com.tripletriad.log.Log
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import tripletriad.shared.generated.resources.Res

class BannerArt internal constructor(private val locale: AppLocale) {

    // Unsynchronised for [CardArt]'s reason: a race costs one redundant decode.
    private val decoded = mutableMapOf<MatchBanner, ImageBitmap>()

    fun cached(banner: MatchBanner): ImageBitmap? = decoded[banner]

    suspend fun caption(banner: MatchBanner): ImageBitmap? =
        decoded[banner]
            ?: (read(locale, banner) ?: read(AppLocale.Default, banner))
                ?.also { decoded[banner] = it }

    @OptIn(ExperimentalResourceApi::class)
    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(locale: AppLocale, banner: MatchBanner): ImageBitmap? = try {
        Res.readBytes("$BANNER_PATH/${locale.tag}/${banner.textureId}.png")
            .decodeToImageBitmap()
    } catch (failure: Exception) {
        Log.w(TAG, failure) { "no ${banner.name} caption for ${locale.tag}" }
        null
    }

    private companion object {
        const val TAG = "Banners"

        const val BANNER_PATH = "files/banners"
    }
}

val LocalBannerArt = staticCompositionLocalOf<BannerArt?> { null }

@Composable
fun rememberBannerArt(locale: AppLocale): BannerArt = remember(locale) { BannerArt(locale) }

@Composable
internal fun rememberCaption(art: BannerArt?, banner: MatchBanner?): ImageBitmap? {
    if (art == null || banner == null) return null
    var image by remember(art, banner) { mutableStateOf(art.cached(banner)) }
    LaunchedEffect(art, banner) {
        if (image == null) image = art.caption(banner)
    }
    return image
}
