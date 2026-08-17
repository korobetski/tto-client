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

private val DIGIT_POSITIONS: Map<Side, Pair<Dp, Dp>> = mapOf(
    Side.TOP to (14.dp to 0.dp),
    Side.RIGHT to (26.dp to 6.dp),
    Side.BOTTOM to (14.dp to 12.dp),
    Side.LEFT to (2.dp to 6.dp),
)

private val HaloInset = 3.dp
private val HaloWidth = 1.5.dp

private const val HALO_FILL = 0.35f

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

private val CardFaceOffsetX = 8.dp
private val CardFaceOffsetY = 5.dp

private val RarityX = 9.dp
private val RarityY = 6.dp
private val RarityWidth = 29.dp
private val RarityHeight = 28.dp

private val TypeX = 80.dp
private val TypeY = 3.dp
private val TypeSize = 20.dp

private val DigitsClusterWidth = 44.dp
private val DigitsClusterHeight = 30.dp
