package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.data.CardSet
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc

/**
 * The three questions the roster can be narrowed by, held together so the grid reads one answer.
 *
 * Hoisted out of the screen for the same reason `CardFilters` is: three `mutableStateOf`s that
 * have to be reset together — every one of them is a claim about a roster, and a roster the
 * format no longer admits must not leave a rule selected that nobody left plays.
 */
@Stable
internal class OpponentFilters(val blocks: List<Int>, private val groups: Map<Int, Int>) {
    var reason by mutableStateOf(OpponentReason.ALL)

    /**
     * A `StringKeys`-style rule key, or null for "any rule".
     *
     * The key rather than a rule value because that is what [Npc.ruleKeys] holds and what the menu
     * has to name: the engine's own rule type is `:core`'s and this screen never evaluates one, it
     * only asks who imposes it.
     */
    var rule by mutableStateOf<String?>(null)

    var block by mutableStateOf<Int?>(null)

    /**
     * The roster the grid draws, in the order [com.tripletriad.data.NpcCatalog.available] handed
     * it back — the filters remove, they never reorder.
     */
    fun apply(opponents: List<Npc>, cards: Map<Int, Card>, profile: GameSave): List<Npc> =
        opponents.filter { npc ->
            reason.holds(npc, cards, profile) &&
                (rule == null || rule in npc.ruleKeys) &&
                (block == null || npc.group(groups) == block)
        }
}

/**
 * Which of an opponent's own card pool is playing — FFXIV's blocks or FFVIII's.
 *
 * Not a format: the widest format admits every block at once, on purpose (see `App`), so telling
 * two collections apart on this screen is a read of the roster the format already handed back, not
 * a second query. A card's raw block folds down to the block that speaks for its whole *set*
 * before it is compared — FFXIV spans two blocks and an opponent drawing from the second should
 * still file under the same "FFXIV" chip as one drawing from the first, not a "Set 2" of its own.
 * See [representativeBlocks].
 */
private fun Npc.group(groups: Map<Int, Int>): Int? = block()?.let { groups[it] ?: it }

/**
 * The block this opponent's own card pool plays, or null for an opponent with no cards to speak of.
 *
 * Not a field on [Npc]: it is read off the same fact [com.tripletriad.data.ShopOffer.block]
 * derives a shop offer's block from, an opponent's `cards` pool, rather than authored a second
 * time. Roulette-format opponents can only ever draw from one block regardless — nothing in
 * `npcs.json` mixes the two — so the first card is as good a witness as any.
 */
private fun Npc.block(): Int? = cards.firstOrNull()?.shr(Card.BLOCK_SHIFT)

/** Reset with the roster, so a stale choice cannot hide every opponent the new one has. */
@Composable
internal fun rememberOpponentFilters(opponents: List<Npc>, sets: List<CardSet>): OpponentFilters {
    val groups = remember(sets) { representativeBlocks(sets) }
    return remember(opponents, groups) {
        OpponentFilters(
            blocks = opponents.mapNotNull { it.group(groups) }.distinct().sorted(),
            groups = groups,
        )
    }
}

/** Whether this opponent answers the question the chip asks. */
internal fun OpponentReason.holds(npc: Npc, cards: Map<Int, Card>, profile: GameSave): Boolean =
    when (this) {
        OpponentReason.ALL -> true
        // Never in `npcWins`, which is exactly the set a win or a loss adds an opponent's `iconId`
        // to — a queue that empties itself as it is played, and needs nothing counted to build.
        OpponentReason.FRESH -> npc.iconId !in profile.npcWins
        OpponentReason.WANTED -> npc.wants(cards, profile.cards)
        // Everyone on the roster is already open at this hour — `NpcCatalog.available` filtered on
        // exactly that — so this only has to say which of them are not open at *every* hour, which
        // is the difference between being on the list today and being on it always.
        OpponentReason.TIMED -> npc.isTimed()
    }
