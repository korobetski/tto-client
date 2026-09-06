package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.ui.theme.LocalTtoColors

/** A rule named somewhere other than the rule book, and the way from there to its entry. */
fun ruleLinkTestTag(ruleKey: String): String = "rule-link-$ruleKey"

/**
 * A rule's name, as a way into the book rather than as a legend.
 *
 * The course already knew which rules each lesson teaches and printed them joined by dots under
 * the title — the right words in the wrong shape, since the entry explaining each of them was one
 * screen away with nothing pointing at it. This is that same list, tappable.
 *
 * ### Not a [TtoFilterChip]
 *
 * A filter chip is a `Checkbox`: it has an on and an off, and a screen reader says which. This has
 * neither — it is a link, so [Role.Button] and no `selected`. They share the shell's proportions
 * on purpose (a rule named in the course and a rule named in a filter row are the same kind of
 * word) and nothing else.
 *
 * ### The 32 dp
 *
 * [ChipHeight], the height every other chip in the application is drawn at, and below the 48 dp
 * this repository holds rows to. A pill sits *inside* a row that is itself tappable and already
 * meets that floor: growing the pill would grow the row it lives in by half again, for a control
 * that is the second thing on the row rather than the first. The row remains the large target.
 */
@Composable
internal fun RulePill(ruleKey: String, onOpen: (String) -> Unit) {
    val strings = LocalStrings.current
    val shape = MaterialTheme.shapes.small
    val ink = LocalTtoColors.current.transient

    Row(
        modifier = Modifier
            .testTag(ruleLinkTestTag(ruleKey))
            .height(ChipHeight)
            .clip(shape)
            .background(Color.Transparent)
            .border(HairlineWidth, ink.copy(alpha = DISABLED), shape)
            .ttoClickable(role = Role.Button, shape = shape) { onOpen(ruleKey) }
            .padding(horizontal = SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings[ruleKey],
            color = ink,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
