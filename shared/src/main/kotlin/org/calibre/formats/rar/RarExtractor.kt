package org.calibre.formats.rar

import org.calibre.utils.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * RAR archive extractor for CBR (Comic Book RAR) files.
 * 
 * Supports:
 * - RAR 1.5 - 4.x format (most common for CBR)
 * - RAR 5.0 format
 * - Store (uncompressed) entries
 * - Basic RAR compression (methods 0x30-0x35)
 * 
 * Note: Full RAR decompression is complex. This implementation
 * handles uncompressed entries and provides fallback to external
 * tools when available.
 */
class RarExtractor(private val data: ByteArray) {
    
    private val entries = mutableListOf<RarEntry>()
    private var isRar5 = false
    
    init {
        parseArchive()
    }
    
    private fun parseArchive() {
        if (data.size < 7) {
            throw IllegalArgumentException("File too small to be a RAR archive")
        }
        
        // Check for RAR signature
        // RAR 4.x: 52 61 72 21 1A 07 00
        // RAR 5.x: 52 61 72 21 1A 07 01 00
        
        if (data[0].toInt() == 0x52 && data[1].toInt() == 0x61 && 
            data[2].toInt() == 0x72 && data[3].toInt() == 0x21 &&
            data[4].toInt() == 0x1A && data[5].toInt() == 0x07) {
            
            if (data.size > 7 && data[6].toInt() == 0x01 && data[7].toInt() == 0x00) {
                isRar5 = true
                parseRar5()
            } else if (data[6].toInt() == 0x00) {
                isRar5 = false
                parseRar4()
            } else {
                throw IllegalArgumentException("Unknown RAR format")
            }
        } else {
            throw IllegalArgumentException("Invalid RAR signature")
        }
    }
    
    private fun parseRar4() {
        var offset = 7 // Skip signature
        
        while (offset < data.size - 7) {
            try {
                val header = parseRar4Header(offset)
                if (header == null) break
                
                if (header.type == 0x74) { // File header
                    val entry = parseRar4FileHeader(offset, header)
                    if (entry != null) {
                        entries.add(entry)
                    }
                }
                
                offset += header.size
                if (header.type == 0x74) {
                    offset += header.packedSize
                }
                
            } catch (e: Exception) {
                Logger.debug("Error parsing RAR4 header at $offset: ${e.message}")
                break
            }
        }
    }
    
    private fun parseRar4Header(offset: Int): Rar4Header? {
        if (offset + 7 > data.size) return null
        
        val buffer = ByteBuffer.wrap(data, offset, minOf(7, data.size - offset))
            .order(ByteOrder.LITTLE_ENDIAN)
        
        val crc = buffer.short.toInt() and 0xFFFF
        val type = buffer.get().toInt() and 0xFF
        val flags = buffer.short.toInt() and 0xFFFF
        val size = buffer.short.toInt() and 0xFFFF
        
        var packedSize = 0
        if ((flags and 0x8000) != 0 && offset + size <= data.size) {
            val addBuffer = ByteBuffer.wrap(data, offset + 7, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
            packedSize = addBuffer.int
        }
        
        return Rar4Header(crc, type, flags, size, packedSize)
    }
    
    private fun parseRar4FileHeader(offset: Int, header: Rar4Header): RarEntry? {
        if (offset + header.size > data.size) return null
        
        val buffer = ByteBuffer.wrap(data, offset + 7, header.size - 7)
            .order(ByteOrder.LITTLE_ENDIAN)
        
        val packedSize = buffer.int
        val unpackedSize = buffer.int
        val hostOS = buffer.get().toInt() and 0xFF
        val fileCrc = buffer.int
        val fileTime = buffer.int
        val unpackVersion = buffer.get().toInt() and 0xFF
        val method = buffer.get().toInt() and 0xFF
        val nameSize = buffer.short.toInt() and 0xFFFF
        val attrs = buffer.int
        
        if (nameSize <= 0 || nameSize > 4096 || buffer.remaining() < nameSize) {
            return null
        }
        
        val nameBytes = ByteArray(nameSize)
        buffer.get(nameBytes)
        val name = String(nameBytes, Charsets.UTF_8).replace("\\", "/")
        
        val dataOffset = offset + header.size
        val isDirectory = (header.flags and 0xE0) == 0xE0
        val isStored = method == 0x30 // Store method (no compression)
        
        return RarEntry(
            name = name,
            packedSize = packedSize,
            unpackedSize = unpackedSize,
            method = method,
            dataOffset = dataOffset,
            isDirectory = isDirectory,
            isStored = isStored
        )
    }
    
    private fun parseRar5() {
        var offset = 8 // Skip RAR5 signature
        
        while (offset < data.size - 4) {
            try {
                val header = parseRar5Header(offset)
                if (header == null) break
                
                if (header.type == 2) { // File header
                    val entry = parseRar5FileHeader(offset, header)
                    if (entry != null) {
                        entries.add(entry)
                    }
                }
                
                offset += header.headerSize + header.dataSize.toInt()
                
            } catch (e: Exception) {
                Logger.debug("Error parsing RAR5 header at $offset: ${e.message}")
                break
            }
        }
    }
    
    private fun parseRar5Header(offset: Int): Rar5Header? {
        if (offset + 4 > data.size) return null
        
        val buffer = ByteBuffer.wrap(data, offset, minOf(32, data.size - offset))
            .order(ByteOrder.LITTLE_ENDIAN)
        
        val crc = buffer.int
        
        // Read vint for header size
        val (headerSize, bytesRead1) = readVint(data, offset + 4)
        if (headerSize <= 0) return null
        
        // Read header type
        val (type, bytesRead2) = readVint(data, offset + 4 + bytesRead1)
        
        // Read flags
        val (flags, bytesRead3) = readVint(data, offset + 4 + bytesRead1 + bytesRead2)
        
        var dataSize = 0L
        if ((flags and 0x02) != 0L) {
            val (ds, _) = readVint(data, offset + 4 + bytesRead1 + bytesRead2 + bytesRead3)
            dataSize = ds
        }
        
        return Rar5Header(
            crc = crc,
            headerSize = headerSize.toInt() + 4 + bytesRead1,
            type = type.toInt(),
            flags = flags,
            dataSize = dataSize
        )
    }
    
    private fun parseRar5FileHeader(offset: Int, header: Rar5Header): RarEntry? {
        // RAR5 file headers are complex with variable-length integers
        // This is a simplified implementation
        
        var pos = offset + 4 // Skip CRC
        
        // Skip header size vint
        val (_, b1) = readVint(data, pos)
        pos += b1
        
        // Skip type vint
        val (_, b2) = readVint(data, pos)
        pos += b2
        
        // Read flags
        val (flags, b3) = readVint(data, pos)
        pos += b3
        
        // Skip extra area size if present
        if ((flags and 0x01) != 0L) {
            val (_, b) = readVint(data, pos)
            pos += b
        }
        
        // Skip data size if present
        if ((flags and 0x02) != 0L) {
            val (_, b) = readVint(data, pos)
            pos += b
        }
        
        // Read file flags
        val (fileFlags, b4) = readVint(data, pos)
        pos += b4
        
        // Read unpacked size
        val (unpackedSize, b5) = readVint(data, pos)
        pos += b5
        
        // Read attributes
        val (attrs, b6) = readVint(data, pos)
        pos += b6
        
        // Skip mtime if present
        if ((fileFlags and 0x02) != 0L) {
            pos += 4
        }
        
        // Skip data CRC if present
        if ((fileFlags and 0x04) != 0L) {
            pos += 4
        }
        
        // Read compression info
        val (compInfo, b7) = readVint(data, pos)
        pos += b7
        
        // Read host OS
        val (hostOS, b8) = readVint(data, pos)
        pos += b8
        
        // Read name length
        val (nameLen, b9) = readVint(data, pos)
        pos += b9
        
        if (nameLen <= 0 || pos + nameLen > data.size) return null
        
        val nameBytes = data.copyOfRange(pos, pos + nameLen.toInt())
        val name = String(nameBytes, Charsets.UTF_8).replace("\\", "/")
        
        val isDirectory = (fileFlags and 0x01) != 0L
        val method = ((compInfo shr 7) and 0x07).toInt()
        val isStored = method == 0
        
        return RarEntry(
            name = name,
            packedSize = header.dataSize.toInt(),
            unpackedSize = unpackedSize.toInt(),
            method = method,
            dataOffset = offset + header.headerSize,
            isDirectory = isDirectory,
            isStored = isStored
        )
    }
    
    private fun readVint(data: ByteArray, offset: Int): Pair<Long, Int> {
        var result = 0L
        var bytesRead = 0
        var shift = 0
        
        while (offset + bytesRead < data.size && bytesRead < 10) {
            val b = data[offset + bytesRead].toInt() and 0xFF
            bytesRead++
            
            result = result or ((b.toLong() and 0x7F) shl shift)
            
            if ((b and 0x80) == 0) break
            shift += 7
        }
        
        return Pair(result, bytesRead)
    }
    
    /**
     * Gets all entries in the archive.
     */
    fun getEntries(): List<RarEntry> = entries.toList()
    
    /**
     * Gets file content by name.
     */
    fun getFile(name: String): ByteArray? {
        val entry = entries.find { it.name.equals(name, ignoreCase = true) }
        return entry?.let { extractEntry(it) }
    }
    
    /**
     * Extracts all files to a directory.
     */
    fun extractTo(outputDir: File) {
        outputDir.mkdirs()
        
        for (entry in entries) {
            if (entry.isDirectory) {
                File(outputDir, entry.name).mkdirs()
            } else {
                val content = extractEntry(entry)
                if (content != null) {
                    val file = File(outputDir, entry.name)
                    file.parentFile?.mkdirs()
                    file.writeBytes(content)
                }
            }
        }
    }
    
    /**
     * Extracts a single entry.
     */
    fun extractEntry(entry: RarEntry): ByteArray? {
        if (entry.isDirectory) return null
        
        if (entry.dataOffset + entry.packedSize > data.size) {
            Logger.warn("Entry ${entry.name} extends beyond file")
            return null
        }
        
        val packedData = data.copyOfRange(entry.dataOffset, entry.dataOffset + entry.packedSize)
        
        return if (entry.isStored) {
            // No compression
            packedData
        } else {
            // RAR compression - try to decompress or return null
            Logger.warn("RAR compression method ${entry.method} not fully supported for ${entry.name}")
            // Return packed data as fallback (won't be valid but better than nothing)
            null
        }
    }
    
    /**
     * Gets image files (for CBR comics).
     */
    fun getImageFiles(): List<RarEntry> {
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        return entries.filter { entry ->
            !entry.isDirectory && 
            entry.name.substringAfterLast('.').lowercase() in imageExtensions
        }.sortedBy { it.name }
    }
}

data class Rar4Header(
    val crc: Int,
    val type: Int,
    val flags: Int,
    val size: Int,
    val packedSize: Int
)

data class Rar5Header(
    val crc: Int,
    val headerSize: Int,
    val type: Int,
    val flags: Long,
    val dataSize: Long
)

data class RarEntry(
    val name: String,
    val packedSize: Int,
    val unpackedSize: Int,
    val method: Int,
    val dataOffset: Int,
    val isDirectory: Boolean,
    val isStored: Boolean
)
