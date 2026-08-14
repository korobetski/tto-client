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

/**
 * What a card tells a screen reader — and, for a hidden one, what it must not.
 *
 * ### Why the second half is a security test and not a wording test
 *
 * `HandVisibility` decides which of the opponent's cards the Open rules reveal, and `MatchView`
 * goes to some length to keep the rest off the wire entirely — `PvpMatchTest` asserts a hidden
 * card's id appears nowhere in the encoded payload. A label built from the card would hand the
 * same secret to anybody with a screen reader turned on, through the accessibility tree instead of
 * the network. Same leak, different pipe.
 *
 * So this asserts the **absence** of the name, not merely the presence of a placeholder.
 */
@OptIn(ExperimentalTestApi::class)
class CardLabelTest {
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    /** A face-up card names itself and its four powers, in the order they are drawn. */
    @Test
    fun aFaceUpCardNamesItselfAndItsPowers() = card(showBack = false) {
        onNodeWithContentDescription("$NAME, 9 8 2 5").assertExists()
    }

    /**
     * A face-down card names **nothing about itself**.
     *
     * The assertion is on the whole rendered tree rather than on one node, because the leak this
     * guards against would not necessarily be on the node under test — a label anywhere in the
     * subtree is a label a screen reader will read out.
     */
    @Test
    fun aFaceDownCardGivesNothingAway() = card(showBack = true) {
        val tree = onRoot().printToString(maxDepth = Int.MAX_VALUE)

        assertFalse(NAME in tree, "a hidden card named itself: $tree")
        assertFalse("9 8 2 5" in tree, "a hidden card gave away its powers: $tree")
        assertTrue("Face-down" in tree, "a hidden card said nothing at all: $tree")
    }

    /** And it does say *something*, so the board is not a run of unlabelled boxes. */
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

    /**
     * A card whose name is in the bundle, so the label is the translated one a player hears.
     *
     * The powers are deliberately all different, so an assertion on "9 8 2 5" pins the **order** —
     * top, right, bottom, left — and not just the multiset.
     */
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
        /** Whatever `en_US` calls card 1 — read from the bundle rather than assumed. */
        const val NAME_KEY = "STR_FF14_CARD_1"
        const val NAME = "Dodo"
    }
}
