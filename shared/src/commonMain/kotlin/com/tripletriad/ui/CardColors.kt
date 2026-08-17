package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tripletriad.model.CardColor
import com.tripletriad.ui.theme.LocalTtoColors

internal val CardColor.background: Color
    @Composable get() = with(LocalTtoColors.current) {
        when (this@background) {
            CardColor.BLUE -> cardBlue
            CardColor.RED -> cardRed
        }
    }

internal val CardColor.edge: Color
    @Composable get() = with(LocalTtoColors.current) {
        when (this@edge) {
            CardColor.BLUE -> cardBlueEdge
            CardColor.RED -> cardRedEdge
        }
    }

/*
 * Geometry, all of it read off `sources/src/tto/display/Card.as` and
 * `sources/src/tto/display/CardDigits.as`, with texture sizes from
 * `sources/assets/digits/digits.xml`. Starling works in points at scale 1, so these map 1:1 onto
 * dp.
 *
 *   Card sprite            104 x 128, pivot (52, 64)          Card.as:60-63 colour quad
 *   88 x 118 at (8, 5)                Card.as:73-75 rarity stars                     at (9, 6)
 *             Card.as:176-178 type icon                        at (80, 3)
 *   Card.as:181-183 modifier field          32 x  32 at (36, 48)              Card.as:81-83 digit
 *   cluster                    at (28, 88)              Card.as:88-90 `cdbg` plate          28 x
 *   28 at (8, 1), alpha 0.5     CardDigits.as:26-29 digit textures        18 x  18
 *           digits.xml top                          at (14, 0)               CardDigits.as:13 right
 *                          at (26, 6) bottom                       at (14, 12) left
 *           at (2, 6)
 *
 * Note the face is centred in the sprite: 8 dp of horizontal and 5 dp of vertical
 * margin on each side. The margin exists for the `cardSelected` glow, which is drawn
 * at (-16, -4) and so bleeds past even the sprite bounds.
 */

internal val CardSpriteWidth = 104.dp
internal val CardSpriteHeight = 128.dp

internal val CardWidth = 88.dp
internal val CardHeight = 118.dp

internal val DigitsOriginX = 28.dp
internal val DigitsOriginY = 88.dp

internal val DigitsPlateSize = 28.dp
internal val DigitsPlateOffsetX = 8.dp
internal val DigitsPlateOffsetY = 1.dp

internal val DigitSize = 18.dp
