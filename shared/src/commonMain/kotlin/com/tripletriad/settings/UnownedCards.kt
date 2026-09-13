package com.tripletriad.settings

/**
 * How the collection draws a card the profile has never owned.
 *
 * ### Why a setting
 *
 * [UNKNOWN] is FFXIV's own answer, and the right default: a set read for what is missing should
 * leave something to find. It is also the answer a player who is completing the set with a wiki
 * open has no use for, and [DIMMED] is the screen this port had before — every card readable,
 * the ones not owned faded. [HIDDEN] is the third question a collection is read for, "what do I
 * have", asked without the 500 tiles that are not the answer.
 *
 * ### What it changes beyond the tile
 *
 * [UNKNOWN] keeps back everything the "?" does not print — name, picture, element, sides — and so
 * the filters cannot hand them back either: the element menu does not answer with a card whose
 * element is hidden, and ordering by power puts such cards after the rest. The rarity and the
 * number stay, because the detail panel prints both. See `CardFilters.matches`.
 *
 * The collection only. The shop, the auction house and a pack's reveal show a card because it is
 * on offer, and a card on offer is not one to be found.
 */
enum class UnownedCards(val tag: String) {
    UNKNOWN("unknown"),
    DIMMED("dimmed"),
    HIDDEN("hidden"),
    ;

    /** Named in the bundles rather than here, so the chip reads in the player's language. */
    val labelKey: String get() = "APP_UNOWNED_$name"

    companion object {
        val Default: UnownedCards = UNKNOWN

        fun forTag(tag: String): UnownedCards? = entries.firstOrNull { it.tag == tag }
    }
}
