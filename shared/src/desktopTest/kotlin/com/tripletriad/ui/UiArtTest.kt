package com.tripletriad.ui

import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiArtTest {
    private val art = runBlocking { loadUiArt() }

    @Test
    fun everyCardInBothCollectionsHasAThumbnail() {
        val cards = runBlocking { loadCardCatalog() }.all
        val missing = cards.filter { art.thumb(it) == null }

        assertTrue(
            missing.isEmpty(),
            "${missing.size} cards have no thumbnail: " +
                missing.take(MISSING_TO_REPORT).joinToString { "${it.textureId} (${it.name})" },
        )
    }

    @Test
    fun everyAchievementBadgeResolves() {
        val missing = AchievementCatalog.all
            .map { it.iconId }
            .distinct()
            .filter { art.icon(it) == null && thumbTextureId(it)?.let(art::thumb) == null }

        assertTrue(missing.isEmpty(), "no artwork for: ${missing.joinToString()}")
    }

    @Test
    fun everyBoosterPackHasItsIcon() {
        for (booster in BoosterType.entries) {
            assertNotNull(art.icon(booster.iconId), "no icon for ${booster.name}")
        }
    }

    @Test
    fun aPotionIsDrawnRatherThanFetched() {
        for (potion in PotionType.entries) {
            val item = PotionItem(potion)
            assertNotNull(boonOf(item), "no boon for ${potion.name}, so nothing to draw")
            assertNull(art.icon(itemIconId(item)), "a potion should not be asking for a bitmap")
        }
    }

    @Test
    fun theUnresolvedCardItemPlaceholderIsInTheBundle() {
        assertNotNull(art.icon(itemIconId(CardItem(cardId = 1))), "CardItem's own fallback plate")
    }

    @Test
    fun theDefaultAvatarIsInTheBundle() {
        val avatar = runBlocking { art.avatar(GameSave.DEFAULT_AVATAR) }
        assertNotNull(avatar, "the avatar every new character starts with")
    }

    @Test
    fun everyOpponentHasAPortraitExceptTheElevenThatShipNone() {
        val ids = runBlocking { loadNpcCatalog() }.all.map { it.iconId }.distinct().sorted()
        val missing = ids.filter { runBlocking { art.portrait(it) } == null }

        assertEquals(WITHOUT_PORTRAITS, missing, "the opponents with no 50px portrait")
    }

    private companion object {
        const val MISSING_TO_REPORT = 8

        val WITHOUT_PORTRAITS = listOf(
            "club", "diamond", "dobe", "flo", "heart", "jack", "jocker",
            "ma-dincht", "piet", "spade", "trepies",
        )
    }
}
