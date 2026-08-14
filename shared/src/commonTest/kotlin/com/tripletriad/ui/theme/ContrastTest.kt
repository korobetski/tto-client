package com.tripletriad.ui.theme

import androidx.compose.ui.graphics.Color
import com.tripletriad.ui.FAINT
import com.tripletriad.ui.MUTED
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the text this app draws can actually be read.
 *
 * ### Why this is arithmetic and not an opinion
 *
 * WCAG contrast is a formula over two colours, so "is this legible" has an answer rather than a
 * view. The palette is dark and the two alphas it dims text with — [MUTED] and [FAINT] — are
 * applied all over the app, on four different grounds. Whether the result clears AA is not
 * something anybody can tell by looking, and it is exactly the sort of thing that decays: somebody
 * lightens a surface, somebody else drops an alpha, and each change looks fine on its own.
 *
 * The answer today is that everything clears **4.5:1**, with the tightest at 4.63 — [FAINT] on
 * [Surface]. That is not much headroom, which is the reason to write it down.
 *
 * ### The alphas are composited, not applied to the ratio
 *
 * `onSurface.copy(alpha = 0.7f)` does not produce 70% of the contrast; it produces a *new colour*,
 * the text blended onto whatever is behind it. Computing the ratio from the unblended colour would
 * report the full-strength figure and pass a palette that is genuinely too dim.
 */
class ContrastTest {

    /** Every ground this app draws body text on, with the name it is known by. */
    private val grounds = mapOf(
        "Background" to Background,
        "Surface" to Surface,
        "SurfaceRaised" to SurfaceRaised,
        "SurfaceSunken" to SurfaceSunken,
    )

    @Test
    fun everyDimmedTextClearsTheBodyThreshold() {
        for ((name, ground) in grounds) {
            for ((label, alpha) in listOf("MUTED" to MUTED, "FAINT" to FAINT)) {
                val ratio = contrast(LightText.over(ground, alpha), ground)
                assertTrue(
                    ratio >= BODY_TEXT,
                    "$label text on $name is $ratio:1, below WCAG AA's $BODY_TEXT:1",
                )
            }
        }
    }

    /** And undimmed text has room to spare, which is what makes the dimmed cases the tight ones. */
    @Test
    fun undimmedTextIsComfortable() {
        for ((name, ground) in grounds) {
            val ratio = contrast(LightText, ground)
            assertTrue(ratio >= COMFORTABLE, "text on $name is only $ratio:1")
        }
    }

    /**
     * The orange that marks a selection is legible on the surfaces it is drawn on.
     *
     * It is the one colour here chosen for meaning rather than for contrast — `SELECTED_TEXT_COLOR`
     * in the original — so it is the one most likely to fail, and it is used for text.
     */
    @Test
    fun theSelectionColourIsLegible() {
        for ((name, ground) in grounds) {
            val ratio = contrast(SelectedText, ground)
            assertTrue(ratio >= BODY_TEXT, "the selection colour on $name is $ratio:1")
        }
    }

    // ---- WCAG 2.1 ---------------------------------------------------------

    /** This colour composited onto [ground] at [alpha] — what is actually on screen. */
    private fun Color.over(ground: Color, alpha: Float): Color = Color(
        red = red * alpha + ground.red * (1 - alpha),
        green = green * alpha + ground.green * (1 - alpha),
        blue = blue * alpha + ground.blue * (1 - alpha),
    )

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.relativeLuminance(), b.relativeLuminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun Color.relativeLuminance(): Double =
        0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)

    private fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private companion object {
        /** WCAG AA for ordinary text. Large text is allowed 3.0; nothing here relies on that. */
        const val BODY_TEXT = 4.5

        /** What full-strength text should have, so the dimmed cases are the tight ones. */
        const val COMFORTABLE = 7.0
    }
}
