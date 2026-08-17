package com.tripletriad.storage

import kotlin.random.Random

class SaveCorruptException(message: String) : Exception(message)

object SaveCodec {
    const val MAGIC: String = "TTO1"

    private const val SALT_HEX_LENGTH = INT_BYTES * 2
    private const val HEADER_LENGTH = MAGIC_LENGTH + SALT_HEX_LENGTH
    private const val CHECKSUM_BYTES = INT_BYTES

    private const val KEY: Int = 0x54_54_4F_21 // "TTO!"

    private const val FNV_OFFSET_BASIS: Int = -0x7EE3623B // 0x811C9DC5
    private const val FNV_PRIME: Int = 0x01000193

    // xorshift32's shift triple. Any other triple from Marsaglia's table would do; these must not
    // change, because a save written with them has to stay readable.
    private const val SHIFT_A = 13
    private const val SHIFT_B = 17
    private const val SHIFT_C = 5

    fun encode(json: String, random: Random = Random.Default): String {
        val salt = nonZeroSalt(random)
        val plain = json.encodeToByteArray()
        val payload = ByteArray(CHECKSUM_BYTES + plain.size)
        writeInt(payload, 0, fnv1a(plain))
        plain.copyInto(payload, CHECKSUM_BYTES)
        applyKeystream(payload, salt)
        return MAGIC + salt.toHex() + payload.toHex()
    }

    fun decode(blob: String): String {
        val text = blob.trim()
        ensureIntact(text.startsWith(MAGIC)) { "not a $MAGIC save file" }

        val bodyLength = text.length - HEADER_LENGTH
        ensureIntact(bodyLength >= CHECKSUM_BYTES * 2 && bodyLength % 2 == 0) {
            "save file is truncated (${text.length} chars)"
        }

        val salt = parseHexInt(text.substring(MAGIC_LENGTH, HEADER_LENGTH))
        val payload = hexToBytes(text.substring(HEADER_LENGTH))
        applyKeystream(payload, salt)

        val expected = readInt(payload, 0)
        val plain = payload.copyOfRange(CHECKSUM_BYTES, payload.size)
        ensureIntact(fnv1a(plain) == expected) {
            "save file checksum does not match; it has been altered"
        }
        return plain.decodeToString()
    }

    private fun applyKeystream(bytes: ByteArray, salt: Int) {
        // Seeded from salt xor KEY, forced non-zero: xorshift is stuck at 0 forever.
        var state = (salt xor KEY).let { if (it == 0) 1 else it }
        for (i in bytes.indices) {
            state = state xor (state shl SHIFT_A)
            state = state xor (state ushr SHIFT_B)
            state = state xor (state shl SHIFT_C)
            bytes[i] = (bytes[i].toInt() xor (state and BYTE_MASK)).toByte()
        }
    }

    private fun fnv1a(bytes: ByteArray): Int {
        var hash = FNV_OFFSET_BASIS
        for (byte in bytes) {
            hash = (hash xor (byte.toInt() and BYTE_MASK)) * FNV_PRIME
        }
        return hash
    }

    private fun nonZeroSalt(random: Random): Int {
        val salt = random.nextInt()
        return if (salt == 0) 1 else salt
    }
}

// ---------------------------------------------------------------------------------------------
// Hex and big-endian Int helpers.
//
// File-private rather than members of [SaveCodec]: they are format plumbing with no knowledge of
// saves, and keeping them out leaves the object at five functions that each say something about the
// format.
// ---------------------------------------------------------------------------------------------

private const val MAGIC_LENGTH = 4
private const val INT_BYTES = 4
private const val BYTE_MASK = 0xFF
private const val NIBBLE_MASK = 0xF
private const val BITS_PER_NIBBLE = 4
private const val BITS_PER_BYTE = 8
private const val HEX_DIGITS = "0123456789abcdef"

private inline fun ensureIntact(intact: Boolean, message: () -> String) {
    if (!intact) throw SaveCorruptException(message())
}

private fun Int.toHex(): String = buildString(INT_BYTES * 2) {
    for (nibble in (INT_BYTES * 2 - 1) downTo 0) {
        append(HEX_DIGITS[(this@toHex ushr (nibble * BITS_PER_NIBBLE)) and NIBBLE_MASK])
    }
}

private fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val int = byte.toInt() and BYTE_MASK
        append(HEX_DIGITS[int ushr BITS_PER_NIBBLE])
        append(HEX_DIGITS[int and NIBBLE_MASK])
    }
}

private fun hexToBytes(hex: String): ByteArray {
    val bytes = ByteArray(hex.length / 2)
    for (i in bytes.indices) {
        val high = hexDigit(hex[i * 2]) shl BITS_PER_NIBBLE
        bytes[i] = (high or hexDigit(hex[i * 2 + 1])).toByte()
    }
    return bytes
}

private fun parseHexInt(hex: String): Int {
    var value = 0
    for (char in hex) {
        value = (value shl BITS_PER_NIBBLE) or hexDigit(char)
    }
    return value
}

private fun hexDigit(char: Char): Int {
    val digit = HEX_DIGITS.indexOf(char.lowercaseChar())
    ensureIntact(digit >= 0) { "save file contains non-hex character '$char'" }
    return digit
}

private fun writeInt(target: ByteArray, offset: Int, value: Int) {
    for (i in 0 until INT_BYTES) {
        target[offset + i] = (value ushr ((INT_BYTES - 1 - i) * BITS_PER_BYTE)).toByte()
    }
}

private fun readInt(source: ByteArray, offset: Int): Int {
    var value = 0
    for (i in 0 until INT_BYTES) {
        value = (value shl BITS_PER_BYTE) or (source[offset + i].toInt() and BYTE_MASK)
    }
    return value
}
