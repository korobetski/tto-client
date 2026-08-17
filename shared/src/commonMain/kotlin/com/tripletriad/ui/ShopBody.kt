package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.BoosterPricing
import com.tripletriad.data.ShopCatalog
import com.tripletriad.data.ShopOffer
import com.tripletriad.data.StarterCatalog
import com.tripletriad.data.StarterPack
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import kotlin.math.roundToInt

const val SHOP_LIST_TEST_TAG: String = "shop-list"
const val SHOP_BUY_TEST_TAG: String = "shop-buy"

/** The free starter pack, present only while the character is owed one. */
const val SHOP_STARTER_TEST_TAG: String = "shop-starter"
const val SHOP_STARTER_CLAIM_TEST_TAG: String = "shop-starter-claim"

/** The purchase confirmation, which the original never gave: `//Save.save(…)` was commented out. */
const val SHOP_NOTE_TEST_TAG: String = "shop-note"

/** `shop-offer-<slug>` — the offer's item, since no item is on either shelf twice. */
fun shopOfferTestTag(offer: ShopOffer): String = "shop-offer-${itemSlug(offer.item)}"

/** `shop-odds-<slug>` — the guarantee-and-odds line, present only on a pack. */
fun shopOddsTestTag(offer: ShopOffer): String = "shop-odds-${itemSlug(offer.item)}"

/**
 * `One card 4★ or better, guaranteed · ★5 chance 63%`, or null for anything that is not a pack.
 *
 * Derived from the pool on every composition rather than authored per pack: a description that
 * said "one guaranteed three-star" would be a sentence somebody has to remember to change, and two
 * of the shipped pools cannot honour that claim anyway — Bronze's whole pool tops out at three
 * stars. See [com.tripletriad.data.BoosterPricing.guaranteedFloor].
 */
@Composable
private fun packTerms(strings: Strings, item: Item, cards: Map<Int, Card>): String? {
    val pack = (item as? BoosterItem)?.boosterType ?: return null
    val odds = (BoosterPricing.fiveStarChance(pack, cards) * PERCENT).roundToInt()
    val floor = strings.format(
        StringKeys.PACK_GUARANTEE,
        BoosterPricing.guaranteedFloor(pack, cards).toString(),
    )
    return if (odds == 0) {
        floor
    } else {
        "$floor$DOT_SEPARATOR" +
            strings.format(StringKeys.PACK_ODDS, odds.toString())
    }
}

/**
 * What the shop sells — the original's `shopScreen`.
 *
 * Tap an offer to select it, then Buy. That is the original's arrangement, and Buy is enabled by
 * affordability exactly as `shopList_changeHandler` (`:152-155`) computed it.
 *
 * What is on the shelf is [ShopCatalog], transcribed from the two static tables; the prices,
 * the order and the asymmetry between the collections are all documented there rather than here.
 *
 * ### The original's two defects, both fixed in the data layer
 *
 * - `buyButton_triggeredHandler` **subtracts the price and then checks whether it could be paid**
 *   (`:144-146`), so a profile could be taken below its own means; the check only decided whether
 *   the button stayed lit. [ShopCatalog.buy] is one operation that either happens or does not.
 * - It ends on a commented-out `//Save.save(Game.PROFILE_DATAS)` (`:149`), so **a purchase was
 *   never written**: the MGP and the item were both gone on quit. Persisted here, through
 *   [ProfileSession] like every other mutation.
 *
 * The unaffordable rows are shown greyed rather than hidden, which is the original's behaviour and
 * the right one: a card costing a million MGP is a goal, and a shop that hid it would only ever
 * show what the player has already outgrown.
 *
 * ### The one thing on this shelf that is not for sale
 *
 * A character short of five fieldable cards cannot play, and therefore cannot earn the MGP that
 * every other row on this screen wants. [StarterPackPanel] is the way out of that, and the shop is
 * where it belongs: it is already the screen a player reaches for when they have nothing, and it
 * needs no new navigation to find. See [StarterPack] for the rule and for the defect that made it
 * necessary.
 *
 * @param onClaimStarter grants the pack. Null when nothing is owed, which is what hides the panel —
 *   a "free cards" banner over a full collection would be an offer that does nothing.
 */
@Composable
@Suppress("LongParameterList")
internal fun ColumnScope.ShopBody(
    profile: GameSave,
    offers: List<ShopOffer>,
    cards: Map<Int, Card>,
    starters: StarterCatalog,
    selectedTag: String?,
    onSelect: (String?) -> Unit,
    onClaimStarter: (() -> Unit)? = null,
) {
    if (onClaimStarter != null) {
        StarterPackPanel(
            starters = starters,
            cards = cards,
            onClaim = onClaimStarter,
        )
    }

    LazyColumn(
        modifier = Modifier.testTag(SHOP_LIST_TEST_TAG).fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        items(offers, key = ::shopOfferTestTag) { offer ->
            OfferRow(
                offer = offer,
                cards = cards,
                isAffordable = offer.isAffordableBy(profile),
                isSelected = shopOfferTestTag(offer) == selectedTag,
                onClick = { onSelect(shopOfferTestTag(offer).takeIf { it != selectedTag }) },
            )
        }
    }
}

/**
 * The free pack, with the five cards it holds drawn rather than described.
 *
 * Above the shelf and outside the `LazyColumn`, so it is on screen without scrolling: a player who
 * cannot play is looking at a list of things they cannot buy, and the one row that would help them
 * must not be the one they have to find.
 *
 * The thumbnails are the point. This is the authored starter of the character's own set — see
 * [StarterCatalog] — so showing them is showing exactly what the button does, which is what a
 * sentence about "starter cards" cannot do for a player who has never seen the table.
 *
 * The **deck** is drawn rather than all ten cards: it is the five the pack is about, it is what the
 * character will be holding on its next match, and ten thumbnails would wrap on a phone.
 */
@Composable
private fun StarterPackPanel(
    starters: StarterCatalog,
    cards: Map<Int, Card>,
    onClaim: () -> Unit,
) {
    val strings = LocalStrings.current
    val granted = remember(starters, cards) {
        starters.starters.firstOrNull()?.deck.orEmpty().mapNotNull(cards::get)
    }

    Column(
        modifier = Modifier
            .testTag(SHOP_STARTER_TEST_TAG)
            .fillMaxWidth()
            .padding(bottom = SpaceSm)
            .rowSurface(selected = true)
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = strings[StringKeys.STARTER_PACK],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings[StringKeys.FREE],
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                softWrap = false,
            )
        }

        Text(
            text = strings[StringKeys.STARTER_PACK_DESC],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (card in granted) {
                CardThumb(card = card)
            }
        }

        WideButton(
            label = strings[StringKeys.CLAIM],
            tag = SHOP_STARTER_CLAIM_TEST_TAG,
            onClick = onClaim,
        )
    }
}

@Composable
private fun OfferRow(
    offer: ShopOffer,
    cards: Map<Int, Card>,
    isAffordable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    // Dimmed rather than disabled: an offer out of reach is still worth reading, and it is still
    // selectable so the Buy button can say what it would cost.
    val alpha = if (isAffordable) 1f else UNAFFORDABLE_ALPHA

    Row(
        modifier = Modifier
            .testTag(shopOfferTestTag(offer))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            // Exclusive: the shelf has one selection at a time and the Buy button acts on it, so
            // these are radio buttons wearing a row's clothes.
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        // The card if the offer is a card, its icon otherwise — a booster pack has artwork of
        // its own, and a shelf of nothing but text is the thing this screen was worst at.
        val card = itemCard(offer.item, cards)
        if (card != null) {
            CardThumb(card = card)
        } else {
            ItemGlyph(
                item = offer.item,
                description = itemName(strings, offer.item, cards),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = itemName(strings, offer.item, cards),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings[offer.item.descriptionKey],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * FAINT),
                style = MaterialTheme.typography.labelSmall,
                // Two lines, not one. At the old type scale a pack's description fitted; at
                // Material's it does not, and `Six cartes communes pour démarrer une collec…` is a
                // shelf that will not say what it is selling. A row growing by one line is a
                // cheaper price than a description nobody can finish reading.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // What a pack actually promises. A row that says "five cards" and nothing else asks
            // the player to guess its odds, and they guess wrong in whichever direction
            // disappoints them. Both numbers come from the pool — see `BoosterPricing`.
            packTerms(strings, offer.item, cards)?.let { terms ->
                Text(
                    text = terms,
                    modifier = Modifier.testTag(shopOddsTestTag(offer)),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
                    style = MaterialTheme.typography.labelSmall,
                    // Same reasoning as the description above, and it matters more here: this
                    // line is the pack's *odds*, and `· Chance de …` is the half that got cut.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = "${offer.price} ${strings[StringKeys.MGP]}",
            color = if (isAffordable) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** `isEnabled = false` in the original, which greyed the whole renderer. */
private const val UNAFFORDABLE_ALPHA = DISABLED

/** A chance is shown as a whole percentage; nobody reads a pack's odds to two decimals. */
private const val PERCENT = 100
