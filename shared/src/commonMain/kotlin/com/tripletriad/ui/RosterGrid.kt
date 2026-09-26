package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tripletriad.data.ZoneProgress

const val OPPONENT_PLACES_TEST_TAG: String = "opponent-places"

/**
 * The roster's one scrolling container: everything above the tiles is a full-width span in the
 * same grid rather than a static header over a separately scrolling list, so nothing above the
 * fold can starve what is below it on a short window — the fix `ShopBody` makes for the same
 * reason.
 */
@Composable
internal fun RosterGrid(
    state: LazyGridState,
    tagged: Boolean = true,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TileMinWidth),
        state = state,
        modifier = (if (tagged) Modifier.testTag(OPPONENT_LIST_TEST_TAG) else Modifier)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        content = content,
    )
}

/**
 * The open places side by side, so a swipe goes to the next one without the round trip through
 * the home.
 *
 * The view follows the **settled** page, not the one under the finger: the title and the die read
 * it, and a die that drew from the place half-swiped-to would draw from a place the player had not
 * chosen yet.
 *
 * The current page's grid alone carries [OPPONENT_LIST_TEST_TAG]. A pager composes its neighbour
 * while it is dragged, and two grids answering to one tag is a test that cannot say which it
 * scrolled.
 */
@Composable
internal fun PlacePager(
    open: List<ZoneProgress>,
    current: String,
    onSettle: (String) -> Unit,
    page: LazyGridScope.(ZoneProgress) -> Unit,
) {
    val pager = rememberPagerState(
        initialPage = open.indexOfFirst { it.zone.id == current }.coerceAtLeast(0),
    ) { open.size }
    LaunchedEffect(pager, open) {
        snapshotFlow { pager.settledPage }.collect { index ->
            open.getOrNull(index)?.let { onSettle(it.zone.id) }
        }
    }
    HorizontalPager(
        state = pager,
        key = { open[it].zone.id },
        modifier = Modifier.testTag(OPPONENT_PLACES_TEST_TAG).fillMaxWidth(),
        pageSpacing = SpaceMd,
        verticalAlignment = Alignment.Top,
    ) { index ->
        RosterGrid(state = rememberLazyGridState(), tagged = index == pager.currentPage) {
            page(open[index])
        }
    }
}

private val TileMinWidth = 104.dp
