package org.calibre.formats.pdb

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * eReader format parser (.pdb files with PNPd/PNRd identity).
 * 
 * eReader was a popular ebook format for Palm devices, later acquired by
 * Barnes & Noble. It supports:
 * - Basic text formatting (bold, italic, headings)
 * - Images
 * - Encryption (DRM) - we only support non-DRM content
 * - Footnotes and sidebars
 * 
 * The format uses zlib compression for text records.
 */
class EReaderParser(private val data: ByteArray) {
    
    private val header: PdbHeader
    private val eReaderHeader: EReaderHeader
    private val records: List<ByteArray>
    
    init {
        val stream = ByteArrayInputStream(data)
        header = PdbHeader.read(stream)
        
        if (header.identity != PdbHeader.EREADER_1 && header.identity != PdbHeader.EREADER_2) {
            throw IllegalArgumentException("Not an eReader file: identity is ${header.identity}")
        }
        
        // Read all records
        records = mutableListOf<ByteArray>().apply {
            for (i in 0 until header.numRecords) {
                add(readRecord(i))
            }
        }
        
        // Parse eReader header from first record
        eReaderHeader = EReaderHeader.read(records[0])
    }
    
    private fun readRecord(index: Int): ByteArray {
        val offset = header.recordOffsets[index].offset.toInt()
        val endOffset = if (index + 1 < header.numRecords) {
            header.recordOffsets[index + 1].offset.toInt()
        } else {
            data.size
        }
        
        return data.copyOfRange(offset, endOffset)
    }
    
    /**
     * Returns the book title from the PDB header.
     */
    fun getTitle(): String = header.name
    
    /**
     * Checks if the book is encrypted (DRM).
     */
    fun isEncrypted(): Boolean = eReaderHeader.hasEncryption
    
    /**
     * Gets metadata from the book.
     */
    fun getMetadata(): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        
        // Try to read metadata from the metadata record
        if (eReaderHeader.metadataRecordIndex > 0 && 
            eReaderHeader.metadataRecordIndex < records.size) {
            val metadataRecord = records[eReaderHeader.metadataRecordIndex]
            parseMetadataRecord(metadataRecord, metadata)
        }
        
        metadata["title"] = header.name
        return metadata
    }
    
    private fun parseMetadataRecord(data: ByteArray, metadata: MutableMap<String, String>) {
        try {
            val text = String(data, Charsets.UTF_8)
            val lines = text.split("\n")
            for (line in lines) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    metadata[parts[0].trim().lowercase()] = parts[1].trim()
                }
            }
        } catch (e: Exception) {
            // Ignore metadata parsing errors
        }
    }
    
    /**
     * Extracts the text content of the book.
     * 
     * @return The text content, formatted as HTML
     */
    fun extractText(): String {
        if (isEncrypted()) {
            throw UnsupportedOperationException("Encrypted eReader files are not supported")
        }
        
        val textBuilder = StringBuilder()
        textBuilder.append("<html><head><title>${header.name}</title></head><body>\n")
        
        // Text records start after header record
        val startRecord = 1
        val endRecord = eReaderHeader.textRecordCount + 1
        
        for (i in startRecord until minOf(endRecord, records.size)) {
            val recordData = records[i]
            val decompressed = decompressRecord(recordData)
            val text = convertToHtml(decompressed)
            textBuilder.append(text)
        }
        
        textBuilder.append("</body></html>")
        return textBuilder.toString()
    }
    
    private fun decompressRecord(data: ByteArray): ByteArray {
        return when (eReaderHeader.compression) {
            EReaderHeader.COMPRESSION_ZLIB -> {
                try {
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
                    // Fall back to raw data if decompression fails
                    data
                }
            }
            else -> data
        }
    }
    
    private fun convertToHtml(data: ByteArray): String {
        val text = String(data, Charsets.ISO_8859_1)
        val html = StringBuilder()
        
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    // Escape sequence
                    val next = text[i + 1]
                    when (next) {
                        'n' -> { html.append("<br>\n"); i += 2 }
                        'p' -> { html.append("<p>"); i += 2 }
                        'c' -> { html.append("<center>"); i += 2 }
                        'r' -> { html.append("</center>"); i += 2 }
                        'B' -> { html.append("<b>"); i += 2 }
                        'b' -> { html.append("</b>"); i += 2 }
                        'I' -> { html.append("<i>"); i += 2 }
                        'i' -> { html.append("</i>"); i += 2 }
                        'U' -> { html.append("<u>"); i += 2 }
                        'u' -> { html.append("</u>"); i += 2 }
                        's' -> { html.append("<s>"); i += 2 }
                        't' -> { html.append("</s>"); i += 2 }
                        'x' -> {
                            // Chapter heading
                            html.append("<h2>")
                            i += 2
                        }
                        '\\' -> { html.append("\\"); i += 2 }
                        else -> { html.append(c); i++ }
                    }
                }
                c == '<' -> { html.append("&lt;"); i++ }
                c == '>' -> { html.append("&gt;"); i++ }
                c == '&' -> { html.append("&amp;"); i++ }
                c.code < 32 && c != '\n' && c != '\r' && c != '\t' -> i++
                else -> { html.append(c); i++ }
            }
        }
        
        return html.toString()
    }
    
    /**
     * Extracts images from the book.
     * 
     * @return Map of image names to image data
     */
    fun extractImages(): Map<String, ByteArray> {
        val images = mutableMapOf<String, ByteArray>()
        
        if (eReaderHeader.imageRecordCount > 0 && eReaderHeader.imageDataRecordIndex > 0) {
            for (i in 0 until eReaderHeader.imageRecordCount) {
                val recordIndex = eReaderHeader.imageDataRecordIndex + i
                if (recordIndex < records.size) {
                    val imageData = records[recordIndex]
                    val imageName = "image_${i}.png"
                    images[imageName] = imageData
                }
            }
        }
        
        return images
    }
}

/**
 * eReader format header (record 0).
 */
data class EReaderHeader(
    val version: Int,
    val encryption: Int,
    val textRecordCount: Int,
    val imageRecordCount: Int,
    val imageDataRecordIndex: Int,
    val metadataRecordIndex: Int,
    val footnoteRecordIndex: Int,
    val sidebarRecordIndex: Int,
    val compression: Int
) {
    val hasEncryption: Boolean get() = encryption != 0
    
    companion object {
        const val COMPRESSION_NONE = 0
        const val COMPRESSION_ZLIB = 2
        
        fun read(data: ByteArray): EReaderHeader {
            if (data.size < 132) {
                throw IllegalArgumentException("eReader header too short")
            }
            
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            
            val compression = buffer.short.toInt() and 0xFFFF
            buffer.position(buffer.position() + 2) // Skip unused
            val encryption = buffer.short.toInt() and 0xFFFF
            buffer.position(buffer.position() + 2) // Skip eReaderVersion
            
            val textRecordCount = buffer.short.toInt() and 0xFFFF
            val imageRecordCount = buffer.short.toInt() and 0xFFFF
            
            buffer.position(buffer.position() + 8) // Skip reserved
            
            // Version-specific header parsing
            val version = buffer.short.toInt() and 0xFFFF
            
            var metadataRecordIndex = 0
            var imageDataRecordIndex = 0
            var footnoteRecordIndex = 0
            var sidebarRecordIndex = 0
            
            if (data.size >= 132) {
                buffer.position(20)
                metadataRecordIndex = buffer.short.toInt() and 0xFFFF
                buffer.position(buffer.position() + 2)
                imageDataRecordIndex = buffer.short.toInt() and 0xFFFF
                buffer.position(buffer.position() + 2)
                footnoteRecordIndex = buffer.short.toInt() and 0xFFFF
                sidebarRecordIndex = buffer.short.toInt() and 0xFFFF
            }
            
            return EReaderHeader(
                version = version,
                encryption = encryption,
                textRecordCount = textRecordCount,
                imageRecordCount = imageRecordCount,
                imageDataRecordIndex = imageDataRecordIndex,
                metadataRecordIndex = metadataRecordIndex,
                footnoteRecordIndex = footnoteRecordIndex,
                sidebarRecordIndex = sidebarRecordIndex,
                compression = compression
            )
        }
    }
}
