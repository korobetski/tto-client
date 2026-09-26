package com.tripletriad.ui

import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Achievement
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.Requirement

/**
 * A ladder of tiers read as one thing — `ac-tt1` to `ac-tt5` are "Triple Team", five rungs of it.
 *
 * @param progress how far [next] is along, or null once every tier is earned. Taken once here
 *   because the ranking, the four standings and the card all ask it, and each asking walks the
 *   save.
 */
internal data class AchievementFamily(
    val key: String,
    val tiers: List<Achievement>,
    val earned: Achievement?,
    val next: Achievement?,
    val progress: Requirement.Progress?,
) {
    val face: Achievement get() = earned ?: next ?: tiers.first()

    val earnedCount: Int get() = tiers.indexOf(earned) + 1

    val category: AchievementCategory get() = categoryOf(tiers.first().requirement)
}

/**
 * The five things a family can be about, read off what its tiers *ask for*.
 *
 * Derived from the requirement rather than listed by id, so an achievement authored in `tto-core`
 * lands in a category without this file hearing of it. Rule wins sit with the plain ones: "win
 * ten matches under Roulette" is a thing done at the table, and a category of one family would be
 * a rail entry that filters nothing.
 */
internal enum class AchievementCategory(val labelKey: String, val tag: String) {
    COLLECTION(StringKeys.COLLECTION, "collection"),
    MATCHES(StringKeys.WINS, "wins"),
    PLACES(StringKeys.ZONES, "places"),
    CAMPAIGNS(StringKeys.CAMPAIGNS, "campaigns"),
    MGP(StringKeys.MGP, "mgp"),
}

internal fun categoryOf(requirement: Requirement): AchievementCategory = when (requirement) {
    is Requirement.CardsOwned, is Requirement.CardSetOwned -> AchievementCategory.COLLECTION
    // A deed is something that happened at a table — losing FFVIII's Odin, so far.
    is Requirement.NpcWins, is Requirement.RuleWins, is Requirement.Deed ->
        AchievementCategory.MATCHES
    // Twenty places, one family each: among the plain wins they would bury the rest.
    is Requirement.NpcsBeaten -> AchievementCategory.PLACES
    is Requirement.CampaignWins -> AchievementCategory.CAMPAIGNS
    is Requirement.MgpHeld -> AchievementCategory.MGP
}

/**
 * Where a family stands, as the chips over the grid ask it.
 *
 * Not a partition: [ALMOST] is a part of [IN_PROGRESS], the way the mock-up's counts overlapped.
 * A family one card from its next rung is both being worked on and nearly done, and a player
 * looking for the second should not lose it from the first.
 */
internal enum class Standing(val labelKey: String, val tag: String) {
    IN_PROGRESS(StringKeys.ACHIEVEMENT_IN_PROGRESS, "in-progress"),
    ALMOST(StringKeys.ACHIEVEMENT_ALMOST, "almost"),
    COMPLETED(StringKeys.ACHIEVEMENT_COMPLETED, "completed"),
    NOT_STARTED(StringKeys.ACHIEVEMENT_NOT_STARTED, "not-started"),
    ;

    fun admits(family: AchievementFamily): Boolean = when (this) {
        IN_PROGRESS -> family.next != null && !family.isUntouched
        ALMOST -> family.isAlmost
        COMPLETED -> family.next == null
        NOT_STARTED -> family.isUntouched
    }
}

/** No tier earned and nothing counted towards the first — not merely unearned. */
internal val AchievementFamily.isUntouched: Boolean
    get() = earned == null && (progress?.current ?: 0) == 0

/**
 * At [ALMOST_FRACTION] of the next rung or past it — measured against that rung, not the
 * ladder.
 */
internal val AchievementFamily.isAlmost: Boolean
    get() = progress != null && progress.fraction >= ALMOST_FRACTION

/**
 * Every family, the started ones first and most recently earned leading, then the rest by how near
 * their first rung is.
 */
internal fun rankedFamilies(profile: GameSave): List<AchievementFamily> {
    val families = visibleAchievements(profile)
        .groupBy { it.id.trimEnd { character -> character.isDigit() } }
        .map { (key, tiers) ->
            val next = tiers.firstOrNull { !profile.hasAchievement(it.id) }
            AchievementFamily(
                key = key,
                tiers = tiers,
                earned = tiers.lastOrNull { profile.hasAchievement(it.id) },
                next = next,
                progress = next?.progressFor(profile),
            )
        }
    val (started, untouched) = families.partition { it.earned != null }
    return started.sortedByDescending { profile.achievements[it.earned?.id] ?: 0L } +
        untouched.sortedByDescending { it.face.progressFor(profile).fraction }
}

/**
 * The catalogue as this profile may see it: a [Achievement.hidden] one only once it is earned.
 *
 * Hidden from the grid rather than drawn as a blank medallion, because a blank with a card reward
 * under it is a hint, and the hint for Zantetsuken lives on the card it is about (see
 * `CARD_HINTS`). What is left of it is a count — [AchievementTotals.hiddenLeft].
 */
internal fun visibleAchievements(profile: GameSave): List<Achievement> =
    AchievementCatalog.all.filter { !it.hidden || profile.hasAchievement(it.id) }

/**
 * The three numbers over the grid.
 *
 * @param mgpEarned what the earned tiers paid, not the purse: the purse is in the app bar already,
 *   and most of it was won at the table.
 * @param cardsTotal the tiers that pay a card at all — most pay Gil or nothing.
 * @param hiddenLeft hidden tiers not yet earned. The totals count them — a total that grew the
 *   day one was found would say the same thing less clearly than this line does.
 */
internal data class AchievementTotals(
    val tiersEarned: Int,
    val tiersTotal: Int,
    val mgpEarned: Int,
    val cardsEarned: Int,
    val cardsTotal: Int,
    val hiddenLeft: Int = 0,
)

internal fun achievementTotals(profile: GameSave): AchievementTotals {
    val all = AchievementCatalog.all
    val earned = all.filter { profile.hasAchievement(it.id) }
    return AchievementTotals(
        tiersEarned = earned.size,
        tiersTotal = all.size,
        mgpEarned = earned.sumOf { it.mgpReward },
        cardsEarned = earned.count { it.reward is CardItem },
        cardsTotal = all.count { it.reward is CardItem },
        hiddenLeft = all.count { it.hidden && !profile.hasAchievement(it.id) },
    )
}

/** The mock-up's "Presque" line: four fifths of the way to the next rung. */
internal const val ALMOST_FRACTION = 0.8f
