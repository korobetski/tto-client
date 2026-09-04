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
        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio)
        }
        awaitTitle()

        // The title screen never calls `shuffleLoop` — nothing plays there but the tap.
        assertFalse(Sound.MATCH_MUSIC in audio, "the music started before a match")

        // Play leads straight to the dashboard of the account's profile, so reaching a board is
        // the whole flow — and the music must not start on any screen along the way.
        openDashboard()
        openOpponents()
        assertFalse(Sound.MATCH_MUSIC in audio, "the music started before a board was up")

        challenge()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { Sound.MATCH_MUSIC in audio }
        val stopsDuringMatch = audio.musicStops

        leaveMatch()
        waitForIdle()

        assertTrue(audio.musicStops > stopsDuringMatch, "leaving the match left the music running")
    }

    @Test
    fun openingAMatchPlaysTheDealSound() = runComposeUiTest {
        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio)
        }
        startMatch()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { Sound.MATCH_OPEN in audio }
    }

    @Test
    fun everyMenuButtonClicks() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), audio = audio) }
        awaitTitle()

        onNodeWithTag(TITLE_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        assertEquals(listOf(Sound.UI_CLICK), audio.played.filter { it == Sound.UI_CLICK })
    }

    /**
     * At the shipped pace, because the window this measures *is* the shipped pace.
     *
     * It clears the recorder, plays one card and reads back what sounded — which only attributes
     * the sounds to the player's own placement for as long as the opponent has not answered yet.
     * `OPPONENT_PAUSE_MS` is what buys that. At [TEST_PACING] the reply lands inside the window and
     * the assertion reads `[CARD_PLACED, TURN_CHANGE, CARD_CAPTURED, TURN_CHANGE]` — two placements
     * counted as one, which is a broken measurement rather than a broken app.
     */
    @Test
    fun eachPlayerPlacementPlaysTheSoundThatMatchesWhatItDid() = runComposeUiTest {
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                server = stub.connection,
                audio = audio,
                pacing = Pacing.Default,
            )
        }
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
        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio)
        }
        startMatch()

        playOneCard()

        // **Counted, not cleared.** `clear()` here was a race the suite lost about one run in
        // five: the opponent's reply is walked onto the board a couple of hundred milliseconds
        // after the player's card lands, and when it sounded *before* the clear rather than after,
        // the clear deleted the one sound the wait below was waiting for — so the wait timed out
        // on a match where everything had gone right. Counting cannot lose a sound it has already
        // seen. Two placement sounds is the player's card and the reply, which is the assertion.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { audio.placements() >= BOTH_PLACEMENTS }

        assertTrue(
            audio.placements() >= BOTH_PLACEMENTS,
            "the opponent's own placement was silent: ${audio.played}",
        )
    }

    @Test
    fun theLastPlacementPlaysTheOutcomeInsteadOfATurnChange() = runComposeUiTest {
        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio)
        }
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
        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio)
        }
        startMatch()

        audio.clear()
        playOneCard()

        assertTrue(Sound.TURN_CHANGE in audio, "played: ${audio.played}")
        assertFalse(Sound.BLUE_WINS in audio)
        assertFalse(Sound.RED_WINS in audio)
    }

    @Test
    fun theRematchControlSoundsAndDealsAgain() = runComposeUiTest {
        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection, audio = audio)
        }
        startMatch()
        playOut()
        audio.clear()

        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        waitForIdle()

        assertTrue(Sound.NEW_MATCH in audio, "played: ${audio.played}")
        // And then the deck question again, because a second match is a second deal — see
        // `rematchExit`. `settleDeck` answers it and waits for the board.
        settleDeck()
        // The deal sound comes with the cards, which is when the referee's board arrives rather
        // than when the screen opens — the two were the same moment before the match was refereed.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { Sound.MATCH_OPEN in audio }
    }

    @Test
    fun theStoredVolumesAreHandedToThePlayer() = runComposeUiTest {
        setContent {
            TestApp(
                store = InMemorySettingsStore(STORED_VOLUMES),
                audio = audio,
            )
        }
        awaitTitle()

        assertEquals(STORED_BACKGROUND to STORED_NOISE, audio.volumes)
    }

    @Test
    fun changingAVolumeInTheOptionsReachesThePlayer() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), audio = audio) }
        awaitTitle()
        onNodeWithTag(TITLE_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(OPTIONS_NOISE_VOLUME_TEST_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { audio.volumes?.second == 0f }

        assertEquals(0f, audio.volumes?.second)
    }

    @Test
    fun aChainSoundsOncePerGenerationAndAfterTheCaptureThatStartedIt() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), audio = audio) }
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

    /** How many cards have been heard landing — either sound a placement can make. */
    private fun RecordingAudioPlayer.placements(): Int =
        played.count { it == Sound.CARD_PLACED || it == Sound.CARD_CAPTURED }

    private companion object {
        /** The player's card and the opponent's reply. */
        const val BOTH_PLACEMENTS = 2

        const val COMBO_LESSON_ROW = 3

        const val PLAYER_PLACEMENTS_MIN = 4

        const val STORED_BACKGROUND = 0.25f
        const val STORED_NOISE = 0.5f

        val STORED_VOLUMES = """
            {"language":"en_US","background_volume":$STORED_BACKGROUND,"noise_volume":$STORED_NOISE}
        """.trimIndent()
    }
}
