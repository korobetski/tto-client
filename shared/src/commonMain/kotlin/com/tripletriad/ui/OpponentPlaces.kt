package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.Campaign
import com.tripletriad.data.TourPick
import com.tripletriad.data.TourReason
import com.tripletriad.data.ZoneCatalog
import com.tripletriad.data.ZoneProgress
import com.tripletriad.data.ZoneStatus
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.MatchResult
import com.tripletriad.model.Npc
import com.tripletriad.model.Rivalry

const val QUICK_MATCH_TEST_TAG: String = "opponent-quick-match"

const val ALL_OPPONENTS_TEST_TAG: String = "opponent-all"

const val TOUR_TEST_TAG: String = "opponent-tour"

fun tourPickTestTag(iconId: String): String = "opponent-tour-$iconId"

fun zoneRowTestTag(zoneId: String): String = "opponent-zone-$zoneId"

fun zoneStatusTestTag(zoneId: String): String = "opponent-zone-status-$zoneId"

fun zoneArtTestTag(zoneId: String): String = "opponent-zone-art-$zoneId"

/**
 * The roster's home: the quick match, the day's tour, the places, and the way to everybody.
 *
 * In that order because it is the order of how much choosing each asks for — none, one of three,
 * one of a handful of places, one of everybody.
 */
@Suppress("LongParameterList")
internal fun LazyGridScope.homeItems(
    places: List<ZoneProgress>,
    zones: ZoneCatalog,
    tour: List<TourPick>,
    canQuickMatch: Boolean,
    onQuickMatch: () -> Unit,
    onPlace: (String) -> Unit,
    onEveryone: () -> Unit,
    tile: @Composable (Npc, String) -> Unit,
) {
    span("quick-match") {
        WideButton(
            label = LocalStrings.current[StringKeys.QUICK_MATCH],
            tag = QUICK_MATCH_TEST_TAG,
            enabled = canQuickMatch,
            onClick = onQuickMatch,
        )
    }

    if (tour.isNotEmpty()) {
        span("tour-header") {
            SectionHeader(
                text = LocalStrings.current[StringKeys.TOUR],
                modifier = Modifier.testTag(TOUR_TEST_TAG).padding(top = SpaceSm),
            )
        }
        items(tour, key = { "tour-${it.npc.iconId}" }) { pick ->
            Box(Modifier.testTag(tourPickTestTag(pick.npc.iconId))) {
                tile(pick.npc, LocalStrings.current[pick.reason.labelKey])
            }
        }
    }

    span("zones-header") {
        SectionHeader(
            text = LocalStrings.current[StringKeys.ZONES],
            modifier = Modifier.padding(top = SpaceSm),
        )
    }
    for (place in places) {
        span("zone-${place.zone.id}") {
            ZoneRow(place = place, zones = zones, onOpen = { onPlace(place.zone.id) })
        }
    }
    span("zones-note") {
        Text(
            text = LocalStrings.current[StringKeys.ZONES_NOTE],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth().padding(vertical = SpaceXs),
        )
    }

    span("everyone") {
        WideButton(
            label = LocalStrings.current[StringKeys.ALL_OPPONENTS],
            tag = ALL_OPPONENTS_TEST_TAG,
            filled = false,
            onClick = onEveryone,
        )
    }
}

/**
 * One place: its tournament, then every member, the ones not challengeable now included and
 * dimmed by [tile].
 *
 * The tournament leads rather than trails: it is the one row here that is not a face, and under
 * a dozen opponents it would be the one nobody scrolls down to.
 */
internal fun LazyGridScope.placeItems(
    place: ZoneProgress,
    tournament: PlaceTournament?,
    tile: @Composable (Npc) -> Unit,
) {
    span("place-art") { ZoneBanner(place.zone.id) }
    span("place-summary") {
        Text(
            text = placeSummary(LocalStrings.current, place),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag(
                zoneStatusTestTag(place.zone.id),
            ).padding(vertical = SpaceXs),
        )
    }
    if (tournament != null) {
        span("place-tournament") {
            CampaignRow(
                campaign = tournament.campaign,
                locked = tournament.locked,
                lockedNote = tournament.lockedNote,
                onClick = tournament.onOpen,
            )
        }
    }
    items(place.members, key = { it.iconId }) { tile(it) }
}

/** A place's ladder as [placeItems] draws it. */
internal class PlaceTournament(
    val campaign: Campaign,
    val locked: Boolean,
    val lockedNote: String,
    val onOpen: () -> Unit,
)

/**
 * A place as a row: its name, how far along it is, and — while it is shut — what opens it.
 *
 * A row rather than a tile: the places are a list read top to bottom, open ones first, and a
 * locked one has a sentence to say that a tile would have to cut.
 */
@Composable
private fun ZoneRow(place: ZoneProgress, zones: ZoneCatalog, onOpen: () -> Unit) {
    val strings = LocalStrings.current
    val open = place.isOpen
    val art = rememberZoneArt(LocalUiArt.current, place.zone.id)
    val ground = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier = Modifier
            .testTag(zoneRowTestTag(place.zone.id))
            .fillMaxWidth()
            .rowSurface()
            .then(if (open) Modifier.ttoClickable(onClick = onOpen) else Modifier)
            .alpha(if (open) 1f else FAINT),
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // A shut place keeps its picture but not its colour: it is somewhere to go, not
                // somewhere to be yet.
                colorFilter = if (open) null else Grayscale,
                modifier = Modifier.matchParentSize().testTag(zoneArtTestTag(place.zone.id)),
            )
            // The text sits on the left, so the picture shows through on the right only.
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to ground.copy(alpha = SCRIM_TEXT),
                        SCRIM_MIDDLE to ground.copy(alpha = SCRIM_FADE),
                        1f to ground.copy(alpha = SCRIM_CLEAR),
                    ),
                ),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (art != null) ZoneArtRowHeight else 0.dp)
                .padding(horizontal = SpaceMd, vertical = SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings[place.zone.nameKey],
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (open) {
                        placeSummary(
                            strings,
                            place,
                        )
                    } else {
                        lockHint(strings, place, zones)
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag(zoneStatusTestTag(place.zone.id)),
                )
            }
            Text(
                text = "${place.beaten}/${place.members.size}",
                color = if (place.status >= ZoneStatus.CLEARED) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED)
                },
                // Over the bright end of the picture when there is one.
                style = MaterialTheme.typography.labelLarge.let {
                    if (art != null) it.copy(shadow = TextShadow) else it
                },
            )
        }
    }
}

/** A place's illustration above its members, or nothing while it has none. */
@Composable
private fun ZoneBanner(zoneId: String) {
    val art = rememberZoneArt(LocalUiArt.current, zoneId) ?: return
    Image(
        bitmap = art,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .testTag(zoneArtTestTag(zoneId))
            .fillMaxWidth()
            .height(ZoneBannerHeight)
            .clip(MaterialTheme.shapes.small),
    )
}

private fun placeSummary(strings: Strings, place: ZoneProgress): String =
    strings[place.status.labelKey] + DOT_SEPARATOR +
        strings.format(StringKeys.ZONE_BEATEN, "${place.beaten}", "${place.members.size}")

private fun lockHint(strings: Strings, place: ZoneProgress, zones: ZoneCatalog): String {
    val names = place.zone.after.mapNotNull { zones[it] }.joinToString(", ") { strings[it.nameKey] }
    val key = if (place.zone.afterAny) StringKeys.ZONE_NEEDS_ANY else StringKeys.ZONE_NEEDS_ALL
    return strings.format(key, names)
}

private val ZoneStatus.labelKey: String
    get() = when (this) {
        ZoneStatus.LOCKED -> StringKeys.ZONE_LOCKED
        ZoneStatus.OPEN -> StringKeys.ZONE_OPEN
        ZoneStatus.CLEARED -> StringKeys.ZONE_CLEARED
        ZoneStatus.COMPLETE -> StringKeys.ZONE_COMPLETE
    }

internal val TourReason.labelKey: String
    get() = when (this) {
        TourReason.WANTED -> StringKeys.TOUR_WANTED
        TourReason.FRESH -> StringKeys.TOUR_FRESH
        TourReason.RIVAL -> StringKeys.TOUR_RIVAL
        TourReason.TIMED -> StringKeys.TOUR_TIMED
    }

private fun LazyGridScope.span(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** The hours this opponent keeps, or null for one who is always there. */
internal fun Npc.hoursNote(strings: Strings): String? =
    if (isTimed()) {
        strings.format(StringKeys.NPC_HOURS, "${availability.begins}", "${availability.ends}")
    } else {
        null
    }

/**
 * Why this opponent cannot be met right now, or null when they can.
 *
 * The achievement first: it is the one a player can act on, and waiting for the hour would not
 * produce an opponent still behind a door.
 */
internal fun Npc.absenceNote(strings: Strings, hour: Int, earned: Set<String>): String? = when {
    !isEarnedBy(earned) -> strings.format(
        StringKeys.NPC_NEEDS_ACHIEVEMENT,
        requiresAchievement.orEmpty().let { strings[AchievementCatalog[it]?.labelKey ?: it] },
    )
    !availability.isOpenAtHour(hour) ->
        hoursNote(strings)?.let { "${strings[StringKeys.NPC_AWAY]}$DOT_SEPARATOR$it" }
    else -> null
}

/** Mirrors `NpcCatalog`'s private test: no door, or a door [earned] opens. */
internal fun Npc.isEarnedBy(earned: Set<String>): Boolean =
    requiresAchievement?.let { it in earned } != false

/** How far a rivalry has gone, for the sheet; null before the first win. See [Rivalry]. */
internal fun rivalLine(strings: Strings, wins: Int): String? {
    if (wins <= 0) return null
    val left = Rivalry.winsToNextStage(wins) ?: return strings[StringKeys.RIVAL_MAX]
    return strings.format(StringKeys.RIVAL_NEXT, "$left")
}

internal fun dieLabel(view: RosterView): String =
    if (view is RosterView.Place) StringKeys.RANDOM_HERE else StringKeys.RANDOM_OPPONENT

internal fun rewardLine(strings: Strings, npc: Npc): String = buildList {
    add("${strings[StringKeys.DIFFICULTY]} ${npc.difficulty}")
    if (npc.matchFee > 0) add("${strings[StringKeys.MATCH_FEE]} ${npc.matchFee}")
    add("${npc.mgpFor(MatchResult.WIN)} ${strings[StringKeys.MGP]}")
    val xp = npc.xpFor(MatchResult.WIN)
    if (xp > 0) add("$xp ${strings[StringKeys.XP]}")
}.joinToString(DOT_SEPARATOR)

/** Where a place row's scrim stops hiding its picture, from the left edge. */
private const val SCRIM_MIDDLE = 0.55f

private const val SCRIM_TEXT = 0.94f

private const val SCRIM_FADE = 0.7f

private const val SCRIM_CLEAR = 0.2f

private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private val TextShadow = Shadow(color = Color.Black, blurRadius = 6f)

private val ZoneArtRowHeight = 72.dp

private val ZoneBannerHeight = 140.dp
