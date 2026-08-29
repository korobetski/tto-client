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

    @Test
    fun theGameColoursAreTheCardsOwn() {
        val (_, _, game) = read()

        assertEquals(CARD_BLUE, game.cardBlue, "Card.BLUE_COLOR")
        assertEquals(CARD_RED, game.cardRed, "Card.RED_COLOR")
        assertEquals(CARD_GREY, game.cardGrey, "Card.GREY_COLOR")
        assertEquals(LARGE_BLUE_ELEMENT, game.cardBlueEdge, "largeBlueElementFormat")
        assertEquals(LARGE_RED_ELEMENT, game.cardRedEdge, "largeRedElementFormat")
    }

    @Test
    fun theAccentFamiliesPlayTheirDeclaredParts() {
        val (scheme, _, game) = read()

        assertTrue(scheme.primary.isWarm(), "primary should be the amber family")
        assertTrue(!scheme.secondary.isWarm(), "secondary should be the blue family")
        assertTrue(!game.selectedOutline.isWarm(), "a selected row is state, so it is blue")
        assertTrue(game.transient.isWarm(), "a boon is a temporary accent, so it is amber")
    }

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

    @Test
    fun theFontResourcesAreShipped() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }

        awaitTitle()
    }

    private companion object {
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

        fun Color.isWarm(): Boolean = red > blue

        val DELIBERATELY_MATERIALS = setOf("scrim")

        /* `display/Card.as:29-31` and `BaseTTOTheme.as:1537-1544`, typed out independently. */
        val CARD_BLUE = Color(0xFF2D4660)
        val CARD_RED = Color(0xFF602D2D)
        val CARD_GREY = Color(0xFF5A595A)
        val LARGE_BLUE_ELEMENT = Color(0xFF43A7C8)
        val LARGE_RED_ELEMENT = Color(0xFFBB594F)

        val LADDER = listOf(24, 22, 16, 14, 16, 12, 12, 11)
    }
}
