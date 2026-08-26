package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
import com.tripletriad.model.CoinFlip
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class MatchBannerUiTest {

    @Test
    fun aCaptionPlaysAndThenLeaves() = runComposeUiTest {
        setContent { Overlay(BannerEvent(at = 0, listOf(MatchBanner.SAME).asAnimations())) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.SAME)) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(MATCH_BANNER_TEST_TAG) }
    }

    @Test
    fun captionsFromOnePlacementPlayOneAtATime() = runComposeUiTest {
        val event = BannerEvent(at = 0, listOf(MatchBanner.SAME, MatchBanner.COMBO).asAnimations())
        setContent { Overlay(event) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.SAME)) }
        assertFalse(
            exists(matchBannerTestTag(MatchBanner.COMBO)),
            "both captions were on screen at once",
        )

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.COMBO)) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(MATCH_BANNER_TEST_TAG) }
    }

    @Test
    fun theSameCaptionTwiceInARowPlaysTwice() = runComposeUiTest {
        var at by mutableStateOf(0)
        setContent { Overlay(BannerEvent(at, listOf(MatchBanner.SAME).asAnimations())) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.SAME)) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(MATCH_BANNER_TEST_TAG) }

        at = 1

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.SAME)) }
    }

    @Test
    fun aCaptionNamesItsRule() = runComposeUiTest {
        setContent { Overlay(BannerEvent(at = 0, listOf(MatchBanner.FALLEN_ACE).asAnimations())) }

        val tag = matchBannerTestTag(MatchBanner.FALLEN_ACE)

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(tag) }
        onNodeWithTag(tag)
            .assertContentDescriptionEquals(MatchBanner.FALLEN_ACE.name)
    }

    @Test
    fun theOverlayIsAbsentWithoutArtwork() = runComposeUiTest {
        val event = BannerEvent(at = 0, listOf(MatchBanner.SAME).asAnimations())
        setContent { MatchBannerOverlay(event) }

        waitForIdle()
        assertFalse(exists(MATCH_BANNER_TEST_TAG), "a caption was drawn with no artwork to draw")
    }

    @Test
    fun aCaptionWithNoArtworkDoesNotBlockTheQueue() = runComposeUiTest {
        var event by mutableStateOf(BannerEvent(0, listOf(MatchBanner.SAME).asAnimations()))
        setContent { MatchBannerOverlay(event) }

        waitForIdle()
        event = BannerEvent(1, listOf(MatchBanner.START).asAnimations())

        waitForIdle()
        assertFalse(
            exists(MATCH_BANNER_TEST_TAG),
            "the queue stalled on a caption it could not draw",
        )
    }

    // ---- The coin flip ------------------------------------------------------

    @Test
    fun theCoinFlipDealsThreeCardsAndClears() = runComposeUiTest {
        val toss = MatchAnimation.Toss(CoinFlip.forced(CardColor.BLUE))
        setContent { MatchBannerOverlay(BannerEvent(at = 0, listOf(toss))) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(COIN_FLIP_TEST_TAG) }
        repeat(CoinFlip.ROLLS) { onNodeWithTag(coinFlipTestTag(it)).assertExists() }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(COIN_FLIP_TEST_TAG) }
    }

    @Test
    fun theCardsShowTheRollsThatWereDrawn() = runComposeUiTest {
        val rolls = listOf(CardColor.RED, CardColor.BLUE, CardColor.RED)
        val toss = MatchAnimation.Toss(CoinFlip(rolls))
        setContent { MatchBannerOverlay(BannerEvent(at = 0, listOf(toss))) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(COIN_FLIP_TEST_TAG) }
        rolls.forEachIndexed { roll, color ->
            onNodeWithTag(coinFlipTestTag(roll), useUnmergedTree = true)
                .assertContentDescriptionEquals("$COIN_FLIP_TEST_TAG-${color.name}")
        }
    }

    @Test
    fun theCoinFlipPlaysBeforeStart() = runComposeUiTest {
        val toss = MatchAnimation.Toss(CoinFlip.forced(CardColor.RED))
        val start = MatchAnimation.Caption(MatchBanner.START)
        setContent { Overlay(BannerEvent(at = 0, listOf(toss, start))) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(COIN_FLIP_TEST_TAG) }
        assertFalse(exists(matchBannerTestTag(MatchBanner.START)), "Start jumped the coin flip")

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.START)) }
    }

    // ---- Swap ---------------------------------------------------------------

    /**
     * Rendered on its own rather than through the overlay, for the reason [SwapCardsCrossing]
     * gives: played from the queue it finishes before the first layout, leaving no node to find.
     */
    @Test
    fun theSwapCrossingPutsACardOfEachSideOnScreen() = runComposeUiTest {
        setContent { SwapCardsCrossing {} }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SWAP_CARDS_TEST_TAG) }
        for (color in CardColor.entries) {
            onNodeWithTag(swapCardsTestTag(color), useUnmergedTree = true)
                .assertContentDescriptionEquals(swapCardsTestTag(color))
        }
    }

    // ---- The beat that opens a board ----------------------------------------

    @Test
    fun theOpeningBeatHoldsTheQueueAndDrawsNothing() = runComposeUiTest {
        val start = MatchAnimation.Caption(MatchBanner.START)
        setContent { Overlay(BannerEvent(at = 0, listOf(MatchAnimation.Opening, start))) }

        waitForIdle()
        assertFalse(exists(MATCH_BANNER_TEST_TAG), "Start jumped the opening beat")

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.START)) }
    }

    @Composable
    private fun Overlay(event: BannerEvent?) {
        val art = remember { BannerArt(AppLocale.EN_US) }
        CompositionLocalProvider(LocalBannerArt provides art) {
            MatchBannerOverlay(event)
        }
    }
}
