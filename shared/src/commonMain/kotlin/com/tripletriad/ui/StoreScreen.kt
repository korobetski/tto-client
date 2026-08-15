package com.tripletriad.ui

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

/** The tab bar of the store screen — what is for sale, and what has been bought. */
const val STORE_TABS_TEST_TAG: String = "store-tabs"

/** The two halves of the store screen, in the order they are shown. */
internal enum class StoreTab {
    /** The shelf, and the Buy button under it. */
    SHOP,

    /** The bag, and what can be done with what is in it. */
    BAG,
}

/**
 * The shop and the bag, on one screen with two tabs.
 *
 * They are the two ends of one transaction — a pack is bought on the first tab and opened on the
 * second — and the original made that a trip through the dashboard, which is why its own shop ends
 * on a commented-out save: nothing about the flow encouraged looking at what you had just bought.
 * Buy, switch tab, open: the pack never leaves the screen.
 *
 * ### The two things this screen owns rather than its tabs
 *
 * - **Which offer is selected**, because the Buy button is in the app bar's bottom bar and not in
 *   the shelf. A button that commits has to be reachable without scrolling the list it commits
 *   from, and the snackbar that confirms the purchase is drawn above it — see [ScreenScaffold].
 * - **The unlocked-card reveal**, because [UnlockedCard] covers the whole screen and a tab is not
 *   one. [InventoryBody] reports the card upwards instead of drawing it.
 *
 * @param initial which tab to open on; the screen keeps its own selection from then on.
 * @param onUseItem consumes a bag item, threaded to [InventoryBody] — see there.
 */
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

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.SHOP],
        onBack = onBack,
        snackbar = note,
        bottomBar = if (tab != StoreTab.SHOP) {
            null
        } else {
            {
                WideButton(
                    label = selected
                        ?.let { "${strings[StringKeys.BUY]}$DOT_SEPARATOR${it.price}" }
                        ?: strings[StringKeys.BUY],
                    tag = SHOP_BUY_TEST_TAG,
                    enabled = selected?.isAffordableBy(profile) == true,
                    onClick = {
                        val offer = selected ?: return@WideButton
                        val bought = itemName(strings, offer.item, cards)
                        scope.launch {
                            // Asked, not computed. On an account the price is the server's and the
                            // profile that comes back is the one it wrote — see `BuyRequest`.
                            val outcome = onIntent(Intent.Buy(offer, format.id))
                            // After the write, not before: the note says the purchase happened,
                            // and a line shown while the save was in flight would be a promise.
                            //
                            // **And only if it did.** This line used to be unconditional, so a
                            // purchase the server declined — or one that never reached it —
                            // announced the item anyway, over a purse that had not moved. The
                            // button's `isAffordableBy` guard is the client's opinion about the
                            // client's price; the server holds both.
                            note.show(boughtNote(strings, outcome, bought))
                        }
                    },
                )
            }
        },
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

    // Outside the scaffold, so it is drawn over the whole screen rather than inside the column
    // that lists the bag.
    unlocked?.let { card ->
        UnlockedCard(card = card) { unlocked = null }
    }
}

/**
 * What the shelf says after a Buy or a Claim — three answers where there used to be one.
 *
 * The snackbar is the only account this screen gives of a purchase, and it announced the item
 * unconditionally. So a refused purchase and a request that never left said the same thing as a
 * successful one, over a purse and a bag that had not changed. The bag's own version of this is
 * `sellNote`, and the split is the same one [IntentOutcome] draws.
 *
 * @param bought what the player asked for, named. Only used when they actually got it.
 */
private fun boughtNote(strings: Strings, outcome: IntentOutcome, bought: String): String =
    when (outcome) {
        IntentOutcome.APPLIED -> strings.format(StringKeys.OBTAINED, bought)
        IntentOutcome.REFUSED -> strings[StringKeys.NOTHING_HAPPENED]
        IntentOutcome.UNREACHABLE -> strings[StringKeys.ACTION_FAILED]
    }
