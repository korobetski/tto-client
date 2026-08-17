package com.tripletriad.storage

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SaveCodecTest {
    @Test
    fun roundTripsJson() {
        val json = """{"USERNAME":"Kuplu Kopo","MGP":100,"CARDS":[1,3,6,7,10]}"""

        assertEquals(json, SaveCodec.decode(SaveCodec.encode(json, Random(1))))
    }

    @Test
    fun roundTripsAnEmptyDocumentAndOneLargerThanTheKeystreamPeriodCouldMatter() {
        assertEquals("", SaveCodec.decode(SaveCodec.encode("", Random(2))))

        val large = buildString { repeat(20_000) { append("""{"n":$it},""") } }
        assertEquals(large, SaveCodec.decode(SaveCodec.encode(large, Random(3))))
    }

    @Test
    fun roundTripsMultiByteCharacters() {
        val json = """{"USERNAME":"Frédéric 日本語 🃏"}"""

        assertEquals(json, SaveCodec.decode(SaveCodec.encode(json, Random(4))))
    }

    @Test
    fun outputIsHexAfterTheMagicAndDoesNotContainThePlaintext() {
        val encoded = SaveCodec.encode("""{"USERNAME":"Kuplu Kopo"}""", Random(5))

        assertTrue(encoded.startsWith(SaveCodec.MAGIC), encoded.take(16))
        assertFalse(encoded.contains("Kuplu"), "the point of the exercise")
        assertTrue(
            encoded.drop(SaveCodec.MAGIC.length).all { it in "0123456789abcdef" },
            "everything after the magic must be hex",
        )
    }

    @Test
    fun theSameInputEncodesDifferentlyEachTime() {
        val json = """{"USERNAME":"Kuplu Kopo"}"""

        val first = SaveCodec.encode(json, Random(6))
        val second = SaveCodec.encode(json, Random(7))

        assertNotEquals(first, second)
        assertEquals(json, SaveCodec.decode(first))
        assertEquals(json, SaveCodec.decode(second))
    }

    @Test
    fun encodingIsDeterministicForAGivenSalt() {
        val json = """{"MGP":100}"""

        assertEquals(SaveCodec.encode(json, Random(8)), SaveCodec.encode(json, Random(8)))
    }

    @Test
    fun rejectsSomethingThatIsNotASaveFile() {
        val failure = assertFailsWith<SaveCorruptException> {
            SaveCodec.decode("""{"USERNAME":"Kuplu Kopo"}""")
        }

        assertTrue(failure.message!!.contains(SaveCodec.MAGIC), failure.message)
    }

    @Test
    fun rejectsATruncatedFile() {
        val encoded = SaveCodec.encode("""{"MGP":100}""", Random(9))

        assertFailsWith<SaveCorruptException> { SaveCodec.decode(encoded.dropLast(20)) }
        // An odd number of hex characters is a different failure path from a short one.
        assertFailsWith<SaveCorruptException> { SaveCodec.decode(encoded.dropLast(1)) }
    }

    @Test
    fun rejectsNonHexPayload() {
        assertFailsWith<SaveCorruptException> { SaveCodec.decode("${SaveCodec.MAGIC}00000001zzzz") }
    }

    @Test
    fun rejectsAnAlteredPayload() {
        val encoded = SaveCodec.encode("""{"MGP":100,"XP":0}""", Random(10))
        val at = encoded.length - 6
        val flipped = encoded.substring(0, at) +
            (if (encoded[at] == 'a') 'b' else 'a') +
            encoded.substring(at + 1)

        val failure = assertFailsWith<SaveCorruptException> { SaveCodec.decode(flipped) }

        assertTrue(failure.message!!.contains("checksum"), failure.message)
    }

    @Test
    fun toleratesSurroundingWhitespace() {
        val json = """{"MGP":100}"""
        val encoded = SaveCodec.encode(json, Random(11))

        assertEquals(json, SaveCodec.decode("\n  $encoded  \n"))
    }

    @Test
    fun acceptsUppercaseHex() {
        val json = """{"MGP":100}"""
        val encoded = SaveCodec.encode(json, Random(12))

        assertEquals(json, SaveCodec.decode(encoded.uppercase().replace("TTO1", SaveCodec.MAGIC)))
    }
}
