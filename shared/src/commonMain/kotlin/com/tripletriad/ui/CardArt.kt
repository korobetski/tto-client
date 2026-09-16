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
import com.tripletriad.model.CardType
import com.tripletriad.model.powerLabel
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import tripletriad.shared.generated.resources.Res

class CardArt internal constructor(
    val back: ImageBitmap,
    /**
     * The gold frame of a face with no illustration inside it: transparent within, so the side's
     * colour fill shows through. The game's own, from the battle sheet and cell grid of [back].
     */
    val frame: ImageBitmap,
    private val stars: Map<Int, ImageBitmap>,
    private val types: Map<CardType, ImageBitmap>,
    private val digits: Map<String, Painter>,
) {
    // Not synchronised. Two coroutines racing on the same card decode it twice and the
    // second write wins, which costs one redundant decode and is otherwise harmless; a
    // mutex here would be protecting nothing worth protecting.
    //
    // Bounded, and in recency order: `face` re-inserts on every read, so the first key of this
    // insertion-ordered map is always the least recently used one — see [FACE_CACHE_SIZE].
    private val faces = mutableMapOf<String, ImageBitmap>()

    fun starsFor(rarity: Int): ImageBitmap? = stars[rarity]

    fun typeIcon(type: CardType): ImageBitmap? = types[type]

    val digitPlate: Painter? get() = digits[PLATE_TEXTURE]

    fun digit(power: Int): Painter? = digits["cd${powerLabel(power)}"]

    fun cachedFace(card: Card): ImageBitmap? = faces[card.textureId]

    suspend fun face(card: Card): ImageBitmap {
        val id = card.textureId
        // Card faces live in their own directory now that they are named by id alone: 263 files
        // called `013e.png` beside `back.png` and `digits.png` would be a directory nobody can
        // read.
        val face = faces.remove(id) ?: loadImage("$CARDS_DIR/$id.png")
        faces[id] = face
        if (faces.size > FACE_CACHE_SIZE) faces.remove(faces.keys.first())
        return face
    }
}

internal val Card.textureId: String get() = cardTextureId(id)

internal fun cardTextureId(cardId: Int): String =
    cardId.toString(HEX_RADIX).padStart(HEX_WIDTH, '0')

private const val HEX_RADIX = 16
private const val HEX_WIDTH = 4

internal val CardType.textureName: String get() = "type-${name.lowercase()}"

/**
 * How a type icon is sampled. The four FFXIV tribes' icons are 40x40, taken from ffxivcollect.com
 * on 2026-09-15, and never drawn larger than 20 dp: point sampling would keep one pixel in four.
 * The elements' are still the AS3's 20x20 pixel art, which smoothing would blur once the interface
 * size enlarges it. The same rule, for the same reason, as `AUTHORED_THUMB_PX`.
 */
internal val ImageBitmap.typeIconFilter: FilterQuality
    get() = if (width > AUTHORED_TYPE_ICON_PX) FilterQuality.Medium else FilterQuality.None

internal const val AUTHORED_TYPE_ICON_PX = 20

val LocalCardArt = staticCompositionLocalOf<CardArt?> { null }

@Composable
internal fun rememberCardFace(art: CardArt?, card: Card): ImageBitmap? {
    if (art == null) return null
    val id = card.textureId
    // Seeded from the cache so an already-decoded face is returned without a null frame.
    var face by remember(art, id) { mutableStateOf(art.cachedFace(card)) }
    LaunchedEffect(art, id) {
        if (face == null) face = art.face(card)
    }
    return face
}

suspend fun loadCardArt(): CardArt = CardArt(
    back = loadImage("back.png"),
    frame = loadImage("frame.png"),
    stars = Card.RARITY_RANGE.associateWith { loadImage("${it}stars.png") },
    types = CardType.entries.associateWith { loadImage("${it.textureName}.png") },
    digits = sliceDigitAtlas(loadImage("digits.png")),
)

private val ATLAS_ORDER = listOf(
    "cdp", "cdm", "cd0", "cd1", "cd2",
    "cd3", "cd4", "cd5", "cd6", "cd7",
    "cd8", "cd9", "cdA",
)

private fun digitFrames(): Map<String, IntRect4> = buildMap {
    ATLAS_ORDER.forEachIndexed { slot, name ->
        put(
            name,
            IntRect4(
                x = ATLAS_MARGIN_PX + DIGIT_PITCH_PX * (slot % ATLAS_COLUMNS),
                y = ATLAS_MARGIN_PX + DIGIT_PITCH_PX * (slot / ATLAS_COLUMNS),
                width = DIGIT_PX,
                height = DIGIT_PX,
            ),
        )
    }
    put(PLATE_TEXTURE, IntRect4(x = 0, y = PLATE_Y_PX, width = PLATE_PX, height = PLATE_PX))
}

private data class IntRect4(val x: Int, val y: Int, val width: Int, val height: Int)

private fun sliceDigitAtlas(sheet: ImageBitmap): Map<String, Painter> =
    digitFrames().mapValues { (_, frame) ->
        BitmapPainter(
            image = sheet,
            srcOffset = IntOffset(frame.x, frame.y),
            srcSize = IntSize(frame.width, frame.height),
        )
    }

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadImage(name: String): ImageBitmap =
    Res.readBytes("$ART_PATH/$name").decodeToImageBitmap()

suspend fun loadLogo(): ImageBitmap = loadImage("logo.png")

internal const val ART_PATH = "files/art"

internal const val CARDS_DIR = "cards"

/**
 * How many decoded faces [CardArt] keeps.
 *
 * The FFXIV faces are the game's 208x256 art, 213 KB each once decoded, and scrolling the deck
 * editor's grid asks for every one of the 475: unbounded, that is ~101 MB of pictures held for the
 * life of the app — a browser tab's or a small phone's whole budget. 96 is ~20 MB.
 *
 * Dropping a face that is still on screen costs nothing visible: `rememberCardFace` holds its own
 * reference, so the bitmap only has to be decoded again once it has scrolled away and back.
 */
internal const val FACE_CACHE_SIZE = 96
private const val PLATE_TEXTURE = "cdbg"

private const val DIGIT_PX = 18
private const val PLATE_PX = 28
private const val PLATE_Y_PX = 62

private const val ATLAS_MARGIN_PX = 2
private const val ATLAS_COLUMNS = 5
private const val DIGIT_PITCH_PX = 20
