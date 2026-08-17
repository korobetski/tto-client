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

@Composable
internal fun CardThumb(cardId: Int, size: Dp = THUMB_SIZE, modifier: Modifier = Modifier) {
    val texture = cardTextureId(cardId)
    CardThumb(
        painter = LocalUiArt.current?.thumb(texture),
        size = size,
        modifier = modifier.testTag(thumbTestTag(texture)),
    )
}

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
private const val RING = 0.4f
