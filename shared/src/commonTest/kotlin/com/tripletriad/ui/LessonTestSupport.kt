package com.tripletriad.ui

import com.tripletriad.data.CardCatalog
import com.tripletriad.model.Card

/**
 * The twelve block-1 cards the lessons name, built here rather than loaded.
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
): Card = Card(
    id = id,
    nameKey = "STR_FF14_CARD_$id",
    name = name,
    top = top,
    right = right,
    bottom = bottom,
    left = left,
    rarity = 1,
)
