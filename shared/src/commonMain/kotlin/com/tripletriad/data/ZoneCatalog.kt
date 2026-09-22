package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A place opponents are met in: Balamb, Ul'dah, Kugane.
 *
 * Authored here, in `zones.json`, rather than on [Npc], because a zone is this client's way of
 * walking the roster and not a fact the referee needs: the server opens a match against anybody
 * the format admits and never asks where they stand. So nothing about zones is in `tto-core`, and
 * a zone that is wrong costs a player a detour rather than a refused match.
 *
 * Membership follows the games' own maps, read off ffxivcollect's `location.region` for the FFXIV
 * table (2026-09-18) and off the FFVIII story for the other. The few who have no place there
 * are the original's inventions — Moogle Momo, Tataru, Papalymo, the Queen of Cards — and sit
 * where their story does.
 *
 * @property after the zones that open this one. Empty for a starting zone.
 * @property afterAny whether *one* of [after] cleared is enough rather than all of them.
 * @property npcs member [Npc.iconId]s, in the order a zone lists them.
 */
@Serializable
data class Zone(
    val id: String,
    val nameKey: String,
    val after: List<String> = emptyList(),
    val afterAny: Boolean = false,
    val npcs: List<String>,
) {
    val isStarter: Boolean get() = after.isEmpty()
}

/** Where a player stands with one zone. Ordered: each state implies the ones before it. */
enum class ZoneStatus {
    LOCKED,
    OPEN,

    /** Every member that blocks the way on has been beaten once. See [ZoneCatalog.blocks]. */
    CLEARED,

    /** Every member beaten, and none of them left holding a card the collection lacks. */
    COMPLETE,
}

/**
 * One zone read against one profile.
 *
 * @property members the zone's opponents in the roster it was read against, easiest first.
 * @property beaten how many [members] the profile has a win against.
 * @property left how many of the [members] that [ZoneCatalog.blocks] are still unbeaten. Smaller
 *   than `members.size - beaten` wherever an opponent keeps hours or waits behind a badge.
 * @property opens the shut places this one's clearing would open, in map order. Empty unless it is
 *   [ZoneStatus.OPEN]: a place is named here before it opens, and only here, so the home can say
 *   what a player is working towards without listing the whole map (see `homeItems`). A place that
 *   also waits on another uncleared one is not named — clearing this would not open it.
 */
data class ZoneProgress(
    val zone: Zone,
    val status: ZoneStatus,
    val members: List<Npc>,
    val beaten: Int,
    val left: Int,
    val opens: List<Zone>,
) {
    val isOpen: Boolean get() = status != ZoneStatus.LOCKED
}

class ZoneCatalog(val zones: List<Zone>) {
    private val byId: Map<String, Zone> = zones.associateBy { it.id }

    private val byNpc: Map<String, Zone> =
        zones.flatMap { zone -> zone.npcs.map { it to zone } }.toMap()

    operator fun get(id: String): Zone? = byId[id]

    /** The zone [iconId] stands in, or null for an opponent no zone names. */
    fun zoneOf(iconId: String): Zone? = byNpc[iconId]

    /**
     * Every zone that has a member in [roster], each read against [save].
     *
     * A zone with no member in the roster is left out rather than shown empty: under an FFXIV-only
     * format, Balamb is not a locked place, it is not a place at all.
     *
     * The open ones come first, each group in map order. The shut ones are still returned — the
     * roster's home leaves them out (a place is discovered when it opens, not listed ahead of it),
     * but [reachable] and [openIds] are answered from the same reading.
     */
    fun progress(roster: List<Npc>, save: GameSave, cards: Map<Int, Card>): List<ZoneProgress> {
        val byIcon = roster.associateBy { it.iconId }
        val cleared = zones.filter { isCleared(it, byIcon, save) }.map { it.id }.toSet()
        return zones.mapNotNull { zone ->
            val members = zone.npcs.mapNotNull(byIcon::get)
                .sortedWith(compareBy({ it.difficulty }, { it.matchFee }))
            if (members.isEmpty()) return@mapNotNull null
            val beaten = members.count { it.isBeatenBy(save) }
            val status = when {
                !isUnlocked(zone, cleared) -> ZoneStatus.LOCKED
                zone.id !in cleared -> ZoneStatus.OPEN
                beaten == members.size && members.none { it.dropsMissing(cards, save) } ->
                    ZoneStatus.COMPLETE
                else -> ZoneStatus.CLEARED
            }
            val left = members.count { blocks(it) && !it.isBeatenBy(save) }
            val opens =
                if (status == ZoneStatus.OPEN) opening(zone, cleared, byIcon) else emptyList()
            ZoneProgress(zone, status, members, beaten, left, opens)
        }.sortedBy { !it.isOpen }
    }

    /** The zones still shut that [zone] would open on its own, if it were cleared now. */
    private fun opening(zone: Zone, cleared: Set<String>, byIcon: Map<String, Npc>): List<Zone> =
        zones.filter { next ->
            zone.id in next.after &&
                next.npcs.any(byIcon::containsKey) &&
                !isUnlocked(next, cleared) &&
                isUnlocked(next, cleared + zone.id)
        }

    /**
     * The members of [roster] whose zone is open, in roster order.
     *
     * An opponent no zone names is kept. The bundle test holds every opponent to one zone, so this
     * only matters to a roster newer than its `zones.json` — and a new opponent missing from the
     * list would be a worse failure than one met a little early.
     */
    fun reachable(roster: List<Npc>, save: GameSave, cards: Map<Int, Card>): List<Npc> {
        val open = progress(roster, save, cards).filter { it.isOpen }.map { it.zone.id }.toSet()
        return roster.filter { npc -> zoneOf(npc.iconId)?.let { it.id in open } ?: true }
    }

    /**
     * The ids of the zones open to [save], for a screen that needs to know which places a player
     * has been shown without drawing them — the tournaments tab, which hides a shut place's ladder.
     *
     * No card table: it only tells [ZoneStatus.CLEARED] from [ZoneStatus.COMPLETE], and both are
     * open.
     */
    fun openIds(roster: List<Npc>, save: GameSave): Set<String> =
        progress(roster, save, emptyMap()).filter { it.isOpen }.map { it.zone.id }.toSet()

    private fun isUnlocked(zone: Zone, cleared: Set<String>): Boolean = when {
        zone.isStarter -> true
        zone.afterAny -> zone.after.any { it in cleared }
        else -> zone.after.all { it in cleared }
    }

    private fun isCleared(zone: Zone, byIcon: Map<String, Npc>, save: GameSave): Boolean =
        zone.npcs.mapNotNull(byIcon::get).filter(::blocks).all { it.isBeatenBy(save) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ZoneCatalog =
            ZoneCatalog(json.decodeFromString<ZonesFile>(text).zones)

        /**
         * Whether [npc] has to be beaten before the zones after its own open.
         *
         * Not an opponent who keeps hours: a player who only plays at lunch would otherwise find
         * the way on shut by somebody who is never there, and nothing they could do about it but
         * change their day. Not one behind an achievement either, for the same reason — the door
         * would be shut by another door. Both still count towards [ZoneStatus.COMPLETE].
         */
        fun blocks(npc: Npc): Boolean =
            npc.availability.isAlwaysAvailable && npc.requiresAchievement == null
    }
}

@Serializable
private data class ZonesFile(val zones: List<Zone>)

internal fun Npc.isBeatenBy(save: GameSave): Boolean = (save.npcWins[iconId] ?: 0) > 0

/**
 * A card this opponent can drop that the collection does not hold.
 *
 * The same test the roster's cyan dot makes (`Npc.wants` in the UI), repeated rather than imported
 * because `data` does not see `ui`.
 */
internal fun Npc.dropsMissing(cards: Map<Int, Card>, save: GameSave): Boolean =
    itemRewards.any { reward ->
        reward.cardId?.let { it in cards && !save.ownsCard(it) } ?: false
    }
