package com.tripletriad.ui

import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.BoonType
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.Card
import com.tripletriad.model.CardItem
import com.tripletriad.model.Item
import com.tripletriad.model.MiscItem
import com.tripletriad.model.PotionItem

internal fun itemName(strings: Strings, item: Item, cards: Map<Int, Card>): String = when (item) {
    is CardItem -> cards[item.cardId]?.let { strings[it.nameKey] } ?: "#${item.cardId}"
    is BoosterItem -> strings[item.boosterType.nameKey]
    is PotionItem -> strings[item.potionType.nameKey]
    is MiscItem -> strings[StringKeys.UNKNOWN_ITEM]
}

internal fun itemKey(item: Item): Item = item.withStack(1)

internal fun itemSlug(item: Item): String = when (item) {
    is CardItem -> "card-${item.cardId}"
    is BoosterItem -> "booster-${item.boosterType.name}"
    is PotionItem -> "potion-${item.potionType.name}"
    is MiscItem -> "misc"
}

internal fun itemCard(item: Item, cards: Map<Int, Card>): Card? =
    (item as? CardItem)?.let { cards[it.cardId] }

internal fun itemIconId(item: Item): String = item.iconId

internal fun boonOf(item: Item): BoonType? = (item as? PotionItem)?.potionType?.modifier?.type

internal fun ownedNote(strings: Strings, item: Item, owned: Map<Int, Int>): String? {
    val copies = (item as? CardItem)?.let { owned[it.cardId] } ?: 0
    return if (copies > 0) "${strings[StringKeys.ALREADY_OWNED]} $COPIES_PREFIX$copies" else null
}
