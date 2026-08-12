package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.CardCollection
import com.tripletriad.model.GameRules
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a match may be played with — the cards, and the rules that may be drawn.
 *
 * `docs/migration/19-CARD-SETS-AND-FORMATS.md` § What a format is, which is **decided** rather than
 * proposed. A format is not new machinery: it is the four things that are already per-collection,
 * named once and generalised —
 *
 * | Today | Becomes |
 * |---|---|
 * | `Roulette.pools[collection]` | [rules] |
 * | `NpcCatalog.available(collection, …)` | the opponents that declare this format |
 * | `ShopCatalog.offers(collection)` | boosters yielding cards of a set |
 * | `campaigns.forCollection(mode)` | a tournament, which *is* a format plus a ladder |
 *
 * The rule pools are the evidence this is real rather than decorative: FFVIII's carries Elemental
 * and Same Wall, FFXIV's carries Ascension, Descension, Order, Chaos, Swap, Fallen Ace and Reverse.
 * Which rules may be drawn is genuinely a property of the pool of cards being played with.
 *
 * ### This is the transcription step, not the switch
 *
 * `Roulette.pools` is still what the engine draws from — it is compiled into `:core`, and moving it
 * onto this data is a `:core` release. What lands first is the **data plus the proof they agree**:
 * `FormatBundleTest` asserts every format's rules are identical, in order, to the pool `:core`
 * holds for the same block. That test is what makes the switch mechanical rather than a rewrite
 * nobody can check, and it is also what would catch the pools being edited in only one of the two
 * places while both exist.
 *
 * Written in `:shared` for the reason [StarterCatalog] was: it is pure Kotlin with no Compose in
 * it and moves to `:core` verbatim, but `:core` is a published artifact of another repository and
 * adding to it costs a release the client would then have to wait for.
 *
 * @property blocks the sets it admits, by [Card.block]. Blocks and not slugs, so legality is
 *   `card.id shr 8 in format.blocks` — a shift and a set lookup, with nothing to resolve. The slug
 *   stays for paths and for anything a human types.
 * @property rules the rules that may be in force. The one piece of *logic* document 19 moves into
 *   data, and the piece most likely to be tuned without a release.
 */
@Serializable
data class Format(
    val id: String,
    val nameKey: String,
    val blocks: List<Int>,
    val rules: List<String>,
) {
    /** Whether a card of [block] may be played in this format. */
    fun admits(block: Int): Boolean = block in blocks

    /** Whether [cardId] may be played in this format. */
    fun admitsCard(cardId: Int): Boolean = admits(cardId shr Card.BLOCK_SHIFT)
}

/** Every authored format. */
@Serializable
data class FormatCatalog(val formats: List<Format>) {

    operator fun get(id: String): Format? = formats.firstOrNull { it.id == id }

    /**
     * The formats admitting [block], in authored order.
     *
     * A block can appear in several — a single-set format and a free-play one that takes
     * everything — which is why this returns a list rather than the format.
     */
    fun admitting(block: Int): List<Format> = formats.filter { it.admits(block) }

    /**
     * The single format that admits exactly [collection]'s block and nothing else.
     *
     * The bridge while `MODE` still exists: every opponent, shop shelf and ladder is currently
     * chosen by collection, and this is the format that means the same thing. It goes when the
     * opponents declare their formats themselves.
     */
    fun forCollection(collection: CardCollection): Format? =
        formats.firstOrNull { it.blocks == listOf(collection.block) }

    /**
     * Everything wrong with this catalogue, as sentences, or empty when it is sound.
     *
     * The same shape as [StarterCatalog.violations] and for the same reason: these are content
     * bugs, an authoring pass wants to see all of them at once, and the rule is stated here rather
     * than restated in whichever test happens to check it.
     */
    fun violations(sets: List<CardSet>): List<String> = buildList {
        val known = sets.mapTo(mutableSetOf()) { it.block }
        val released = sets.filter { it.released }.mapTo(mutableSetOf()) { it.block }

        val ids = formats.map { it.id }
        if (ids.size != ids.toSet().size) add("format ids are not unique: $ids")

        for (format in formats) {
            if (format.blocks.isEmpty()) add("${format.id} admits no set at all")
            val unknown = format.blocks.filterNot { it in known }
            if (unknown.isNotEmpty()) add("${format.id} names blocks nothing ships: $unknown")

            if (format.rules.isEmpty()) add("${format.id} has an empty rule pool")
            val notRules = format.rules.filterNot(::namesARule)
            if (notRules.isNotEmpty()) {
                add("${format.id} names things that are not rules: $notRules")
            }
            if (format.rules.size != format.rules.toSet().size) {
                add("${format.id} lists a rule twice: ${format.rules}")
            }
        }

        // A released set nothing admits is a set that ships and cannot be played.
        val admitted = formats.flatMapTo(mutableSetOf()) { it.blocks }
        for (block in released - admitted) {
            add("block $block is released and no format admits it")
        }
    }
}

/**
 * Whether [key] names a rule the engine can put in force.
 *
 * Asked by applying it, because `RuleKeys` — the table that would answer directly — is `internal`
 * to `:core`. `withRuleKey` is documented as ignoring a key it does not recognise, so a rule set
 * that comes back unchanged is the answer. It also correctly refuses `RULE_COMBO`, which is in the
 * help screen's list and is a dead constant everywhere else: combo fires whenever Same or Plus
 * captures and no flag turns it on, so a format naming it would be a format promising nothing.
 */
private fun namesARule(key: String): Boolean = GameRules().withRuleKey(key) != GameRules()

/**
 * Parses the format catalogue.
 *
 * Split from the loader for the reason [CardCatalogParser] is: this layer must stay free of any way
 * to *obtain* the text, so the same parser can serve a server that gets it from somewhere else.
 */
object FormatCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): FormatCatalog = json.decodeFromString(text)
}
