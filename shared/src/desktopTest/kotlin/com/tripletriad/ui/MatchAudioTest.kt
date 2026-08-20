package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.audio.RecordingAudioPlayer
import com.tripletriad.audio.Sound
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.PLACEMENTS_PER_MATCH
import com.tripletriad.settings.InMemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MatchAudioTest {
    private val stub = PveStubServer()

    private val audio = RecordingAudioPlayer()

    @Test
    fun theMusicStartsWithTheMatchAndStopsWhenItIsLeft() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio) }
        awaitMenu()

        // `MenuScreen` never called `shuffleLoop` — nothing plays on the menu but the tap.
        assertFalse(Sound.MATCH_MUSIC in audio, "the music started before a match")

        // Play leads straight to the dashboard of the account's profile, so reaching a board is
        // the whole flow — and the music must not start on any screen along the way.
        openDashboard()
        openOpponents()
        assertFalse(Sound.MATCH_MUSIC in audio, "the music started before a board was up")

        challenge()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { Sound.MATCH_MUSIC in audio }
        val stopsDuringMatch = audio.musicStops

        onNodeWithTag(MATCH_EXIT_TEST_TAG).performClick()
        waitForIdle()

        assertTrue(audio.musicStops > stopsDuringMatch, "leaving the match left the music running")
    }

    @Test
    fun openingAMatchPlaysTheDealSound() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio) }
        startMatch()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { Sound.MATCH_OPEN in audio }
    }

    @Test
    fun everyMenuButtonClicks() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        awaitMenu()

        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        assertEquals(listOf(Sound.UI_CLICK), audio.played.filter { it == Sound.UI_CLICK })
    }

    @Test
    fun eachPlayerPlacementPlaysTheSoundThatMatchesWhatItDid() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio) }
        startMatch()

        var withCaptures = 0
        var without = 0
        while (!isFinished()) {
            awaitPlayer()
            if (isFinished()) break
            audio.clear()
            val before = score()
            playOneCard()
            val after = score()

            // Blue played, so red losing a card is the capture.
            val captures = before.second - after.second > 0
            if (captures) withCaptures++ else without++

            assertEquals(
                captures,
                Sound.CARD_CAPTURED in audio,
                "a placement that captured=$captures played ${audio.played} " +
                    "(score $before -> $after)",
            )
            assertEquals(!captures, Sound.CARD_PLACED in audio, "played ${audio.played}")

            waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isPlayerTurn() || isFinished() }
        }

        val placements = withCaptures + without
        assertTrue(placements >= PLAYER_PLACEMENTS_MIN, "blue played only $placements times")
        assertTrue(withCaptures > 0, "no capture in a whole match — one branch went unexercised")
    }

    @Test
    fun anOpponentPlacementAlsoSounds() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio) }
        startMatch()

        playOneCard()
        audio.clear()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            Sound.CARD_PLACED in audio || Sound.CARD_CAPTURED in audio
        }

        assertTrue(
            Sound.CARD_PLACED in audio || Sound.CARD_CAPTURED in audio,
            "the opponent's own placement was silent: ${audio.played}",
        )
    }

    @Test
    fun theLastPlacementPlaysTheOutcomeInsteadOfATurnChange() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio) }
        startMatch()
        // The opponent may already have played, if the coin flip favoured it — so the window this
        // test measures starts at whatever is on the board, not at zero.
        val alreadyPlaced = placementsMade()
        audio.clear()

        playOut()

        assertEquals(
            PLACEMENTS_PER_MATCH - 1 - alreadyPlaced,
            audio.played.count { it == Sound.TURN_CHANGE },
            "one turn change per placement except the last: ${audio.played}",
        )
        val outcomeSounds = audio.played.filter { it == Sound.BLUE_WINS || it == Sound.RED_WINS }
        val drawn = !isVisible("You win") && !isVisible("You lose")
        if (drawn) {
            assertTrue(outcomeSounds.isEmpty(), "a draw should be silent: $outcomeSounds")
        } else {
            assertEquals(1, outcomeSounds.size, "expected one winner sound: ${audio.played}")
        }
    }

    @Test
    fun anUnfinishedMatchPlaysATurnChange() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio) }
        startMatch()

        audio.clear()
        playOneCard()

        assertTrue(Sound.TURN_CHANGE in audio, "played: ${audio.played}")
        assertFalse(Sound.BLUE_WINS in audio)
        assertFalse(Sound.RED_WINS in audio)
    }

    @Test
    fun theRematchControlSoundsAndDealsAgain() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio) }
        startMatch()
        playOut()
        audio.clear()

        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        waitForIdle()

        assertTrue(Sound.NEW_MATCH in audio, "played: ${audio.played}")
        // The deal sound comes with the cards, which is when the referee's board arrives rather
        // than when the screen opens — the two were the same moment before the match was refereed.
        awaitBoard()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { Sound.MATCH_OPEN in audio }
    }

    @Test
    fun theStoredVolumesAreHandedToThePlayer() = runComposeUiTest {
        setContent {
            App(
                store = InMemorySettingsStore(STORED_VOLUMES),
                audio = audio,
            )
        }
        awaitMenu()

        assertEquals(STORED_BACKGROUND to STORED_NOISE, audio.volumes)
    }

    @Test
    fun changingAVolumeInTheOptionsReachesThePlayer() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        awaitMenu()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(OPTIONS_NOISE_VOLUME_TEST_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { audio.volumes?.second == 0f }

        assertEquals(0f, audio.volumes?.second)
    }

    @Test
    fun aChainSoundsOncePerGenerationAndAfterTheCaptureThatStartedIt() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        newCharacter()
        openLessons()
        onNodeWithTag(lessonRowTestTag(COMBO_LESSON_ROW)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }

        audio.clear()
        playOneCard()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { Sound.COMBO in audio }

        assertEquals(
            1,
            audio.played.count { it == Sound.COMBO },
            "one generation, one combo: ${audio.played}",
        )
        assertTrue(Sound.CARD_CAPTURED in audio, "the placement captured: ${audio.played}")
        assertTrue(
            audio.played.indexOf(Sound.CARD_CAPTURED) < audio.played.indexOf(Sound.COMBO),
            "the chain sounds after the capture it came from: ${audio.played}",
        )
        assertTrue(
            audio.played.indexOf(Sound.COMBO) < audio.played.indexOf(Sound.BLUE_WINS),
            "and the result is announced after the last card turns: ${audio.played}",
        )
    }

    private companion object {
        const val COMBO_LESSON_ROW = 3

        const val PLAYER_PLACEMENTS_MIN = 4

        const val STORED_BACKGROUND = 0.25f
        const val STORED_NOISE = 0.5f

        val STORED_VOLUMES = """
            {"language":"en_US","background_volume":$STORED_BACKGROUND,"noise_volume":$STORED_NOISE}
        """.trimIndent()
    }
}
