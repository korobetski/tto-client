package com.tripletriad.settings

/**
 * How much larger than its own sizes the whole interface is drawn.
 *
 * ### Why a setting, when every platform already has one
 *
 * The system's scale is chosen once for the whole screen, for documents and every other window. A
 * maximised window on a 2560 × 1440 monitor at 100 % is 2560 dp wide, and the game drew its board,
 * its text and its buttons there at the sizes a phone gets. In the browser the answer was the page
 * zoom, which the player has to know about and which the tab keeps for the whole site.
 *
 * ### [AUTO] is the default
 *
 * It enlarges only a window that is large in both directions — see `factor` in `ui/` for where the
 * steps are. A phone, a tablet and an ordinary laptop window stay at 100 %, so a player who never
 * opens the setting sees what they saw before it existed.
 *
 * [percent] is what the chip prints; the tag is the same number as text, so the file reads `"125"`
 * and not a factor a later build could not name back.
 */
enum class UiScale(val tag: String, val percent: Int?) {
    AUTO("auto", null),
    NORMAL("100", 100),
    LARGE("125", 125),
    LARGER("150", 150),
    LARGEST("175", 175),
    ;

    companion object {
        val Default: UiScale = AUTO

        fun forTag(tag: String): UiScale? = entries.firstOrNull { it.tag == tag }
    }
}
