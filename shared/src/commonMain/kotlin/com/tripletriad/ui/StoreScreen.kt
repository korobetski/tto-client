package com.tripletriad.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.data.ShopCatalog
import com.tripletriad.data.ShopOffer
import com.tripletriad.data.StarterCatalog
import com.tripletriad.data.StarterPack
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.protocol.ItemEffect
import kotlinx.coroutines.launch

const val STORE_TABS_TEST_TAG: String = "store-tabs"

internal enum class StoreTab {
    SHOP,

    BAG,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
internal fun StoreScreen(
    profile: GameSave,
    catalog: CardCatalog,
    starters: StarterCatalog,
    format: Format,
    initial: StoreTab,
    onUseItem: suspend (Item) -> ItemEffect?,
    onIntent: suspend (Intent) -> IntentOutcome,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(initial) }

    // Keyed on the format, not the character: what is for sale is decided by which sets are in
    // play, which is what a format names. A potion is on every shelf — see `ShopCatalog.offers`.
    val cards = remember(catalog, format) {
        catalog.admittedBy(format).associateBy { it.id }
    }
    // Priced from the card table, not from a list of literals: a booster costs what it holds. See
    // `BoosterPricing`. The whole catalogue rather than `cards`, because a pack's pool may name a
    // card the format does not admit and it still has to be valued.
    val offers = remember(format, catalog) {
        ShopCatalog.offers(format, catalog.all.associateBy { it.id })
    }
    var selectedTag by remember(format) { mutableStateOf<String?>(null) }
    val selected = offers.firstOrNull { shopOfferTestTag(it) == selectedTag }
    val note = rememberNoteHost(SHOP_NOTE_TEST_TAG)

    // The card just drawn from a pack, while it is being shown off. `UnlockCardAnim` is the one
    // thing the original does in the bag that a line of text cannot: the player has often never
    // seen this card, and the note names it without showing it.
    var unlocked by remember(format) { mutableStateOf<Card?>(null) }
    val sheet = rememberModalBottomSheetState()

    // Hoisted out of the bottom bar that used to hold it: the button now lives in the sheet, and
    // the sheet is not the only thing that will ever want to spend the money.
    val buy: (ShopOffer) -> Unit = { offer ->
        val bought = itemName(strings, offer.item, cards)
        // Closed immediately rather than left open behind the note: `ModalBottomSheet` draws in
        // its own `Popup`, on top of the scaffold and everything in it, `snackbarHost` included —
        // so a note shown while the sheet stayed open was there, just hidden under it. Closing the
        // sheet is what lets the confirmation actually be seen.
        selectedTag = null
        scope.launch {
            // Asked, not computed. On an account the price is the server's and the profile that
            // comes back is the one it wrote — see `BuyRequest`.
            val outcome = onIntent(Intent.Buy(offer, format.id))
            // After the write, not before: the note says the purchase happened, and a line shown
            // while the save was in flight would be a promise.
            //
            // **And only if it did.** This line used to be unconditional, so a purchase the
            // server declined — or one that never reached it — announced the item anyway, over a
            // purse that had not moved. The button's `isAffordableBy` guard is the client's
            // opinion about the client's price; the server holds both.
            note.show(boughtNote(strings, outcome, bought))
        }
    }

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.SHOP],
        onBack = onBack,
        snackbar = note,
    ) {
        ScreenTabs(
            tabs = listOf(
                strings[StringKeys.CARD_SHOP] to screenTabTestTag("shop"),
                strings[StringKeys.INVENTORY] to screenTabTestTag("bag"),
            ),
            selected = tab.ordinal,
            onSelect = { index -> tab = StoreTab.entries[index] },
            modifier = Modifier.testTag(STORE_TABS_TEST_TAG),
        )

        when (tab) {
            StoreTab.SHOP -> ShopBody(
                profile = profile,
                offers = offers,
                cards = cards,
                starters = starters,
                selectedTag = selectedTag,
                onSelect = { selectedTag = it },
                // Read from the profile rather than from a flag on it, so the panel disappears the
                // moment the pack lands and comes back if a later build ever takes cards away.
                onClaimStarter = if (!StarterPack.isOwedBy(profile)) {
                    null
                } else {
                    {
                        scope.launch {
                            // Asked, not granted: the pack puts **cards** in the collection, and
                            // that is no longer a field this client may write. See
                            // `ClaimStarterRequest`.
                            val outcome = onIntent(Intent.ClaimStarter(starters))
                            note.show(
                                boughtNote(
                                    strings,
                                    outcome,
                                    strings[StringKeys.STARTER_PACK],
                                ),
                            )
                        }
                    }
                },
            )

            StoreTab.BAG -> InventoryBody(
                profile = profile,
                catalog = catalog,
                format = format,
                onUse = onUseItem,
                onIntent = onIntent,
                onUnlocked = { unlocked = it },
            )
        }
    }

    // Outside the scaffold for the same reason `unlocked` is: it covers the screen rather than
    // sitting in the column. Only on the shop tab — the bag has its own buttons and nothing to
    // sell.
    if (tab == StoreTab.SHOP) {
        selected?.let { offer ->
            ModalBottomSheet(
                onDismissRequest = { selectedTag = null },
                sheetState = sheet,
                modifier = Modifier.testTag(SHOP_SHEET_TEST_TAG),
            ) {
                ShopOfferSheet(
                    offer = offer,
                    cards = cards,
                    profile = profile,
                    onBuy = { buy(offer) },
                )
            }
        }
    }

    unlocked?.let { card ->
        UnlockedCard(card = card) { unlocked = null }
    }
}

private fun boughtNote(strings: Strings, outcome: IntentOutcome, bought: String): String =
    when (outcome) {
        IntentOutcome.APPLIED -> strings.format(StringKeys.OBTAINED, bought)
        IntentOutcome.REFUSED -> strings[StringKeys.NOTHING_HAPPENED]
        IntentOutcome.UNREACHABLE -> strings[StringKeys.ACTION_FAILED]
    }
