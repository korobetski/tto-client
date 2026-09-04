package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.BoosterPricing
import com.tripletriad.data.NpcCatalog
import com.tripletriad.data.ShopCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Achievement
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardItem
import com.tripletriad.model.Npc
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.math.roundToInt

const val CARD_SOURCES_TEST_TAG: String = "card-sources"
const val CARD_SOURCES_NONE_TEST_TAG: String = "card-sources-none"

fun cardSourceTestTag(slug: String): String = "card-source-$slug"

/**
 * One way a card can be come by.
 *
 * ### Why this exists at all
 *
 * Every fact in here was already shipped and none of it was reachable. `npcs.json` says who drops
 * what and at what rate, `BoosterType` carries a weighted pool, `ShopCatalog` a price and
 * `AchievementCatalog` a reward — four tables, all loaded at startup, none of them readable from
 * the one screen where a player is looking at a card they do not own. A collection of 565 tiles
 * with gaps in it is a list of things to want; the same collection with this is a list of things
 * to *do*.
 *
 * ### An index, not a promise
 *
 * A rate is what the authored table says, and the table is authored in this repository — see
 * `CLAUDE.md` § the data files. It is not a claim about what the referee will actually roll: the
 * server keeps its own copy of `npcs.json` and rolls from that one, so a stale copy there makes
 * this optimistic rather than wrong. Nothing on this screen can detect that, and nothing should
 * pretend to.
 */
@Immutable
internal sealed interface CardSource {
    /** How the row is named in the tree it is drawn in. Unique across the four kinds. */
    val slug: String

    data class Opponent(val npc: Npc, val rate: Double) : CardSource {
        override val slug: String get() = "npc-${npc.iconId}"
    }

    data class Booster(val type: BoosterType, val odds: Double) : CardSource {
        override val slug: String get() = "booster-${type.name.lowercase()}"
    }

    data class Shelf(val price: Int) : CardSource {
        override val slug: String get() = "shop"
    }

    data class Reward(val achievement: Achievement) : CardSource {
        override val slug: String get() = "achievement-${achievement.id}"
    }
}

/**
 * Everywhere [cardId] comes from, in the order a player would act on them.
 *
 * ### The order is the point
 *
 * A price and a haut fait are **certain** — pay it, or finish the ladder, and the card is yours.
 * A drop and a pack are a chance. So the two certain kinds come first whatever their cost, and the
 * chances follow by how good they are. Sorting the whole list by probability would bury a card
 * that is simply on sale under six opponents who might drop it.
 *
 * ### Every opponent, not the ones playing this format
 *
 * Naming an opponent needs no format — [Npc.nameKey] is on the row itself. `NpcCatalog.byIcon`
 * takes one only because ids repeat across the two tables, and nothing here goes near an id. An
 * opponent who is locked, or only open at three in the morning, is still where the card is; the
 * roster is the screen that says whether they can be played tonight.
 */
internal fun cardSources(cardId: Int, opponents: NpcCatalog?): List<CardSource> {
    val certain = buildList {
        ShopCatalog.shelf
            .firstOrNull { (it.item as? CardItem)?.cardId == cardId }
            ?.let { add(CardSource.Shelf(it.price)) }
        AchievementCatalog.all
            .filter { (it.reward as? CardItem)?.cardId == cardId }
            .forEach { add(CardSource.Reward(it)) }
    }

    val dropped = opponents?.all.orEmpty()
        .mapNotNull { npc ->
            npc.itemRewards
                .filter { it.cardId == cardId && it.item() is CardItem }
                .maxByOrNull { it.rate }
                ?.let { CardSource.Opponent(npc, it.rate) }
        }
        .sortedWith(compareByDescending<CardSource.Opponent> { it.rate }.thenBy { it.npc.iconId })

    val packed = BoosterType.entries
        .mapNotNull { type ->
            val at = type.pool.indexOf(cardId)
            if (at < 0) null else CardSource.Booster(type, BoosterPricing.oddsOf(type)[at])
        }
        .sortedWith(compareByDescending<CardSource.Booster> { it.odds }.thenBy { it.type.name })

    return certain + dropped + packed
}

/**
 * The sources under a card, or the sentence that says there are none.
 *
 * **The empty case is not nothing to say.** Most of the 565 cards are drops from opponents this
 * port has not finished authoring, and a card with no source is a fact worth stating plainly —
 * a blank space there reads as a panel that failed to load.
 */
@Composable
internal fun CardSources(sources: List<CardSource>) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.testTag(CARD_SOURCES_TEST_TAG).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = strings[StringKeys.CARD_SOURCES],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )

        if (sources.isEmpty()) {
            Text(
                text = strings[StringKeys.NO_CARD_SOURCE],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag(CARD_SOURCES_NONE_TEST_TAG),
            )
            return@Column
        }

        for (source in sources.take(SOURCE_LIMIT)) {
            SourceRow(source)
        }
        // A card in eleven opponents' drop tables is a card whose panel is a list of opponents.
        // The count is kept because "there are more" without a number is worse than either
        // showing all of them or showing none.
        val hidden = sources.size - SOURCE_LIMIT
        if (hidden > 0) {
            Text(
                text = strings.format(StringKeys.CARD_SOURCES_MORE, "$hidden"),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SourceRow(source: CardSource) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.testTag(cardSourceTestTag(source.slug)).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = source.name(strings),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = source.terms(strings),
            // The theme's own colour for a number that is a *cost* or a *chance*, which is what
            // every one of these is — the same one the shop's prices and the quests' payouts use.
            color = LocalTtoColors.current.transient,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Who or what this is, in the player's language. */
private fun CardSource.name(strings: Strings): String = when (this) {
    is CardSource.Opponent -> strings[npc.nameKey]
    is CardSource.Booster -> strings[type.nameKey]
    is CardSource.Shelf -> strings[StringKeys.CARD_SHOP]
    is CardSource.Reward -> strings[achievement.labelKey]
}

/**
 * What it costs or how likely it is — the half of the line a player is comparing.
 *
 * A haut fait has neither, and says `Rewards` rather than a number: it is earned by finishing
 * something the profile screen already tracks, and inventing a percentage for it would be a lie
 * about a certainty.
 */
private fun CardSource.terms(strings: Strings): String = when (this) {
    is CardSource.Opponent -> percent(rate)
    is CardSource.Booster -> percent(odds)
    is CardSource.Shelf -> "$price ${strings[StringKeys.MGP]}"
    is CardSource.Reward -> strings[StringKeys.REWARDS]
}

/**
 * A probability as a percentage, never rounded down to zero.
 *
 * A 0.3 % pull is not a 0 % pull, and a table that showed the rarest card in the game as
 * unobtainable would be the one row a player would be right to disbelieve. One decimal below ten
 * per cent, none above it, because `25.0 %` is two characters of noise on a line that has to fit
 * beside a name.
 */
private fun percent(rate: Double): String {
    val scaled = rate * PERCENT
    return if (scaled < ONE_DECIMAL_BELOW) {
        val tenths = (scaled * TENTHS).roundToInt().coerceAtLeast(1)
        "${tenths / TENTHS}.${tenths % TENTHS}%"
    } else {
        "${scaled.roundToInt()}%"
    }
}

/** Six lines is a panel; a dozen is a list, and the panel is not a list. */
private const val SOURCE_LIMIT = 6

private const val PERCENT = 100.0
private const val ONE_DECIMAL_BELOW = 10.0
private const val TENTHS = 10
