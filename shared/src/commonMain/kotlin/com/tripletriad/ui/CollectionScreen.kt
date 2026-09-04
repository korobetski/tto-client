package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

const val COLLECTION_TABS_TEST_TAG: String = "collection-tabs"

const val COLLECTION_NOTE_TEST_TAG: String = "collection-note"

internal enum class CollectionTab {
    CARDS,

    DECKS,
}

@Composable
internal fun CollectionScreen(
    profile: GameSave,
    catalog: CardCatalog,
    format: Format,
    // Only so a card can say who drops it — see [cardSources]. Null before the roster has loaded,
    // which costs the drop lines and nothing else.
    opponents: NpcCatalog?,
    initial: CollectionTab,
    onPersist: suspend (GameSave) -> Unit,
    onIntent: suspend (Intent) -> IntentOutcome,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    var tab by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val note = rememberNoteHost(COLLECTION_NOTE_TEST_TAG)

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.CARDS],
        onBack = { if (editing != null) editing = null else onBack() },
        snackbar = note,
        // The one screen that lays out two panes, so the one that asks for the wider column. See
        // [WideContentMaxWidth] for why the other screens do not get it for free.
        wide = true,
    ) {
        ScreenTabs(
            tabs = listOf(
                strings[StringKeys.CARD_LIST] to screenTabTestTag("cards"),
                strings[StringKeys.CARD_DECKS] to screenTabTestTag("decks"),
            ),
            selected = tab.ordinal,
            onSelect = { index -> tab = CollectionTab.entries[index] },
            modifier = Modifier.testTag(COLLECTION_TABS_TEST_TAG),
        )

        when (tab) {
            CollectionTab.CARDS -> CardListBody(
                profile = profile,
                catalog = catalog,
                format = format,
                opponents = opponents,
                // The browser writes now: a spare copy can be sold from it. Not through
                // `onPersist`, which the deck editor still uses — a sale moves **money**, so it
                // is an intent the server carries out rather than a profile the client hands in.
                onIntent = onIntent,
                // And the answer needs somewhere to land, which is the scaffold's snackbar and not
                // the tab: a refusal that arrives after the player has switched to the decks is
                // still an answer to the tap they made.
                note = note,
            )

            CollectionTab.DECKS -> DecksBody(
                profile = profile,
                catalog = catalog,
                format = format,
                editing = editing,
                onEdit = { editing = it },
                onPersist = onPersist,
            )
        }
    }
}
