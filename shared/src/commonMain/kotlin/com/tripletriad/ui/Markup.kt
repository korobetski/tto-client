package com.tripletriad.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle

/**
 * The handful of HTML tags the imported locale bundles carry, as styled text.
 *
 * `tto-fr_FR.json` is the only bundle with any: seventeen `<i>` pairs and two line breaks, across
 * the rule help and the card descriptions. They are Square Enix's own markup, kept verbatim — the
 * bundle is not the place to strip them, by this repository's rule about correcting imported
 * wording through `app-<tag>.json` rather than rewriting it in place — and until now they reached
 * the screen as literal angle brackets, tags and all.
 *
 * ### Why this is hand-written
 *
 * `AnnotatedString.fromHtml` exists, and is **Android-only**: it is absent from
 * `org.jetbrains.compose.ui:ui-text`'s common and desktop artifacts at 1.11.1, being a wrapper over
 * `android.text.Html`. Using it would mean an `expect`/`actual` for text formatting, which is not a
 * platform capability, and three of the four targets would still need this function. A full parser
 * would also decode entities and swallow unknown tags — worth having only if the data needed it,
 * and it does not: the bundles contain no entities and no other tags.
 *
 * What would change the mind: markup that nests or carries attributes — a `<font color>`, an
 * `<a href>` — at which point the closed set below stops being a set and a parser earns its place.
 *
 * ### Anything unrecognised is text, not markup
 *
 * A `<` that does not begin one of [TAGS] is appended as the character it is. Same instinct as
 * reporting a data defect rather than repairing it in silence: a bundle that grows a tag this build
 * does not know should show it to somebody, rather than have it disappear.
 */
internal fun markup(text: String): AnnotatedString = buildAnnotatedString {
    var at = 0
    var italicFrom: Int? = null

    while (at < text.length) {
        val open = text.indexOf('<', at)
        if (open < 0) {
            append(text.substring(at))
            break
        }
        append(text.substring(at, open))

        val tag = TAGS.firstOrNull { text.startsWith(it, open, ignoreCase = true) }
        when (tag) {
            // Not markup this build knows, so it is the character it looks like.
            null -> append('<')
            ITALIC_OPEN -> italicFrom = length
            ITALIC_CLOSE -> {
                italicFrom?.let { addStyle(ITALIC, it, length) }
                italicFrom = null
            }
            // Every remaining tag is a line break, in one of its three spellings.
            else -> append('\n')
        }
        at = open + (tag?.length ?: 1)
    }

    // An `<i>` nobody closed runs to the end rather than being dropped: the sentence was meant to
    // be emphasised, and losing the emphasis hides the fault where showing it does not.
    italicFrom?.let { addStyle(ITALIC, it, length) }
}

private const val ITALIC_OPEN = "<i>"

private const val ITALIC_CLOSE = "</i>"

private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)

/**
 * Every tag this build reads, longest spelling of each first so a prefix cannot win.
 *
 * `<br/>` and `<br>` are both in use — the French bundle spells it both ways, in two card
 * descriptions — and neither is worth normalising upstream for two occurrences.
 */
private val TAGS = listOf(ITALIC_CLOSE, ITALIC_OPEN, "<br />", "<br/>", "<br>")
