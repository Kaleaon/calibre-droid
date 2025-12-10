package org.calibre.formats.lrf

import org.calibre.utils.Logger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * Sony LRF (Librie/Reader Format) parser.
 * 
 * LRF is an ebook format used by Sony e-readers. It contains:
 * - A fixed header with metadata
 * - Object streams (text, images, styles)
 * - Page layout information
 * 
 * The format uses a pseudo-encrypted compression where objects
 * are scrambled with a simple XOR cipher.
 */
class LrfParser(private val data: ByteArray) {
    
    private val header: LrfHeader
    private val objects = mutableMapOf<Int, LrfObject>()
    private var textContent = StringBuilder()
    
    init {
        if (data.size < 40) {
            throw IllegalArgumentException("File too small to be a valid LRF file")
        }
        
        header = LrfHeader.read(data)
        
        if (!header.isValid) {
            throw IllegalArgumentException("Invalid LRF file: magic bytes don't match")
        }
        
        parseObjects()
    }
    
    private fun parseObjects() {
        try {
            var offset = header.objectOffset.toInt()
            
            for (i in 0 until header.objectCount) {
                if (offset + 16 >= data.size) break
                
                val buffer = ByteBuffer.wrap(data, offset, minOf(16, data.size - offset))
                    .order(ByteOrder.LITTLE_ENDIAN)
                
                val objId = buffer.int
                val objOffset = buffer.int.toLong() and 0xFFFFFFFFL
                val objSize = buffer.int
                val objType = buffer.short.toInt() and 0xFFFF
                
                if (objOffset > 0 && objOffset < data.size && objSize > 0 && objSize < data.size) {
                    try {
                        val objData = extractObjectData(objOffset.toInt(), objSize)
                        objects[objId] = LrfObject(objId, objType, objData)
                        
                        // Process text objects
                        if (objType == OBJ_TYPE_TEXT || objType == OBJ_TYPE_TEXT_BLOCK) {
                            extractTextFromObject(objData)
                        }
                    } catch (e: Exception) {
                        Logger.debug("Error extracting LRF object $objId: ${e.message}")
                    }
                }
                
                offset += 16
            }
        } catch (e: Exception) {
            Logger.warn("Error parsing LRF objects: ${e.message}")
        }
        
        // If no text was extracted, try scanning for text
        if (textContent.isEmpty()) {
            scanForText()
        }
    }
    
    private fun extractObjectData(offset: Int, size: Int): ByteArray {
        val actualSize = minOf(size, data.size - offset)
        val rawData = data.copyOfRange(offset, offset + actualSize)
        
        // LRF uses a simple XOR cipher for "encryption"
        val key = header.scrambleKey
        if (key != 0.toShort()) {
            for (i in rawData.indices) {
                rawData[i] = (rawData[i].toInt() xor (key.toInt() shr (i % 16 * 8))).toByte()
            }
        }
        
        // Try to decompress
        return tryDecompress(rawData)
    }
    
    private fun tryDecompress(data: ByteArray): ByteArray {
        return try {
            val inflater = Inflater()
            inflater.setInput(data)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                output.write(buffer, 0, count)
            }
            inflater.end()
            output.toByteArray()
        } catch (e: Exception) {
            data
        }
    }
    
    private fun extractTextFromObject(objData: ByteArray) {
        // LRF text objects use a tag-based format
        try {
            var i = 0
            while (i < objData.size) {
                val tag = objData[i].toInt() and 0xFF
                
                when (tag) {
                    TAG_TEXT_RUN -> {
                        // Text run - read length and text
                        if (i + 3 < objData.size) {
                            val len = ((objData[i + 1].toInt() and 0xFF) or
                                      ((objData[i + 2].toInt() and 0xFF) shl 8))
                            if (i + 3 + len <= objData.size) {
                                val text = String(objData.copyOfRange(i + 3, i + 3 + len), Charsets.UTF_16LE)
                                textContent.append(text)
                                i += 3 + len
                            } else {
                                i++
                            }
                        } else {
                            i++
                        }
                    }
                    TAG_PARAGRAPH_END -> {
                        textContent.append("\n\n")
                        i++
                    }
                    TAG_LINE_BREAK -> {
                        textContent.append("\n")
                        i++
                    }
                    else -> i++
                }
            }
        } catch (e: Exception) {
            Logger.debug("Error extracting text from LRF object: ${e.message}")
        }
    }
    
    private fun scanForText() {
        // Look for UTF-16LE text patterns in the data
        var i = header.objectOffset.toInt()
        while (i < data.size - 2) {
            // Look for printable ASCII in UTF-16LE
            if (data[i].toInt() and 0xFF in 0x20..0x7E && data[i + 1].toInt() == 0) {
                val start = i
                while (i < data.size - 1 && 
                       data[i].toInt() and 0xFF in 0x20..0x7E &&
                       data[i + 1].toInt() == 0) {
                    i += 2
                }
                if (i - start >= 8) { // At least 4 characters
                    val text = String(data.copyOfRange(start, i), Charsets.UTF_16LE)
                    textContent.append(text).append(" ")
                }
            } else {
                i++
            }
        }
    }
    
    /**
     * Gets the book title.
     */
    fun getTitle(): String = header.title.ifEmpty { "Unknown Title" }
    
    /**
     * Gets the book author.
     */
    fun getAuthor(): String = header.author.ifEmpty { "Unknown Author" }
    
    /**
     * Gets all metadata.
     */
    fun getMetadata(): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        
        if (header.title.isNotEmpty()) metadata["title"] = header.title
        if (header.author.isNotEmpty()) metadata["author"] = header.author
        if (header.publisher.isNotEmpty()) metadata["publisher"] = header.publisher
        if (header.category.isNotEmpty()) metadata["category"] = header.category
        
        return metadata
    }
    
    /**
     * Gets the text content as HTML.
     */
    fun getHtmlContent(): String {
        val html = StringBuilder()
        html.append("<!DOCTYPE html>\n<html><head>")
        html.append("<meta charset=\"UTF-8\">")
        html.append("<title>${escapeHtml(getTitle())}</title></head><body>\n")
        
        val text = textContent.toString()
        val paragraphs = text.split("\n\n")
        
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isNotEmpty()) {
                html.append("<p>${escapeHtml(trimmed)}</p>\n")
            }
        }
        
        html.append("</body></html>")
        return html.toString()
    }
    
    /**
     * Gets images from the book.
     */
    fun getImages(): Map<String, ByteArray> {
        val images = mutableMapOf<String, ByteArray>()
        
        for ((id, obj) in objects) {
            if (obj.type == OBJ_TYPE_IMAGE || obj.type == OBJ_TYPE_IMAGE_STREAM) {
                // Check for image signatures
                val data = obj.data
                val ext = when {
                    data.size >= 3 && data[0].toInt() == 0xFF && 
                    data[1].toInt() and 0xFF == 0xD8 -> "jpg"
                    data.size >= 8 && data.slice(0..7).map { it.toInt() and 0xFF } ==
                        listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "png"
                    data.size >= 6 && String(data.copyOfRange(0, 6)) == "GIF89a" -> "gif"
                    data.size >= 2 && data[0].toInt() and 0xFF == 0x42 &&
                    data[1].toInt() and 0xFF == 0x4D -> "bmp"
                    else -> "jpg"
                }
                images["image_$id.$ext"] = data
            }
        }
        
        return images
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
    
    companion object {
        // Object types
        private const val OBJ_TYPE_TEXT = 0x11
        private const val OBJ_TYPE_TEXT_BLOCK = 0x13
        private const val OBJ_TYPE_IMAGE = 0x49
        private const val OBJ_TYPE_IMAGE_STREAM = 0x4A
        
        // Text tags
        private const val TAG_TEXT_RUN = 0x32
        private const val TAG_PARAGRAPH_END = 0x33
        private const val TAG_LINE_BREAK = 0x36
    }
}

/**
 * LRF object data.
 */
data class LrfObject(
    val id: Int,
    val type: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LrfObject) return false
        return id == other.id && type == other.type && data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + type
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * LRF file header.
 */
data class LrfHeader(
    val magic: String,
    val version: Int,
    val scrambleKey: Short,
    val rootObjectId: Int,
    val objectCount: Int,
    val objectOffset: Long,
    val title: String,
    val author: String,
    val publisher: String,
    val category: String
) {
    val isValid: Boolean get() = magic == "LRF" || magic == "LRX"
    
    companion object {
        fun read(data: ByteArray): LrfHeader {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            
            // Magic (4 bytes)
            val magicBytes = ByteArray(4)
            buffer.get(magicBytes)
            val magic = String(magicBytes, Charsets.US_ASCII).trimEnd('\u0000')
            
            val version = buffer.short.toInt() and 0xFFFF
            val scrambleKey = buffer.short
            val rootObjectId = buffer.int
            
            buffer.position(16)
            val objectCount = buffer.int
            val objectOffset = buffer.long
            
            // Metadata fields are at fixed offsets
            val title = readMetadataString(data, 0x30, 128)
            val author = readMetadataString(data, 0xB0, 128)
            val publisher = readMetadataString(data, 0x130, 128)
            val category = readMetadataString(data, 0x1B0, 128)
            
            return LrfHeader(
                magic = magic,
                version = version,
                scrambleKey = scrambleKey,
                rootObjectId = rootObjectId,
                objectCount = objectCount,
                objectOffset = objectOffset,
                title = title,
                author = author,
                publisher = publisher,
                category = category
            )
        }
        
        private fun readMetadataString(data: ByteArray, offset: Int, maxLen: Int): String {
            if (offset + 4 >= data.size) return ""
            
            val buffer = ByteBuffer.wrap(data, offset, minOf(4, data.size - offset))
                .order(ByteOrder.LITTLE_ENDIAN)
            val strLen = buffer.int
            
            if (strLen <= 0 || strLen > maxLen || offset + 4 + strLen * 2 > data.size) return ""
            
            return try {
                String(data.copyOfRange(offset + 4, offset + 4 + strLen * 2), Charsets.UTF_16LE)
                    .trimEnd('\u0000')
            } catch (e: Exception) {
                ""
            }
        }
    }
}
