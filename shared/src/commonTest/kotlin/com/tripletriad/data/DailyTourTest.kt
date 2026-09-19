package com.tripletriad.data

import com.tripletriad.model.Availability
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Rivalry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DailyTourTest {
    /** Ten of each kind, so every reason has more candidates than its shortlist. */
    private val pool =
        (1..10).map { npc("fresh-$it", difficulty = it) } +
            (1..10).map { npc("wanted-$it", difficulty = it, dropping = CARD) } +
            (1..10).map { npc("timed-$it", difficulty = it, availability = Availability(8, 12)) } +
            (1..10).map { npc("rival-$it", difficulty = it) }
    private val cards = mapOf(CARD to card(CARD))
    private val save = GameSave.new(createdAt = CREATED).copy(
        npcWins = pool.map { it.iconId }
            .filter { it.startsWith("rival-") || it.startsWith("timed-") }
            .associateWith { 1 },
    )

    @Test
    fun theSameDayGivesTheSameTour() {
        assertEquals(tour(DAY), tour(DAY + HOUR * 20))
    }

    @Test
    fun anotherDayOrAnotherCharacterGivesAnotherTour() {
        val days = (0 until WEEKS_OF_DAYS).map { tour(DAY + it * DAY_MILLIS) }.toSet()
        assertTrue(days.size > 1, "a month of tours should not all be the same")

        val other = save.copy(creationDate = CREATED + 1)
        val theirs = (0 until WEEKS_OF_DAYS).map {
            DailyTour.picks(pool, other, cards, DAY + it * DAY_MILLIS)
        }
        assertNotEquals((0 until WEEKS_OF_DAYS).map { tour(DAY + it * DAY_MILLIS) }, theirs)
    }

    @Test
    fun threeDistinctOpponentsEachHoldingItsReason() {
        repeat(WEEKS_OF_DAYS) { day ->
            val picks = tour(DAY + day * DAY_MILLIS)

            assertEquals(DailyTour.SIZE, picks.size)
            assertEquals(picks.size, picks.map { it.npc.iconId }.distinct().size)
            assertEquals(picks.size, picks.map { it.reason }.distinct().size)
            for (pick in picks) {
                val wins = save.npcWins[pick.npc.iconId] ?: 0
                val holds = when (pick.reason) {
                    TourReason.WANTED -> pick.npc.dropsMissing(cards, save)
                    TourReason.FRESH -> wins == 0
                    TourReason.RIVAL -> wins > 0 && Rivalry.winsToNextStage(wins) != null
                    TourReason.TIMED -> !pick.npc.availability.isAlwaysAvailable
                }
                assertTrue(holds, "$pick")
            }
        }
    }

    @Test
    fun everyPickIsAmongTheEasiestOfItsReason() {
        repeat(WEEKS_OF_DAYS) { day ->
            for (pick in tour(DAY + day * DAY_MILLIS)) {
                // Difficulty n is the n-th easiest of every kind. The shortlist skips whoever an
                // earlier reason already took, which pushes it back by at most one per pick.
                val reach = DailyTour.SHORTLIST + DailyTour.SIZE - 1
                assertTrue(pick.npc.difficulty <= reach, "$pick")
            }
        }
    }

    @Test
    fun aReasonNobodyMeetsYieldsItsPlace() {
        val fresh = (1..10).map { npc("fresh-$it") }
        val picks = DailyTour.picks(fresh, save, cards, DAY)

        assertEquals(listOf(TourReason.FRESH), picks.map { it.reason })
    }

    @Test
    fun anEmptyPoolIsAnEmptyTour() {
        assertEquals(emptyList(), DailyTour.picks(emptyList(), save, cards, DAY))
    }

    private fun tour(atMillis: Long): List<TourPick> = DailyTour.picks(pool, save, cards, atMillis)

    private companion object {
        val CARD = Card.idFor(block = 1, number = 7)
        const val CREATED = 1_700_000_000_000L
        const val HOUR = 3_600_000L
        const val DAY_MILLIS = 24 * HOUR
        const val DAY = 20_000 * DAY_MILLIS
        const val WEEKS_OF_DAYS = 28
    }
}
