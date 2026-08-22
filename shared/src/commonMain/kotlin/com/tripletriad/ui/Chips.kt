package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
/**
 * A filter chip carrying a word.
 *
 * Drawn by [ChipShell] rather than by Material's `FilterChip`, so that it and [TtoIconChip] are
 * the same object at two contents. They were not: a row of `FilterChip`s next to a row of
 * hand-drawn icon chips gave the card list three chip heights — 32, 24 and 36 — for one idea.
 */
@Composable
internal fun TtoFilterChip(
    label: String,
    tag: String,
    selected: Boolean,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    ChipShell(tag = tag, selected = selected, enabled = enabled, onClick = onClick) {
        leading?.invoke()
        Text(
            text = label,
            color = chipContentColor(selected),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The same chip carrying a picture — an element, a rarity plate.
 *
 * @param description what the picture says, for anyone who cannot see it. Required rather than
 *   optional: an icon-only control with no description is a control a screen reader announces as
 *   nothing at all.
 */
@Composable
internal fun TtoIconChip(
    tag: String,
    description: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    ChipShell(
        tag = tag,
        selected = selected,
        enabled = enabled,
        description = description,
        onClick = onClick,
        content = { icon() },
    )
}

/**
 * What every chip in the app is made of: one height, one shape, one pair of colours.
 *
 * The height is [ChipHeight] whatever the content, and nothing is reserved around it:
 * `minimumInteractiveComponentSize` would claim 48x48 of layout per chip while drawing 32, which
 * on a wrapped row of small icon chips is 8 dp of nothing either side of each and 16 between the
 * lines — the gaps read as sloppy spacing rather than as the touch targets they are. Compose
 * already grows a clickable node's *touch* bounds to the 48 dp minimum without growing its
 * layout, which is what the 40 dp grid cells have always relied on.
 */
@Composable
private fun ChipShell(
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.small

    Row(
        modifier = Modifier
            .testTag(tag)
            .height(ChipHeight)
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
            .border(
                width = HairlineWidth,
                color = if (selected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .ttoClickable(
                role = Role.Checkbox,
                enabled = enabled,
                selected = selected,
                shape = shape,
                onClick = onClick,
            )
            .semantics { description?.let { contentDescription = it } }
            .padding(horizontal = SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun chipContentColor(selected: Boolean): Color = if (selected) {
    MaterialTheme.colorScheme.onSecondaryContainer
} else {
    MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED)
}

/** Every chip is this tall, text or picture. The rarity plate's 28 dp fits inside it. */
internal val ChipHeight = 32.dp
