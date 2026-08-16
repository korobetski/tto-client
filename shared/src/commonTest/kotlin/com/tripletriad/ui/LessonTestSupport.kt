package com.tripletriad.ui

import com.tripletriad.data.CardCatalog
import com.tripletriad.model.Card
import com.tripletriad.model.CardType

/**
 * The twenty-eight cards the lessons name, built here rather than loaded.
 *
 * `commonTest` has no filesystem on Kotlin/Native, and what the lesson tests are about is the
 * *positions* rather than the loader — which `CardCatalogTest` covers. The powers are transcribed
 * from `cards.json`, and `TutorialPuzzleTest.theNumbersTheLinesNameAreStillOnTheCard` holds the
 * transcription to the shipped data: if a re-import renumbers the block, that is where it fails.
 */
internal val LESSON_CATALOG: CardCatalog = CardCatalog(
    sets = emptyList(),
    cards = listOf(
        lessonCard(id = 257, top = 4, right = 2, bottom = 3, left = 4, name = "Dodo"),
        lessonCard(id = 258, top = 2, right = 2, bottom = 7, left = 2, name = "Tonberry"),
        lessonCard(id = 259, top = 4, right = 3, bottom = 3, left = 3, name = "Sabotender"),
        lessonCard(id = 261, top = 2, right = 4, bottom = 3, left = 5, name = "Pudding"),
        lessonCard(id = 262, top = 3, right = 4, bottom = 3, left = 3, name = "Bomb"),
        lessonCard(id = 263, top = 4, right = 2, bottom = 5, left = 3, name = "Mandragora"),
        lessonCard(id = 264, top = 3, right = 3, bottom = 3, left = 4, name = "Coblyn"),
        lessonCard(id = 265, top = 5, right = 2, bottom = 5, left = 2, name = "Morbol"),
        lessonCard(id = 266, top = 2, right = 5, bottom = 2, left = 5, name = "Coeurl"),
        lessonCard(id = 267, top = 5, right = 5, bottom = 2, left = 2, name = "Ahriman"),
        lessonCard(id = 268, top = 2, right = 5, bottom = 5, left = 2, name = "Goobbue"),
        lessonCard(id = 269, top = 3, right = 7, bottom = 2, left = 1, name = "Chocobo"),
        // The five beast-tribe cards the Bonus drill is dealt. `Amalj'aa` also carries the Fallen
        // Ace position, which is why it is the one that was here first — and why its tribe was
        // missing until a lesson needed to read it.
        lessonCard(270, 1, 4, 7, 1, "Amalj'aa", CardType.BEAST),
        lessonCard(271, 6, 1, 3, 4, "Ixal", CardType.BEAST),
        lessonCard(272, 2, 4, 5, 4, "Sylph", CardType.BEAST),
        lessonCard(273, 2, 2, 4, 6, "Kobold", CardType.BEAST),
        lessonCard(274, 4, 5, 3, 3, "Sahuagin", CardType.BEAST),
        lessonCard(id = 318, top = 1, right = 8, bottom = 10, left = 8, name = "Hildibrand"),
        lessonCard(id = 319, top = 10, right = 6, bottom = 4, left = 8, name = "Nanamo Ul Namo"),
        // Block 2 — the FF8 set, whose types really are elements. Only the Elemental lesson needs
        // them, and it has to: an FFXIV tribe matches no tile. See `TUTORIAL_COURSE`.
        lessonCard(518, 2, 1, 4, 4, "Gayla", CardType.LIGHTNING),
        lessonCard(520, 3, 5, 2, 4, "Fastitocalon-F", CardType.EARTH),
        lessonCard(523, 2, 1, 2, 6, "Cockatrice", CardType.LIGHTNING),
        lessonCard(527, 6, 1, 4, 3, "Glacial Eye", CardType.ICE),
        lessonCard(529, 5, 3, 2, 5, "Thrustaevis", CardType.WIND),
        lessonCard(530, 5, 1, 3, 5, "Anacondaur", CardType.POISON),
        lessonCard(531, 5, 2, 5, 2, "Creeps", CardType.LIGHTNING),
        lessonCard(532, 4, 4, 5, 3, "Grendel", CardType.LIGHTNING),
        lessonCard(536, 6, 3, 1, 6, "Armadodo", CardType.EARTH),
    ),
)

@Suppress("LongParameterList") // Four edges, an id and a name: a card is what it is.
private fun lessonCard(
    id: Int,
    top: Int,
    right: Int,
    bottom: Int,
    left: Int,
    name: String,
    type: CardType? = null,
): Card = Card(
    id = id,
    nameKey = "STR_FF14_CARD_$id",
    name = name,
    top = top,
    right = right,
    bottom = bottom,
    left = left,
    rarity = 1,
    type = type,
)
