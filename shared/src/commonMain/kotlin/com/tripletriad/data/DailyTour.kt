package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import com.tripletriad.model.Rivalry
import com.tripletriad.model.asRivalOf
import com.tripletriad.time.utcDayNumber
import kotlin.random.Random

/** Why the tour put an opponent in front of the player today. */
enum class TourReason {
    /** Can drop a card the collection lacks. */
    WANTED,

    /** Never beaten. */
    FRESH,

    /** Beaten before, and one or more wins from playing harder. See [Rivalry]. */
    RIVAL,

    /** Only around at some hours — worth knowing when. */
    TIMED,
}

data class TourPick(val npc: Npc, val reason: TourReason)

/**
 * Three opponents a day, each with a reason, so the roster opens on a suggestion rather than on a
 * wall of faces to choose from.
 *
 * ### The same three all day
 *
 * Seeded by the UTC day and the character's creation date, like the daily quests: reopening the
 * screen does not reshuffle, two characters do not share a tour, and a new day brings a new one.
 * Nothing is stored, so there is nothing to sync and nothing the server has to know about.
 *
 * ### Which reasons, and whom
 *
 * The day's shuffle orders the four [TourReason]s and the first [SIZE] that have a candidate
 * each give one pick, so a reason nobody meets simply yields its place. Within a reason the pick
 * is drawn from its [SHORTLIST] easiest candidates, not from all of them: a tour that sent a new
 * character at the hardest opponent of an open zone would be the choice problem again, only made
 * for them.
 */
object DailyTour {
    const val SIZE: Int = 3

    const val SHORTLIST: Int = 4

    /**
     * Today's picks from [pool], which the caller has already cut to reachable, earned opponents —
     * at every hour, since a [TourReason.TIMED] pick is shown with its hours even while closed.
     */
    fun picks(
        pool: List<Npc>,
        save: GameSave,
        cards: Map<Int, Card>,
        atMillis: Long,
    ): List<TourPick> {
        val random = Random(utcDayNumber(atMillis) * SEED_MIX xor save.creationDate)
        val easiest = pool.sortedWith(compareBy({ it.difficulty }, { it.matchFee }, { it.iconId }))
        val taken = mutableSetOf<String>()
        return TourReason.entries.shuffled(random).mapNotNull { reason ->
            if (taken.size >= SIZE) return@mapNotNull null
            easiest.filter { it.iconId !in taken && reason.holds(it, save, cards) }
                .take(SHORTLIST)
                .randomOrNull(random)
                ?.also { taken += it.iconId }
                ?.let { TourPick(it, reason) }
        }
    }

    private fun TourReason.holds(npc: Npc, save: GameSave, cards: Map<Int, Card>): Boolean =
        when (this) {
            TourReason.WANTED -> npc.dropsMissing(cards, save)
            TourReason.FRESH -> !npc.isBeatenBy(save)
            TourReason.RIVAL -> {
                val wins = save.npcWins[npc.iconId] ?: 0
                wins > 0 && Rivalry.winsToNextStage(wins) != null
            }
            TourReason.TIMED -> !npc.availability.isAlwaysAvailable
        }

    /** Spreads consecutive day numbers apart before they meet the creation date. */
    private const val SEED_MIX: Long = -0x61C8864680B583EBL
}

/**
 * "Just give me a match": one opponent picked for the player instead of by them.
 *
 * From the [SHORTLIST] hardest of those open now whose difficulty, as a rival, is within the
 * level's reach — the band [NpcCatalog.available]'s old gate used to draw, so a level-1 character
 * meets difficulty 2 at most. The hardest of the fair ones rather than any fair one, because the
 * easiest of a zone pays least and teaches nothing to someone who has outgrown it. Nobody fair
 * falls back to the easiest open opponent, never to nobody while anybody is there.
 */
object QuickMatch {
    const val SHORTLIST: Int = 4

    fun pick(pool: List<Npc>, save: GameSave, hour: Int, random: Random = Random): Npc? {
        val open = pool.filter { it.availability.isOpenAtHour(hour) }
            .sortedWith(compareBy({ it.asRivalOf(save).difficulty }, { it.iconId }))
        val fair = open.filter { it.asRivalOf(save).difficulty <= save.level + REACH }
        return fair.takeLast(SHORTLIST).ifEmpty { open.take(1) }.randomOrNull(random)
    }

    private const val REACH = 1
}
