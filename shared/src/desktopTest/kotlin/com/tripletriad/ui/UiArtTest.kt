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
    fun everyOpponentHasAPortraitExceptTheOnesThatShipNone() {
        val ids = runBlocking { loadNpcCatalog() }.all.map { it.iconId }.distinct().sorted()
        val missing = ids.filter { runBlocking { art.portrait(it) } == null }

        assertEquals(
            (WITHOUT_PORTRAITS + AWAITING_PORTRAIT).sorted(),
            missing,
            "the opponents with no 50px portrait",
        )
    }

    /**
     * **A reported gap, not an accepted one.**
     *
     * [AWAITING_PORTRAIT] is held apart from [WITHOUT_PORTRAITS] so that folding the two can never
     * happen by accident: one is a set of opponents that ship no art by design, the other is art
     * this repository is *missing*, and a single list would stop telling them apart the moment
     * somebody appended to it.
     */
    @Test
    fun theMissingPortraitsAreAKnownImportGapAndNotADesignChoice() {
        assertEquals(
            AWAITING_PORTRAIT_COUNT,
            AWAITING_PORTRAIT.size,
            "close this gap by importing the art, not by lengthening the list",
        )
    }

    private companion object {
        const val MISSING_TO_REPORT = 8

        /**
         * Opponents that ship no portrait **by design** — the four suits and the face cards among
         * them are placeholders rather than people.
         */
        val WITHOUT_PORTRAITS = listOf(
            "club", "diamond", "dobe", "flo", "heart", "jack", "jocker",
            "ma-dincht", "piet", "spade", "trepies",
        )

        /**
         * Opponents whose portrait has **not been imported yet**. A defect, recorded rather than
         * repaired here: `npcs.json` and the art under `composeResources/files/art/` are generated,
         * and CLAUDE.md is explicit that the importer is what changes them.
         *
         * They arrived with the FFXIV expansion, which added 75 opponents and only 38 portraits —
         * 158 opponents are declared against 112 files in `art/npcs/`. Each of these draws with the
         * fallback plate instead of a face, which is why the count is asserted separately: the
         * right way to shrink this list is to re-run the importer for the missing art.
         */
        val AWAITING_PORTRAIT = listOf(
            "aiglephine", "bruk-noq", "celia", "cheatingway", "cobleva", "droyn", "gamingway",
            "gavoll-ja", "ghasa", "glynard", "hachinan", "hume-black-mage", "ironworks-hand",
            "kilfufu", "larisa", "luwyawa", "maillart", "malevolent-weasel", "mehryde",
            "mero-roggo", "miitso", "nyikweni", "pawkukwe", "prudence", "pudeel-ja", "qetanur",
            "ruissenaud", "sladkey", "tokimori", "uataaye", "ushiogi", "warsowok", "wopli",
            "worldly-imperial", "ylaire",
        )

        const val AWAITING_PORTRAIT_COUNT = 35
    }
}
