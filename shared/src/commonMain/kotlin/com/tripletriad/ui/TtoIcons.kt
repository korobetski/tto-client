package com.tripletriad.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The glyphs the shell needs, drawn here rather than depended on.
 *
 * ### Why not Material's icon set
 *
 * `material-icons-extended` is a single artifact of some twelve hundred vectors, and Compose
 * Multiplatform's own guidance is to stop shipping it — R8 strips what an Android build does not
 * use, and nothing strips it from a desktop jar or an iOS framework. A dozen glyphs is not worth
 * that, and it is not worth a per-icon resource file either: these are eight to twelve path
 * segments each. Two of them are Material's own glyphs even so — copied, not depended on, which
 * is the distinction that matters here and is spelled out at [symbol].
 *
 * ### Why they look like this
 *
 * They are drawn as **cards** wherever a card will do — [Play] is a fanned hand, [Collection] is a
 * grid of them, [Quest] is one with a tick — because that is the one shape this game has and
 * Material's own set does not. The
 * rest ([Home], [Shop], [Person], [Help], [Options], [Logout], [Back]) follow Material's
 * conventions, since a bag that is not bag-shaped costs recognition and buys nothing.
 *
 * [Experience], [MgpBoon] and [XpBoon] follow a third rule: they are **the original's own subjects
 * at this set's weight**. A pentagonal wheel and the boost plaque are what the FFXIV art in
 * `art/icons/` draws, and a player who has seen them should recognise these.
 *
 * [Chip] and [Booster] are the exception to all of it — Material Symbols' `poker_chip` and
 * `playing_cards`, path data and all, for the reason [symbol] gives.
 *
 * Every path drawn here is stroked rather than filled, at 1.8 units on a 24-unit grid — Material
 * Symbols' outlined weight — so they sit beside the game's own 40x40 pixel-art icons without one
 * set shouting over the other. The two copied glyphs are filled, because that is how the source
 * draws an outline; [symbol] explains why they are not re-traced.
 */
@Suppress("MagicNumber") // Path coordinates on a 24-unit grid. Naming them would say less.
internal object TtoIcons {

    /** The dashboard: a roof over the character's own screen. */
    val Home: ImageVector by lazy {
        icon("Home") {
            moveTo(3f, 10.5f)
            lineTo(12f, 3.5f)
            lineTo(21f, 10.5f)
            lineTo(21f, 20f)
            lineTo(3f, 20f)
            close()
            moveTo(9.5f, 20f)
            lineTo(9.5f, 14f)
            lineTo(14.5f, 14f)
            lineTo(14.5f, 20f)
        }
    }

    /** Playing a match: three cards fanned, the middle one upright. */
    val Play: ImageVector by lazy {
        icon("Play") {
            moveTo(9.2f, 5.4f)
            lineTo(4.2f, 8.6f)
            lineTo(8.6f, 16.4f)
            moveTo(14.8f, 5.4f)
            lineTo(19.8f, 8.6f)
            lineTo(15.4f, 16.4f)
            moveTo(9f, 4f)
            lineTo(15f, 4f)
            lineTo(15f, 20f)
            lineTo(9f, 20f)
            close()
        }
    }

    /** The collection: nine cards on a shelf. */
    val Collection: ImageVector by lazy {
        icon("Collection") {
            for (row in 0..2) {
                for (column in 0..2) {
                    val x = 3.5f + column * 6.3f
                    val y = 3.5f + row * 6.3f
                    moveTo(x, y)
                    lineTo(x + 4.2f, y)
                    lineTo(x + 4.2f, y + 4.2f)
                    lineTo(x, y + 4.2f)
                    close()
                }
            }
        }
    }

    /** The shop: a bag with a handle. */
    val Shop: ImageVector by lazy {
        icon("Shop") {
            moveTo(4.5f, 8f)
            lineTo(19.5f, 8f)
            lineTo(18.3f, 20.5f)
            lineTo(5.7f, 20.5f)
            close()
            moveTo(8.5f, 8f)
            lineTo(8.5f, 6f)
            curveTo(8.5f, 4.1f, 9.9f, 3f, 12f, 3f)
            curveTo(14.1f, 3f, 15.5f, 4.1f, 15.5f, 6f)
            lineTo(15.5f, 8f)
        }
    }

    /** The record: head and shoulders. */
    val Person: ImageVector by lazy {
        icon("Person") {
            moveTo(12f, 4f)
            curveTo(14.2f, 4f, 15.6f, 5.6f, 15.6f, 7.8f)
            curveTo(15.6f, 10f, 14.2f, 11.6f, 12f, 11.6f)
            curveTo(9.8f, 11.6f, 8.4f, 10f, 8.4f, 7.8f)
            curveTo(8.4f, 5.6f, 9.8f, 4f, 12f, 4f)
            close()
            moveTo(4.5f, 20.5f)
            curveTo(4.5f, 16.4f, 7.9f, 14.2f, 12f, 14.2f)
            curveTo(16.1f, 14.2f, 19.5f, 16.4f, 19.5f, 20.5f)
        }
    }

    /** The rules: an open book, which is what the help screen is. */
    val Help: ImageVector by lazy {
        icon("Help") {
            moveTo(12f, 6.5f)
            curveTo(10f, 4.6f, 6.8f, 4.6f, 3.8f, 5.2f)
            lineTo(3.8f, 18.4f)
            curveTo(6.8f, 17.8f, 10f, 17.8f, 12f, 19.7f)
            curveTo(14f, 17.8f, 17.2f, 17.8f, 20.2f, 18.4f)
            lineTo(20.2f, 5.2f)
            curveTo(17.2f, 4.6f, 14f, 4.6f, 12f, 6.5f)
            close()
            moveTo(12f, 6.5f)
            lineTo(12f, 19.7f)
        }
    }

    /** The settings: three sliders, Material's `tune`. */
    val Options: ImageVector by lazy {
        icon("Options") {
            for ((index, y) in listOf(6.5f, 12f, 17.5f).withIndex()) {
                moveTo(3.5f, y)
                lineTo(20.5f, y)
                val knob = 8f + index * 4f
                moveTo(knob - 2f, y)
                curveTo(knob - 2f, y - 2f, knob + 2f, y - 2f, knob + 2f, y)
                curveTo(knob + 2f, y + 2f, knob - 2f, y + 2f, knob - 2f, y)
                close()
            }
        }
    }

    /** Leaving the character: a door and the way out of it. */
    val Logout: ImageVector by lazy {
        icon("Logout") {
            moveTo(14f, 4f)
            lineTo(4.5f, 4f)
            lineTo(4.5f, 20f)
            lineTo(14f, 20f)
            moveTo(11.5f, 12f)
            lineTo(20.5f, 12f)
            moveTo(17f, 8.5f)
            lineTo(20.5f, 12f)
            lineTo(17f, 15.5f)
        }
    }

    /** The chevron every screen's app bar carries. */
    val Back: ImageVector by lazy {
        icon("Back") {
            moveTo(15f, 4.5f)
            lineTo(7.5f, 12f)
            lineTo(15f, 19.5f)
        }
    }

    /**
     * The same chevron pointing down — an accordion that can be opened.
     *
     * Drawn rather than rotated from [Back] at the call site, because the row rotates it *again* to
     * point up when it is open, and a glyph that starts at 90° would make that second rotation
     * read as 180° in the code for a quarter turn on screen.
     */
    val Expand: ImageVector by lazy {
        icon("Expand") {
            moveTo(4.5f, 9f)
            lineTo(12f, 16.5f)
            lineTo(19.5f, 9f)
        }
    }

    /**
     * The daily quests: a card with a tick in it.
     *
     * A card again rather than Material's checklist, for the reason the header gives — the upright
     * rectangle is [Play]'s and [Collection]'s shape, and what a quest is *about* is matches. The
     * tick is the only mark on it, because "done" is the one thing this destination reports.
     */
    val Quest: ImageVector by lazy {
        icon("Quest") {
            moveTo(5f, 3f)
            lineTo(19f, 3f)
            lineTo(19f, 21f)
            lineTo(5f, 21f)
            close()
            moveTo(8.5f, 12.2f)
            lineTo(11f, 14.8f)
            lineTo(15.5f, 9.4f)
        }
    }

    /**
     * A tick, alone — a lesson that has been finished.
     *
     * The same stroke [Quest] draws inside its card, without the card. What it marks here is a row
     * in a list rather than a destination, so the shape that identifies the *subject* would be
     * repeated eight times down the screen and identify nothing.
     */
    val Done: ImageVector by lazy {
        icon("Done") {
            moveTo(5f, 12.5f)
            lineTo(10f, 17.5f)
            lineTo(19f, 6.5f)
        }
    }

    /**
     * The purse: Material Symbols' **`poker_chip`**, copied rather than redrawn — see [symbol].
     *
     * This is the one glyph here that replaces a picture rather than filling a gap. The purse was
     * `icons/PGS.png`, the FFXIV MGP token — 29 px of pixel art drawn at 16 dp beside a label in
     * the app bar's own type, which is the size a 29 px bitmap looks worst at: it is neither
     * sharp, since it is being scaled, nor legible, since the token's detail is its concentric
     * rings and they close up.
     *
     * ### Why a chip
     *
     * It was a stack of coins first, and a stack of coins in outline **is the database icon**: a
     * cylinder with two seams is the shape every tool in the industry uses for storage, and a mint
     * mark on the top face was not enough to take that reading away.
     *
     * A chip has no such twin, and it is the better answer anyway. MGP is the **Manderville Gold
     * Saucer**'s currency — it is won at a casino and spent at one — so the token that says what
     * this number is happens also to say where it comes from. `paid` (a coin with a currency sign)
     * would have been the other choice and is worse here for the same reason: the sign would be a
     * dollar, and MGP is not money.
     */
    val Chip: ImageVector by lazy {
        symbol("Chip") {
            moveTo(12f, 22f)
            quadTo(9.93f, 22f, 8.1f, 21.21f)
            quadTo(6.28f, 20.43f, 4.93f, 19.08f)
            quadTo(3.58f, 17.73f, 2.79f, 15.9f)
            reflectiveQuadTo(2f, 12f)
            quadTo(2f, 9.92f, 2.79f, 8.1f)
            quadTo(3.58f, 6.27f, 4.93f, 4.93f)
            quadTo(6.28f, 3.57f, 8.1f, 2.79f)
            quadTo(9.93f, 2f, 12f, 2f)
            reflectiveQuadToRelative(3.9f, 0.79f)
            reflectiveQuadToRelative(3.17f, 2.14f)
            quadToRelative(1.35f, 1.35f, 2.14f, 3.17f)
            quadTo(22f, 9.92f, 22f, 12f)
            reflectiveQuadToRelative(-0.79f, 3.9f)
            reflectiveQuadToRelative(-2.14f, 3.17f)
            quadToRelative(-1.35f, 1.35f, -3.17f, 2.14f)
            reflectiveQuadTo(12f, 22f)
            close()
            moveTo(11f, 19.93f)
            verticalLineToRelative(-1f)
            quadTo(10.13f, 18.8f, 9.31f, 18.45f)
            reflectiveQuadTo(7.8f, 17.6f)
            lineTo(7.1f, 18.33f)
            quadToRelative(0.83f, 0.65f, 1.81f, 1.06f)
            reflectiveQuadTo(11f, 19.93f)
            close()
            moveToRelative(2f, 0f)
            quadToRelative(1.1f, -0.13f, 2.09f, -0.54f)
            quadToRelative(0.99f, -0.41f, 1.81f, -1.06f)
            lineTo(16.2f, 17.6f)
            quadToRelative(-0.7f, 0.5f, -1.51f, 0.85f)
            reflectiveQuadTo(13f, 18.93f)
            verticalLineToRelative(1f)
            close()
            moveTo(12f, 17f)
            quadToRelative(2.08f, 0f, 3.54f, -1.46f)
            reflectiveQuadTo(17f, 12f)
            quadTo(17f, 9.92f, 15.54f, 8.46f)
            reflectiveQuadTo(12f, 7f)
            quadTo(9.93f, 7f, 8.46f, 8.46f)
            reflectiveQuadTo(7f, 12f)
            reflectiveQuadToRelative(1.46f, 3.54f)
            reflectiveQuadTo(12f, 17f)
            close()
            moveToRelative(6.32f, -0.1f)
            quadToRelative(0.65f, -0.82f, 1.06f, -1.81f)
            reflectiveQuadTo(19.93f, 13f)
            horizontalLineToRelative(-1f)
            quadToRelative(-0.13f, 0.88f, -0.48f, 1.69f)
            reflectiveQuadTo(17.6f, 16.2f)
            lineToRelative(0.72f, 0.7f)
            close()
            moveToRelative(-12.65f, 0f)
            lineTo(6.4f, 16.18f)
            quadTo(5.9f, 15.48f, 5.55f, 14.68f)
            quadTo(5.2f, 13.88f, 5.08f, 13f)
            horizontalLineToRelative(-1f)
            quadToRelative(0.13f, 1.1f, 0.54f, 2.09f)
            quadToRelative(0.41f, 0.99f, 1.06f, 1.81f)
            close()
            moveTo(12f, 16f)
            lineTo(9f, 12f)
            lineTo(12f, 8f)
            lineToRelative(3f, 4f)
            lineToRelative(-3f, 4f)
            close()
            moveTo(4.08f, 11f)
            horizontalLineToRelative(1f)
            quadTo(5.2f, 10.13f, 5.55f, 9.32f)
            reflectiveQuadTo(6.4f, 7.82f)
            lineTo(5.68f, 7.1f)
            quadTo(5.03f, 7.93f, 4.61f, 8.91f)
            reflectiveQuadTo(4.08f, 11f)
            close()
            moveToRelative(14.85f, 0f)
            horizontalLineToRelative(1f)
            quadTo(19.8f, 9.9f, 19.38f, 8.91f)
            reflectiveQuadTo(18.3f, 7.1f)
            lineTo(17.6f, 7.8f)
            quadToRelative(0.5f, 0.7f, 0.85f, 1.51f)
            reflectiveQuadTo(18.93f, 11f)
            close()
            moveTo(7.83f, 6.4f)
            quadTo(8.53f, 5.9f, 9.33f, 5.55f)
            reflectiveQuadTo(11f, 5.07f)
            verticalLineToRelative(-1f)
            quadTo(9.9f, 4.2f, 8.91f, 4.61f)
            quadTo(7.93f, 5.02f, 7.1f, 5.68f)
            lineTo(7.83f, 6.4f)
            close()
            moveToRelative(8.38f, 0f)
            lineTo(16.9f, 5.7f)
            quadTo(16.08f, 5.05f, 15.09f, 4.63f)
            reflectiveQuadTo(13f, 4.07f)
            verticalLineToRelative(1f)
            quadToRelative(0.88f, 0.13f, 1.69f, 0.48f)
            reflectiveQuadTo(16.2f, 6.4f)
            close()
        }
    }

    /**
     * Experience: the **five-spoked pentagon** the original marks XP with.
     *
     * Not an arrow and not a star, which are what an icon set would offer. `icons/XP.png` is a
     * pentagonal wheel and the player has been looking at it since the AS3 build; a star would
     * also collide with the star every card wears for its rarity. So the shape is the original's,
     * redrawn at this set's weight — the same trade [Chip] makes, one subject along.
     */
    val Experience: ImageVector by lazy {
        icon("Experience") {
            wheel(WHEEL_R, ring = true)
        }
    }

    /**
     * A **boosted-MGP** potion: [Chip]'s face, on the plaque the FFXIV boost items wear.
     *
     * ### Why the plaque is kept and the picture is not
     *
     * `mgp_boost_icon.png` and `xp_boost_icon.png` are one design in two subjects — a peaked
     * badge with a base band, holding the coin in one and the XP wheel in the other — and that
     * design is worth keeping: it is what tells a player at a glance that a row in the bag is a
     * *booster of something* rather than the something itself. What is not worth keeping is 24x32
     * of pixel art rendered at 16 dp beside vector text.
     *
     * So the badge is redrawn and the subject inside it is the same glyph the rest of the app now
     * uses, reduced to what survives at this size: the chip's two rings **without its edge spots**,
     * which at the third of a 24 dp badge this leaves would be four grey specks.
     */
    val MgpBoon: ImageVector by lazy {
        icon("MgpBoon") {
            plaque()
            ellipse(12f, BOON_Y, BOON_R, BOON_R)
            ellipse(12f, BOON_Y, BOON_EYE, BOON_EYE)
        }
    }

    /** A **boosted-XP** potion: [Experience]'s wheel on [MgpBoon]'s plaque, spokes dropped. */
    val XpBoon: ImageVector by lazy {
        icon("XpBoon") {
            plaque()
            wheel(BOON_R + 0.4f, ring = false)
        }
    }

    /**
     * A booster: Material Symbols' **`playing_cards`**, copied rather than redrawn — see [symbol].
     *
     * ### What it replaces, and what that costs
     *
     * Ten booster kinds drew five bitmaps between them: six of them share `booster_pack_icon`, the
     * generic purple wrapper, and the four tribe packs have artwork of their own. So for most of
     * the shelf this trades one picture for one picture at a size that suits the row, and for
     * `BEAST`, `PRIMAL`, `SCION` and `GARLEAN` it **loses a distinction the art was making** —
     * those four now look like the other six and are told apart by their names, which are on the
     * row beside them in every place a booster is drawn.
     *
     * ### Why it is not [Play]
     *
     * Both are cards and they have to stay apart at 16 dp. [Play] is three of them fanned
     * symmetrically about an upright middle; this is a hand held at an angle with one card lifted
     * out of it, and the tilt is what separates them at a glance.
     */
    val Booster: ImageVector by lazy {
        symbol("Booster") {
            moveTo(15.2f, 14.8f)
            lineToRelative(1.15f, -4.15f)
            lineTo(12.8f, 8.2f)
            lineToRelative(-1.15f, 4.15f)
            lineTo(15.2f, 14.8f)
            close()
            moveTo(4f, 18.83f)
            lineTo(3.18f, 18.43f)
            quadTo(2.4f, 18.1f, 2.13f, 17.31f)
            reflectiveQuadTo(2.2f, 15.75f)
            lineTo(4f, 11.85f)
            verticalLineToRelative(6.98f)
            close()
            moveTo(8f, 21f)
            quadTo(7.18f, 21f, 6.59f, 20.4f)
            reflectiveQuadTo(6f, 18.98f)
            verticalLineTo(13f)
            lineToRelative(2.68f, 7.35f)
            quadToRelative(0.07f, 0.17f, 0.13f, 0.34f)
            reflectiveQuadTo(8.98f, 21f)
            horizontalLineTo(8f)
            close()
            moveToRelative(5.15f, -0.13f)
            quadTo(12.38f, 21.15f, 11.6f, 20.8f)
            reflectiveQuadTo(10.55f, 19.68f)
            lineTo(6.13f, 7.45f)
            quadTo(5.85f, 6.68f, 6.2f, 5.91f)
            reflectiveQuadTo(7.33f, 4.88f)
            lineTo(14.85f, 2.13f)
            quadTo(15.63f, 1.85f, 16.39f, 2.2f)
            reflectiveQuadToRelative(1.04f, 1.13f)
            lineToRelative(4.45f, 12.23f)
            quadToRelative(0.28f, 0.78f, -0.07f, 1.54f)
            reflectiveQuadToRelative(-1.13f, 1.04f)
            lineToRelative(-7.53f, 2.75f)
            close()
            moveTo(12.45f, 19f)
            lineTo(20f, 16.25f)
            lineTo(15.53f, 4f)
            lineTo(8f, 6.75f)
            lineTo(12.45f, 19f)
            close()
        }
    }

    /**
     * The badge both boons wear: a peaked shield with a base band.
     *
     * The band is the brown strip along the bottom of the FFXIV art, which is what makes the
     * shape read as a mounted plaque rather than as a home icon standing on its roof.
     */
    private fun PathBuilder.plaque() {
        moveTo(8.5f, 3.5f)
        lineTo(15.5f, 3.5f)
        lineTo(19f, 7f)
        lineTo(19f, 20.5f)
        lineTo(5f, 20.5f)
        lineTo(5f, 7f)
        close()
        moveTo(5f, 17.5f)
        lineTo(19f, 17.5f)
    }

    /**
     * A regular pentagon on its point, centred on the grid — and, for [Experience], a second one
     * inside it.
     *
     * The ring is what is left of the original's five spokes. Drawn as spokes they were five
     * strokes converging on the middle, which at 16 dp is an asterisk and at any size is the star
     * a card wears for its rarity. A pentagon inside a pentagon keeps the token's silhouette and
     * its sense of being a *wheel* rather than a plain plate, and survives being small.
     */
    private fun PathBuilder.wheel(radius: Float, ring: Boolean) {
        val cy = if (ring) 12.6f else BOON_Y
        val radii = if (ring) listOf(radius, radius * HUB_SHARE) else listOf(radius)

        for (r in radii) {
            val corners = List(PENTAGON_SIDES) { corner ->
                val turn = -PI / 2 + corner * 2 * PI / PENTAGON_SIDES
                12f + r * cos(turn).toFloat() to cy + r * sin(turn).toFloat()
            }
            moveTo(corners.first().first, corners.first().second)
            for ((x, y) in corners.drop(1)) lineTo(x, y)
            close()
        }
    }

    /** A closed ellipse, as four cubics — Compose's path builder has no arc primitive here. */
    private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        moveTo(cx - rx, cy)
        curveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
        curveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
        curveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
        curveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
        close()
    }

    /** The cubic approximation of a quarter circle: 4/3·(√2−1). */
    private const val KAPPA = 0.5523f

    private const val WHEEL_R = 8.2f
    private const val HUB_SHARE = 0.46f
    private const val PENTAGON_SIDES = 5

    /** Inside the plaque, which leaves the band below it and the peak above. */
    private const val BOON_Y = 13f
    private const val BOON_R = 3.4f
    private const val BOON_EYE = 1.7f

    /**
     * One **filled** path on the same grid — a glyph taken from Material Symbols as it is drawn.
     *
     * ### Why two builders
     *
     * [icon] strokes; this fills. That is not a style choice, it is what the source is: Material
     * Symbols' outlined weight is an *outline drawn as a filled shape*, so its path data describes
     * both sides of every stroke. Re-tracing one of those as a centre line would be redrawing it
     * by hand, which is the work copying it exists to avoid — and would drift from the glyph the
     * player recognises. At `wght 400` the result reads at 1.8-ish units, which is why the two
     * kinds sit together without one looking bolder than the other.
     *
     * ### Provenance
     *
     * [Chip] is `poker_chip` and [Booster] is `playing_cards`, both from Material Symbols
     * Outlined at `opsz 24, wght 400, FILL 0, GRAD 0, ROND 50`, fetched as Kotlin from
     * `fonts.gstatic.com/render/v1/…`. Material Symbols is **Apache 2.0**; the path data is
     * copied, not linked, so nothing is added to the dependency graph — which is the whole of the
     * objection the header raises to `material-icons-extended`, and none of it applies to two
     * glyphs pasted in.
     */
    private fun symbol(name: String, segments: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathBuilder = segments)
        }.build()

    /**
     * One stroked path on a 24-unit grid.
     *
     * `defaultWidth`/`defaultHeight` are 24 dp because that is what an [androidx.compose.material3
     * .Icon] sizes itself to when it is given no modifier, and a glyph that had to be measured at
     * every call site would be nine chances to get one wrong.
     */
    private fun icon(name: String, segments: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = segments,
            )
        }.build()
}
