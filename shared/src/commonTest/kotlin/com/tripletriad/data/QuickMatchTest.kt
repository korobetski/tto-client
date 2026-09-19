package com.tripletriad.data

import com.tripletriad.model.Availability
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import com.tripletriad.model.Rivalry
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QuickMatchTest {
    private val level1 = GameSave.new(createdAt = 0L)

    @Test
    fun aNewCharacterIsSentNoHigherThanOneAboveTheirLevel() {
        val pool = (1..10).map { npc("d$it", difficulty = it) }

        val met = draws(pool, level1).map { it.difficulty }.toSet()

        assertEquals(setOf(1, 2), met, "level 1 reaches difficulty 2 and every fair one is drawn")
    }

    @Test
    fun theDrawIsFromTheHardestFairOnes() {
        val pool = (1..10).map { npc("d$it", difficulty = it) }
        val level9 = level1.copy(level = 9)

        val met = draws(pool, level9).map { it.difficulty }.toSet()

        assertEquals((7..10).toSet(), met)
    }

    @Test
    fun aRivalCountsAtTheDifficultyTheyNowPlay() {
        val pool = listOf(npc("easy", difficulty = 1), npc("rival", difficulty = 1))
        val save = level1.copy(npcWins = mapOf("rival" to Rivalry.WINS_PER_STAGE))

        assertEquals(setOf("easy"), draws(pool, save).map { it.iconId }.toSet())
    }

    @Test
    fun nobodyFairFallsBackToTheEasiestOpen() {
        val pool = listOf(npc("hard", difficulty = 9), npc("harder", difficulty = 10))

        assertEquals(setOf("hard"), draws(pool, level1).map { it.iconId }.toSet())
    }

    @Test
    fun whoIsClosedIsNeverSent() {
        val evening = Availability(begins = 18, ends = 22)
        val pool = listOf(npc("closed", availability = evening), npc("open", difficulty = 2))

        assertEquals(setOf("open"), draws(pool, level1).map { it.iconId }.toSet())
        assertEquals(
            setOf("closed", "open"),
            draws(pool, level1, hour = 19).map { it.iconId }.toSet(),
        )
    }

    @Test
    fun nobodyOpenIsNoMatch() {
        val pool = listOf(npc("closed", availability = Availability(begins = 18, ends = 22)))

        assertNull(QuickMatch.pick(pool, level1, NOON))
        assertNull(QuickMatch.pick(emptyList(), level1, NOON))
    }

    private fun draws(pool: List<Npc>, save: GameSave, hour: Int = NOON) =
        List(DRAWS) { seed ->
            assertNotNull(QuickMatch.pick(pool, save, hour, Random(seed)))
        }

    private companion object {
        const val NOON = 12
        const val DRAWS = 200
    }
}
