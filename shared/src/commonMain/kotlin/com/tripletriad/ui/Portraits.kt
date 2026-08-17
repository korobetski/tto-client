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

/**
 * The pictures that identify someone or something at a glance — an avatar, an opponent, a card.
 *
 * Written once here rather than in each screen because they share the part that is easy to get
 * wrong: **every one of them can be absent**, and each has to say so without a gap in the layout.
 * Eleven opponents ship no portrait, one item ships no icon, and a bundle can always be built
 * short. A missing image is drawn as its own initial on a tinted plate, which keeps the row the
 * same height and still tells the player which one it is.
 *
 * ### Why the images are pixel-scaled rather than smoothed
 *
 * [FilterQuality.None]. The source art is 40x40 and 50x50 sprites from a 2013 Flash game, drawn at
 * 1:1 on a display of the era. On a 3x phone they are enlarged three or four times, and bilinear
 * smoothing turns a crisp sprite into a blur. Nearest-neighbour keeps the edges the artist drew.
 */
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

/**
 * An opponent's 50x50 portrait, or their monogram.
 *
 * Square with a soft corner rather than circular: these are cropped character art, and a circle
 * takes the crop further in and cuts heads. The avatars are framed portraits and survive it.
 */
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

/**
 * A bag or achievement icon, by the name the model carries.
 *
 * No monogram fallback: an item's name is already next to it in every place one of these is drawn,
 * so a letter would be repeating what the row says. An absent icon leaves its plate, which holds
 * the alignment of the column it is in.
 */
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

/**
 * Whatever pictures [item]: a drawn glyph for the two kinds that have one, [ItemIcon]'s bitmap for
 * the rest.
 *
 * ### Which kinds are drawn, and why those
 *
 * A **potion** because its picture is a *symbol* rather than a thing — "more MGP for a while" is
 * what [TtoIcons.MgpBoon] draws and what the shipped 24x32 boost bitmaps drew before it, at a size
 * that suited neither the bag's 24 dp plate nor the outcome panel's 16 dp row.
 *
 * A **booster** because ten of them shared five pictures, six of those being one generic wrapper:
 * see [TtoIcons.Booster], including what the four tribe packs give up for it.
 *
 * Everything else keeps its bitmap and should: a card item is a card, and no drawing of a card
 * back is worth losing the artwork the row is *about*.
 *
 * The three screens that draw an item all go through here, for the reason [itemIconId] gives about
 * being reconciled once: three copies of "and these two are different" is three chances for one of
 * them to keep drawing the old picture.
 */
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

/**
 * An achievement's badge, which is an icon for most of them and a card thumbnail for one.
 *
 * `Achievement.iconId` is not one namespace. Most rows name a `misc/` icon — the tiers reuse
 * `card_r{n}_icon` to say how hard they are — but `ac-fob` names `ff14_thumb_37`, the thumbnail of
 * the card it is about. That is the atlas's frame under the AS3's own name for it: the sheet calls
 * the same frame `ff14_37`, which is what a card's [textureId] is. Rather than rename the model or
 * the table, the two spellings are reconciled in the one place that has to know both.
 */
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

/**
 * The atlas frame an achievement's `card_thumb_<id>` icon names.
 *
 * It was `ff14_thumb_37` -> `ff14_37`, a string edit between two names that shared a prefix. Both
 * halves changed with global ids: the achievement names a card id rather than a table and an
 * index, and the frame is that id in hex. So this parses rather than substitutes, and returns null
 * for anything that is not a card thumbnail — every other achievement icon is an ordinary texture
 * name and must pass through untouched.
 */
internal fun thumbTextureId(iconId: String): String? =
    iconId.removePrefix(CARD_THUMB_PREFIX)
        .takeIf { it != iconId }
        ?.toIntOrNull()
        ?.let { id -> id.toString(HEX_RADIX).padStart(HEX_WIDTH, '0') }

private const val CARD_THUMB_PREFIX = "card_thumb_"
private const val HEX_RADIX = 16
private const val HEX_WIDTH = 4

/**
 * A card's 40x40 thumbnail — the tile the original's collection grid is built from.
 *
 * Takes a [Painter] rather than an [ImageBitmap] because that is what a slice of an atlas is; see
 * [UiArt.thumb]. Null while the sheets load, and null for a card with no frame, which is why the
 * plate is drawn whether or not there is an image on it.
 */
@Composable
internal fun CardThumb(
    card: Card,
    size: Dp = THUMB_SIZE,
    modifier: Modifier = Modifier,
) {
    CardThumb(
        painter = LocalUiArt.current?.thumb(card),
        size = size,
        modifier = modifier.testTag(thumbTestTag(card.textureId)),
    )
}

/**
 * [CardThumb] for a caller holding an id rather than a card.
 *
 * The starter preview draws five cards straight off `starters.json`, on a screen that has no
 * `CardCatalog`. See [cardTextureId].
 */
@Composable
internal fun CardThumb(cardId: Int, size: Dp = THUMB_SIZE, modifier: Modifier = Modifier) {
    val texture = cardTextureId(cardId)
    CardThumb(
        painter = LocalUiArt.current?.thumb(texture),
        size = size,
        modifier = modifier.testTag(thumbTestTag(texture)),
    )
}

/** The plate, and the slice on it if there is one. Drawn whether or not the atlas has loaded. */
@Composable
private fun CardThumb(painter: Painter?, size: Dp, modifier: Modifier) {
    Box(
        modifier = modifier
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
}

/** The image if there is one, its first letter if there is not. */
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

/** `avatar` — one profile is shown at a time, so one tag is enough. */
const val AVATAR_TEST_TAG: String = "avatar"

/** `portrait-<iconId>`, so a test can name the opponent whose picture it is looking for. */
fun portraitTestTag(iconId: String): String = "portrait-$iconId"

/** `thumb-<textureId>` — the card's own id, the same key the grid keys its items on. */
fun thumbTestTag(textureId: String): String = "thumb-$textureId"

private val AVATAR_SIZE = 56.dp
private val PORTRAIT_SIZE = 44.dp
private val ICON_SIZE = 32.dp
private val THUMB_SIZE = 40.dp
private const val RING = 0.4f
