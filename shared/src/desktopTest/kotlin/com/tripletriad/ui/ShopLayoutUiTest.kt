package com.tripletriad.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tripletriad.data.ShopCatalog
import com.tripletriad.data.ShopOffer
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
import com.tripletriad.storage.InMemoryDocumentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shop's two arrangements — rows and a sheet on a phone, a grid and a pane beside it on
 * anything wider — and what the purchase itself now says about a pack and the purse.
 *
 * `ShopUiTest` runs in the default 1024 dp window, which is the wide arrangement; the phone half
 * is here, at 390 × 844.
 */
@OptIn(ExperimentalTestApi::class)
class ShopLayoutUiTest {
    private fun ComposeUiTest.openShop(documents: InMemoryDocumentStore) {
        loadCharacter(documents)
        openFromBar("store", SHOP_LIST_TEST_TAG)
    }

    private fun ComposeUiTest.lineOf(tag: String): String =
        onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

    private fun ComposeUiTest.bounds(tag: String) =
        onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun aPhoneListsItsPacksOneToALineAndClosesTheSheetOnABuy() = runSkikoComposeUiTest(
        size = Size(PHONE_WIDTH, PHONE_HEIGHT),
        density = Density(1f),
    ) {
        val documents = seeded(freshSave().copy(mgp = ENOUGH_FOR_ANY_PACK))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        val first = bounds(shopOfferTestTag(BRONZE))
        val second = bounds(shopOfferTestTag(SILVER))
        assertEquals(first.left, second.left, "two packs side by side: still a grid of tiles")
        assertTrue(second.top >= first.bottom, "the second pack is not under the first")
        assertTrue(
            first.bottom - first.top <= RowHeightCeiling,
            "a pack row is ${first.bottom - first.top} tall — a tile, not a row",
        )

        onNodeWithTag(shopOfferTestTag(BRONZE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(SHOP_SHEET_TEST_TAG) }
        onNodeWithTag(SHOP_BUY_TEST_TAG, useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !existsUnmerged(SHOP_SHEET_TEST_TAG) }
    }

    @Test
    fun aWideShopShowsThePickInAPaneBesideTheShelfAndNoSheet() = runComposeUiTest {
        val documents = seeded(freshSave().copy(mgp = ENOUGH_FOR_ANY_PACK))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        assertTrue(exists(SHOP_PANE_TEST_TAG), "no pane before a pick")
        assertTrue(isVisible("Pick an offer to see what it holds"), "the empty pane says nothing")
        assertFalse(existsUnmerged(SHOP_OFFER_DETAIL_TEST_TAG))

        onNodeWithTag(shopOfferTestTag(BRONZE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(SHOP_OFFER_DETAIL_TEST_TAG) }

        assertFalse(existsUnmerged(SHOP_SHEET_TEST_TAG), "the wide shop opened the sheet as well")
        assertTrue(
            bounds(SHOP_PANE_TEST_TAG).left >= bounds(SHOP_LIST_TEST_TAG).right,
            "the pane is not beside the shelf",
        )
    }

    @Test
    fun aPicksPoolTicksWhatIsHeldAndHidesWhatIsNot() = runComposeUiTest {
        val pool = BoosterType.BRONZE.pool
        val held = pool.first()
        val documents =
            seeded(freshSave().copy(mgp = ENOUGH_FOR_ANY_PACK, cards = mapOf(held to 1)))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        onNodeWithTag(shopOfferTestTag(BRONZE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(SHOP_OFFER_DETAIL_TEST_TAG) }

        val save = storedSave(documents)
        val missing = pool.filterNot(save::ownsCard)
        check(missing.isNotEmpty()) { "the fixture needs a pool with something left to find" }
        assertEquals(
            "${missing.size} of ${pool.size} missing",
            lineOf(shopPoolCountTestTag(BRONZE)),
        )
        assertTrue(existsUnmerged(shopPoolOwnedTestTag(held)), "a held card is not ticked")
        assertTrue(existsUnmerged(unknownCardTestTag(missing.first())), "a missing card is shown")
        assertFalse(existsUnmerged(shopPoolOwnedTestTag(missing.first())))
    }

    @Test
    fun theBalanceSaysWhatIsLeftAfterTheBuy() = runComposeUiTest {
        val purse = MID_PURSE
        val documents = seeded(freshSave().copy(mgp = purse))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openShop(documents)

        onNodeWithTag(shopShelfTestTag("boons")).performClick()
        val potion = ShopCatalog.ff14.first { it.item == PotionItem(PotionType.MGP) }
        check(potion.price <= purse) { "the fixture needs a boon the purse reaches" }
        onNodeWithTag(shopOfferTestTag(potion)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(shopBalanceAfterTestTag(potion)) }

        assertEquals(grouped(purse - potion.price), lineOf(shopBalanceAfterTestTag(potion)))
        assertFalse(existsUnmerged(shopShortTestTag(potion)), "a reachable price has no gap")
    }

    private companion object {
        // Only the slug reaches a tag, and an offer must cost something.
        val BRONZE = ShopOffer(BoosterItem(BoosterType.BRONZE), price = 1)
        val SILVER = ShopOffer(BoosterItem(BoosterType.SILVER), price = 1)

        const val PHONE_WIDTH = 390f
        const val PHONE_HEIGHT = 844f
        const val ENOUGH_FOR_ANY_PACK = 200_000
        const val MID_PURSE = 1_000

        /** The mockup's 78 dp and some slack for the font; a tile was 145. */
        val RowHeightCeiling = 90.dp
    }
}
