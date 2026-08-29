package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.model.BoonType
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.model.Npc
import com.tripletriad.ui.theme.LocalTtoColors

@Composable
internal fun AvatarBadge(
    profile: GameSave,
    size: Dp = AVATAR_SIZE,
    modifier: Modifier = Modifier,
    // Named by the surface rather than fixed here, because on the lobby this badge is not
    // a portrait but *the way into the record* — L2 gives that screen no card of its own.
    // Layering a second `testTag` over this one does not work: semantics merge first-wins,
    // so the outer tag silently replaced this one and the record lost its avatar.
    tag: String = AVATAR_TEST_TAG,
) {
    val image = rememberAvatar(LocalUiArt.current, profile.avatarId)
    val shape = CircleShape

    Box(
        modifier = modifier
            .testTag(tag)
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // A hairline ring, which is what lifts a portrait off a dark backdrop. The original
            // draws a heavy bevelled frame; at this size on a phone that reads as noise.
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = RING), shape),
        contentAlignment = Alignment.Center,
    ) {
        Bitmap(image = image, description = profile.username, fallback = profile.username)
    }
}

/**
 * Whose face the board draws for the opponent.
 *
 * A board has exactly one opponent and two kinds of them, and until now only one kind had a face:
 * `StatusBar` and `MatchSidePanel` took an [Npc], which is why the multiplayer board could not use
 * either and grew a header of its own. This is the seam that let both boards become one.
 *
 * @property artId what the art is looked up by, and what the portrait's test tag is built from.
 */
internal sealed interface OpponentFace {
    val artId: String

    /** A program, drawn from the same 50x50 portrait the opponent list showed. */
    data class Program(val npc: Npc) : OpponentFace {
        override val artId: String get() = npc.iconId
    }

    /**
     * A person, drawn from the avatar they chose.
     *
     * A blank id is the ordinary case rather than an error: it is what a client holds for an
     * opponent whose avatar the wire does not carry, and [Bitmap] answers it with their initial —
     * which is a face of sorts and is what the board showed for a person before this existed.
     */
    data class Person(val avatarId: String) : OpponentFace {
        override val artId: String get() = avatarId
    }
}

/**
 * The opponent's face, whichever kind of opponent it is.
 *
 * One box, one size and one shape for both, and that is the point rather than an economy: the two
 * boards are supposed to be the same board, and a portrait that changed shape between them would
 * move everything beside it.
 */
@Composable
internal fun OpponentPortrait(
    face: OpponentFace,
    name: String,
    modifier: Modifier = Modifier,
) {
    val art = LocalUiArt.current
    val image = when (face) {
        is OpponentFace.Program -> rememberPortrait(art, face.npc.iconId)
        // Read unconditionally, blank id and all. `UiArt` answers null for a name it does not
        // hold, and a `remember` that is called on some compositions and not others is not one
        // Compose can keep.
        is OpponentFace.Person -> rememberAvatar(art, face.avatarId)
    }
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .testTag(portraitTestTag(face.artId))
            .size(PORTRAIT_SIZE)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Bitmap(image = image, description = name, fallback = name)
    }
}

/**
 * An opponent's portrait, always at the art's own [PORTRAIT_SIZE] — 50x50, what every scraped NPC
 * portrait file measures. Callers used to ask for this at half a dozen different sizes (26dp in
 * the match banner, 36dp on a campaign tile, 64dp in the confirmation sheet…), and each one but the
 * source's own forced a resize: upscaled the low-resolution source into a blur, or downscaled it
 * and threw resolution away for no reason, since a fixed 50dp box costs a caller nothing a bigger
 * one did. Fixed at the source size instead, so a portrait is never stretched.
 */
@Composable
internal fun NpcPortrait(
    npc: Npc,
    name: String,
    modifier: Modifier = Modifier,
) {
    val image = rememberPortrait(LocalUiArt.current, npc.iconId)
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .testTag(portraitTestTag(npc.iconId))
            .size(PORTRAIT_SIZE)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Bitmap(image = image, description = name, fallback = name)
    }
}

@Composable
internal fun ItemIcon(
    iconId: String,
    description: String,
    size: Dp = ICON_SIZE,
    modifier: Modifier = Modifier,
) {
    val image = LocalUiArt.current?.icon(iconId)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = description,
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
internal fun ItemGlyph(
    item: Item,
    description: String,
    size: Dp = ICON_SIZE,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTtoColors.current
    val boon = boonOf(item)
    val drawn = when {
        boon == BoonType.XP -> TtoIcons.XpBoon to colors.experience
        boon == BoonType.MGP -> TtoIcons.MgpBoon to colors.currency
        // Neither of the earned-thing colours: a pack is not MGP and not XP, it is what they buy.
        item is BoosterItem -> TtoIcons.Booster to MaterialTheme.colorScheme.onSurface
        else -> null
    }

    if (drawn == null) {
        ItemIcon(
            iconId = itemIconId(item),
            description = description,
            size = size,
            modifier = modifier,
        )
        return
    }

    Icon(
        imageVector = drawn.first,
        contentDescription = description,
        tint = drawn.second,
        modifier = modifier.size(size),
    )
}

@Composable
internal fun AchievementIcon(
    iconId: String,
    description: String,
    size: Dp = ICON_SIZE,
    modifier: Modifier = Modifier,
) {
    val art = LocalUiArt.current
    val bitmap = art?.icon(iconId)
    val painter = if (bitmap == null) thumbTextureId(iconId)?.let { art?.thumb(it) } else null

    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = description,
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )

            painter != null -> Image(
                painter = painter,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
    }
}

internal fun thumbTextureId(iconId: String): String? =
    iconId.removePrefix(CARD_THUMB_PREFIX)
        .takeIf { it != iconId }
        ?.toIntOrNull()
        ?.let { id -> id.toString(HEX_RADIX).padStart(HEX_WIDTH, '0') }

private const val CARD_THUMB_PREFIX = "card_thumb_"
private const val HEX_RADIX = 16
private const val HEX_WIDTH = 4

/**
 * A card's thumbnail in its frame — the one card picture the whole app shows at small size.
 *
 * The frame is drawn here rather than by each caller so that a thumbnail looks the same in the
 * card list, the deck builder, the shop and the prize lists. It used to be the caller's job, and
 * the result was four different borders: `rowSurface`, a bare surface, nothing at all.
 *
 * @param size the **picture's** size, not the widget's. The frame adds [FrameMargin] on every
 *   side, so this composable measures `size + 2 * FrameMargin` — [FramedThumbSide] at the
 *   authored size. Both images are then drawn at the size they were authored.
 * @param selected draws the wider stroke over the frame. Selection is the caller's state, so it
 *   stays the caller's parameter; what is shared is what selection *looks like*.
 */
@Composable
internal fun CardThumb(
    card: Card,
    size: Dp = THUMB_SIZE,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    CardThumb(
        painter = LocalUiArt.current?.thumb(card),
        size = size,
        selected = selected,
        modifier = modifier.testTag(thumbTestTag(card.textureId)),
    )
}

/** The same picture, for a card known only by id — a prize, a deck slot read off a save. */
@Composable
internal fun CardThumb(
    cardId: Int,
    size: Dp = THUMB_SIZE,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val texture = cardTextureId(cardId)
    CardThumb(
        painter = LocalUiArt.current?.thumb(texture),
        size = size,
        selected = selected,
        modifier = modifier.testTag(thumbTestTag(texture)),
    )
}

/**
 * A card as every grid in the app draws it: the framed thumbnail, its element in the top corner
 * and a count in the bottom one.
 *
 * One composable rather than one per screen. The collection, the deck builder and the shop all
 * show the same object answering the same two questions — *what element is it* and *how many do
 * I have* — and they had drifted into three arrangements of it, the shop's being a fourth I had
 * just invented. What differs between them is only what the count *counts*: copies owned, copies
 * still unspent by a draft, copies already on the shelf. That stays the caller's business, which
 * is why the number and its tag are passed in rather than worked out here.
 *
 * @param count the badge's number, or null for no badge. A caller that hides `x1` passes
 *   `copies.takeIf { it > 1 }` — the rule is not the same on every screen.
 * @param showType false where the element is already said underneath, as the collection's
 *   detail panel says it.
 */
@Composable
internal fun CardTile(
    card: Card,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dim: Boolean = false,
    count: Int? = null,
    countTag: String? = null,
    showType: Boolean = true,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CardThumb(
            card = card,
            selected = selected,
            modifier = if (dim) Modifier.alpha(TILE_DIM) else Modifier,
        )

        if (showType) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(1.dp)) {
                CardTypeBadge(card = card, size = TileBadgeSize)
            }
        }

        if (count != null) {
            Text(
                text = "$COPIES_PREFIX$count",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .then(countTag?.let { Modifier.testTag(it) } ?: Modifier)
                    .align(Alignment.BottomEnd)
                    .padding(1.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(TileBadgeCorner),
                    )
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

/**
 * A card-sized hole: the frame with nothing in it.
 *
 * An empty deck position is a place a card goes, so it is drawn as one — same footprint, same
 * border. Leaving it blank made a half-built deck look like a shorter deck.
 */
@Composable
internal fun EmptyCardSlot(size: Dp = THUMB_SIZE, modifier: Modifier = Modifier) {
    CardThumb(painter = null, size = size, selected = false, modifier = modifier)
}

@Composable
private fun CardThumb(painter: Painter?, size: Dp, selected: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier.size(size + FrameMargin * 2),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (painter != null) {
                // No `filterQuality` here: the painter overload has none, so the slices carry it
                // themselves — see `ThumbFrame.painterOn`.
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size),
                )
            }
        }

        CardFrame(selected = selected)
    }
}

/**
 * The border laid **over** a thumbnail, and the selected stroke over that.
 *
 * Over rather than around: `card_frame.png` is authored with the margin the picture sits in, so
 * drawing it on top puts its border exactly where its author drew it. A border placed around the
 * picture instead would be the frame's border plus a second one.
 */
@Composable
private fun CardFrame(selected: Boolean) {
    LocalUiArt.current?.icon(CARD_FRAME_ICON)?.let { frame ->
        Image(
            bitmap = frame,
            contentDescription = null,
            // `None` for the same reason the thumbnails use it: pixel art at its authored size,
            // where smoothing is a blur rather than an improvement.
            filterQuality = FilterQuality.None,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (selected) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = SelectedFrameWidth,
                    color = LocalTtoColors.current.selectedOutline,
                    shape = RoundedCornerShape(CardFrameCorner),
                ),
        )
    }
}

@Composable
private fun Bitmap(image: ImageBitmap?, description: String, fallback: String) {
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = description,
            filterQuality = FilterQuality.None,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Text(
            text = fallback.take(1).uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

const val AVATAR_TEST_TAG: String = "avatar"

fun portraitTestTag(iconId: String): String = "portrait-$iconId"

fun thumbTestTag(textureId: String): String = "thumb-$textureId"

private val AVATAR_SIZE = 56.dp

/** What every NPC portrait file measures — see [NpcPortrait]. */
private val PORTRAIT_SIZE = 50.dp
private val ICON_SIZE = 32.dp
private val THUMB_SIZE = 40.dp

/** The element badge on a tile. Bigger than the stats line's, because it is read at a glance. */
private val TileBadgeSize = 16.dp

private val TileBadgeCorner = 3.dp

/** How far a tile fades when its card is unowned, or spent by the draft that is being built. */
private const val TILE_DIM = 0.35f

/** The margin `card_frame.png` leaves around the picture, on each side. */
internal val FrameMargin = 2.dp

/** What a [CardThumb] at the authored size measures, frame included. */
internal val FramedThumbSide = THUMB_SIZE + FrameMargin * 2

/** The frame laid over every thumbnail, authored at 44x44 for a 40x40 picture. */
private const val CARD_FRAME_ICON = "card_frame"

/** How much wider the selected stroke reads than the drawn border under it. */
private val SelectedFrameWidth = 2.dp

/** Matched to the frame art's own rounding, so the stroke sits on it rather than beside it. */
private val CardFrameCorner = 5.dp
private const val RING = 0.4f
