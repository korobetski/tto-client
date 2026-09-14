package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.ACE_POWER
import com.tripletriad.model.Card
import com.tripletriad.model.Deck
import com.tripletriad.model.Side
import com.tripletriad.model.power

const val DECK_ANALYSIS_TEST_TAG: String = "deck-analysis"

fun deckSideAverageTestTag(side: Side): String = "deck-side-average-${side.name.lowercase()}"

/**
 * The deck editor's right-hand column on a wide window: what the draft adds up to, then [status] —
 * the rule counters and the controls the narrow editor stacks over its grid.
 *
 * ### Averages of the edges, not a second "power"
 *
 * "Deck power" already names the stars on this screen (`STR_DECK_POWER`, the original's own word).
 * A total of the edges beside it would be a second number called power. An edge's average is also
 * the number a board is read with: a deck averaging 3 on its left loses what attacks it from there.
 *
 * Averaged over the cards **picked**, not over five. Over five, every half-built deck would read as
 * weak on every side, which is a fact about how far the player has got and not about the cards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeckAnalysisPanel(
    draft: Deck,
    cards: Map<Int, Card>,
    modifier: Modifier = Modifier,
    status: @Composable ColumnScope.() -> Unit,
) {
    val strings = LocalStrings.current
    // Elements and tribes both: `CardType` is one field for the two, and the icon is what the
    // board's plus and same rules are read against either way.
    val families = remember(draft, cards) {
        draft.cards.mapNotNull { cards[it]?.type }.groupingBy { it }.eachCount()
    }

    Column(
        modifier = modifier
            .testTag(DECK_ANALYSIS_TEST_TAG)
            .rowSurface()
            .verticalScroll(rememberScrollState())
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        Text(
            text = strings[StringKeys.DECK_ANALYSIS],
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )

        Column(verticalArrangement = Arrangement.spacedBy(SpaceXs)) {
            SectionHeader(strings[StringKeys.DECK_SIDE_AVERAGES])
            for (side in Side.entries) {
                val tenths = sideAverageTenths(draft, cards, side)
                SideAverage(
                    label = strings[side.labelKey],
                    tenths = tenths,
                    text = tenthsText(tenths, strings.locale),
                    tag = deckSideAverageTestTag(side),
                )
            }
        }

        if (families.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(SpaceXs)) {
                SectionHeader(strings[StringKeys.CARD_TYPE])
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SpaceSm),
                    verticalArrangement = Arrangement.spacedBy(SpaceXs),
                ) {
                    for ((type, count) in families.entries.sortedByDescending { it.value }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TypeIcon(type)
                            Text(
                                // The enum's own name, as the filter menus write it: nothing
                                // translates the elements yet.
                                text = " ${type.name} $COPIES_PREFIX$count",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        status()
    }
}

@Composable
private fun SideAverage(label: String, tenths: Int?, text: String, tag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(SideLabelWidth),
        )
        // Out of an ace, so a bar is comparable across sides and across decks rather than scaled
        // to whichever side happens to be strongest.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(BarHeight)
                .clip(RoundedCornerShape(BarHeight / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(((tenths ?: 0) / (ACE_POWER * TENTHS).toFloat()).coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(SideValueWidth).testTag(tag),
        )
    }
}

/**
 * [side]'s average over the cards [deck] holds, in tenths and rounded half up; null for a deck with
 * no card to average. Tenths rather than a `Double`, so the rounding is this function's and not
 * whichever `toString` a platform ships.
 */
internal fun sideAverageTenths(deck: Deck, cards: Map<Int, Card>, side: Side): Int? {
    val powers = deck.cards.mapNotNull { cards[it]?.power(side) }
    if (powers.isEmpty()) return null
    return (powers.sum() * TENTHS + powers.size / 2) / powers.size
}

/** `6.5`, or `6,5` where the locale writes a comma; a dash for no average at all. */
internal fun tenthsText(tenths: Int?, locale: AppLocale): String {
    if (tenths == null) return "–"
    val separator = when (locale) {
        AppLocale.FR_FR, AppLocale.DE_DE -> ','
        AppLocale.EN_US, AppLocale.JA_JA -> '.'
    }
    return "${tenths / TENTHS}$separator${tenths % TENTHS}"
}

private const val TENTHS = 10

/** "Gauche" and "Unten" fit; the bar takes what is left. */
private val SideLabelWidth = 64.dp

/** `10,0`, the widest an average can be written. */
private val SideValueWidth = 36.dp

private val BarHeight = 6.dp
