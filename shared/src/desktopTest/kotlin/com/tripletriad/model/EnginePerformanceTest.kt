package com.tripletriad.model

import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

class EnginePerformanceTest {
    private fun card(id: Int, top: Int, right: Int, bottom: Int, left: Int) = Card(
        // Fixtures number their cards from 1; ids are global.
        id = Card.idFor(block = 1, number = id),
        nameKey = "STR_TEST_$id",
        name = "Test $id",
        top = top,
        right = right,
        bottom = bottom,
        left = left,
        rarity = 1,
        type = MatchState.FF8_ELEMENTS[id % MatchState.FF8_ELEMENTS.size],
    )

    private val pool = (1..20).map {
        card(
            it,
            top = it % 10 + 1,
            right = 10 - it % 9,
            bottom = (it * 3) % 10 + 1,
            left = it % 7 + 2,
        )
    }

    private val heaviest = GameRules(
        same = true,
        sameWall = true,
        plus = true,
        typeRule = TypeRule.ELEMENTAL,
        fallenAce = true,
    )

    private fun medianMicros(runs: Int = SAMPLES, block: (Int) -> Unit): Double {
        repeat(runs) { block(it) }
        val timings = (0 until runs).map { measureNanoTime { block(it) } }
        return timings.sorted()[runs / 2] / MICROS.toDouble()
    }

    private fun report(what: String, micros: Double, budgetMicros: Double) {
        println("$what: ${(micros * MICROS).toInt()} ns (budget ${budgetMicros.toInt()} us)")
        assertTrue(
            micros < budgetMicros,
            "$what took $micros us, which is past the ${budgetMicros.toInt()} us budget — " +
                "something has changed complexity, not constants",
        )
    }

    @Test
    fun resolvingOnePlacementIsWellInsideItsBudget() {
        val engine = RulesEngine(heaviest)
        val boards = (0 until SAMPLES).map { seed -> crowdedBoard(Random(seed)) }

        val micros = medianMicros { i ->
            val (board, free) = boards[i]
            engine.resolve(board, free, pool[i % pool.size], CardColor.BLUE)
        }

        report("resolve (8 cards placed, all rules)", micros, RESOLVE_BUDGET_MICROS)
    }

    @Test
    fun playingAWholeMatchIsWellInsideItsBudget() {
        val micros = medianMicros(MATCH_SAMPLES) { seed -> playOut(Random(seed)) }

        report("full 9-placement match", micros, MATCH_BUDGET_MICROS)
    }

    @Test
    fun oneAiTurnIsWellInsideItsBudget() {
        val ai = MatchAi()
        val start = MatchState.start(
            blueHand = pool.take(HAND_SIZE),
            redHand = pool.drop(HAND_SIZE).take(HAND_SIZE),
            rules = heaviest,
            elements = MatchState.randomElements(Random(1)),
        )

        val micros = medianMicros { seed -> ai.choose(start, Random(seed)) }

        report("AI turn (45 candidates, all rules)", micros, AI_TURN_BUDGET_MICROS)
    }

    @Test
    fun anEntireAiVersusAiMatchIsWellInsideItsBudget() {
        val ai = MatchAi()

        val micros = medianMicros(MATCH_SAMPLES) { seed ->
            val random = Random(seed)
            var state = MatchState.start(
                blueHand = pool.take(HAND_SIZE),
                redHand = pool.drop(HAND_SIZE).take(HAND_SIZE),
                rules = heaviest,
                elements = MatchState.randomElements(random),
            )
            while (!state.isFinished) state = ai.play(state, random)
        }

        report("AI vs AI, whole match", micros, AI_MATCH_BUDGET_MICROS)
    }

    private fun crowdedBoard(random: Random): Pair<Board, Int> {
        val free = random.nextInt(Board.SIZE)
        var board = Board(elements = MatchState.randomElements(random))
        var next = 0
        for (position in 0 until Board.SIZE) {
            if (position == free) continue
            val owner = if (next % 2 == 0) CardColor.BLUE else CardColor.RED
            board = board.place(position, pool[next % pool.size], owner)
            next++
        }
        return board to free
    }

    private fun playOut(random: Random) {
        var state = MatchState.start(
            blueHand = pool.take(HAND_SIZE),
            redHand = pool.drop(HAND_SIZE).take(HAND_SIZE),
            rules = heaviest,
            elements = MatchState.randomElements(random),
        )
        while (!state.isFinished) {
            state = state.play(state.currentHand.first(), state.playablePositions().first())
        }
    }

    private companion object {
        const val MICROS = 1_000
        const val SAMPLES = 201
        const val MATCH_SAMPLES = 51

        const val RESOLVE_BUDGET_MICROS = 500.0
        const val MATCH_BUDGET_MICROS = 6_000.0
        const val AI_TURN_BUDGET_MICROS = 6_000.0
        const val AI_MATCH_BUDGET_MICROS = 75_000.0
    }
}
