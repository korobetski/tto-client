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

/**
 * What to call an item on screen.
 *
 * Its own function because the bag and the shop name the same four kinds and would otherwise each
 * carry their own four-branch `when` — and because a **card item cannot name itself**: its label is
 * the card's name, which lives in the catalogue and not in the bag entry (see [CardItem]).
 *
 * `CardItem.as:19-22` composes the label as `STR_CARD` plus the card's name, in an order that
 * differs between English and French. That composition is not reproduced: the card's own name is
 * enough, and a word-order rule expressed as an `if (language == 'fr_FR')` inside a display class
 * is exactly what a `{0}` placeholder exists to replace — but no such string is in the imported
 * bundles to use, so the plain name it is.
 *
 * @param cards the profile's collection, by id. An id that names nothing yields `#id`, which is
 *   visible and greppable rather than blank: a bag holding an id outside its own collection is a
 *   corrupt save, not a state worth hiding.
 */
internal fun itemName(strings: Strings, item: Item, cards: Map<Int, Card>): String = when (item) {
    is CardItem -> cards[item.cardId]?.let { strings[it.nameKey] } ?: "#${item.cardId}"
    is BoosterItem -> strings[item.boosterType.nameKey]
    is PotionItem -> strings[item.potionType.nameKey]
    is MiscItem -> strings[StringKeys.UNKNOWN_ITEM]
}

/**
 * A stable identity for a bag entry, ignoring how many are held.
 *
 * A row's selection has to survive the stack changing under it — selling one of three must leave
 * the same row selected — and [Item] is a data class whose `equals` includes [Item.stack]. This is
 * the same key [com.tripletriad.data.Inventory] stacks on, for the same reason.
 */
internal fun itemKey(item: Item): Item = item.withStack(1)

/**
 * A short printable identity: `card-13`, `booster-GOLD`, `potion-MGP`, `misc`.
 *
 * What a `LazyColumn` key and a test tag need, which [itemKey] cannot be — it is an object. Derived
 * from the same fields, so two entries share a slug exactly when they would share a stack.
 *
 * The AS3 addressed a row by its **position in `BAG`** (`Item.bagIndex`, assigned during
 * `refreshList`) and that is the one thing not reproduced: selling from a stack re-sorts the bag,
 * so the index a handler captured could name a different item by the time it ran.
 */
internal fun itemSlug(item: Item): String = when (item) {
    is CardItem -> "card-${item.cardId}"
    is BoosterItem -> "booster-${item.boosterType.name}"
    is PotionItem -> "potion-${item.potionType.name}"
    is MiscItem -> "misc"
}

/** The card a [CardItem] stands for, or null for anything else. Null too if the id names none. */
internal fun itemCard(item: Item, cards: Map<Int, Card>): Card? =
    (item as? CardItem)?.let { cards[it.cardId] }

/**
 * Which shipped icon to draw for [item] — [Item.iconId], except where the atlas disagrees with it.
 *
 * ### The one item kind whose own name finds nothing
 *
 * `PotionItem.iconId` is `potionItem`, which is the AS3 texture name (`PotionItem.as:36`, camelCase
 * where every other one is not) and is **in no shipped icon folder**: `tools/import_ui_art.py`
 * copies `xp_boost_icon` and `mgp_boost_icon`, which are the same two pictures under the names the
 * FFXIV art uses. So every potion in the game drew an empty plate — in the bag, on the shop shelf
 * and in the list of what a match dropped — while the file it wanted sat beside the ones that
 * worked, differently named.
 *
 * Reconciled here rather than in [com.tripletriad.model.PotionItem], for the reason [AchievementIcon]
 * gives about `ac-fob`: which texture is shipped under which name is the UI's business, and `:core`
 * is right to carry the name the original used. Reconciled *once* rather than at the three call
 * sites, because three copies of a mapping is three chances for one of them to be missed — which is
 * how this was missed in the first place.
 *
 * Keyed on the boon rather than on the six potion types: the art has two pictures, one per boon,
 * and a `when` over the types would be six lines saying the same two things.
 */
internal fun itemIconId(item: Item): String = when (item) {
    is PotionItem -> when (item.potionType.modifier.type) {
        BoonType.XP -> "xp_boost_icon"
        BoonType.MGP -> "mgp_boost_icon"
    }

    else -> item.iconId
}

/**
 * What the row should add about [item], or null when there is nothing to add.
 *
 * ### This used to be `useRefusal`, and the refusal is gone
 *
 * `InventoryScreen.as:111-113` disables Use for a card already in the collection, after enabling it
 * from `item.useable` — the flag said yes and the screen said no. That was right while cards were a
 * set: using the item would have consumed it to grant something the profile already had, and
 * `Inventory.use` would happily have done it.
 *
 * A card can be owned several times now (§ 1 of
 * `docs/migration/20-CARD-COPIES-AND-PLATFORM-ACCOUNTS.md`), so a second copy is exactly what the
 * player wants out of that item, and refusing would withhold the only thing that made a duplicate
 * worth keeping. What the player still needs is the *fact* — that this is not their first copy —
 * so the same slot on the row now says how many are held instead of why Use is dead.
 */
internal fun ownedNote(strings: Strings, item: Item, owned: Map<Int, Int>): String? {
    val copies = (item as? CardItem)?.let { owned[it.cardId] } ?: 0
    return if (copies > 0) "${strings[StringKeys.ALREADY_OWNED]} $COPIES_PREFIX$copies" else null
}

/** The multiplication sign, not the letter x — it sits beside a numeral. */
private const val COPIES_PREFIX = "\u00d7"
