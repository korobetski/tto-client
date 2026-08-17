package com.tripletriad.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tripletriad.ui.theme.LocalTtoColors

@Composable
internal fun PlayableRing(scale: Float) {
    Box(
        modifier = Modifier
            .testTag(CHOSEN_CARD_TEST_TAG)
            .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
            .border(
                SelectionRingWidth,
                LocalTtoColors.current.selectionRing.copy(alpha = CHOSEN_CARD_ALPHA),
                TileShape,
            ),
    )
}

internal const val CHOSEN_CARD_TEST_TAG: String = "hand-chosen"

internal fun handIsNarrowed(held: Int, playable: Int, isMyTurn: Boolean): Boolean =
    isMyTurn && playable in 1 until held

private const val CHOSEN_CARD_ALPHA = 0.55f
