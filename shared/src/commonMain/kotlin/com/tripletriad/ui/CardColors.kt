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
