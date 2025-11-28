package org.calibre.formats.mobi

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Huff/CDIC decompressor for MOBI files.
 * Implements the Huffman dictionary compression algorithm used in MOBI format.
 */
class HuffCdicDecompressor {
    
    data class DictEntry(
        val codeLen: Int,
        val term: Boolean,
        val maxCode: Long
    )
    
    private var dict1: Array<DictEntry>? = null
    private var minCode: LongArray = LongArray(33)
    private var maxCode: LongArray = LongArray(33)
    private val dictionary = mutableListOf<Pair<ByteArray, Boolean>>() // (phrase, isFinal)
    
    /**
     * Load HUFF header record.
     */
    fun loadHuff(huff: ByteArray) {
        // Check header: "HUFF\x00\x00\x00\x18"
        if (huff.size < 16 || !huff.sliceArray(0..7).contentEquals(byteArrayOf(
            0x48, 0x55, 0x46, 0x46, 0x00, 0x00, 0x00, 0x18
        ))) {
            throw Exception("Invalid HUFF header")
        }
        
        val buffer = ByteBuffer.wrap(huff).order(ByteOrder.BIG_ENDIAN)
        buffer.position(8)
        val off1 = buffer.int.toLong() and 0xFFFFFFFFL
        val off2 = buffer.int.toLong() and 0xFFFFFFFFL
        
        // Load dict1 (256 entries)
        buffer.position(off1.toInt())
        dict1 = Array(256) { i ->
            val v = buffer.long
            val codeLen = (v and 0x1F).toInt()
            val term = (v and 0x80) != 0L
            val maxcode = (v shr 8) and 0xFFFFFFFFL
            val maxCodeValue = ((maxcode + 1) shl (32 - codeLen)) - 1
            DictEntry(codeLen, term, maxCodeValue)
        }
        
        // Load dict2 (64 entries for mincode/maxcode)
        buffer.position(off2.toInt())
        val dict2 = LongArray(64)
        for (i in 0 until 64) {
            dict2[i] = buffer.long
        }
        
        // Build mincode and maxcode arrays
        minCode[0] = 0
        maxCode[0] = 0
        for (codelen in 1 until 33) {
            val idx = (codelen - 1) * 2
            if (idx < dict2.size) {
                minCode[codelen] = dict2[idx] shl (32 - codelen)
                val maxcodeVal = dict2[idx + 1]
                maxCode[codelen] = ((maxcodeVal + 1) shl (32 - codelen)) - 1
            }
        }
    }
    
    /**
     * Load CDIC record (dictionary phrases).
     */
    fun loadCdic(cdic: ByteArray) {
        // Check header: "CDIC\x00\x00\x00\x10"
        if (cdic.size < 16 || !cdic.sliceArray(0..7).contentEquals(byteArrayOf(
            0x43, 0x44, 0x49, 0x43, 0x00, 0x00, 0x00, 0x10
        ))) {
            throw Exception("Invalid CDIC header")
        }
        
        val buffer = ByteBuffer.wrap(cdic).order(ByteOrder.BIG_ENDIAN)
        buffer.position(8)
        val phrases = buffer.int.toLong() and 0xFFFFFFFFL
        val bits = buffer.int.toLong() and 0xFFFFFFFFL
        
        val n = minOf(1 shl bits.toInt(), (phrases - dictionary.size).toInt())
        
        // Read phrase offsets
        buffer.position(16)
        val offsets = ShortArray(n)
        for (i in 0 until n) {
            offsets[i] = buffer.short
        }
        
        // Read phrases
        for (offset in offsets) {
            val off = offset.toInt() and 0xFFFF
            if (off + 2 <= cdic.size) {
                val blen = (cdic[off].toInt() and 0xFF) or ((cdic[off + 1].toInt() and 0xFF) shl 8)
                val length = blen and 0x7FFF
                val flag = (blen and 0x8000) != 0
                
                if (off + 2 + length <= cdic.size) {
                    val slice = cdic.sliceArray(off + 2 until off + 2 + length)
                    dictionary.add(Pair(slice, flag))
                }
            }
        }
    }
    
    /**
     * Decompress data using Huff/CDIC algorithm.
     */
    fun unpack(data: ByteArray): ByteArray {
        val dictionary = dict1 ?: throw Exception("HUFF header not loaded")
        
        val output = ByteArrayOutputStream()
        var bitsLeft = data.size * 8
        
        // Pad data with zeros for safe reading
        val paddedData = data + ByteArray(8)
        var pos = 0
        var x = readUInt64(paddedData, pos)
        var n = 32
        
        while (bitsLeft > 0) {
            if (n <= 0) {
                pos += 4
                x = readUInt64(paddedData, pos)
                n += 32
            }
            
            val code = (x shr n) and 0xFFFFFFFFL
            
            // Get code length from dict1
            val codeByte = (code shr 24).toInt() and 0xFF
            val entry = dictionary[codeByte]
            var codeLen = entry.codeLen
            var term = entry.term
            var maxcode = entry.maxCode
            
            // If not terminal, find correct code length
            if (!term) {
                while (code < minCode[codeLen] && codeLen < 32) {
                    codeLen++
                }
                maxcode = maxCode[codeLen]
            }
            
            n -= codeLen
            bitsLeft -= codeLen
            if (bitsLeft < 0) break
            
            // Calculate dictionary index
            val r = ((maxcode - code) shr (32 - codeLen)).toInt()
            if (r < dictionary.size) {
                var (slice, flag) = dictionary[r]
                
                // If not final, recursively unpack
                if (!flag) {
                    dictionary[r] = Pair(byteArrayOf(), true) // Mark as processing
                    slice = unpack(slice)
                    dictionary[r] = Pair(slice, true)
                }
                
                output.write(slice)
            }
        }
        
        return output.toByteArray()
    }
    
    private fun readUInt64(data: ByteArray, offset: Int): Long {
        if (offset + 8 > data.size) return 0L
        val buffer = ByteBuffer.wrap(data, offset, 8).order(ByteOrder.BIG_ENDIAN)
        return buffer.long
    }
}

/**
 * Helper class to manage Huff/CDIC decompression for MOBI files.
 */
class HuffReader(huffs: List<ByteArray>) {
    private val reader = HuffCdicDecompressor()
    
    init {
        if (huffs.isEmpty()) {
            throw Exception("No HUFF records provided")
        }
        reader.loadHuff(huffs[0])
        for (i in 1 until huffs.size) {
            reader.loadCdic(huffs[i])
        }
    }
    
    fun unpack(section: ByteArray): ByteArray {
        return reader.unpack(section)
    }
}
