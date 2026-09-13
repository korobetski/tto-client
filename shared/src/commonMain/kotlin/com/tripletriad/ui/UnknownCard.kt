package com.tripletriad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.ui.theme.LocalTtoColors

fun unknownCardTestTag(cardId: Int): String = "card-unknown-$cardId"

/**
 * A card the profile has never owned, as FFXIV's own card list draws one: no picture, no element,
 * a "?" in a card outline, and the card's number in its set under it.
 *
 * It replaces the dimmed thumbnail, which was still the picture and the element — and, one tap
 * away, the name — so there was nothing left for a card to be *found* as. The number stays: it is
 * what places the gap in the set, and it says nothing about what fills it.
 *
 * Same footprint and the same frame art as [CardTile], so a grid mixing the two keeps its rhythm
 * and a selected "?" wears the stroke every other tile does.
 */
@Composable
internal fun UnknownCardTile(card: Card, modifier: Modifier = Modifier, selected: Boolean = false) {
    val strings = LocalStrings.current
    val colors = LocalTtoColors.current

    Box(modifier = modifier.size(FramedThumbSide), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                // On the picture and not on the cell: the cell's own tag and `clickable` sit on the
                // node the caller hands in, and a second tag there would be one of the two lost.
                .testTag(unknownCardTestTag(card.id))
                .size(FramedThumbSide - FrameMargin * 2)
                .clip(RoundedCornerShape(TileCorner))
                .background(unknownGround()),
        ) {
            QuestionMark(
                outline = TileOutline,
                stroke = TileStroke,
                mark = TileMark,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = TileOutlineTop)
                    .semantics { contentDescription = strings[StringKeys.UNKNOWN_CARD] },
            )
            Text(
                text = catalogueNumber(card),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = colors.unknownCardOutline.copy(alpha = MUTED),
                    fontSize = TileNumber.fixedSp(),
                    lineHeight = TileNumber.fixedSp(),
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = TileNumberBottom),
            )
        }

        CardFrame(selected = selected)
    }
}

/**
 * The "?" at the size [CardFace] draws a card, for the detail panel — see [UnknownCardPanel].
 *
 * It measures the sprite and draws the card inside it, as [CardFace] does, so the panel's text
 * column starts at the same x whether the card is owned or not.
 */
@Composable
internal fun UnknownCardFace(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val colors = LocalTtoColors.current
    val shape = RoundedCornerShape(FaceCorner)

    Box(
        modifier = modifier
            .size(CardSpriteWidth, CardSpriteHeight)
            .semantics { contentDescription = strings[StringKeys.UNKNOWN_CARD] },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(CardWidth, CardHeight)
                .clip(shape)
                .background(unknownGround())
                .border(FaceEdge, colors.unknownCardOutline.copy(alpha = EDGE_ALPHA), shape),
            contentAlignment = Alignment.Center,
        ) {
            QuestionMark(outline = FaceOutline, stroke = FaceStroke, mark = FaceMark)
        }
    }
}

/** A card's number in its set, as the tile and the panel both print it: `044`. */
internal fun catalogueNumber(card: Card): String =
    card.number.toString().padStart(NUMBER_DIGITS, '0')

@Composable
private fun QuestionMark(outline: DpSize, stroke: Dp, mark: Dp, modifier: Modifier = Modifier) {
    val colors = LocalTtoColors.current
    val glow = with(LocalDensity.current) { mark.toPx() } * MARK_GLOW

    Box(modifier = modifier.size(outline), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val line = stroke.toPx()
            val halo = line * HALO_WIDTH
            val corner = CornerRadius(size.width * CORNER_RATIO)
            // Inset by half the halo, because a stroke is centred on its path and the outer half
            // of it would otherwise be clipped by the canvas.
            val topLeft = Offset(halo / 2, halo / 2)
            val box = Size(size.width - halo, size.height - halo)
            // The glow is the outline again, wider and mostly transparent, under the line itself.
            // A blur would be closer to the game, and a blur mask filter is not in common code.
            drawRoundRect(
                color = colors.unknownCardOutline.copy(alpha = HALO_ALPHA),
                topLeft = topLeft,
                size = box,
                cornerRadius = corner,
                style = Stroke(halo),
            )
            drawRoundRect(
                color = colors.unknownCardOutline,
                topLeft = topLeft,
                size = box,
                cornerRadius = corner,
                style = Stroke(line),
            )
        }

        Text(
            text = QUESTION_MARK,
            style = MaterialTheme.typography.titleLarge.copy(
                color = colors.unknownCardMark,
                fontSize = mark.fixedSp(),
                lineHeight = mark.fixedSp(),
                fontWeight = FontWeight.Bold,
                shadow = Shadow(color = colors.unknownCardMark, blurRadius = glow),
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun unknownGround(): Brush {
    val colors = LocalTtoColors.current
    return Brush.radialGradient(listOf(colors.unknownCardGlow, colors.unknownCardGround))
}

/**
 * A size in dp, as text. The "?" and the number are drawn *into* a picture of fixed size, so they
 * must not grow with the system font scale the way running text does — at 200 % the number would
 * be pushed out of a 40 dp tile.
 */
@Composable
private fun Dp.fixedSp(): TextUnit = with(LocalDensity.current) { this@fixedSp.toSp() }

/** The clip [CardThumb] puts on a picture, so the frame art sits on this ground as it does. */
private val TileCorner = 4.dp
private val TileOutline = DpSize(22.dp, 28.dp)
private val TileOutlineTop = 3.dp
private val TileStroke = 1.5.dp
private val TileMark = 14.dp
private val TileNumber = 8.dp
private val TileNumberBottom = 1.dp

private val FaceCorner = 6.dp
private val FaceEdge = 1.dp
private val FaceOutline = DpSize(56.dp, 74.dp)
private val FaceStroke = 2.5.dp
private val FaceMark = 40.dp

private const val QUESTION_MARK = "?"
private const val NUMBER_DIGITS = 3
private const val CORNER_RATIO = 0.16f
private const val HALO_WIDTH = 3f
private const val HALO_ALPHA = 0.3f
private const val MARK_GLOW = 0.35f
private const val EDGE_ALPHA = 0.25f
