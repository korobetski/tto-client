package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.Side
import com.tripletriad.model.power
import com.tripletriad.ui.theme.LocalTtoColors

/**
 * The face of a card, at [scale] times its authored size.
 *
 * ### Layer order
 *
 * This is `Card.as`'s display list in `addChild` order, and the reason the layers are here
 * rather than baked into one image: the artwork is a 104x128 frame with a **translucent
 * centre**, and what shows through it is the owner's colour quad. One artwork therefore
 * serves both sides, which is how the original gets away with 263 images and not 526.
 *
 * | # | Layer | Where | Source |
 * |--:|---|---|---|
 * | 0 | `cardSelected` glow | (-16, -4) | `Card.as:66-70` — **not ported**, see below |
 * | 1 | owner colour quad 88x118 | (8, 5) | `Card.as:73-76` |
 * | 2 | artwork 104x128 | (0, 0) | `Card.as:169-170` |
 * | 3 | rarity row 29x28 | (9, 6) | `Card.as:176-178` |
 * | 4 | type icon 20x20 | (80, 3) | `Card.as:181-183` |
 * | 5 | `_modifier` text 32x32 | (36, 48) | `Card.as:81-85` — **not ported**, see below |
 * | 6 | digit cluster 44x30 | (28, 88) | `Card.as:88-90` |
 * | 7 | card back 104x128 | (0, 0) | `Card.as:93-94`, shown while flipping |
 *
 * Layer 0 is the selection glow; this port rings the card instead ([HandCard]), because the
 * glow texture is drawn outside even the sprite bounds and would have to grow every slot.
 *
 * Layer 5 is the Bonus/Malus `±N` badge. It **is** drawn now, but by the tile rather than by the
 * card — see `TileCell`. A card is drawn in six places and only one of them is a board; a badge
 * living here would need a rule, a tally and a cell element passed to the shop, the collection and
 * the pack reveal, all of which would pass nothing. The digits below it stay the card's own.
 *
 * ### Why every dimension is multiplied rather than the layer scaled
 *
 * The obvious way to shrink this is to measure it at full size and scale the render layer
 * (`requiredSize` + `graphicsLayer { scaleX = scale }`). That was the first implementation
 * and it was wrong: the composable then *reports* a small size while *drawing* a large one,
 * so anything that puts it in an offscreen layer clips it. In particular the dimmed hand
 * applies `alpha`, which forces exactly such a layer — so the waiting side's cards rendered
 * as slivers while the active side's, drawn straight into the parent, looked fine. Multiplying
 * the geometry keeps drawn bounds and reported bounds identical, which is the only version of
 * this that composes safely.
 */
/**
 * What a card says to a screen reader: its name and its four powers, or nothing it may not tell.
 *
 * ### The face-down case is a leak, not a wording choice
 *
 * A card drawn with [showBack] is one the player is **not allowed to see** — the Open rules reveal
 * some of the opponent's hand and hide the rest, and `HandVisibility` is what decides which. A
 * label built from the card would announce the hidden ones to anybody with a screen reader on,
 * which is the same information leak the whole `MatchView` design exists to prevent, arriving
 * through the accessibility tree instead of the wire.
 *
 * So a face-down card is labelled as a face-down card, and the value is never read.
 *
 * ### Why the powers are digits and not words
 *
 * They are the same four numbers in every language, they are what a player compares, and a
 * sentence around them would be four words of preamble before the only part that matters. The
 * order matches the card: top, right, bottom, left.
 *
 * They are the card's **printed** four, everywhere, including on a board under Bonus, Malus or
 * Elemental. That is not an omission: those are the values Same and Plus read
 * (`RulesEngineOptions.specialPowerBasis`), so they are the values a player reasons with, and a
 * label that quietly announced modified ones would describe a board nobody else is playing on. The
 * modifier is announced separately, by the badge `TileCell` draws — which is a `Text`, and so is in
 * the accessibility tree already.
 */
@Composable
private fun cardLabel(card: Card, showBack: Boolean): String {
    val strings = LocalStrings.current
    if (showBack) return strings[StringKeys.CARD_FACE_DOWN]

    val name = strings[card.nameKey]
    return "$name, ${card.top} ${card.right} ${card.bottom} ${card.left}"
}

@Composable
internal fun CardFace(
    card: Card,
    scale: Float = 1f,
    showBack: Boolean = false,
    highlight: Set<Side> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val art = LocalCardArt.current
    val face = rememberCardFace(art, card)
    val label = cardLabel(card, showBack)

    Box(
        modifier = modifier
            .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
            // The one place every card in the game is drawn, so the one place worth labelling: a
            // hand, a board cell, a shop row and a collection tile all come through here. Without
            // it a screen reader is handed a stack of unlabelled boxes, which is a card game that
            // cannot be played without sight — see [cardLabel].
            .semantics { contentDescription = label },
    ) {
        // Layer 1. A `Quad` in the original, so a fill here and not a texture.
        Box(
            modifier = Modifier
                .offset(x = CardFaceOffsetX * scale, y = CardFaceOffsetY * scale)
                .size(CardWidth * scale, CardHeight * scale)
                .background(card.owner.background),
        )
        Layer(face, x = 0.dp, y = 0.dp, width = CardSpriteWidth, height = CardSpriteHeight, scale)
        Layer(art?.starsFor(card.rarity), RarityX, RarityY, RarityWidth, RarityHeight, scale)
        card.type?.let { Layer(art?.typeIcon(it), TypeX, TypeY, TypeSize, TypeSize, scale) }
        CardDigits(
            card = card,
            art = art,
            scale = scale,
            highlight = highlight,
            modifier = Modifier.offset(x = DigitsOriginX * scale, y = DigitsOriginY * scale),
        )
        if (showBack) {
            Layer(art?.back, 0.dp, 0.dp, CardSpriteWidth, CardSpriteHeight, scale)
        }
    }
}

/**
 * A face-down card in a colour, with **no card behind it**.
 *
 * `new Card()` followed by `draw('blue')` — the original's idiom for a card back that is
 * not any particular card. `PileOuFace` uses it for the three coin-flip cards and `Mogu`
 * for its stack.
 *
 * Deliberately not [CardFace]`(showBack = true)`, which was the first attempt and fails:
 * that reads `card.textureId` and asks for that card's artwork, so a placeholder card
 * throws `MissingResourceException` for a picture nothing was ever going to draw. The
 * covered layers are not merely hidden here, they are absent.
 *
 * @param color which side's colour shows through the back's transparent parts.
 */
@Composable
internal fun CardBack(color: CardColor, scale: Float = 1f, modifier: Modifier = Modifier) {
    val art = LocalCardArt.current
    Box(modifier = modifier.size(CardSpriteWidth * scale, CardSpriteHeight * scale)) {
        Box(
            modifier = Modifier
                .offset(x = CardFaceOffsetX * scale, y = CardFaceOffsetY * scale)
                .size(CardWidth * scale, CardHeight * scale)
                .background(color.background),
        )
        Layer(art?.back, 0.dp, 0.dp, CardSpriteWidth, CardSpriteHeight, scale)
    }
}

/**
 * The four edge powers, laid out exactly as `tto.display.CardDigits`:
 * ```
 * private static const positions:Array = [{x:14,y:0},{x:26,y:6},{x:14,y:12},{x:2,y:6}];
 * // power [top, right, bottom, left];
 * ```
 * Those are the *top-left corners* of 18x18 digit textures, over a 28x28 `cdbg` plate
 * drawn at (8, 1). The digits therefore overhang the plate on the left and the top, which
 * is what gives the badge its diamond silhouette — so the cluster's own bounds are 44x30,
 * wider than the plate.
 *
 * `CardDigits.as:29` sets `alpha = 0.5` on the plate, but the `cdbg` texture is already
 * semi-transparent, so nothing dims it again here.
 *
 * Always the card's own numbers. What a modifier does is drawn *over* the card by `TileCell`, and
 * deliberately not folded in here — see [CardFace].
 */
@Composable
private fun CardDigits(
    card: Card,
    art: CardArt?,
    scale: Float,
    highlight: Set<Side>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(DigitsClusterWidth * scale, DigitsClusterHeight * scale)) {
        Glyph(art?.digitPlate, DigitsPlateOffsetX, DigitsPlateOffsetY, DigitsPlateSize, scale)
        for (side in Side.entries) {
            val (x, y) = DIGIT_POSITIONS.getValue(side)
            // Behind the glyph, so the number stays readable and the ring reads as being *around*
            // it. Drawn per side rather than as one overlay because which sides are lit is the
            // whole message — see [captureHighlights].
            if (side in highlight) DigitHalo(x, y, scale)
            Glyph(art?.digit(card.power(side)), x = x, y = y, size = DigitSize, scale = scale)
        }
    }
}

/**
 * A ring behind one digit: *this* number is why the card turned.
 *
 * ### Why it is a shape and not a tint
 *
 * The digits are 18x18 textures from the AS3 atlas, one per value — there is no glyph to recolour,
 * and tinting the bitmap would repaint the number itself rather than mark it. A ring behind it
 * leaves the artwork exactly as the original drew it and still says which of the four to read.
 *
 * ### Why it does not pulse
 *
 * A `rememberInfiniteTransition` would be the obvious way to draw the eye, and it would hang every
 * Compose UI test in the suite: `waitForIdle` waits for the composition to settle, and an animation
 * that never ends never lets it. The ring is static and sized well clear of the digit instead.
 */
@Composable
private fun DigitHalo(x: Dp, y: Dp, scale: Float) {
    val ring = LocalTtoColors.current.selectionRing

    Box(
        modifier = Modifier
            .offset(x = (x - HaloInset) * scale, y = (y - HaloInset) * scale)
            .size((DigitSize + HaloInset * 2) * scale)
            .background(ring.copy(alpha = HALO_FILL), CircleShape)
            .border(HaloWidth * scale, ring, CircleShape),
    )
}

/**
 * `CardDigits.positions` — the top-left corner of each digit, by side.
 *
 * The same four pairs the KDoc above transcribes, as a table so the halo and the glyph cannot
 * disagree about where a digit is.
 */
private val DIGIT_POSITIONS: Map<Side, Pair<Dp, Dp>> = mapOf(
    Side.TOP to (14.dp to 0.dp),
    Side.RIGHT to (26.dp to 6.dp),
    Side.BOTTOM to (14.dp to 12.dp),
    Side.LEFT to (2.dp to 6.dp),
)

/** How far the ring stands off the digit, and how heavy its edge is. */
private val HaloInset = 3.dp
private val HaloWidth = 1.5.dp

/** Enough to read as a disc behind the number without swallowing it. */
private const val HALO_FILL = 0.35f

/**
 * One absolutely-positioned layer: a `Starling.Image` at a fixed offset and size.
 *
 * A null [bitmap] leaves the slot empty rather than drawing a placeholder, so a card composes
 * correctly before its artwork has decoded and in a preview with no [CardArt] at all.
 *
 * Filtering is left at Compose's default (bilinear), which is also Starling's
 * (`TextureSmoothing.BILINEAR`). These are 104x128 textures authored for a 1:1 stage and drawn
 * here on a 2.6x-density screen, so they are being *up*scaled — nearest-neighbour would show
 * the card frame as a staircase.
 */
@Composable
private fun Layer(
    bitmap: ImageBitmap?,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    scale: Float,
) {
    val placed = Modifier.offset(x = x * scale, y = y * scale).size(width * scale, height * scale)
    if (bitmap == null) {
        Box(modifier = placed)
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = placed,
            contentScale = ContentScale.FillBounds,
        )
    }
}

/**
 * As [Layer], for the square atlas-backed glyphs.
 *
 * They arrive as [BitmapPainter]s holding a source rectangle rather than as separate bitmaps —
 * see [CardArt] — so they need the painter overload of `Image`.
 */
@Composable
private fun Glyph(painter: Painter?, x: Dp, y: Dp, size: Dp, scale: Float) {
    val placed = Modifier.offset(x = x * scale, y = y * scale).size(size * scale)
    if (painter == null) {
        Box(modifier = placed)
    } else {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = placed,
            contentScale = ContentScale.FillBounds,
        )
    }
}

/** Face origin inside the 104x128 sprite: `colorBackground.x/.y` — `Card.as:74-75`. */
private val CardFaceOffsetX = 8.dp
private val CardFaceOffsetY = 5.dp

/** `rc.x = 9; rc.y = 6` — `Card.as:177-178`. Size read off the `card_rarities` PNGs. */
private val RarityX = 9.dp
private val RarityY = 6.dp
private val RarityWidth = 29.dp
private val RarityHeight = 28.dp

/** `type.x = 80; type.y = 3` — `Card.as:182-183`. Size read off the `card_types` PNGs. */
private val TypeX = 80.dp
private val TypeY = 3.dp
private val TypeSize = 20.dp

/** Cluster bounds: x spans 2..26+18 = 44, y spans 0..12+18 = 30. */
private val DigitsClusterWidth = 44.dp
private val DigitsClusterHeight = 30.dp
