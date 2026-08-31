package com.tripletriad.ui

import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.BoonType
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.Card
import com.tripletriad.model.CardItem
import com.tripletriad.model.CardOrigin
import com.tripletriad.model.Item
import com.tripletriad.model.MiscItem
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PouchItem

internal fun itemName(strings: Strings, item: Item, cards: Map<Int, Card>): String = when (item) {
    is CardItem -> cardName(strings, item.cardId, cards)
    is BoosterItem -> strings[item.boosterType.nameKey]
    is PotionItem -> strings[item.potionType.nameKey]
    // Named after the sale rather than after itself. "Bourse" alone would be one of several
    // identical rows for a seller who woke up to three settlements; the card is what tells them
    // apart, and it is the only word either of them will remember.
    is PouchItem -> strings.format(StringKeys.POUCH_ITEM, cardName(strings, item.cardId, cards))
    is MiscItem -> strings[StringKeys.UNKNOWN_ITEM]
}

/**
 * The line under the name.
 *
 * A function rather than `strings[item.descriptionKey]` at the call site, because one item's
 * description has an argument: a pouch says which sale it came from, and the card's name is not
 * something `Item` can reach — the model has no card table. Everything else is the plain lookup it
 * always was, which is why the shop still does that directly.
 */
internal fun itemDescription(strings: Strings, item: Item, cards: Map<Int, Card>): String =
    when (item) {
        is PouchItem -> strings.format(
            item.descriptionKey,
            cardName(strings, item.cardId, cards),
        )

        else -> strings[item.descriptionKey]
    }

internal fun itemKey(item: Item): Item = item.withStack(1)

internal fun itemSlug(item: Item): String = when (item) {
    // The origin is part of the identity, and has to be part of the slug: a card bought in the
    // shop and the same card handed back unsold are two bag entries — `Inventory.stacksWith`
    // compares whole items — so one slug for both would give the bag two rows under one key. The
    // plain case keeps the bare shape it has always had, because it is in test tags all over.
    is CardItem -> "card-${item.cardId}${item.origin.slugSuffix}"
    is BoosterItem -> "booster-${item.boosterType.name}"
    is PotionItem -> "potion-${item.potionType.name}"
    // The lot, not the card: two sales of the same card at the same price are two pouches, and
    // `PouchItem` says at length why they must never be one.
    is PouchItem -> "pouch-${item.lotId}"
    is MiscItem -> "misc"
}

private val CardOrigin.slugSuffix: String
    get() = if (this == CardOrigin.PLAIN) "" else "-${name.lowercase()}"

internal fun itemCard(item: Item, cards: Map<Int, Card>): Card? =
    (item as? CardItem)?.let { cards[it.cardId] }

internal fun itemIconId(item: Item): String = item.iconId

internal fun boonOf(item: Item): BoonType? = (item as? PotionItem)?.potionType?.modifier?.type

internal fun ownedNote(strings: Strings, item: Item, owned: Map<Int, Int>): String? {
    val copies = (item as? CardItem)?.let { owned[it.cardId] } ?: 0
    return if (copies > 0) "${strings[StringKeys.ALREADY_OWNED]} $COPIES_PREFIX$copies" else null
}

/** A card's name, or its id when the catalog has not got it. Both callers above want this. */
internal fun cardName(strings: Strings, cardId: Int, cards: Map<Int, Card>): String =
    cards[cardId]?.let { strings[it.nameKey] } ?: "#$cardId"
