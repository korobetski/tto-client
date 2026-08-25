package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ArtworkUiTest {
    private val art = runBlocking { loadUiArt() }

    @Test
    fun theRecordShowsTheCharactersAvatar() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_STATS_TEST_TAG, STATS_TABLE_TEST_TAG)

        assertTrue(existsUnmerged(AVATAR_TEST_TAG), "the record should show the avatar")
    }

    @Test
    fun theCollectionGridDrawsThumbnails() = runComposeUiTest {
        val card = runBlocking { loadCardCatalog() }.all
            .first { it.id == STARTER_CARDS.first() }
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)

        assertTrue(
            existsUnmerged(thumbTestTag(card.textureId)),
            "the grid should draw ${card.name} as its thumbnail",
        )
    }

    @Test
    fun theOpponentListShowsPortraits() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()
        scrollToOpponent(TEST_OPPONENT)

        assertTrue(
            existsUnmerged(portraitTestTag(TEST_OPPONENT)),
            "the row for $TEST_OPPONENT should carry its portrait",
        )
    }

    @Test
    fun aDeckSlotFillsWithAThumbnailWhenACardIsPicked() = runComposeUiTest {
        val first = STARTER_CARDS.first()
        val card = runBlocking { loadCardCatalog() }.all.first { it.id == first }
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        onNodeWithTag(deckPickTestTag(first)).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(thumbTestTag(card.textureId)) }
    }

    @Test
    fun anOpponentWithNoPortraitIsDrawnAsAMonogram() = runComposeUiTest {
        val npc = runBlocking { loadNpcCatalog() }.all.first { it.iconId == "jack" }

        setContent {
            CompositionLocalProvider(LocalUiArt provides art) {
                TripleTriadTheme {
                    NpcPortrait(npc = npc, name = "Jack")
                }
            }
        }

        waitForIdle()
        assertTrue(existsUnmerged(portraitTestTag("jack")), "the plate is drawn either way")
        assertVisible("J", "a portrait-less opponent shows their initial")
    }
}
