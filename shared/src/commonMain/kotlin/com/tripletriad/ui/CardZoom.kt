package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tripletriad.model.Card

/** The whole-window view of one card that tapping the sprite in [CardPanel] opens. */
internal const val CARD_ZOOM_TEST_TAG = "card-zoom"

internal const val CARD_ZOOM_FACE_TEST_TAG = "card-zoom-face"

/**
 * One card over the whole window, as large as the window allows up to [ZOOM_CEILING]; any tap
 * closes it.
 *
 * A [Popup] rather than an overlay hoisted to the screen, as `UnlockedCard` is: the panel is drawn
 * inside a bottom sheet on a phone and inside a pane on a desktop, and neither can lay anything
 * over the scaffold around it.
 *
 * ### Why the ceiling is 2
 *
 * The faces are painted at 208 × 256 px, twice [CardSpriteWidth] × [CardSpriteHeight], so scale 2
 * at density 1 — a desktop window at 100 % — is the art pixel for pixel, which is what the zoom was
 * asked for. Above that it would only be enlarging a bitmap. On a phone (density 2.5 or so) scale
 * 2 is already an upscale, but it is also about half the screen's width: capping at the native
 * pixels there would draw the card *smaller* than the panel it was opened from.
 *
 * The digits and stars stay drawn over the art, as they are everywhere else: [CardFace] is one
 * picture of a card, and a second one without its numbers would be a card the game never shows.
 */
@Composable
internal fun CardZoom(card: Card, onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .testTag(CARD_ZOOM_TEST_TAG)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = MUTED))
                .ttoClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val fit = minOf(maxWidth / CardSpriteWidth, maxHeight / CardSpriteHeight) * ZOOM_MARGIN
            CardFace(
                card = card,
                scale = fit.coerceAtMost(ZOOM_CEILING),
                modifier = Modifier.testTag(CARD_ZOOM_FACE_TEST_TAG),
            )
        }
    }
}

private const val ZOOM_CEILING = 2f

/** The share of the window the card may take, so the scrim around it shows where to tap. */
private const val ZOOM_MARGIN = 0.9f
