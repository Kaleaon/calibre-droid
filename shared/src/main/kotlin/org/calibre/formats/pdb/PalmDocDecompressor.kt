package org.calibre.formats.pdb

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PalmDoc LZ77 decompression and compression.
 * 
 * PalmDoc uses a simple LZ77-style compression where:
 * - Bytes 0x01-0x08: Copy 1-8 following literal bytes
 * - Bytes 0x09-0x7F: Literal byte
 * - Bytes 0x80-0xBF: Two bytes encode a back-reference (distance, length)
 * - Bytes 0xC0-0xFF: Single byte encodes a space followed by a character
 * 
 * This is used by PalmDOC, MOBI, and other Palm-based ebook formats.
 */
object PalmDocDecompressor {
    
    /**
     * Decompresses PalmDoc-compressed data.
     * 
     * @param data The compressed data
     * @return The decompressed data
     */
    fun decompress(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var i = 0
        
        while (i < data.size) {
            val c = data[i].toInt() and 0xFF
            
            when {
                c == 0x00 -> {
                    // Null byte - literal
                    output.write(0)
                    i++
                }
                c in 0x01..0x08 -> {
                    // Copy next c bytes literally
                    val count = c
                    i++
                    for (j in 0 until count) {
                        if (i < data.size) {
                            output.write(data[i].toInt() and 0xFF)
                            i++
                        }
                    }
                }
                c in 0x09..0x7F -> {
                    // Literal byte
                    output.write(c)
                    i++
                }
                c in 0x80..0xBF -> {
                    // Back-reference: Two bytes encode distance and length
                    if (i + 1 >= data.size) break
                    
                    val b1 = c
                    val b2 = data[i + 1].toInt() and 0xFF
                    i += 2
                    
                    // Decode distance and length
                    val distance = ((b1 shl 8) or b2) shr 3 and 0x7FF
                    val length = (b2 and 0x07) + 3
                    
                    if (distance > 0) {
                        val outBytes = output.toByteArray()
                        val startPos = outBytes.size - distance
                        
                        for (j in 0 until length) {
                            val pos = startPos + j
                            if (pos >= 0 && pos < output.size()) {
                                output.write(outBytes[pos].toInt() and 0xFF)
                            }
                        }
                    }
                }
                c in 0xC0..0xFF -> {
                    // Space followed by character (c XOR 0x80)
                    output.write(' '.code)
                    output.write(c xor 0x80)
                    i++
                }
            }
        }
        
        return output.toByteArray()
    }
    
    /**
     * Compresses data using PalmDoc LZ77 compression.
     * 
     * @param data The uncompressed data
     * @return The compressed data
     */
    fun compress(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var i = 0
        
        while (i < data.size) {
            val c = data[i].toInt() and 0xFF
            
            // Check for space + ASCII character (common optimization)
            if (c == ' '.code && i + 1 < data.size) {
                val next = data[i + 1].toInt() and 0xFF
                if (next in 0x40..0x7F) {
                    output.write((next or 0x80) and 0xFF)
                    i += 2
                    continue
                }
            }
            
            // Try to find a back-reference
            val match = findBestMatch(data, i)
            if (match != null && match.length >= 3) {
                // Encode as back-reference
                val distance = match.distance
                val length = minOf(match.length, 10) // Max length is 10 (3 + 7 bits)
                
                val encoded = (0x8000 or (distance shl 3) or (length - 3)) and 0xFFFF
                output.write((encoded shr 8) and 0xFF)
                output.write(encoded and 0xFF)
                i += length
            } else {
                // Literal byte
                when {
                    c == 0x00 || c in 0x09..0x7F -> {
                        output.write(c)
                        i++
                    }
                    c in 0x01..0x08 -> {
                        // Must escape these bytes
                        output.write(0x01)
                        output.write(c)
                        i++
                    }
                    else -> {
                        // Encode as literal with count prefix
                        output.write(0x01)
                        output.write(c)
                        i++
                    }
                }
            }
        }
        
        return output.toByteArray()
    }
    
    private data class Match(val distance: Int, val length: Int)
    
    private fun findBestMatch(data: ByteArray, pos: Int): Match? {
        if (pos < 3) return null
        
        val maxDistance = minOf(pos, 2047) // 11-bit distance
        val maxLength = minOf(data.size - pos, 10)
        
        var bestMatch: Match? = null
        
        for (dist in 1..maxDistance) {
            val startPos = pos - dist
            var length = 0
            
            while (length < maxLength && 
                   pos + length < data.size && 
                   data[startPos + (length % dist)] == data[pos + length]) {
                length++
            }
            
            if (length >= 3 && (bestMatch == null || length > bestMatch.length)) {
                bestMatch = Match(dist, length)
            }
        }
        
        return bestMatch
    }
}

/**
 * PalmDoc record header for DOC format.
 */
data class PalmDocHeader(
    val compression: Int,       // 1 = uncompressed, 2 = PalmDoc, 17480 = Huff/CDIC
    val unused: Int,
    val textLength: Int,        // Uncompressed text length
    val recordCount: Int,       // Number of text records
    val recordSize: Int,        // Max size of each record (usually 4096)
    val currentPosition: Int    // Current reading position
) {
    companion object {
        const val SIZE = 16
        
        const val COMPRESSION_NONE = 1
        const val COMPRESSION_PALMDOC = 2
        const val COMPRESSION_HUFF_CDIC = 17480
        
        fun read(data: ByteArray): PalmDocHeader {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            
            return PalmDocHeader(
                compression = buffer.short.toInt() and 0xFFFF,
                unused = buffer.short.toInt() and 0xFFFF,
                textLength = buffer.int,
                recordCount = buffer.short.toInt() and 0xFFFF,
                recordSize = buffer.short.toInt() and 0xFFFF,
                currentPosition = buffer.int
            )
        }
    }
    
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(compression.toShort())
        buffer.putShort(unused.toShort())
        buffer.putInt(textLength)
        buffer.putShort(recordCount.toShort())
        buffer.putShort(recordSize.toShort())
        buffer.putInt(currentPosition)
        return buffer.array()
    }
}
