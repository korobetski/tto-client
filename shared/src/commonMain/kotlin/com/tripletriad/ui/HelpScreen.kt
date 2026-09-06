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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave

const val HELP_LIST_TEST_TAG: String = "help-list"

const val HELP_SEARCH_TEST_TAG: String = "help-search"
const val HELP_SEARCH_CLEAR_TEST_TAG: String = "help-search-clear"
const val HELP_NO_MATCH_TEST_TAG: String = "help-no-match"

fun helpRuleTestTag(ruleKey: String): String = "help-rule-$ruleKey"

fun helpTextTestTag(ruleKey: String): String = "help-text-$ruleKey"

fun helpFamilyTestTag(labelKey: String): String = "help-family-$labelKey"

/**
 * A section of the book: a family, or the handful of rules the last match was played under.
 *
 * The last match is a section rather than a shelf of chips because it is read the same way as a
 * family — a heading and rows that open — and a player who has just come off a board looking for
 * *why did that happen* should find the entry where entries live.
 */
internal data class HelpSection(val labelKey: String, val rules: List<String>)

/**
 * The book as it is shown: what the search admits, under the headings it belongs to.
 *
 * Pure, and apart from the composable for the reason `roomLots` is: an order and a filter are
 * invisible to any test that only checks the rows exist.
 *
 * The search reads **the name and the paragraph**, not the name alone. Somebody who half-remembers
 * a rule types what it does — "wall", "element" — and a book of seventeen entries that only
 * matched titles would answer nothing to the question it exists for.
 *
 * A search hides the last-match section. It is a shortcut past the table of contents, and a player
 * who is typing has already gone past it; leaving it would print three rules twice.
 *
 * @param lastRules the rules of the most recent match, or empty when there is no match to read.
 */
internal fun helpSections(
    query: String,
    lastRules: List<String>,
    nameOf: (String) -> String,
    textOf: (String) -> String,
): List<HelpSection> {
    val needle = query.trim()
    val matches: (String) -> Boolean = { key ->
        needle.isEmpty() ||
            nameOf(key).contains(needle, ignoreCase = true) ||
            textOf(key).contains(needle, ignoreCase = true)
    }

    val recent = lastRules.filter { it in HELP_RULES }
    val head = if (needle.isEmpty() && recent.isNotEmpty()) {
        listOf(HelpSection(StringKeys.HELP_LAST_MATCH, recent))
    } else {
        emptyList()
    }

    return head + HELP_FAMILIES.mapNotNull { family ->
        family.rules.filter(matches)
            .takeIf { it.isNotEmpty() }
            ?.let { HelpSection(family.labelKey, it) }
    }
}

/**
 * The rule book.
 *
 * @param lastRules what the most recent match was played under, so the three or four rules a
 *   player has just met are in front of the seventeen they have not. Null before a match has been
 *   played, and on a profile whose history is unreadable — both mean "nothing to put on top".
 * @param openAt the entry to open on arrival, which is how a lesson's rule pill lands here.
 */
@Composable
internal fun HelpScreen(
    profile: GameSave,
    lastRules: GameRules?,
    openAt: String?,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    var query by remember { mutableStateOf("") }

    // Keyed on the request, so arriving from a lesson opens that rule and a second arrival at the
    // same rule does not fight the player closing it.
    var open by remember(openAt) { mutableStateOf(openAt) }

    val recent = remember(lastRules) { lastRules?.activeRuleKeys().orEmpty() }
    val sections = remember(query, recent, strings) {
        helpSections(
            query = query,
            lastRules = recent,
            nameOf = { strings[it] },
            // Absent rather than a key: the four locales do not describe the same set, and a rule
            // with no paragraph is then searchable by its name alone rather than by "RULE_X_HELP".
            textOf = { key -> "${key}_HELP".let { if (strings.has(it)) strings[it] else "" } },
        )
    }

    val listState = rememberLazyListState()

    CharacterScaffold(profile = profile, title = strings[StringKeys.HELP], onBack = onBack) {
        TtoSearchField(
            value = query,
            onValueChange = { query = it },
            tag = HELP_SEARCH_TEST_TAG,
            clearTag = HELP_SEARCH_CLEAR_TEST_TAG,
            placeholder = strings[StringKeys.HELP_SEARCH],
            modifier = Modifier.fillMaxWidth().padding(bottom = SpaceSm),
        )

        // A book with entries but none of them shown is something the player did, and the search
        // above is still full: this says which word to take back out.
        if (sections.isEmpty()) {
            EmptyNote(strings[StringKeys.HELP_NO_MATCH], HELP_NO_MATCH_TEST_TAG)
            return@CharacterScaffold
        }

        // An entry opened from elsewhere is opened *and shown*. Seventeen rows and four headings
        // are taller than a phone, so a rule the player asked for by name can be expanded well
        // below the fold — which looks exactly like nothing having happened.
        LaunchedEffect(open, sections) {
            val index = open?.let { rule -> rowIndexOf(sections, rule) } ?: return@LaunchedEffect
            listState.animateScrollToItem(index)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.testTag(HELP_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            for (section in sections) {
                item(key = "section-${section.labelKey}") {
                    HelpSectionHeader(section.labelKey)
                }
                items(section.rules, key = { "${section.labelKey}-$it" }) { ruleKey ->
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
 * Where a rule's row sits in the flat list the `LazyColumn` lays out.
 *
 * Headings are items too, which is what makes this arithmetic rather than an `indexOf`. The first
 * occurrence wins: a rule in the last-match section is also in its family, and scrolling to the
 * copy the player can see without scrolling is the right one of the two.
 */
internal fun rowIndexOf(sections: List<HelpSection>, ruleKey: String): Int? {
    var index = 0
    for (section in sections) {
        index++
        val at = section.rules.indexOf(ruleKey)
        if (at >= 0) return index + at
        index += section.rules.size
    }
    return null
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
            Column(verticalArrangement = Arrangement.spacedBy(SpaceMd)) {
                Text(
                    // The French bundle sets the "FF14 only" qualifier in italics — see [markup].
                    text = markup(strings["${ruleKey}_HELP"]),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(helpTextTestTag(ruleKey)),
                )
                // Under the paragraph and not instead of it: the picture settles which way the
                // rule runs, the paragraph says what it is called and where it applies.
                RULE_DIAGRAMS[ruleKey]?.let { frames ->
                    RuleDiagram(ruleKey = ruleKey, frames = frames)
                }
            }
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
