package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    val image = rememberAvatar(LocalUiArt.current, profile.avatarId)
    val shape = CircleShape

    Box(
        modifier = modifier
            .testTag(AVATAR_TEST_TAG)
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

@Composable
internal fun NpcPortrait(
    npc: Npc,
    name: String,
    size: Dp = PORTRAIT_SIZE,
    modifier: Modifier = Modifier,
) {
    val image = rememberPortrait(LocalUiArt.current, npc.iconId)
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .testTag(portraitTestTag(npc.iconId))
            .size(size)
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
private val PORTRAIT_SIZE = 44.dp
private val ICON_SIZE = 32.dp
private val THUMB_SIZE = 40.dp

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
