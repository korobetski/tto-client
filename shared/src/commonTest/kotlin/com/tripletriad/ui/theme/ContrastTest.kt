package com.tripletriad.ui.theme

import androidx.compose.ui.graphics.Color
import com.tripletriad.ui.FAINT
import com.tripletriad.ui.MUTED
import com.tripletriad.ui.SUBDUED
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class ContrastTest {

    private val grounds = mapOf(
        "surface" to TtoColorScheme.surface,
        "surfaceContainerLowest" to TtoColorScheme.surfaceContainerLowest,
        "surfaceContainerLow" to TtoColorScheme.surfaceContainerLow,
        "surfaceContainer" to TtoColorScheme.surfaceContainer,
        "surfaceContainerHigh" to TtoColorScheme.surfaceContainerHigh,
        "surfaceContainerHighest" to TtoColorScheme.surfaceContainerHighest,
    )

    private val alphas = listOf("SUBDUED" to SUBDUED, "MUTED" to MUTED, "FAINT" to FAINT)

    @Test
    fun everyDimmedTextClearsTheBodyThreshold() {
        for ((name, ground) in grounds) {
            for ((label, alpha) in alphas) {
                val ratio = contrast(TtoColorScheme.onSurface.over(ground, alpha), ground)
                assertTrue(
                    ratio >= BODY_TEXT,
                    "$label text on $name is $ratio:1, below WCAG AA's $BODY_TEXT:1",
                )
            }
        }
    }

    @Test
    fun undimmedTextIsComfortable() {
        for ((name, ground) in grounds) {
            val ratio = contrast(TtoColorScheme.onSurface, ground)
            assertTrue(ratio >= COMFORTABLE, "text on $name is only $ratio:1")
        }
    }

    @Test
    fun theAccentIsLegibleAsText() {
        for ((name, ground) in grounds) {
            val ratio = contrast(TtoColorScheme.primary, ground)
            assertTrue(ratio >= BODY_TEXT, "primary as text on $name is $ratio:1")
        }
    }

    @Test
    fun everyRoleReadsAgainstItsOwnForeground() {
        val scheme = TtoColorScheme
        val pairs = listOf(
            "primary" to (scheme.primary to scheme.onPrimary),
            "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
            "secondary" to (scheme.secondary to scheme.onSecondary),
            "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
            "tertiary" to (scheme.tertiary to scheme.onTertiary),
            "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
            "error" to (scheme.error to scheme.onError),
            "errorContainer" to (scheme.errorContainer to scheme.onErrorContainer),
            "background" to (scheme.background to scheme.onBackground),
            "surface" to (scheme.surface to scheme.onSurface),
            "surfaceVariant" to (scheme.surfaceVariant to scheme.onSurfaceVariant),
            "inverseSurface" to (scheme.inverseSurface to scheme.inverseOnSurface),
        )

        for ((name, pair) in pairs) {
            val ratio = contrast(pair.first, pair.second)
            assertTrue(ratio >= BODY_TEXT, "$name against its on- role is $ratio:1")
        }
    }

    @Test
    fun theSurfaceContainersAreFiveOrderedSteps() {
        val steps = listOf(
            "Lowest" to TtoColorScheme.surfaceContainerLowest,
            "Low" to TtoColorScheme.surfaceContainerLow,
            "" to TtoColorScheme.surfaceContainer,
            "High" to TtoColorScheme.surfaceContainerHigh,
            "Highest" to TtoColorScheme.surfaceContainerHighest,
        )

        assertTrue(
            steps.map { it.second }.toSet().size == steps.size,
            "two container tones are the same colour: ${steps.map { it.first }}",
        )

        val brightness = steps.map { it.second.relativeLuminance() }
        assertTrue(
            brightness == brightness.sorted(),
            "the container ramp should get lighter, lowest to highest: $brightness",
        )
    }

    @Test
    fun theAffirmativePairIsLegible() {
        val game = TtoColors()

        assertTrue(
            contrast(game.positive, game.onPositive) >= BODY_TEXT,
            "the positive pair is ${contrast(game.positive, game.onPositive)}:1",
        )
        for ((name, ground) in grounds) {
            val ratio = contrast(game.positive, ground)
            assertTrue(ratio >= BODY_TEXT, "positive as text on $name is $ratio:1")
        }
    }

    // ---- WCAG 2.1 ---------------------------------------------------------

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
        const val BODY_TEXT = 4.5

        const val COMFORTABLE = 7.0
    }
}
