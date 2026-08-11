package com.tripletriad.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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

/** `help-rule-<constant>`, by the AS3 rule constant — which is also the label's i18n key. */
fun helpRuleTestTag(ruleKey: String): String = "help-rule-$ruleKey"

/** `help-text-<constant>`, present only while that rule is the expanded one. */
fun helpTextTestTag(ruleKey: String): String = "help-text-$ruleKey"

/** `help-family-<key>` — one per section header. */
fun helpFamilyTestTag(labelKey: String): String = "help-family-$labelKey"

/**
 * The rules, explained — `HelpScreen.as`.
 *
 * Tap a rule to open its text, tap it again to close. The original was a master/detail pair, a list
 * on the left and a text pane on the right; an accordion is the same information on a phone and
 * needs no second column.
 *
 * ### The list is not [com.tripletriad.model.RuleKeys], deliberately
 *
 * `RULE_COMBO` is in this list and in no other: it is a **dead constant** everywhere else — nothing
 * writes or reads a combo flag, and combo fires unconditionally whenever Same, Same Wall or Plus
 * captures (see [com.tripletriad.model.GameRules.comboEnabled]). The help screen is the one place
 * it legitimately appears, because it describes something the game really does. So this file keeps
 * its own order, transcribed from `HelpScreen.as:72-89`, rather than deriving one from the rules
 * engine's table — which correctly excludes combo and would therefore omit it.
 *
 * `HelpScreen.as:77` and `:84` both list `RULE_CHAOS`, so the original's list is eighteen entries
 * with one shown twice. Deduplicated here; a duplicated row is not behaviour to preserve.
 *
 * ### Three help texts are placeholders in the original data
 *
 * `RULE_SAME_WALL_HELP`, `RULE_COMBO_HELP` and `RULE_ELEMENTAL_HELP` resolve to the rule's own name
 * in all four bundles — "Same Wall", "Combo", "Elemental" — so the original showed the title twice
 * and explained nothing. Shown as-is: the bundles are Square Enix wording imported by
 * `tools/import_locales.py` and writing three paragraphs of our own into them would be inventing
 * source text. The gap is real and it is the translators' to fill, not this screen's to paper over.
 *
 * `RULE_ORDER`'s **label** has the same shape of defect the other way round: it reads `Ordre` in
 * `en_US`, an untranslated French leftover in the imported bundle.
 */
@Composable
internal fun HelpScreen(profile: GameSave, onBack: () -> Unit) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf<String?>(null) }

    CharacterScaffold(profile = profile, title = strings[StringKeys.HELP], onBack = onBack) {
        LazyColumn(
            modifier = Modifier.testTag(HELP_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

/**
 * A family's name, over the rules in it.
 *
 * `labelMedium` on the accent rather than another `titleSmall`: a header that competed with the
 * seventeen rows under it would make the screen look like thirty-four entries instead of four
 * groups. The extra top padding is what separates the groups — a divider between them would be a
 * second horizontal line in a list whose rows are already outlined boxes.
 */
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
            .padding(top = 14.dp, bottom = 2.dp),
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

/** Half a turn, so the chevron points at the text it opened. */
private const val OPEN_DEGREES = 180f
private val ChevronSize = 18.dp

/** A group of rules, and the heading it sits under. */
internal data class HelpFamily(val labelKey: String, val rules: List<String>)

/**
 * The seventeen rules in four groups.
 *
 * ### Why this grouping is editorial, and says so
 *
 * [com.tripletriad.model.GameRules] does group these — into `slots` and `flags` — but that split is
 * about *how the rules combine*: a slot holds one of a mutually exclusive set, a flag is an
 * independent boolean. It puts Chaos beside Elemental and Sudden Death beside Same, which answers a
 * question no player asked. Deriving the headings from it would be deriving them from the wrong
 * fact, so these four are chosen for what a rule *does during a match* and are stated here rather
 * than pretended to come from the engine.
 *
 * The order within each group is `HelpScreen.as:72-89`'s, so a player who knew the original list
 * still meets the rules in the order it introduced them.
 *
 * `RULE_COMBO` sits with the capture rules although nothing sets it — see this file's header for
 * why it is in the list at all.
 */
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

/**
 * `HelpScreen.as:72-89`, with the duplicated `RULE_CHAOS` dropped.
 *
 * Derived from [HELP_FAMILIES] rather than kept beside it, so a rule cannot be listed here and
 * shown nowhere — which is exactly what a second hand-maintained list would eventually do.
 *
 * Public so a test can sweep it: every entry must resolve to both a label and a `_HELP` text in the
 * `en_US` bundle, which is the check that would have caught the three placeholder texts above being
 * *missing* rather than merely thin.
 */
internal val HELP_RULES: List<String> = HELP_FAMILIES.flatMap { it.rules }
