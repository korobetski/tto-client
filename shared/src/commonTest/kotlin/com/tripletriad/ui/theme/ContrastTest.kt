package com.tripletriad.ui.theme

import androidx.compose.ui.graphics.Color
import com.tripletriad.ui.FAINT
import com.tripletriad.ui.MUTED
import com.tripletriad.ui.SUBDUED
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the text this app draws can actually be read.
 *
 * ### Why this is arithmetic and not an opinion
 *
 * WCAG contrast is a formula over two colours, so "is this legible" has an answer rather than a
 * view. The palette is dark and the three alphas it dims text with — [SUBDUED], [MUTED] and
 * [FAINT] — are applied all over the app, on six different grounds. Whether the result clears AA
 * is not something anybody can tell by looking, and it is exactly the sort of thing that decays:
 * somebody lightens a surface, somebody else drops an alpha, and each change looks fine alone.
 *
 * ### The alphas are composited, not applied to the ratio
 *
 * `onSurface.copy(alpha = 0.6f)` does not produce 60% of the contrast; it produces a *new colour*,
 * the text blended onto whatever is behind it. Computing the ratio from the unblended colour would
 * report the full-strength figure and pass a palette that is genuinely too dim.
 *
 * ### `surfaceVariant` is deliberately not among the grounds
 *
 * It is the one surface in the scheme this app must **not** dim text on, and the figure is why:
 * [FAINT] text on `surfaceVariant` measures **3.77:1**, under AA, and [MUTED] measures 4.51 —
 * clear by a hundredth. In Material 3 `surfaceVariant` is a de-emphasis role several tones lighter
 * than the surface, not the thing a row sits on; that is `surfaceContainerHigh`, which is where the
 * row surface moved and where the same [FAINT] text measures 5.04. Listing `surfaceVariant` here
 * and asserting it fails would be a test that breaks the day somebody improves the palette. Writing
 * the measurement down and leaving it out of the list is the version that stays true.
 *
 * The tightest case that *is* asserted is [FAINT] on `surfaceContainerHighest`, at 4.55:1. That is
 * a hundredth and a half of headroom, which is the reason this file exists.
 */
class ContrastTest {

    /**
     * Every ground this app draws body text on, with the role it is known by.
     *
     * Read off [TtoColorScheme] rather than off the `Palette.kt` constants, because the scheme is
     * what a screen sees: a role re-pointed at a different tone should change this test's answer.
     */
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

    /** And undimmed text has room to spare, which is what makes the dimmed cases the tight ones. */
    @Test
    fun undimmedTextIsComfortable() {
        for ((name, ground) in grounds) {
            val ratio = contrast(TtoColorScheme.onSurface, ground)
            assertTrue(ratio >= COMFORTABLE, "text on $name is only $ratio:1")
        }
    }

    /**
     * The accent that marks an action is legible on every surface it is drawn on.
     *
     * `primary` is used as *text* — a price, a highlighted figure, an outlined button's label —
     * and not only as a button fill, so it has to clear the body threshold on its own. It is also
     * the role the refresh moved: the amber family took it from the card blue, and the whole
     * argument for that swap assumes the amber reads on a dark ground. This is the assertion.
     */
    @Test
    fun theAccentIsLegibleAsText() {
        for ((name, ground) in grounds) {
            val ratio = contrast(TtoColorScheme.primary, ground)
            assertTrue(ratio >= BODY_TEXT, "primary as text on $name is $ratio:1")
        }
    }

    /**
     * Every role reads against the role Material names as its foreground.
     *
     * The other half of the claim: the tests above cover text the app places itself, this one
     * covers the pairs the **component library** places without asking — a filled button's label
     * on its container, a chip's label on its selected state, a snackbar on `inverseSurface`.
     *
     * Every `x` / `onX` relationship the scheme declares, not a sample, because a sample can pass
     * by picking the easy ones. The tones are what produce these ratios, so somebody adjusting a
     * family's chroma or nudging a surface a step darker will move them without meaning to.
     */
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

    /**
     * The five surfaces a row, a card and a dialog sit on are five distinct, ordered steps.
     *
     * Material's `surfaceContainer*` ramp exists so that stacked things stay legible against each
     * other — a card on a screen, a row inside the card. Two of them collapsing to the same value,
     * or the ramp coming out of order, would leave the app looking flat with no single call site to
     * blame, which is exactly the drift a scheme is supposed to prevent.
     */
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

    /**
     * The app's own affirmative pair, which Material has no role for.
     *
     * [TtoColors.positive] is the counterpart of `error` and is used the same way — a reachable
     * server, a payout — so it has to clear the same bar. It is outside the scheme, which means
     * nothing else checks it.
     */
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
