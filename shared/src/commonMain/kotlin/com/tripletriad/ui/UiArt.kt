package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tripletriad.model.Card
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import tripletriad.shared.generated.resources.Res

class UiArt internal constructor(
    private val icons: Map<String, ImageBitmap>,
    private val thumbs: Map<String, Painter>,
) {
    // Not synchronised, for the reason given on `CardArt.faces`: a race costs one redundant
    // decode and nothing else.
    private val avatars = mutableMapOf<String, ImageBitmap>()
    private val portraits = mutableMapOf<String, ImageBitmap>()
    private val zones = mutableMapOf<String, ImageBitmap>()

    fun icon(name: String): ImageBitmap? = icons[name]

    fun thumb(card: Card): Painter? = thumbs[card.textureId]

    fun thumb(textureId: String): Painter? = thumbs[textureId]

    internal fun cachedAvatar(id: String): ImageBitmap? = avatars[id]

    internal suspend fun avatar(id: String): ImageBitmap? =
        avatars[id] ?: loadOrNull("avatars/$id.png")?.also { avatars[id] = it }

    internal fun cachedPortrait(iconId: String): ImageBitmap? = portraits[iconId]

    internal suspend fun portrait(iconId: String): ImageBitmap? =
        portraits[iconId] ?: loadOrNull("npcs/$iconId.png")?.also { portraits[iconId] = it }

    internal fun cachedZone(zoneId: String): ImageBitmap? = zones[zoneId]

    /**
     * A place's illustration, or null while it has none — every caller draws the place without
     * one. JPEG rather than PNG because these are painted scenes: twenty of them as PNG would
     * outweigh the rest of the art, and the web build downloads all of it.
     */
    internal suspend fun zone(zoneId: String): ImageBitmap? =
        zones[zoneId] ?: loadOrNull("zones/$zoneId.jpg")?.also { zones[zoneId] = it }
}

@Composable
internal fun rememberAvatar(art: UiArt?, avatarId: String): ImageBitmap? {
    if (art == null) return null
    var image by remember(art, avatarId) { mutableStateOf(art.cachedAvatar(avatarId)) }
    LaunchedEffect(art, avatarId) {
        if (image == null) image = art.avatar(avatarId)
    }
    return image
}

@Composable
internal fun rememberZoneArt(art: UiArt?, zoneId: String): ImageBitmap? {
    if (art == null) return null
    var image by remember(art, zoneId) { mutableStateOf(art.cachedZone(zoneId)) }
    LaunchedEffect(art, zoneId) {
        if (image == null) image = art.zone(zoneId)
    }
    return image
}

@Composable
internal fun rememberPortrait(art: UiArt?, iconId: String): ImageBitmap? {
    if (art == null) return null
    var image by remember(art, iconId) { mutableStateOf(art.cachedPortrait(iconId)) }
    LaunchedEffect(art, iconId) {
        if (image == null) image = art.portrait(iconId)
    }
    return image
}

suspend fun loadUiArt(): UiArt {
    val table = ThumbTableParser.parse(readText(THUMBS_TABLE))
    val sheets = table.frames.values.map { it.sheet }.distinct()
        .mapNotNull { name -> loadOrNull("thumbs/$name.png")?.let { name to it } }
        .toMap()

    return UiArt(
        icons = ICON_NAMES.mapNotNull { name -> loadOrNull("icons/$name.png")?.let { name to it } }
            .toMap(),
        thumbs = table.frames.mapNotNull { (id, frame) ->
            sheets[frame.sheet]?.let { sheet -> id to frame.painterOn(sheet) }
        }.toMap(),
    )
}

@Serializable
private data class ThumbFrame(
    val sheet: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun painterOn(sheet: ImageBitmap): Painter = BitmapPainter(
        image = sheet,
        srcOffset = IntOffset(x, y),
        srcSize = IntSize(width, height),
        // The FFXIV frames are the game's 80x80 icons in a 40 dp thumbnail: halved with point
        // sampling they shimmer, so they are smoothed. The FF8 frames are pixel art authored at
        // the drawn size, which smoothing would only blur as the interface size enlarges it.
        filterQuality = when {
            width > AUTHORED_THUMB_PX -> FilterQuality.Medium
            else -> FilterQuality.None
        },
    )
}

@Serializable
private data class ThumbTable(@SerialName("frames") val frames: Map<String, ThumbFrame>)

private object ThumbTableParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): ThumbTable = json.decodeFromString(text)
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun readText(path: String): String = Res.readBytes(path).decodeToString()

@OptIn(ExperimentalResourceApi::class)
@Suppress("SwallowedException", "TooGenericExceptionCaught")
private suspend fun loadOrNull(path: String): ImageBitmap? = try {
    Res.readBytes("$ART_PATH/$path").decodeToImageBitmap()
} catch (error: Exception) {
    null
}

val LocalUiArt = staticCompositionLocalOf<UiArt?> { null }

private const val THUMBS_TABLE = "files/thumbs.json"

/**
 * The size, in pixels, of a thumbnail drawn 1:1 in `CardThumb`'s 40 dp on a 1x screen.
 *
 * The FFXIV sheets are packed at twice this since 2026-09-15, which costs 14.6 MB decoded where
 * the 40 px sheets cost 3.6 MB — held for the life of the app, because every grid wants them at
 * once and a bounded cache like `CardArt`'s would only decode them again while scrolling.
 */
internal const val AUTHORED_THUMB_PX = 40

/**
 * How a bag or achievement icon is sampled. The card and pack icons are the game's 80x80 since
 * 2026-09-15 (`docs/resources`, the booster emblems composited on the pack) and are drawn at 32 or
 * 44 dp; the rest are still the AS3's pixel art, none wider than 44 px. The thumbnails' rule.
 */
internal val ImageBitmap.iconFilter: FilterQuality
    get() = if (width > AUTHORED_ICON_PX) FilterQuality.Medium else FilterQuality.None

internal const val AUTHORED_ICON_PX = 44

internal val AVATAR_NAMES: List<String> = listOf(
    "ffxiv_twi01001", "ffxiv_twi01002", "ffxiv_twi01003", "ffxiv_twi01004", "ffxiv_twi01005",
    "ffxiv_twi01006", "ffxiv_twi01007", "ffxiv_twi01008", "ffxiv_twi01009", "ffxiv_twi01010",
    "ffxiv_twi02001", "ffxiv_twi02002", "ffxiv_twi02003", "ffxiv_twi02004", "ffxiv_twi02005",
    "ffxiv_twi02006", "ffxiv_twi02007", "ffxiv_twi02008",
    "ffxiv_twi03001", "ffxiv_twi03002", "ffxiv_twi03003", "ffxiv_twi03004", "ffxiv_twi03005",
    "ffxiv_twi03006", "ffxiv_twi03007",
    "ffxiv_twi04001", "ffxiv_twi04002",
)

private val ICON_NAMES = listOf(
    "card_r1_icon", "card_r2_icon", "card_r3_icon", "card_r4_icon", "card_r5_icon",
    "beast_booster", "garlean_booster", "primal_booster", "scion_booster", "booster_pack_icon",
    "xp_boost_icon", "mgp_boost_icon", "PGS", "XP",
    "item_borders", "achievement_border",
    // The frame laid over every card-list thumbnail. Authored at the thumbnails' own
    // 40x40 so it can be drawn over one without scaling either — see `CardListBody`.
    "card_frame",
    // A raw FFXIV icon id, which is how `AchievementCatalog.NPC_ICON` names it.
    "000713",
)
