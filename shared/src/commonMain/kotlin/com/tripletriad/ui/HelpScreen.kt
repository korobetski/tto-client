package com.tripletriad.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

const val HELP_LIST_TEST_TAG: String = "help-list"

fun helpRuleTestTag(ruleKey: String): String = "help-rule-$ruleKey"

fun helpTextTestTag(ruleKey: String): String = "help-text-$ruleKey"

fun helpFamilyTestTag(labelKey: String): String = "help-family-$labelKey"

@Composable
internal fun HelpScreen(profile: GameSave, onBack: () -> Unit) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf<String?>(null) }

    CharacterScaffold(profile = profile, title = strings[StringKeys.HELP], onBack = onBack) {
        LazyColumn(
            modifier = Modifier.testTag(HELP_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            for (family in HELP_FAMILIES) {
                item(key = family.labelKey) { HelpSectionHeader(family.labelKey) }
                items(family.rules, key = { it }) { ruleKey ->
                    HelpRow(
                        ruleKey = ruleKey,
                        isOpen = open == ruleKey,
                        onClick = { open = if (open == ruleKey) null else ruleKey },
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpSectionHeader(labelKey: String) {
    val strings = LocalStrings.current

    Text(
        text = strings[labelKey],
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .testTag(helpFamilyTestTag(labelKey))
            .fillMaxWidth()
            .padding(top = SpaceLg, bottom = 2.dp),
    )
}

@Composable
private fun HelpRow(ruleKey: String, isOpen: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
    // Animated, so the chevron turns rather than flipping. It is the only moving part of the row,
    // and it is what tells a player the row is a control at all before they tap it.
    val turn by animateFloatAsState(
        targetValue = if (isOpen) OPEN_DEGREES else 0f,
        label = "help-chevron",
    )

    Column(
        modifier = Modifier
            .testTag(helpRuleTestTag(ruleKey))
            .fillMaxWidth()
            .rowSurface(selected = isOpen)
            // A rule row opens and closes its own explanation, so it is a disclosure rather than a
            // choice: `selected` is what a screen reader needs to say whether the text below it is
            // showing, and without it an expanded row and a collapsed one sound identical.
            .ttoClickable(selected = isOpen, onClick = onClick)
            .padding(horizontal = SpaceMd, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            Text(
                text = strings[ruleKey],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = TtoIcons.Expand,
                // Null: the row already says what it is, and the chevron announced separately
                // would have a screen reader read every rule's name twice.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                modifier = Modifier
                    .size(ChevronSize)
                    .graphicsLayer { rotationZ = turn },
            )
        }
        // `AnimatedVisibility` rather than an `if`, so the text slides in instead of the row
        // snapping to twice its height — an accordion that jumps reads as a layout bug.
        AnimatedVisibility(visible = isOpen) {
            Text(
                text = strings["${ruleKey}_HELP"],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(helpTextTestTag(ruleKey)),
            )
        }
    }
}

private const val OPEN_DEGREES = 180f
private val ChevronSize = 18.dp

internal data class HelpFamily(val labelKey: String, val rules: List<String>)

internal val HELP_FAMILIES: List<HelpFamily> = listOf(
    // What you can see of the other hand.
    HelpFamily(StringKeys.HELP_FAMILY_SIGHT, listOf("RULE_ALL_OPEN", "RULE_THREE_OPEN")),
    // What the match is made of, and in what order it is played.
    HelpFamily(
        StringKeys.HELP_FAMILY_PLAY,
        listOf(
            "RULE_SUDDEN_DEATH",
            "RULE_RANDOM",
            "RULE_ORDER",
            "RULE_CHAOS",
            "RULE_SWAP",
            "RULE_ROULETTE",
        ),
    ),
    // Which card beats which, and what happens when one does.
    HelpFamily(
        StringKeys.HELP_FAMILY_CAPTURE,
        listOf(
            "RULE_REVERSE",
            "RULE_FALLEN_ACE",
            "RULE_SAME",
            "RULE_SAME_WALL",
            "RULE_PLUS",
            "RULE_COMBO",
        ),
    ),
    // The three that read a card's element. `TypeRule` holds exactly these, and it is the one place
    // the engine's own grouping and a player's agree.
    HelpFamily(
        StringKeys.HELP_FAMILY_ELEMENTS,
        listOf("RULE_ASCENSION", "RULE_DESCENSION", "RULE_ELEMENTAL"),
    ),
)

internal val HELP_RULES: List<String> = HELP_FAMILIES.flatMap { it.rules }
