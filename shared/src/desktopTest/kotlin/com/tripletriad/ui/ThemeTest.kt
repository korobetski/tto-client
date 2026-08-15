package com.tripletriad.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.isUnspecified
import com.tripletriad.i18n.AppLocale
import com.tripletriad.ui.theme.LocalTtoColors
import com.tripletriad.ui.theme.TripleTriadTheme
import com.tripletriad.ui.theme.TtoColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The theme, read the way a screen reads it.
 *
 * Asserted through a real composition rather than against the private scheme object, because what
 * matters is what `MaterialTheme.colorScheme` hands a composable — a scheme that were built
 * correctly and then not provided would pass any test that only inspected the value.
 *
 * ### What this file guards, after the refresh
 *
 * It used to be a transcription fence: the scheme's values were `BaseTTOTheme.as`'s constants,
 * typed by hand out of a file that is not compiled, and nothing but a test would notice one being
 * mistyped. The refresh replaced the transcription with a tonal system, so that job is gone and a
 * harder one takes its place — **completeness**. No role left at Material's baseline, which is
 * the defect that made every `Snackbar` in the app a light grey box, and which is not checkable
 * by looking at any one value.
 *
 * Legibility is the other half of that claim and is **not** here: `ContrastTest` does it, in
 * `commonTest`, because it is arithmetic over the palette and needs no composition to run.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeTest {
    private fun read(): Triple<ColorScheme, Typography, TtoColors> {
        var captured: Triple<ColorScheme, Typography, TtoColors>? = null
        runComposeUiTest {
            setContent {
                TripleTriadTheme {
                    captured = Triple(
                        MaterialTheme.colorScheme,
                        MaterialTheme.typography,
                        LocalTtoColors.current,
                    )
                }
            }
            waitForIdle()
        }
        return assertNotNull(captured, "the theme provided nothing")
    }

    /**
     * **No role is left at Material's baseline.**
     *
     * The assertion the old scheme would have failed, and the reason this rewrite happened. It
     * filled eighteen roles of the thirty-odd and left the rest at `darkColorScheme()`, whose
     * defaults are a lavender purple. Two of those holes were visible in the shipped app:
     * `Snackbar` draws on `inverseSurface`, so every confirmation appeared as a light grey box on
     * a dark screen, and `FilterChip` draws its selected state on `secondaryContainer`, so the
     * same chip looked like two different controls depending on which of four screens it was on.
     *
     * Comparing against the baseline rather than against a written-down list is what makes this
     * survive a Material release adding a role: a new slot arrives with its default value, and this
     * fails until somebody decides what it should be here.
     *
     * **`scrim` is exempt, and it is the only one.** Material's default is black, this scheme's is
     * black, and they agree because black is what a scrim should be — the dim behind a modal has
     * no hue to carry. Matching the baseline there is a decision that landed on the same value,
     * not a role nobody filled. The exemption is a list of one so that a second name appearing in
     * it is visible in a diff.
     */
    @Test
    fun noRoleIsLeftAtMaterialsDefault() {
        val (scheme, _, _) = read()
        val baseline = darkColorScheme()

        val untouched = roles(scheme).filter { (name, color) ->
            name !in DELIBERATELY_MATERIALS && color == roles(baseline).toMap()[name]
        }

        assertTrue(
            untouched.isEmpty(),
            "still Material's baseline: ${untouched.map { it.first }}",
        )
    }

    /**
     * The two card quads and the two side text colours, from `Card.as` and the AS3 theme.
     *
     * These survive the refresh untouched and this is the assertion that says so. They are what the
     * two **players** are, not what the app's chrome is, and re-deriving them from a ramp would
     * have changed the board to make the buttons tidier. Written out below as a second,
     * independent transcription rather than read off the theme.
     */
    @Test
    fun theGameColoursAreTheCardsOwn() {
        val (_, _, game) = read()

        assertEquals(CARD_BLUE, game.cardBlue, "Card.BLUE_COLOR")
        assertEquals(CARD_RED, game.cardRed, "Card.RED_COLOR")
        assertEquals(CARD_GREY, game.cardGrey, "Card.GREY_COLOR")
        assertEquals(LARGE_BLUE_ELEMENT, game.cardBlueEdge, "largeBlueElementFormat")
        assertEquals(LARGE_RED_ELEMENT, game.cardRedEdge, "largeRedElementFormat")
    }

    /**
     * Amber for actions, blue for state — and the two do not swap places quietly.
     *
     * The refresh reversed what the old scheme did: the card blue used to be `primary`, which put
     * every button in the same colour as one of the two players, and the AS3's orange was kept off
     * `primary` on the grounds that it would make everything look selected. Inside a tonal system
     * the accent arrives as tone 80 rather than as raw `#FF9900`, and the role it conflicted with
     * is now the blue family's. See `Palette.kt`, which records the argument.
     *
     * Asserted by *hue family* rather than by value: what must not drift is the relationship, and
     * pinning the exact bytes would fail on any tonal adjustment that kept the design intact.
     */
    @Test
    fun theAccentFamiliesPlayTheirDeclaredParts() {
        val (scheme, _, game) = read()

        assertTrue(scheme.primary.isWarm(), "primary should be the amber family")
        assertTrue(!scheme.secondary.isWarm(), "secondary should be the blue family")
        assertTrue(!game.selectedOutline.isWarm(), "a selected row is state, so it is blue")
        assertTrue(game.transient.isWarm(), "a boon is a temporary accent, so it is amber")
    }

    /**
     * **Every** type slot carries the game font, not only the ones this app names.
     *
     * `Text`'s default style is `typography.bodyLarge`, and a `Text` that sets `fontSize` without
     * setting `style` still takes its *family* from there. So a typography that set the family on
     * seven slots and left the other eight at Material's defaults would leave most of the screen in
     * the platform font while the theme claimed to have set one. This is the assertion that says it
     * does not.
     */
    @Test
    fun everyTypeSlotCarriesTheGameFont() {
        val (_, typography, _) = read()
        val slots: List<Pair<String, TextStyle>> = listOf(
            "displayLarge" to typography.displayLarge,
            "displayMedium" to typography.displayMedium,
            "displaySmall" to typography.displaySmall,
            "headlineLarge" to typography.headlineLarge,
            "headlineMedium" to typography.headlineMedium,
            "headlineSmall" to typography.headlineSmall,
            "titleLarge" to typography.titleLarge,
            "titleMedium" to typography.titleMedium,
            "titleSmall" to typography.titleSmall,
            "bodyLarge" to typography.bodyLarge,
            "bodyMedium" to typography.bodyMedium,
            "bodySmall" to typography.bodySmall,
            "labelLarge" to typography.labelLarge,
            "labelMedium" to typography.labelMedium,
            "labelSmall" to typography.labelSmall,
        )

        val platform = slots.filter { (_, style) ->
            style.fontFamily == null || style.fontFamily == FontFamily.Default
        }
        assertTrue(platform.isEmpty(), "left in the platform font: ${platform.map { it.first }}")
    }

    /**
     * The type scale is Material 3's, and every slot has a line height.
     *
     * Pinned because the ladder is a design decision made twice — see `appTypography`, which
     * records the AS3's four sizes, the port's first re-anchoring to 11–18 sp, and why that one
     * was too flat to be a hierarchy. Coming back down to a compressed scale should have to come
     * past a failing test rather than happening as a side effect of tidying one screen.
     *
     * The line-height half is the part with teeth: the previous scale set sizes and nothing else,
     * so every slot inherited `TextStyle`'s unspecified line height and the denser screens ran
     * their lines together. A slot with a size and no metrics is the failure this catches.
     */
    @Test
    fun theTypeScaleIsMaterialsLadder() {
        val (_, typography, _) = read()

        val ladder = listOf(
            "screen title" to typography.headlineSmall,
            "app bar title" to typography.titleLarge,
            "button" to typography.titleMedium,
            "row title" to typography.titleSmall,
            "body, and the default" to typography.bodyLarge,
            "secondary line" to typography.bodySmall,
            "metadata" to typography.labelMedium,
            "the smallest" to typography.labelSmall,
        )

        assertEquals(
            LADDER,
            ladder.map { (_, style) -> style.fontSize.value.toInt() },
            "the scale is ${ladder.map { it.first }}",
        )

        val unspecified = ladder.filter { (_, style) -> style.lineHeight.isUnspecified }
        assertTrue(unspecified.isEmpty(), "no line height: ${unspecified.map { it.first }}")
    }

    /**
     * The two Raleway faces are in the bundle.
     *
     * The same job `CardBundleTest` does for `cards.json`: a font dropped from packaging is a
     * failure no unit test would otherwise see, and the app would go on rendering in the platform
     * font without complaining. Reaching a composed screen at all is what proves it, since
     * `appTypography` resolves both faces before the first frame.
     */
    @Test
    fun theFontResourcesAreShipped() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }

        awaitMenu()
    }

    private companion object {
        /**
         * Every role a [ColorScheme] declares, by name.
         *
         * Listed rather than reflected because Kotlin/Native has no reflection and this file's
         * assertions should mean the same thing on every target the suite might one day run on.
         * A Material release adding a role leaves it off this list, which under-reports rather than
         * failing wrongly — the trade a hand-written list makes, and the reason the list is here
         * in one place instead of repeated per test.
         */
        fun roles(scheme: ColorScheme): List<Pair<String, Color>> = listOf(
            "primary" to scheme.primary,
            "onPrimary" to scheme.onPrimary,
            "primaryContainer" to scheme.primaryContainer,
            "onPrimaryContainer" to scheme.onPrimaryContainer,
            "inversePrimary" to scheme.inversePrimary,
            "secondary" to scheme.secondary,
            "onSecondary" to scheme.onSecondary,
            "secondaryContainer" to scheme.secondaryContainer,
            "onSecondaryContainer" to scheme.onSecondaryContainer,
            "tertiary" to scheme.tertiary,
            "onTertiary" to scheme.onTertiary,
            "tertiaryContainer" to scheme.tertiaryContainer,
            "onTertiaryContainer" to scheme.onTertiaryContainer,
            "background" to scheme.background,
            "onBackground" to scheme.onBackground,
            "surface" to scheme.surface,
            "onSurface" to scheme.onSurface,
            "surfaceVariant" to scheme.surfaceVariant,
            "onSurfaceVariant" to scheme.onSurfaceVariant,
            "surfaceTint" to scheme.surfaceTint,
            "inverseSurface" to scheme.inverseSurface,
            "inverseOnSurface" to scheme.inverseOnSurface,
            "error" to scheme.error,
            "onError" to scheme.onError,
            "errorContainer" to scheme.errorContainer,
            "onErrorContainer" to scheme.onErrorContainer,
            "outline" to scheme.outline,
            "outlineVariant" to scheme.outlineVariant,
            "scrim" to scheme.scrim,
            "surfaceBright" to scheme.surfaceBright,
            "surfaceDim" to scheme.surfaceDim,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        )

        /** Amber against blue, which is the only distinction the accent assertions need. */
        fun Color.isWarm(): Boolean = red > blue

        /** Roles whose right answer happens to be Material's. See the test's own note. */
        val DELIBERATELY_MATERIALS = setOf("scrim")

        /* `display/Card.as:29-31` and `BaseTTOTheme.as:1537-1544`, typed out independently. */
        val CARD_BLUE = Color(0xFF2D4660)
        val CARD_RED = Color(0xFF602D2D)
        val CARD_GREY = Color(0xFF5A595A)
        val LARGE_BLUE_ELEMENT = Color(0xFF43A7C8)
        val LARGE_RED_ELEMENT = Color(0xFFBB594F)

        /** Screen title, app bar, button, row title, body, secondary, metadata, smallest. */
        val LADDER = listOf(24, 22, 16, 14, 16, 12, 12, 11)
    }
}
