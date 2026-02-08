package org.calibre.conversion

import org.calibre.utils.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

/**
 * Output plugin for LRF (Sony Reader) format.
 * 
 * Creates LRF files from the OEB intermediate format.
 * LRF is a binary format used by Sony Reader devices.
 */
class LrfOutput : OutputPlugin {
    
    override val name = "LRF Output"
    override val fileType = "lrf"
    
    companion object {
        private const val LRF_SIGNATURE = "LRF"
        private const val LRF_VERSION = 999
        private const val OBJECT_HEADER_SIZE = 18
        
        // Object types
        private const val OBJECT_PAGE_TREE = 1
        private const val OBJECT_PAGE = 2
        private const val OBJECT_HEADER = 3
        private const val OBJECT_FOOTER = 4
        private const val OBJECT_BLOCK = 5
        private const val OBJECT_TEXT = 6
        private const val OBJECT_IMAGE = 7
        private const val OBJECT_CANVAS = 8
        private const val OBJECT_PARAGRAPH = 9
        private const val OBJECT_STYLE = 10
    }
    
    override fun convert(book: OebBook, outputFile: File) {
        val output = ByteArrayOutputStream()
        
        // Extract text content from OEB
        val textContent = extractTextContent(book)
        val images = extractImages(book)
        
        // Build objects
        val objects = mutableListOf<LrfObject>()
        var objectId = 1
        
        // Create root object
        objects.add(LrfObject(
            id = objectId++,
            type = OBJECT_PAGE_TREE,
            data = buildPageTree()
        ))
        
        // Create text blocks
        val paragraphs = textContent.split("\n\n").filter { it.isNotBlank() }
        val textBlockIds = mutableListOf<Int>()
        
        for (para in paragraphs) {
            val textId = objectId++
            objects.add(LrfObject(
                id = textId,
                type = OBJECT_TEXT,
                data = buildTextObject(para)
            ))
            textBlockIds.add(textId)
        }
        
        // Create image objects
        val imageIds = mutableMapOf<String, Int>()
        for ((name, data) in images) {
            val imageId = objectId++
            objects.add(LrfObject(
                id = imageId,
                type = OBJECT_IMAGE,
                data = data
            ))
            imageIds[name] = imageId
        }
        
        // Create pages
        val pageIds = mutableListOf<Int>()
        val blocksPerPage = 20
        
        for (i in textBlockIds.indices step blocksPerPage) {
            val pageId = objectId++
            val pageBlocks = textBlockIds.subList(i, minOf(i + blocksPerPage, textBlockIds.size))
            objects.add(LrfObject(
                id = pageId,
                type = OBJECT_PAGE,
                data = buildPageObject(pageBlocks)
            ))
            pageIds.add(pageId)
        }
        
        // Build LRF file
        val lrfData = buildLrfFile(book, objects, pageIds)
        
        outputFile.writeBytes(lrfData)
        Logger.info("Created LRF file: ${outputFile.absolutePath}")
    }
    
    private fun buildLrfFile(book: OebBook, objects: List<LrfObject>, pageIds: List<Int>): ByteArray {
        val output = ByteArrayOutputStream()
        
        // Compress object data
        val compressedObjects = objects.map { obj ->
            LrfObject(obj.id, obj.type, compress(obj.data))
        }
        
        // Calculate sizes
        val headerSize = 78
        val metadataSize = 256
        val objectTableOffset = headerSize + metadataSize
        val objectTableSize = objects.size * 16
        val objectDataOffset = objectTableOffset + objectTableSize
        
        var totalObjectDataSize = 0
        for (obj in compressedObjects) {
            totalObjectDataSize += OBJECT_HEADER_SIZE + obj.data.size
        }
        
        // Write signature
        output.write(LRF_SIGNATURE.toByteArray(Charsets.US_ASCII))
        output.write(0) // Null terminator
        
        // Write version
        output.write(shortToBytes(LRF_VERSION))
        
        // Write pseudo encryption key (0 = unencrypted)
        output.write(shortToBytes(0))
        
        // Write root object ID
        output.write(intToBytes(1))
        
        // Write object count
        output.write(longToBytes(objects.size.toLong()))
        
        // Write object index offset
        output.write(longToBytes(objectTableOffset.toLong()))
        
        // Write unknown fields
        output.write(intToBytes(0))
        output.write(intToBytes(0x0A))
        
        // Write binding direction (0 = LTR)
        output.write(byteArrayOf(0))
        
        // Write padding
        output.write(ByteArray(1))
        
        // Write dimensions (600x800 default Sony Reader size)
        output.write(shortToBytes(600))
        output.write(shortToBytes(800))
        
        // Write color depth
        output.write(byteArrayOf(24))
        
        // Padding
        output.write(ByteArray(23))
        
        // Write thumbnail (empty)
        output.write(intToBytes(0)) // Thumbnail offset
        output.write(intToBytes(0)) // Thumbnail size
        
        // Pad to header size
        while (output.size() < headerSize) {
            output.write(0)
        }
        
        // Write metadata
        val metadata = book.metadata
        val titleBytes = metadata.title.toByteArray(Charsets.UTF_16LE)
        output.write(titleBytes.copyOf(minOf(titleBytes.size, 128)))
        while (output.size() < headerSize + 128) output.write(0)
        
        val authorBytes = metadata.authors.joinToString(", ").toByteArray(Charsets.UTF_16LE)
        output.write(authorBytes.copyOf(minOf(authorBytes.size, 128)))
        while (output.size() < headerSize + metadataSize) output.write(0)
        
        // Write object table
        var dataOffset = objectDataOffset
        for (obj in compressedObjects) {
            output.write(intToBytes(obj.id))
            output.write(intToBytes(dataOffset))
            output.write(intToBytes(obj.data.size + OBJECT_HEADER_SIZE))
            output.write(intToBytes(0)) // Reserved
            dataOffset += obj.data.size + OBJECT_HEADER_SIZE
        }
        
        // Write object data
        for (obj in compressedObjects) {
            // Object header
            output.write(shortToBytes(0xF500)) // Tag
            output.write(shortToBytes(obj.type))
            output.write(intToBytes(obj.id))
            output.write(intToBytes(obj.data.size))
            output.write(intToBytes(0)) // Reserved
            output.write(shortToBytes(0)) // Reserved
            
            // Object data
            output.write(obj.data)
        }
        
        return output.toByteArray()
    }
    
    private fun buildPageTree(): ByteArray {
        // Simple page tree structure
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(0, 0, 0, 1)) // Page count placeholder
        return output.toByteArray()
    }
    
    private fun buildPageObject(blockIds: List<Int>): ByteArray {
        val output = ByteArrayOutputStream()
        
        // Page properties
        output.write(intToBytes(blockIds.size))
        for (id in blockIds) {
            output.write(intToBytes(id))
        }
        
        return output.toByteArray()
    }
    
    private fun buildTextObject(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        
        // Text encoded as UTF-16LE
        val textBytes = text.toByteArray(Charsets.UTF_16LE)
        
        // Text object header
        output.write(intToBytes(textBytes.size))
        output.write(textBytes)
        
        return output.toByteArray()
    }
    
    private fun extractTextContent(book: OebBook): String {
        val sb = StringBuilder()
        
        for (item in book.spine) {
            if (item.file.exists() && item.mediaType.contains("html")) {
                val html = item.file.readText()
                // Simple HTML to text conversion
                val text = html
                    .replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
                    .replace(Regex("<br[^>]*>"), "\n")
                    .replace(Regex("</p>"), "\n\n")
                    .replace(Regex("</div>"), "\n")
                    .replace(Regex("</h[1-6]>"), "\n\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace(Regex("&nbsp;"), " ")
                    .replace(Regex("&lt;"), "<")
                    .replace(Regex("&gt;"), ">")
                    .replace(Regex("&amp;"), "&")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                
                if (text.isNotBlank()) {
                    sb.append(text).append("\n\n")
                }
            }
        }
        
        return sb.toString()
    }
    
    private fun extractImages(book: OebBook): Map<String, ByteArray> {
        val images = mutableMapOf<String, ByteArray>()
        
        for ((id, item) in book.manifest) {
            if (item.mediaType.startsWith("image/") && item.file.exists()) {
                images[item.href] = item.file.readBytes()
            }
        }
        
        return images
    }
    
    private fun compress(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        
        deflater.end()
        
        val compressed = output.toByteArray()
        return if (compressed.size < data.size) compressed else data
    }
    
    private fun shortToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
    }
    
    private fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }
    
    private fun longToBytes(value: Long): ByteArray {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
    }
}

private data class LrfObject(
    val id: Int,
    val type: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LrfObject) return false
        return id == other.id && type == other.type
    }
    
    override fun hashCode(): Int = id
}
