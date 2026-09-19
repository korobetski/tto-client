package com.tripletriad.data

import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.Requirement
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The shipped map: `zones.json` against `npcs.json` and the four `app-*` bundles. */
class ZoneBundleTest {
    private val zones = runBlocking { loadZoneCatalog() }
    private val npcs = runBlocking { loadNpcCatalog() }
    private val campaigns = runBlocking { loadCampaignCatalog() }

    /**
     * `:core` carries its own copy of who must be beaten in each place (it cannot read this
     * file); this is what keeps the copy honest. Change the map, watch this fail, then change
     * `PlaceAchievements` in `tto-core`.
     */
    @Test
    fun clearingAPlaceAsksForExactlyTheOpponentsThatHoldItShut() {
        for (zone in zones.zones) {
            val clearing = assertNotNull(
                AchievementCatalog[AchievementCatalog.placeCleared(zone.id)],
                "${zone.id} has no clearing achievement",
            )
            val asked = assertIs<Requirement.NpcsBeaten>(clearing.requirement).iconIds
            val holding = zone.npcs.filter { icon ->
                npcs.all.filter { it.iconId == icon }.any { ZoneCatalog.blocks(it) }
            }
            assertEquals(holding, asked, zone.id)
        }
    }

    /**
     * Each place opens exactly one tournament, played against its own opponents, and clearing
     * it pays that tournament's fee — the first entry is on the house.
     */
    @Test
    fun everyPlaceOpensOneTournamentOfItsOwnOpponents() {
        for (zone in zones.zones) {
            val gate = AchievementCatalog.placeCleared(zone.id)
            val ladder = campaigns.all.filter { it.requiresAchievement == gate }
            assertEquals(1, ladder.size, "${zone.id}: ${ladder.map { it.key }}")
            val campaign = ladder.single()
            // The three ladders that predate the map were authored against the whole roster
            // (the Gold Saucer's has Elmer, who stands elsewhere); the rest are the place's own.
            if (campaign.key !in PREDATING_LADDERS) {
                val strangers = campaign.steps.map { it.npc.iconId } - zone.npcs.toSet()
                assertEquals(emptyList(), strangers, "${zone.id}: rungs from elsewhere")
            }
            assertEquals(campaign.fee, AchievementCatalog[gate]?.mgpReward, zone.id)
        }
        assertEquals(
            emptyList(),
            campaigns.all.filter { it.requiresAchievement == null }.map { it.key },
            "every ladder is a place's",
        )
    }

    /**
     * Nobody is missed and nobody stands in two places. A missing opponent would be met from the
     * first day on (see [ZoneCatalog.reachable]); a doubled one would make a zone's count lie.
     */
    @Test
    fun everyOpponentStandsInExactlyOnePlace() {
        val listed = zones.zones.flatMap { it.npcs }
        val roster = npcs.all.map { it.iconId }.toSet()

        assertEquals(
            listed.distinct(),
            listed,
            "listed twice: ${listed.groupBy { it }.filterValues { it.size > 1 }.keys}",
        )
        assertEquals(emptySet(), roster - listed.toSet(), "in no place")
        assertEquals(emptySet(), listed.toSet() - roster, "no such opponent")
    }

    @Test
    fun thePlacesOpenFromTheStartAreTheGoldSaucerAndBalamb() {
        assertEquals(
            setOf("gold-saucer", "balamb"),
            zones.zones.filter { it.isStarter }.map { it.id }.toSet(),
        )
    }

    @Test
    fun everyPlaceIsReachable() {
        val ids = zones.zones.map { it.id }.toSet()
        for (zone in zones.zones) {
            assertTrue(
                ids.containsAll(zone.after),
                "${zone.id} opens after an unknown place: ${zone.after}",
            )
        }

        // Opened by walking the map with every place's `after` rule, the way progress() does.
        val open = mutableSetOf<String>()
        do {
            val next = zones.zones.filter { it.id !in open && it.opensAfter(open) }
            open += next.map { it.id }
        } while (next.isNotEmpty())
        assertEquals(ids, open, "never opens")
    }

    @Test
    fun everyPlaceIsNamedInEveryLanguage() {
        for (locale in AppLocale.entries) {
            val strings = runBlocking { loadStrings(locale) }
            val missing = zones.zones.map { it.nameKey }.filterNot { it in strings.translatedKeys }
            assertEquals(emptyList(), missing, "$locale")
        }
    }

    /**
     * Every picture in `art/zones/` belongs to a place and was converted: 960x540 JPEG, which is
     * what the list's crop and the place banner are laid out for and what keeps twenty of them
     * light enough for the web build. The originals stay out of the resources, in `art-src/`.
     */
    @Test
    fun everyPlacePictureIsAConvertedJpegOfAKnownPlace() {
        val dir = File(ZONE_ART_DIR)
        val files = dir.listFiles().orEmpty().filter { it.isFile }
        val ids = zones.zones.map { it.id }.toSet()

        assertTrue(files.isNotEmpty(), "no picture in $dir")
        for (file in files) {
            assertEquals("jpg", file.extension, "${file.name} should be converted to JPEG")
            assertTrue(file.nameWithoutExtension in ids, "${file.name} names no place")
            val image = assertNotNull(ImageIO.read(file), "${file.name} does not decode")
            assertEquals(ART_WIDTH to ART_HEIGHT, image.width to image.height, file.name)
        }
    }

    private fun Zone.opensAfter(open: Set<String>): Boolean = when {
        isStarter -> true
        afterAny -> after.any { it in open }
        else -> after.all { it in open }
    }

    private companion object {
        val PREDATING_LADDERS = setOf("gs", "cc", "balamb")
        const val ZONE_ART_DIR = "src/commonMain/composeResources/files/art/zones"
        const val ART_WIDTH = 960
        const val ART_HEIGHT = 540
    }
}
