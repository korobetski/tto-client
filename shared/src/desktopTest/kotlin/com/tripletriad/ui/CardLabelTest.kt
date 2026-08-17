package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CardLabelTest {
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    @Test
    fun aFaceUpCardNamesItselfAndItsPowers() = card(showBack = false) {
        onNodeWithContentDescription("$NAME, 9 8 2 5").assertExists()
    }

    @Test
    fun aFaceDownCardGivesNothingAway() = card(showBack = true) {
        val tree = onRoot().printToString(maxDepth = Int.MAX_VALUE)

        assertFalse(NAME in tree, "a hidden card named itself: $tree")
        assertFalse("9 8 2 5" in tree, "a hidden card gave away its powers: $tree")
        assertTrue("Face-down" in tree, "a hidden card said nothing at all: $tree")
    }

    @Test
    fun aFaceDownCardStillAnnouncesItself() = card(showBack = true) {
        onNodeWithContentDescription("Face-down card").assertExists()
    }

    private fun card(
        showBack: Boolean,
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    CardFace(card = subject, showBack = showBack)
                }
            }
        }
        block()
    }

    private val subject = Card(
        id = Card.idFor(block = 1, number = 1),
        nameKey = NAME_KEY,
        name = NAME,
        top = 9,
        right = 8,
        bottom = 2,
        left = 5,
        rarity = 1,
    )

    private companion object {
        const val NAME_KEY = "STR_FF14_CARD_1"
        const val NAME = "Dodo"
    }
}
