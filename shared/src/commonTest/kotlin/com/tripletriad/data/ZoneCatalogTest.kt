package com.tripletriad.data

import com.tripletriad.model.Availability
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.ItemReward
import com.tripletriad.model.Npc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * How a place opens, clears and completes — on a made-up map, so these say what the rule is and
 * not what `zones.json` holds. The shipped map is `ZoneBundleTest`'s.
 */
class ZoneCatalogTest {
    private val start = Zone("start", "S", npcs = listOf("a", "b", "night", "badge"))
    private val left = Zone("left", "L", after = listOf("start"), npcs = listOf("c"))
    private val right = Zone("right", "R", after = listOf("start"), npcs = listOf("d"))
    private val both = Zone("both", "B", after = listOf("left", "right"), npcs = listOf("e"))
    private val either =
        Zone("either", "E", after = listOf("left", "right"), afterAny = true, npcs = listOf("f"))
    private val nobody = Zone("nobody", "N", npcs = listOf("absent"))
    private val zones = ZoneCatalog(listOf(start, left, right, both, either, nobody))

    private val roster = listOf(
        npc("a", difficulty = 2, dropping = WANTED_CARD),
        npc("b", difficulty = 1),
        npc("night", availability = Availability(begins = 20, ends = 4)),
        npc("badge", requiresAchievement = "ac-test"),
        npc("c"),
        npc("d"),
        npc("e"),
        npc("f"),
        npc("unzoned"),
    )
    private val cards = mapOf(WANTED_CARD to card(WANTED_CARD))

    @Test
    fun aNewCharacterHasOnlyTheStartingPlaces() {
        val status = statuses(save())

        assertEquals(ZoneStatus.OPEN, status["start"])
        for (id in listOf("left", "right", "both", "either")) {
            assertEquals(ZoneStatus.LOCKED, status[id], id)
        }
    }

    @Test
    fun aPlaceWithNobodyInTheRosterIsNotShownAtAll() {
        assertFalse("nobody" in statuses(save()))
    }

    @Test
    fun theOpenPlacesComeFirstEachInMapOrder() {
        val order = zones.progress(roster, save("a", "b", "c"), cards).map { it.zone.id }

        assertEquals(listOf("start", "left", "right", "either", "both"), order)
    }

    @Test
    fun theMembersComeEasiestFirst() {
        val start = zones.progress(roster, save(), cards).first { it.zone.id == "start" }
        assertEquals("b", start.members.first().iconId)
    }

    @Test
    fun opponentsWithHoursOrABadgeDoNotHoldTheWayShut() {
        val status = statuses(save("a", "b"))

        assertEquals(ZoneStatus.CLEARED, status["start"])
        assertEquals(ZoneStatus.OPEN, status["left"])
        assertEquals(ZoneStatus.OPEN, status["right"])
    }

    @Test
    fun onePlaceOfSeveralOpensAnAfterAnyPlace() {
        val status = statuses(save("a", "b", "c"))

        assertEquals(ZoneStatus.OPEN, status["either"])
        assertEquals(ZoneStatus.LOCKED, status["both"], "`after` without afterAny needs them all")
        assertEquals(ZoneStatus.OPEN, statuses(save("a", "b", "c", "d"))["both"])
    }

    @Test
    fun completeWantsEveryMemberBeatenAndNothingLeftToDrop() {
        val everyone = save("a", "b", "night", "badge")

        assertEquals(
            ZoneStatus.CLEARED,
            statuses(save("a", "b").withCard(WANTED_CARD))["start"],
            "nothing left to drop, but the timed are unbeaten",
        )
        assertEquals(
            ZoneStatus.CLEARED,
            statuses(everyone)["start"],
            "a still drops a missing card",
        )
        assertEquals(ZoneStatus.COMPLETE, statuses(everyone.withCard(WANTED_CARD))["start"])
    }

    @Test
    fun anOpenPlaceNamesWhatItsClearingOpensAndWhoStandsInTheWay() {
        val start = place(save("b"), "start")

        assertEquals(listOf("left", "right"), start.opens.map { it.id })
        assertEquals(1, start.left, "`night` and `badge` do not hold the way shut")
        assertEquals(2, start.members.size - start.beaten - start.left)
    }

    @Test
    fun aPlaceThatAlsoWaitsOnAnotherUnclearedOneIsNotNamed() {
        val cleared = save("a", "b")
        assertEquals(listOf("either"), place(cleared, "left").opens.map { it.id }, "`both` waits")

        val oneSide = save("a", "b", "c")
        assertEquals(listOf("both"), place(oneSide, "right").opens.map { it.id })
    }

    @Test
    fun aClearedOrShutPlaceOpensNothing() {
        val progress = zones.progress(roster, save("a", "b"), cards).associateBy { it.zone.id }

        assertEquals(emptyList(), progress.getValue("start").opens, "already cleared")
        assertEquals(emptyList(), progress.getValue("either").opens, "not open, so not named")
    }

    @Test
    fun theReachableRosterKeepsWhomNoPlaceNames() {
        val reachable = zones.reachable(roster, save(), cards).map { it.iconId }

        assertEquals(listOf("a", "b", "night", "badge", "unzoned"), reachable)
    }

    private fun statuses(save: GameSave): Map<String, ZoneStatus> =
        zones.progress(roster, save, cards).associate { it.zone.id to it.status }

    private fun place(save: GameSave, id: String): ZoneProgress =
        zones.progress(roster, save, cards).first { it.zone.id == id }

    private fun save(vararg beaten: String): GameSave =
        GameSave.new(createdAt = 0L).copy(npcWins = beaten.associateWith { 1 })

    private companion object {
        val WANTED_CARD = Card.idFor(block = 1, number = 42)
    }
}

internal fun npc(
    iconId: String,
    difficulty: Int = 1,
    availability: Availability = Availability(),
    requiresAchievement: String? = null,
    dropping: Int? = null,
): Npc = Npc(
    id = 1,
    nameKey = "STR_TEST_$iconId",
    iconId = iconId,
    difficulty = difficulty,
    availability = availability,
    requiresAchievement = requiresAchievement,
    itemRewards = listOfNotNull(
        dropping?.let { ItemReward(type = "card", rate = 1.0, cardId = it) },
    ),
)

internal fun card(id: Int): Card = Card(
    id = id,
    nameKey = "STR_TEST_CARD_$id",
    name = "Test $id",
    top = 1,
    right = 1,
    bottom = 1,
    left = 1,
    rarity = 1,
)
