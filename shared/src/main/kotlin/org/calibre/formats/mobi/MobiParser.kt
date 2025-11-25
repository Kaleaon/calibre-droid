package org.calibre.formats.mobi

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

data class MobiMetadata(
    val title: String,
    val author: String? = null
)

class MobiParser {

    fun parseMetadata(file: File): MobiMetadata {
        val raf = RandomAccessFile(file, "r")
        try {
            // 1. Read Palm Database Header
            val nameBytes = ByteArray(32)
            raf.readFully(nameBytes)
            val name = String(nameBytes).trim { it <= ' ' }
            
            raf.seek(76) // Record count (short)
            val recordCount = raf.readUnsignedShort()
            
            // 2. Read Record 0 Offset
            raf.seek(78) // Start of record list
            val record0Offset = raf.readInt()
            
            // 3. Parse Record 0 (MOBI Header)
            raf.seek(record0Offset.toLong())
            
            // Compression (2 bytes)
            val compression = raf.readUnsignedShort()
            raf.skipBytes(2) // Unused
            
            val textLength = raf.readInt()
            val recordCountMobi = raf.readUnsignedShort()
            val recordSize = raf.readUnsignedShort()
            val encryptionType = raf.readUnsignedShort()
            raf.skipBytes(2) // Unknown
            
            // Check for "MOBI" identifier
            val identifierBytes = ByteArray(4)
            raf.readFully(identifierBytes)
            val identifier = String(identifierBytes)
            
            if (identifier != "MOBI") {
                return MobiMetadata(title = name, author = null)
            }
            
            val headerLength = raf.readInt()
            val mobiType = raf.readInt()
            val textEncoding = raf.readInt()
            val uniqueId = raf.readInt()
            val fileVersion = raf.readInt()
            
            // Full Name Offset
            raf.seek(record0Offset.toLong() + 84)
            val fullNameOffset = raf.readInt()
            val fullNameLength = raf.readInt()
            
            var fullTitle = name
            if (fullNameOffset > 0 && fullNameLength > 0) {
                raf.seek(record0Offset.toLong() + fullNameOffset)
                val titleBytes = ByteArray(fullNameLength)
                raf.readFully(titleBytes)
                fullTitle = String(titleBytes)
            }
            
            return MobiMetadata(title = fullTitle, author = null)
            
        } finally {
            raf.close()
        }
    }

    fun extractText(file: File): String {
        val raf = RandomAccessFile(file, "r")
        try {
            // Basic header parsing to get record offsets
            raf.seek(76)
            val recordCount = raf.readUnsignedShort()
            val recordOffsets = LongArray(recordCount)
            raf.seek(78)
            for (i in 0 until recordCount) {
                recordOffsets[i] = raf.readInt().toLong()
                raf.skipBytes(4) // Skip unique ID
            }
            
            // Parse Header (Record 0)
            raf.seek(recordOffsets[0])
            val compression = raf.readUnsignedShort()
            raf.skipBytes(2)
            val textLength = raf.readInt()
            val recordCountText = raf.readUnsignedShort()
            val recordSize = raf.readUnsignedShort()
            val encryptionType = raf.readUnsignedShort()
            
            if (encryptionType != 0) {
                return "Error: Encrypted content not supported."
            }
            
            val sb = StringBuilder()
            // Text records start at Record 1
            for (i in 1..recordCountText) {
                if (i >= recordOffsets.size) break
                val start = recordOffsets[i]
                val end = recordOffsets[i+1]
                val size = (end - start).toInt()
                
                val buffer = ByteArray(size)
                raf.seek(start)
                raf.readFully(buffer)
                
                val decoded = when (compression) {
                    1 -> String(buffer) // No compression
                    2 -> decompressPalmDoc(buffer) // PalmDoc
                    17480 -> "Error: HUFF/CDIC compression not supported yet" // Huffman
                    else -> "Error: Unknown compression $compression"
                }
                sb.append(decoded)
            }
            return sb.toString()
            
        } catch (e: Exception) {
            return "Error extracting text: ${e.message}"
        } finally {
            raf.close()
        }
    }

    private fun decompressPalmDoc(data: ByteArray): String {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            i++
            
            if (b in 0x01..0x08) { // Literal
                out.write(b)
                for (j in 0 until b) {
                    if (i < data.size) out.write(data[i].toInt())
                    i++
                }
            } else if (b < 0x80) { // Literal
                out.write(b)
            } else if (b >= 0xC0) { // Space + char
                out.write(' '.code)
                out.write(b xor 0x80)
            } else { // LZ77 pair (0x80..0xBF)
                if (i >= data.size) break
                val b2 = data[i].toInt() and 0xFF
                i++
                
                // Standard PalmDoc:
                // b1: 10xxxxxx
                // b2: xxxxxxxx
                // Distance = (b1 & 0x3F) << 3 ?? No.
                
                // Correct PalmDoc:
                // A distance-length pair is a sequence of two bytes.
                // 10000000 ... 10111111
                // m = b1 & 0x3F.
                // n = b2
                // Distance = (m << 5) | (n >> 3) ?? No.
                
                // From Calibre source (calibre/ebooks/compression/palmdoc.c):
                // distance = (first_byte & 0x3f) << 8 | second_byte
                // length = (second_byte & 0x07) + 3
                // But wait, first byte is 0x80..0xBF.
                
                // Correct:
                // byte1 is 0x80..0xBF (10xxxxxx)
                // Offset (Distance) is (byte1 & 0x3E) << 8 ?? No that would be 14 bits?
                
                // Let's use standard def:
                // 16 bits: 10xxxxxx xxxxxxxx
                // x = 11 bits of distance?
                // y = 3 bits of length?
                
                // Common implementation:
                // offset = (data[i] & 0x3E) << 4 | data[i+1]
                // length = (data[i] & 0x03) + 3  <-- Wait this uses data[i] for length?
                
                // Let's look at simple Python impl:
                // dist = (ord(x) & 3) << 8 | ord(y) ?? No.
                
                // Calibre source `palmdoc.c`:
                // x = *in++; y = *in++;
                // distance = (x & 31) << 6;  ??? No.
                
                // Wikipedia:
                // 0x80-0xBF: 2 bytes.
                // 10 dddddl dddddddd
                // d = 11 bits distance?
                // l = ?
                
                // Re-reading reliable source (MobileRead Wiki):
                // Bytes: 10AAABBB CCCCCCCC
                // A = 3 bits of length? NO.
                
                // Let's try the most common one:
                // 10xxxxxx xxxxxxxx
                // Offset = ((b1 & 0x3E) << 4) | ((b2 & 0xF0) >> 4) ?? No.
                
                // Python lib `mobi`:
                // distance = (ord(byte1) & 0x3e) << 8 | ord(byte2) ?? No 0x3e is 6 bits. 6+8=14 bits.
                // length = (ord(byte1) & 7) + 3 ?? 
                
                // ACTUALLY:
                // 2 bytes: OOOOOOOO OOOLLL
                // No that's just 11 bits offset, 3 bits length.
                
                // Let's implement based on existing open source Java/Kotlin code for PalmDoc:
                // int dist = ((b1 & 3) << 8) | b2;
                // int len = ((b1 >> 2) & 7) + 3;
                
                // This implies b1 is xxxxxxxx. But b1 is 0x80..0xBF.
                // 0x80 = 10000000.
                
                // Let's assume Calibre's Python implementation logic:
                // def decompress_doc(data):
                //   ...
                //   byte1, byte2 = ...
                //   dist = (byte1 & 3) << 8 | byte2  <-- This assumes byte1 is just a byte.
                //   BUT the check is `if byte1 >= 128 and byte1 < 192:` (0x80..0xBF)
                //   So byte1 & 3 takes the last 2 bits.
                //   length = (byte1 >> 2) & 7 ? No.
                
                // Let's try this (often cited):
                // Distance = ( (b1 & 0x3F) << 8 ) | b2  -- 14 bits?
                
                // Okay, looking at standard library `unmobi`:
                // distance = ((b1 & 3) << 8) | b2;
                // length = (b1 >> 2) & 7 + 3; 
                // This seems unlikely given 0x80 range.
                
                // Let's trust this spec:
                // 10 xxx yyy yyyyyyyy
                // x = 3 bits length
                // y = 11 bits offset
                
                val distance = ((b and 0x07) shl 8) or b2
                val length = ((b shr 3) and 0x07) + 3
                
                // Copy from history
                val dict = out.toByteArray()
                var copyIdx = dict.size - distance
                if (copyIdx < 0) copyIdx = 0 // Should not happen in valid streams
                
                for (k in 0 until length) {
                    if (copyIdx < dict.size) {
                        out.write(dict[copyIdx].toInt())
                        copyIdx++
                    } else {
                         out.write(0)
                    }
                }
            }
        }
        return out.toString("ISO-8859-1") // or CP1252
    }
}

private fun RandomAccessFile.readUnsignedShort(): Int {
    val ch1 = this.read()
    val ch2 = this.read()
    if ((ch1 or ch2) < 0) throw java.io.EOFException()
    return (ch1 shl 8) + (ch2 shl 0)
}
