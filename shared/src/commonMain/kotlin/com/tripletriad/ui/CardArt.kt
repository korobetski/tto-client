package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
    private val stars: Map<Int, ImageBitmap>,
    private val types: Map<CardType, ImageBitmap>,
    private val digits: Map<String, Painter>,
) {
    // Not synchronised. Two coroutines racing on the same card decode it twice and the
    // second write wins, which costs one redundant decode and is otherwise harmless; a
    // mutex here would be protecting nothing worth protecting.
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
        // read. See `tools/renumber_to_blocks.py`, which is what moved them.
        return faces.getOrPut(id) { loadImage("$CARDS_DIR/$id.png") }
    }
}

internal val Card.textureId: String get() = cardTextureId(id)

internal fun cardTextureId(cardId: Int): String =
    cardId.toString(HEX_RADIX).padStart(HEX_WIDTH, '0')

private const val HEX_RADIX = 16
private const val HEX_WIDTH = 4

internal val CardType.textureName: String get() = "type-${name.lowercase()}"

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
private const val PLATE_TEXTURE = "cdbg"

private const val DIGIT_PX = 18
private const val PLATE_PX = 28
private const val PLATE_Y_PX = 62

private const val ATLAS_MARGIN_PX = 2
private const val ATLAS_COLUMNS = 5
private const val DIGIT_PITCH_PX = 20
