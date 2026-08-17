package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ResumeCardTest {
    @Test
    fun aRestoredSessionSaysSoAndOffersToContinue() = runComposeUiTest {
        var went = 0
        setContent { Menu(account("Kaelith", SessionState.RESTORED, onGo = { went += 1 })) }

        onNodeWithTag(MENU_RESUME_STATE_TEST_TAG).assertTextEquals("Signed in")
        onNodeWithTag(MENU_RESUME_GO_TEST_TAG).assertTextEquals("Continue")
        onNodeWithTag(MENU_RESUME_GO_TEST_TAG).performClick()
        assertEquals(1, went, "continuing should be one action, not a sign-in")
    }

    @Test
    fun aLapsedSessionSaysSoAndOffersToSignInAgain() = runComposeUiTest {
        setContent { Menu(account("Kaelith", SessionState.LAPSED)) }

        onNodeWithTag(MENU_RESUME_STATE_TEST_TAG).assertTextEquals("Session expired")
        onNodeWithTag(MENU_RESUME_GO_TEST_TAG).assertTextEquals("Sign in again")
    }

    @Test
    fun aSessionStillBeingRestoredSaysSoAndCannotBePressed() = runComposeUiTest {
        setContent { Menu(account("Kaelith", SessionState.CONNECTING)) }

        onNodeWithTag(MENU_RESUME_STATE_TEST_TAG).assertTextEquals("Reconnecting…")
        onNodeWithTag(MENU_RESUME_GO_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun switchingAccountIsOfferedBesideContinuing() = runComposeUiTest {
        var switched = 0
        setContent {
            Menu(account("Kaelith", SessionState.RESTORED, onSwitch = { switched += 1 }))
        }

        onNodeWithTag(MENU_RESUME_SWITCH_TEST_TAG).performClick()
        assertEquals(1, switched, "the card should offer a way out of this account")
    }

    @Test
    fun rememberingNobodyDrawsNoCard() = runComposeUiTest {
        setContent { Menu(remembered = null) }

        assertFalse(exists(MENU_RESUME_TEST_TAG), "there is nobody to resume")
        assertTrue(exists(MENU_PLAY_TEST_TAG), "and the menu is otherwise itself")
    }

    private fun account(
        username: String,
        state: SessionState,
        onGo: () -> Unit = {},
        onSwitch: () -> Unit = {},
    ) = RememberedAccount(username, state, onGo, onSwitch)
}

@Composable
private fun Menu(remembered: RememberedAccount?) {
    val strings = remember { runBlocking { loadStrings(AppLocale.EN_US) } }
    TripleTriadTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
            MainMenuScreen(
                active = null,
                remembered = remembered,
                connectivity = null,
                onPlay = {},
                onProfiles = {},
                onServers = {},
                onOptions = {},
                onQuit = {},
            )
        }
    }
}
