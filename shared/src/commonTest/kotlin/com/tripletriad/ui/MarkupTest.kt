package com.tripletriad.ui

import androidx.compose.ui.text.font.FontStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [markup] against the four tags the imported bundles actually contain, and against the text that
 * only looks like a tag.
 *
 * The strings quoted here are shortened from `tto-fr_FR.json`; the shapes are its own.
 */
class MarkupTest {

    // ---- What is drawn ----------------------------------------------------

    @Test
    fun theTagsThemselvesNeverReachTheScreen() {
        val drawn = markup("<i>FF14 uniquement</i>\nInverse la puissance des cartes.").text

        assertEquals("FF14 uniquement\nInverse la puissance des cartes.", drawn)
    }

    @Test
    fun textWithNoTagsIsLeftExactlyAsItIs() {
        val plain = "Augmente d'un point la puissance des cartes."

        assertEquals(plain, markup(plain).text)
        assertTrue(markup(plain).spanStyles.isEmpty(), "nothing to style, so no styles")
    }

    // ---- The emphasis -----------------------------------------------------

    @Test
    fun whatSatBetweenTheItalicTagsIsTheThingItalicised() {
        val styled = markup("<i>FF14 uniquement</i>\nInverse la puissance.")

        val span = styled.spanStyles.single()
        assertEquals(FontStyle.Italic, span.item.fontStyle)
        assertEquals("FF14 uniquement", styled.text.substring(span.start, span.end))
    }

    /** `STR_FF14_CARD_80_DESC` emphasises two separate words in one sentence. */
    @Test
    fun eachItalicRunIsStyledSeparately() {
        val styled = markup("Il y a des choses qu'on <i>doit</i> faire, on <i>peut</i> aussi.")

        assertEquals(2, styled.spanStyles.size)
        assertEquals(
            listOf("doit", "peut"),
            styled.spanStyles.map { styled.text.substring(it.start, it.end) },
        )
    }

    @Test
    fun anItalicRunInTheMiddleLeavesWhatSurroundsItAlone() {
        val styled = markup("touchée par le <i>septième fléau</i>. Un dragon.")

        val span = styled.spanStyles.single()
        assertEquals("septième fléau", styled.text.substring(span.start, span.end))
        assertEquals("touchée par le septième fléau. Un dragon.", styled.text)
    }

    // ---- The line breaks --------------------------------------------------

    @Test
    fun everySpellingOfABreakBecomesOneNewline() {
        for (tag in listOf("<br>", "<br/>", "<br />")) {
            assertEquals(
                "Nous autres\net la première",
                markup("Nous autres${tag}et la première").text,
            )
        }
    }

    // ---- What is not markup -----------------------------------------------

    /**
     * A tag this build does not know stays on screen.
     *
     * The deliberate choice, and the same instinct as reporting a data defect rather than repairing
     * it in silence: a bundle that grows `<font color>` should make somebody look at it.
     */
    @Test
    fun aTagThisBuildDoesNotKnowIsLeftWhereSomebodyWillSeeIt() {
        val drawn = markup("un <b>gras</b> inattendu").text

        assertEquals("un <b>gras</b> inattendu", drawn)
    }

    @Test
    fun aBareAngleBracketIsACharacterAndNotTheStartOfATag() {
        assertEquals("5 < 6 et 7 > 6", markup("5 < 6 et 7 > 6").text)
    }

    @Test
    fun anEmptyStringSurvives() {
        assertEquals("", markup("").text)
    }

    // ---- Malformed input --------------------------------------------------

    /** Emphasis nobody closed runs to the end: losing it hides the fault, showing it does not. */
    @Test
    fun anUnclosedItalicRunsToTheEnd() {
        val styled = markup("avant <i>et tout le reste")

        val span = styled.spanStyles.single()
        assertEquals("et tout le reste", styled.text.substring(span.start, span.end))
    }

    @Test
    fun aCloseWithNoOpenIsDroppedRatherThanThrowing() {
        val styled = markup("rien à fermer</i> ici")

        assertEquals("rien à fermer ici", styled.text)
        assertTrue(styled.spanStyles.isEmpty())
    }

    @Test
    fun anUnterminatedTagIsTextRatherThanRunningOffTheEnd() {
        assertEquals("fin abrupte <i", markup("fin abrupte <i").text)
    }
}
