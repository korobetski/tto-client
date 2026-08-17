package com.tripletriad.ui

import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.BoonType
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

class ItemRowTest {

    // ---- Names ------------------------------------------------------------

    @Test
    fun aCardItemIsCalledAfterItsCard() {
        assertEquals("Dodo", itemName(strings, CardItem(DODO), cards))
    }

    @Test
    fun aCardItemWhoseCardIsUnknownIsNamedByItsId() {
        assertEquals("#4321", itemName(strings, CardItem(4321), cards))
    }

    @Test
    fun aPackAndAPotionAreCalledWhatTheirTypeIs() {
        assertEquals("Bronze pack", itemName(strings, BoosterItem(BoosterType.BRONZE), cards))
        assertEquals("MGP potion", itemName(strings, PotionItem(PotionType.MGP), cards))
    }

    @Test
    fun anUnknownItemSaysThatIsWhatItIs() {
        assertEquals("Unknown item", itemName(strings, MiscItem(), cards))
    }

    // ---- Identity ---------------------------------------------------------

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

    @Test
    fun twoEntriesShareASlugOnlyWhenTheyShareAStack() {
        assertEquals(itemSlug(CardItem(DODO, stack = 2)), itemSlug(CardItem(DODO)))
        assertEquals(
            itemSlug(CardItem(DODO)) == itemSlug(CardItem(DODO + 1)),
            itemKey(CardItem(DODO)) == itemKey(CardItem(DODO + 1)),
        )
    }

    // ---- Pictures ---------------------------------------------------------

    @Test
    fun onlyACardItemResolvesToACard() {
        assertEquals(cards.getValue(DODO), itemCard(CardItem(DODO), cards))
        assertNull(itemCard(CardItem(4321), cards), "an unresolvable id is not a card")
        assertNull(itemCard(BoosterItem(BoosterType.BRONZE), cards))
        assertNull(itemCard(PotionItem(PotionType.MGP), cards))
    }

    @Test
    fun aPotionIsPicturedByWhichBoonItRaises() {
        assertEquals(BoonType.MGP, boonOf(PotionItem(PotionType.MGP)))
        assertEquals(BoonType.MGP, boonOf(PotionItem(PotionType.BIG_MGP)))
        assertEquals(BoonType.XP, boonOf(PotionItem(PotionType.SMALL_XP)))
    }

    @Test
    fun noOtherKindIsPicturedAsABoon() {
        assertNull(boonOf(BoosterItem(BoosterType.BEAST)))
        assertNull(boonOf(MiscItem()))
        assertNull(boonOf(CardItem(DODO)))
    }

    @Test
    fun everyOtherKindKeepsItsOwnIconName() {
        val pack = BoosterItem(BoosterType.BEAST)
        assertEquals(pack.iconId, itemIconId(pack))
        assertEquals(MiscItem().iconId, itemIconId(MiscItem()))
        assertEquals(CardItem(DODO).iconId, itemIconId(CardItem(DODO)))
    }

    // ---- The duplicate note -----------------------------------------------

    @Test
    fun theRowSaysHowManyCopiesAreAlreadyHeld() {
        assertEquals("already owned ×2", ownedNote(strings, CardItem(DODO), mapOf(DODO to 2)))
        assertNull(ownedNote(strings, CardItem(DODO), emptyMap()), "the first copy is not a note")
        assertNull(ownedNote(strings, PotionItem(PotionType.MGP), mapOf(DODO to 2)))
    }

    // ---- Fixtures ---------------------------------------------------------

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
        const val DODO = 257
    }
}
