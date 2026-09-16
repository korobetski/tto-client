package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The search placeholder on a phone. "Chercher une carte" beside the collection's count wrapped
 * onto two lines at 360 dp, and the field grew by one to hold it.
 *
 * Read from the placeholder's own text layout rather than the field's height: a height would
 * also move with the theme's padding, and only the line count says why.
 */
@OptIn(ExperimentalTestApi::class)
class SearchFieldUiTest {
    @Test
    fun theCollectionSaysSearchOnOneLineOnANarrowPhone() = runSkikoComposeUiTest(
        size = PHONE,
        density = Density(1f),
    ) {
        setContent { TestApp(store = InMemorySettingsStore("""{"language":"fr_FR"}""")) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)

        val layout = placeholder(CARD_SEARCH_TEST_TAG, "Chercher")
        assertEquals(1, layout.lineCount, "the placeholder wrapped")
        // Not `hasVisualOverflow`: inside the field it reports a width overflow even for a word
        // drawn whole (seen for all four languages on 2026-09-15), where an ellipsis is not.
        assertFalse(layout.isLineEllipsized(0), "the placeholder was cut short")
    }

    /** The guard for a language longer than any of the four: cut short, never a second line. */
    @Test
    fun aPlaceholderTooLongForTheFieldStaysOnOneLine() = runComposeUiTest {
        val strings = runBlocking { loadStrings(AppLocale.EN_US) }
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    Box(modifier = Modifier.width(NARROW.dp)) {
                        TtoSearchField(
                            value = "",
                            onValueChange = {},
                            tag = TAG,
                            placeholder = LONG,
                        )
                    }
                }
            }
        }

        assertEquals(1, placeholder(TAG, LONG).lineCount, "a long placeholder took two lines")
    }

    private fun ComposeUiTest.placeholder(field: String, text: String): TextLayoutResult {
        val node = onAllNodes(
            hasText(text) and hasAnyAncestor(hasTestTag(field)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().single()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private companion object {
        /** The narrowest common Android width, where the French placeholder first wrapped. */
        val PHONE = Size(360f, 780f)
        const val NARROW = 160
        const val TAG = "search-under-test"
        const val LONG = "A placeholder far longer than a field this narrow can hold"
    }
}
