package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.CLIENT_VERSION
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameSave
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The first screen, driven by the [TitleEntry]s `App` can hand it.
 *
 * Mounted directly rather than through `TestApp`, because two of the five states are a *round trip
 * in flight* — reachable through the whole app only by holding a socket open, and the point here is
 * what the screen does with the answer, not how the answer is fetched.
 */
@OptIn(ExperimentalTestApi::class)
class TitleScreenTest {
    @Test
    fun aRestoredSessionNamesTheCharacterAndTheWholeScreenContinues() = runComposeUiTest {
        var went = 0
        setContent {
            Title(
                profile = GameSave(username = "Kaelith", level = 7),
                entry = TitleEntry(
                    promptKey = StringKeys.TITLE_CONTINUE,
                    onContinue = { went += 1 },
                ),
            )
        }

        onNodeWithTag(TITLE_NAME_TEST_TAG).assertTextEquals("Kaelith")
        onNodeWithTag(TITLE_PROMPT_TEST_TAG).assertTextEquals("Tap to continue")

        onNodeWithTag(TITLE_CONTINUE_TEST_TAG).performClick()
        assertEquals(1, went, "one tap anywhere should be the whole of it")
    }

    @Test
    fun aLapsedSessionSaysSoAndTheTapLeadsToSigningInAgain() = runComposeUiTest {
        var went = 0
        setContent {
            Title(
                entry = TitleEntry(
                    promptKey = StringKeys.SESSION_LAPSED,
                    alarming = true,
                    onContinue = { went += 1 },
                ),
            )
        }

        onNodeWithTag(TITLE_PROMPT_TEST_TAG).assertTextEquals("Session expired")

        onNodeWithTag(TITLE_CONTINUE_TEST_TAG).performClick()
        assertEquals(1, went, "an expired session is still somewhere to go, not a dead end")
    }

    @Test
    fun aTapTakenWhileTheSessionIsStillOutIsHeldRatherThanDropped() = runComposeUiTest {
        var went = 0
        // Driven from the test rather than from a timer, so the tap is provably taken *before*
        // the round trip comes back — which is the only ordering the behaviour is about.
        val waiting = mutableStateOf(true)
        setContent {
            Title(
                entry = if (waiting.value) {
                    TitleEntry(promptKey = StringKeys.SESSION_CONNECTING, isWaiting = true)
                } else {
                    TitleEntry(promptKey = StringKeys.TITLE_CONTINUE, onContinue = { went += 1 })
                },
            )
        }

        onNodeWithTag(TITLE_PROMPT_TEST_TAG).assertTextEquals("Reconnecting…")

        // The whole screen is a target while the answer is out — it just cannot act on one yet.
        onNodeWithTag(TITLE_CONTINUE_TEST_TAG).performClick()
        waitForIdle()
        assertEquals(0, went, "there was nowhere to go yet")

        // And when the answer lands, the tap that was taken is the tap that is spent. A player
        // who touches a screen that is not ready touches it again, and the second one would
        // otherwise land on whatever the first opened.
        waiting.value = false
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { went == 1 }
    }

    @Test
    fun withNothingToContinueIntoTheScreenIsNotAButtonAtAll() = runComposeUiTest {
        var picked = 0
        setContent {
            Title(
                entry = TitleEntry(
                    promptKey = StringKeys.NO_PROFILE,
                    choices = listOf(
                        TitleChoice(StringKeys.NEW_PROFILE, "new") { picked += 1 },
                    ),
                ),
            )
        }

        assertFalse(exists(TITLE_CONTINUE_TEST_TAG), "a tap was offered with nowhere to go")
        onNodeWithTag(TITLE_PROMPT_TEST_TAG)
            .assertTextEquals("No character yet — create one to play.")

        onNodeWithTag(titleChoiceTestTag("new")).performClick()
        assertEquals(1, picked, "the one thing there is to do has to be a control")
    }

    @Test
    fun theTapTargetDoesNotSwallowTheScreenItCovers() = runComposeUiTest {
        setContent {
            Title(
                profile = GameSave(username = "Kaelith"),
                entry = TitleEntry(
                    promptKey = StringKeys.TITLE_CONTINUE,
                    onContinue = {},
                ),
            )
        }

        // The regression this is here for: the tap target was the content's *parent*, and
        // `clickable` merges its descendants — so the name, the prompt, the version and the
        // footer collapsed into one node that a screen reader read out in a single breath.
        // Each of these being addressable is the same thing as each of them being announced.
        assertTrue(exists(TITLE_NAME_TEST_TAG), "the character's name was merged away")
        assertTrue(exists(TITLE_PROMPT_TEST_TAG), "the prompt was merged away")
        assertTrue(exists(TITLE_VERSION_TEST_TAG), "the version was merged away")
        assertTrue(exists(TITLE_OPTIONS_TEST_TAG), "the settings button was merged away")
    }

    @Test
    fun theVersionIsOnScreenBecauseThatIsWhereABugReportStarts() = runComposeUiTest {
        setContent { Title(entry = TitleEntry(promptKey = StringKeys.NO_PROFILE)) }

        onNodeWithTag(TITLE_VERSION_TEST_TAG).assertTextEquals("v$CLIENT_VERSION")
    }

    @Test
    fun switchingAccountIsOfferedOnlyWhereThereIsSomebodyToLeave() = runComposeUiTest {
        var switched = 0
        setContent {
            Title(
                profile = GameSave(username = "Kaelith"),
                entry = TitleEntry(promptKey = StringKeys.TITLE_CONTINUE, onContinue = {}),
                onSwitchAccount = { switched += 1 },
            )
        }

        onNodeWithTag(TITLE_SWITCH_TEST_TAG).performClick()
        assertEquals(1, switched, "the screen should offer a way out of this account")
    }

    @Test
    fun aDeviceNobodyHasSignedInOnOffersNothingToSwitchAwayFrom() = runComposeUiTest {
        setContent { Title(entry = TitleEntry(promptKey = StringKeys.NO_PROFILE)) }

        assertFalse(exists(TITLE_SWITCH_TEST_TAG), "there was no account to leave")
    }
}

@Composable
private fun Title(
    entry: TitleEntry,
    profile: GameSave? = null,
    onSwitchAccount: (() -> Unit)? = null,
) {
    val strings = remember { runBlocking { loadStrings(AppLocale.EN_US) } }
    TripleTriadTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
            TitleScreen(
                profile = profile,
                entry = entry,
                // No card back and no server: this is about the states, and the backdrop draws
                // nothing at all without art — see `TitleBackdrop`.
                back = null,
                connectivity = null,
                onServers = {},
                onSwitchAccount = onSwitchAccount,
                onOptions = {},
                onQuit = {},
            )
        }
    }
}
