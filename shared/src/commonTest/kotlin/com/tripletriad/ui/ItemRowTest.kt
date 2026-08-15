package com.tripletriad.ui

import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.Card
import com.tripletriad.model.CardItem
import com.tripletriad.model.MiscItem
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How a bag entry names, keys and pictures itself — the four small functions three screens share.
 *
 * ### Why these are worth their own file
 *
 * Because they are the parts of the bag that a screen test reaches only through the two item kinds
 * a fixture happens to hold. Every one of them has a branch for something a fixture does not
 * normally contain — a card the catalogue cannot resolve, an item type this build does not know —
 * and those are exactly the branches that exist for a save that has gone wrong. A row that renders
 * a corrupt entry as a blank line is how a corrupt entry stays undiscovered.
 *
 * `commonTest` and not a Compose test: none of this composes anything, and a test that had to lay
 * out a screen to ask what a card item is called would be slower and would fail for other reasons.
 */
class ItemRowTest {

    // ---- Names ------------------------------------------------------------

    @Test
    fun aCardItemIsCalledAfterItsCard() {
        assertEquals("Dodo", itemName(strings, CardItem(DODO), cards))
    }

    /**
     * A card the catalogue cannot resolve is named `#id`, which is visible and greppable.
     *
     * A bag holding an id outside the table it is read against is a corrupt save, not a state worth
     * hiding — and a blank label would hide it. The id is what anybody diagnosing it needs.
     */
    @Test
    fun aCardItemWhoseCardIsUnknownIsNamedByItsId() {
        assertEquals("#4321", itemName(strings, CardItem(4321), cards))
    }

    @Test
    fun aPackAndAPotionAreCalledWhatTheirTypeIs() {
        assertEquals("Bronze pack", itemName(strings, BoosterItem(BoosterType.BRONZE), cards))
        assertEquals("MGP potion", itemName(strings, PotionItem(PotionType.MGP), cards))
    }

    /** An item type this build does not know still has a row and still says what it is. */
    @Test
    fun anUnknownItemSaysThatIsWhatItIs() {
        assertEquals("Unknown item", itemName(strings, MiscItem(), cards))
    }

    // ---- Identity ---------------------------------------------------------

    /** The stack is not part of the identity, which is what keeps a row selected as it empties. */
    @Test
    fun theKeyIgnoresHowManyAreHeld() {
        assertEquals(itemKey(CardItem(DODO, stack = 1)), itemKey(CardItem(DODO, stack = 9)))
        assertEquals(CardItem(DODO, stack = 1), itemKey(CardItem(DODO, stack = 9)))
    }

    @Test
    fun eachKindHasItsOwnSlug() {
        assertEquals("card-$DODO", itemSlug(CardItem(DODO)))
        assertEquals("booster-BRONZE", itemSlug(BoosterItem(BoosterType.BRONZE)))
        assertEquals("potion-MGP", itemSlug(PotionItem(PotionType.MGP)))
        assertEquals("misc", itemSlug(MiscItem()))
    }

    /** Two entries share a slug exactly when they would share a stack — the list key's contract. */
    @Test
    fun twoEntriesShareASlugOnlyWhenTheyShareAStack() {
        assertEquals(itemSlug(CardItem(DODO, stack = 2)), itemSlug(CardItem(DODO)))
        assertEquals(
            itemSlug(CardItem(DODO)) == itemSlug(CardItem(DODO + 1)),
            itemKey(CardItem(DODO)) == itemKey(CardItem(DODO + 1)),
        )
    }

    // ---- Pictures ---------------------------------------------------------

    /**
     * A card item resolves to its card, and everything else to none.
     *
     * What decides whether a row draws a card or an icon.
     */
    @Test
    fun onlyACardItemResolvesToACard() {
        assertEquals(cards.getValue(DODO), itemCard(CardItem(DODO), cards))
        assertNull(itemCard(CardItem(4321), cards), "an unresolvable id is not a card")
        assertNull(itemCard(BoosterItem(BoosterType.BRONZE), cards))
        assertNull(itemCard(PotionItem(PotionType.MGP), cards))
    }

    /**
     * A potion is pictured by its **boon**, not by the name the model carries.
     *
     * See [itemIconId]: `PotionItem.iconId` is the AS3 texture name and nothing ships under it.
     * `UiArtTest` proves both of these are in the bundle; this proves which one each potion asks
     * for.
     */
    @Test
    fun aPotionIsPicturedByWhichBoonItRaises() {
        assertEquals("mgp_boost_icon", itemIconId(PotionItem(PotionType.MGP)))
        assertEquals("mgp_boost_icon", itemIconId(PotionItem(PotionType.BIG_MGP)))
        assertEquals("xp_boost_icon", itemIconId(PotionItem(PotionType.SMALL_XP)))
    }

    /** Everything else keeps the name its own model gives. */
    @Test
    fun everyOtherKindKeepsItsOwnIconName() {
        val pack = BoosterItem(BoosterType.BEAST)
        assertEquals(pack.iconId, itemIconId(pack))
        assertEquals(MiscItem().iconId, itemIconId(MiscItem()))
        assertEquals(CardItem(DODO).iconId, itemIconId(CardItem(DODO)))
    }

    // ---- The duplicate note -----------------------------------------------

    /** Only a card item can be a duplicate, and only when the collection already holds one. */
    @Test
    fun theRowSaysHowManyCopiesAreAlreadyHeld() {
        assertEquals("already owned ×2", ownedNote(strings, CardItem(DODO), mapOf(DODO to 2)))
        assertNull(ownedNote(strings, CardItem(DODO), emptyMap()), "the first copy is not a note")
        assertNull(ownedNote(strings, PotionItem(PotionType.MGP), mapOf(DODO to 2)))
    }

    // ---- Fixtures ---------------------------------------------------------

    /** Only the keys these functions look up, so a wording change elsewhere cannot fail this. */
    private val strings = Strings(
        AppLocale.EN_US,
        mapOf(
            "STR_CARD_$DODO" to "Dodo",
            BoosterType.BRONZE.nameKey to "Bronze pack",
            PotionType.MGP.nameKey to "MGP potion",
            StringKeys.UNKNOWN_ITEM to "Unknown item",
            StringKeys.ALREADY_OWNED to "already owned",
        ),
        emptyMap(),
    )

    private val cards: Map<Int, Card> = listOf(DODO, DODO + 1).associateWith { id ->
        Card(
            id = id,
            nameKey = "STR_CARD_$id",
            name = "Card $id",
            top = 1,
            right = 1,
            bottom = 1,
            left = 1,
            rarity = 1,
        )
    }

    private companion object {
        /** An ff14 id, so [Card.nameKey] is the `STR_CARD_<id>` the fixture above defines. */
        const val DODO = 257
    }
}
