package com.tripletriad.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import tripletriad.shared.generated.resources.Res
import tripletriad.shared.generated.resources.raleway_medium
import tripletriad.shared.generated.resources.raleway_regular

/**
 * Raleway, in the two weights the AS3 theme embeds.
 *
 * `BaseTTOTheme.as:115-116` embeds Regular as `normal` and Medium as `bold` — so the game's "bold"
 * is a medium weight, not a bold one, and reproducing that is why [FontWeight.Bold] maps to Medium
 * here. Compose synthesises the weights in between from these two.
 *
 * **Not Eurostile**, which `docs/migration/08-PHASE-4-UI-LAYER.md` Task 4.1 names. Eurostile
 * appears once in the whole AS3 source, at `display/Card.as:81`, and it draws the `±N` modifier on
 * a card — a field this port does not render. See `tools/import_fonts.py`, which also disposes of
 * the plan's licence caveat: it was raised about Eurostile, and Raleway is under the SIL Open Font
 * License.
 *
 * Raleway has **no CJK coverage**, so `ja_JA` renders through the platform's own per-glyph font
 * fallback. The AS3 needed the two `Noto-ja` bitmap fonts in `sources/bin/assets/fonts` for this;
 * Skia and Android substitute on their own, and the `ja_JA` screens were rendered and read to check
 * it — Latin text takes Raleway, kana and kanji take the system face, in the same line. No test
 * asserts it, because none of them can look at a glyph.
 */
@Composable
internal fun rememberGameFontFamily(): FontFamily = FontFamily(
    Font(Res.font.raleway_regular, FontWeight.Normal),
    Font(Res.font.raleway_medium, FontWeight.Medium),
    Font(Res.font.raleway_medium, FontWeight.Bold),
)

/**
 * The type scale — Material 3's own, in Raleway.
 *
 * ### Why the ladder was re-anchored, twice
 *
 * The AS3 declares four sizes at `BaseTTOTheme.as:669-672` — 18, 24, 28, 36 — multiplied by the
 * device's DPI over 326. They are **pixels at 326 DPI**, so at 160 dp-per-inch they come out near
 * 9, 12, 14 and 18 dp. The first re-anchoring took the *shape* of that ladder — four steps, each
 * about a fifth larger than the last — and floated it up to 11 / 12 / 13 / 14 / 15 / 16 / 18 sp,
 * which is what the screens were written against.
 *
 * That ladder is the one this replaces, and the reason is that it never stopped being the AS3's:
 * seven steps inside eight points, so a screen title was two points larger than the body under it
 * and the whole app read as one dense weight of grey. **The gap between 11 and 18 sp is not a
 * hierarchy a player can see.** Material's scale spans 11 to 24 for the slots this app uses, and
 * the difference is not decoration — it is what lets somebody find the title of a screen without
 * reading it.
 *
 * The slots below are Material 3's published sizes, line heights and letter spacings. Line height
 * and tracking were **absent entirely** before this: every style set a size and left the rest at
 * `TextStyle`'s defaults, which is why the denser screens ran their lines together.
 *
 * ### What this costs, and where it is checked
 *
 * Bigger text in the same columns. `PvpTableScreen` is the worst case in the app by some distance —
 * a format picker, twelve rule chips, a checkbox with two lines of explanation, a slider, five
 * trade options and a button, in one column — and `TextScalingTest` already renders it at 200% to
 * prove its button stays reachable. That test is the fence this change had to come past.
 *
 * ### Colour is not set here
 *
 * Deliberately: a `TextStyle` carrying a colour silently overrides the `ColorScheme` everywhere it
 * is used, and then two things claim to decide what colour text is. The scheme decides; these carry
 * family, size, weight and metrics only.
 *
 * ### Every slot carries the family, including the ones nothing names
 *
 * `Text`'s default style is `bodyLarge`, and a `Text` that sets `fontSize` without setting `style`
 * still inherits its *family* from there. Leaving the unnamed slots at Material's defaults would
 * leave most of the screen in the platform font while the theme claimed to have set one.
 * `ThemeTest.everyTypeSlotCarriesTheGameFont` is what says it does not.
 */
@Composable
internal fun appTypography(): Typography {
    val game = rememberGameFontFamily()

    fun style(size: Int, lineHeight: Int, tracking: Double, weight: FontWeight) = TextStyle(
        fontFamily = game,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
        fontWeight = weight,
    )

    val regular = FontWeight.Normal
    val medium = FontWeight.Medium

    return Typography(
        displayLarge = style(57, 64, -0.25, regular),
        displayMedium = style(45, 52, 0.0, regular),
        displaySmall = style(36, 44, 0.0, regular),

        headlineLarge = style(32, 40, 0.0, regular),
        headlineMedium = style(28, 36, 0.0, regular),
        // A screen title, and the largest thing the app draws outside the splash.
        headlineSmall = style(24, 32, 0.0, medium),

        // The app bar's own title.
        titleLarge = style(22, 28, 0.0, regular),
        // The label on a `WideButton`, and a card's name in a detail pane.
        titleMedium = style(16, 24, 0.15, medium),
        // A list row's name.
        titleSmall = style(14, 20, 0.1, medium),

        // Body text, and the default every `Text` inherits.
        bodyLarge = style(16, 24, 0.5, regular),
        bodyMedium = style(14, 20, 0.25, regular),
        // A row's secondary line.
        bodySmall = style(12, 16, 0.4, regular),

        labelLarge = style(14, 20, 0.1, medium),
        // The `·`-joined metadata line.
        labelMedium = style(12, 16, 0.5, medium),
        // The smallest thing on screen: a rules strip, a stack count, a progress figure.
        labelSmall = style(11, 16, 0.5, medium),
    )
}
