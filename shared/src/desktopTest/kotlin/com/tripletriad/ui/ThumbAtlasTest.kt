package com.tripletriad.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import tripletriad.shared.generated.resources.Res
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalResourceApi::class)
class ThumbAtlasTest {

    @Test
    fun everySheetIsCoveredByTheFramesThatClaimIt() {
        val bySheet = frames().values.groupBy { it.sheet }
        assertTrue(bySheet.isNotEmpty(), "thumbs.json declared no frames at all")

        for ((sheet, entries) in bySheet) {
            val (width, height) = opaqueBounds(sheet)
            val right = entries.maxOf { it.x + it.width }
            val bottom = entries.maxOf { it.y + it.height }

            assertTrue(
                kotlin.math.abs(right - width) <= TOLERANCE &&
                    kotlin.math.abs(bottom - height) <= TOLERANCE,
                "$sheet: frames cover ${right}x$bottom but the artwork is ${width}x$height — " +
                    "this table was packed for a different sheet",
            )
        }
    }

    @Test
    fun noTwoCardsAreDrawnFromTheSameRectangle() {
        val duplicated = frames().entries
            .groupBy { (_, frame) -> listOf(frame.sheet, frame.x, frame.y) }
            .filter { it.value.size > 1 }
            .map { entry -> entry.value.map { it.key }.sorted() }

        assertTrue(duplicated.isEmpty(), "cards sharing one frame: $duplicated")
    }

    private fun opaqueBounds(sheet: String): Pair<Int, Int> {
        val image: ImageBitmap = runBlocking {
            Res.readBytes("files/art/thumbs/$sheet.png").decodeToImageBitmap()
        }
        val pixels = image.toPixelMap()
        val opaque = (0 until image.height).flatMap { y ->
            (0 until image.width).mapNotNull { x -> (x to y).takeIf { pixels[x, y].alpha > 0f } }
        }
        return (opaque.maxOf { it.first } + 1) to (opaque.maxOf { it.second } + 1)
    }

    private fun frames(): Map<String, Frame> = runBlocking {
        JSON.decodeFromString<Table>(Res.readBytes(TABLE).decodeToString()).frames
    }

    @Serializable
    private data class Frame(
        val sheet: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    @Serializable
    private data class Table(val frames: Map<String, Frame>)

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }

        const val TABLE = "files/thumbs.json"

        const val TOLERANCE = 8
    }
}
