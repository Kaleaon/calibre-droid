package org.calibre.conversion

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PalmDoc compression utility.
 * Implements the PalmDoc compression algorithm used in MOBI format.
 * 
 * PalmDoc uses a simple LZ77-style compression:
 * - Literal bytes (0x00-0x08, 0x09-0x7F): written as-is
 * - Space compression (0x40-0x7F): space + char encoded as single byte
 * - LZ77 back-references (0x80-0xBF): 2-byte distance-length pairs
 */
object PalmDocCompression {
    
    /**
     * Compress data using PalmDoc algorithm.
     * Based on the Python implementation in calibre/ebooks/compression/palmdoc.py
     */
    fun compress(data: ByteArray): ByteArray {
        if (data.isEmpty()) return ByteArray(0)
        
        val out = ByteArrayOutputStream()
        var i = 0
        val len = data.size
        
        while (i < len) {
            // Try to find a back-reference (LZ77)
            if (i > 10 && (len - i) > 10) {
                var bestMatch = -1
                var bestLength = 0
                
                // Look for matches of length 3-10
                for (matchLen in 10 downTo 3) {
                    if (i + matchLen > len) continue
                    
                    val chunk = data.sliceArray(i until (i + matchLen))
                    // Search backwards for this chunk (max distance 2047)
                    val searchStart = maxOf(0, i - 2047)
                    var matchPos = -1
                    
                    for (j in (i - 1) downTo searchStart) {
                        if (j + matchLen > i) continue
                        var found = true
                        for (k in 0 until matchLen) {
                            if (data[j + k] != chunk[k]) {
                                found = false
                                break
                            }
                        }
                        if (found) {
                            matchPos = j
                            break
                        }
                    }
                    
                    if (matchPos >= 0) {
                        bestMatch = matchPos
                        bestLength = matchLen
                        break
                    }
                }
                
                if (bestMatch >= 0) {
                    // Encode back-reference
                    // Based on Python: code = 0x8000 + ((m << 3) & 0x3ff8) + (n - 3)
                    // Where m = distance, n = length
                    val distance = i - bestMatch
                    val length = bestLength
                    
                    // Format: 16-bit big-endian
                    // 0x8000 = base flag
                    // (distance << 3) & 0x3ff8 = 11 bits of distance (max 2047)
                    // (length - 3) = 3 bits of length (max 7, so length max 10)
                    val code = 0x8000 or ((distance shl 3) and 0x3ff8) or (length - 3)
                    
                    // Write as big-endian 16-bit
                    out.write((code shr 8) and 0xFF)
                    out.write(code and 0xFF)
                    i += bestLength
                    continue
                }
            }
            
            // Handle space compression
            val ch = data[i]
            val chInt = ch.toInt() and 0xFF
            
            if (ch == ' '.code.toByte() && (i + 1) < len) {
                val nextCh = data[i + 1].toInt() and 0xFF
                // Space compression: if next char is 0x40-0x7F, encode as single byte
                if (nextCh >= 0x40 && nextCh < 0x80) {
                    out.write(nextCh xor 0x80)
                    i += 2
                    continue
                }
            }
            
            // Literal byte
            if (chInt == 0 || (chInt > 8 && chInt < 0x80)) {
                out.write(chInt)
                i++
            } else {
                // Binary sequence: write length byte followed by bytes
                // Collect binary bytes (0x00-0x08, 0x80-0xFF)
                val binSeq = mutableListOf<Byte>()
                binSeq.add(ch)
                var j = i + 1
                while (j < len && binSeq.size < 8) {
                    val nextCh = data[j].toInt() and 0xFF
                    if (nextCh == 0 || (nextCh > 8 && nextCh < 0x80)) {
                        break
                    }
                    binSeq.add(data[j])
                    j++
                }
                
                out.write(binSeq.size)
                for (b in binSeq) {
                    out.write(b.toInt() and 0xFF)
                }
                i += binSeq.size
            }
        }
        
        return out.toByteArray()
    }
    
    /**
     * Decompress PalmDoc data.
     * This is a simpler version for reference - the full implementation
     * is in MobiParser.kt
     */
    fun decompress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            i++
            
            if (b in 0x01..0x08) {
                // Binary sequence
                val len = b
                for (j in 0 until len) {
                    if (i < data.size) {
                        out.write(data[i].toInt() and 0xFF)
                        i++
                    }
                }
            } else if (b < 0x80) {
                // Literal
                out.write(b)
            } else if (b >= 0xC0) {
                // Space + char
                out.write(' '.code)
                out.write(b xor 0x80)
            } else {
                // LZ77 back-reference (0x80-0xBF)
                if (i >= data.size) break
                val b2 = data[i].toInt() and 0xFF
                i++
                
                // Decode distance and length
                // Format: 10DDDDDD DDDDDLLL
                val distance = ((b and 0x3F) shl 8) or b2
                val length = ((b shr 2) and 0x07) + 3
                
                // Copy from back-reference
                val start = out.size() - distance
                if (start >= 0 && start < out.size()) {
                    for (j in 0 until length) {
                        if (start + j < out.size()) {
                            out.write(out.toByteArray()[start + j].toInt() and 0xFF)
                        }
                    }
                }
            }
        }
        
        return out.toByteArray()
    }
}
