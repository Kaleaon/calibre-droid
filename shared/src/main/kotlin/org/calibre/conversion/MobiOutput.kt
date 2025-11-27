package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * MOBI Output plugin.
 * Creates MOBI/AZW format files from OEB books.
 * 
 * This implementation provides enhanced MOBI format support with:
 * - Palm Database (PDB) format wrapper
 * - MOBI header with EXTH records for metadata
 * - PalmDoc compression
 * - Proper record structure
 * 
 * Note: This is still a simplified implementation. Full MOBI support would also include:
 * - Huff/CDIC compression option
 * - Image embedding
 * - NCX index generation
 * - Guide references
 * - KF8 format support
 */
class MobiOutput : OutputPlugin {
    override val name = "MOBI Output"
    override val fileType = "mobi"
    
    companion object {
        private const val RECORD_SIZE = 4096 // Standard MOBI record size
        private const val MOBI_TYPE_BOOK = 0x002
        private const val COMPRESSION_PALMDOC = 2
        private const val COMPRESSION_UNCOMPRESSED = 1
    }
    
    override fun convert(book: OebBook, outputFile: File) {
        // Convert OEB to HTML first
        val htmlContent = convertToHtml(book)
        
        // Create MOBI file
        createMobiFile(htmlContent, book.metadata, outputFile)
    }
    
    private fun convertToHtml(book: OebBook): String {
        val sb = StringBuilder()
        sb.append("""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${escapeHtml(book.metadata.title)}</title>
</head>
<body>
""")
        
        // Add title page
        sb.append("<h1>${escapeHtml(book.metadata.title)}</h1>")
        if (book.metadata.authors.isNotEmpty()) {
            sb.append("<h2>${escapeHtml(book.metadata.authors.joinToString(", "))}</h2>")
        }
        sb.append("<hr/>")
        
        // Add content from spine
        for (item in book.spine) {
            if (item.isXhtml()) {
                if (item.file.exists()) {
                    val content = item.file.readText()
                    val bodyContent = extractBody(content)
                    sb.append("<div class='chapter'>")
                    sb.append(bodyContent)
                    sb.append("</div>")
                }
            }
        }
        
        sb.append("</body></html>")
        return sb.toString()
    }
    
    private fun extractBody(content: String): String {
        val bodyStart = content.indexOf("<body", ignoreCase = true)
        if (bodyStart == -1) return content
        
        val actualStart = content.indexOf(">", bodyStart) + 1
        val bodyEnd = content.lastIndexOf("</body>", ignoreCase = true)
        
        if (actualStart > 0 && bodyEnd > actualStart) {
            return content.substring(actualStart, bodyEnd)
        }
        if (actualStart > 0) return content.substring(actualStart)
        
        return content
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    /**
     * Creates a MOBI file with proper PDB header, MOBI header, and compression.
     */
    private fun createMobiFile(htmlContent: String, metadata: Metadata, outputFile: File) {
        val records = mutableListOf<ByteArray>()
        
        // Convert HTML to bytes
        val htmlBytes = htmlContent.toByteArray(StandardCharsets.UTF_8)
        val textLength = htmlBytes.size
        
        // Create text records with PalmDoc compression
        val textRecords = createTextRecords(htmlBytes, compress = true)
        records.addAll(textRecords)
        
        val lastTextRecordIdx = textRecords.size
        val firstNonTextRecordIdx = lastTextRecordIdx + 1
        
        // Create FLIS record
        val flisRecord = createFlisRecord()
        records.add(flisRecord)
        
        // Create FCIS record
        val fcisRecord = createFcisRecord(textLength)
        records.add(fcisRecord)
        
        // Create EOF record
        records.add(byteArrayOf(0xE9.toByte(), 0x8E.toByte(), 0x0D.toByte(), 0x0A.toByte()))
        
        // Create MOBI header (record 0)
        val mobiHeader = createMobiHeader(metadata, textLength, lastTextRecordIdx, firstNonTextRecordIdx)
        records.add(0, mobiHeader)
        
        // Write PDB file
        writePdbFile(outputFile, records, metadata)
        
        org.calibre.utils.Logger.info(
            "MOBI file created: ${outputFile.name} " +
            "(${records.size} records, ${textLength} bytes text, compressed)"
        )
    }
    
    /**
     * Creates text records from HTML content with optional PalmDoc compression.
     */
    private fun createTextRecords(htmlBytes: ByteArray, compress: Boolean): List<ByteArray> {
        val records = mutableListOf<ByteArray>()
        var offset = 0
        
        while (offset < htmlBytes.size) {
            val remaining = htmlBytes.size - offset
            val chunkSize = minOf(RECORD_SIZE, remaining)
            val chunk = htmlBytes.sliceArray(offset until (offset + chunkSize))
            
            // Compress if requested
            val compressed = if (compress) {
                PalmDocCompression.compress(chunk)
            } else {
                chunk
            }
            
            // Calculate overlap (for next record)
            val overlapSize = if (offset + chunkSize < htmlBytes.size) {
                minOf(10, htmlBytes.size - offset - chunkSize)
            } else {
                0
            }
            
            val record = ByteArrayOutputStream().apply {
                write(compressed)
                if (overlapSize > 0) {
                    val overlap = htmlBytes.sliceArray(offset + chunkSize until offset + chunkSize + overlapSize)
                    write(overlap)
                }
                write(overlapSize.toByte().toInt())
            }.toByteArray()
            
            records.add(record)
            offset += chunkSize
        }
        
        return records
    }
    
    /**
     * Creates FLIS record (File Information Section).
     */
    private fun createFlisRecord(): ByteArray {
        // FLIS header: "FLIS" + length + version + unknown
        return byteArrayOf(
            0x46, 0x4C, 0x49, 0x53, // "FLIS"
            0x00, 0x00, 0x00, 0x08, // Length
            0x00, 0x41, // Version
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x00, 0x01, 0x00, 0x03, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
    }
    
    /**
     * Creates FCIS record (File Content Information Section).
     */
    private fun createFcisRecord(textLength: Int): ByteArray {
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN)
        buffer.put("FCIS".toByteArray(StandardCharsets.ISO_8859_1))
        buffer.putInt(20) // Length
        buffer.putInt(16) // Unknown
        buffer.putInt(1) // Unknown
        buffer.putInt(0) // Unknown
        buffer.putInt(textLength) // Text length
        buffer.putInt(0) // Unknown
        buffer.putInt(0) // Unknown
        buffer.putInt(32) // Unknown
        buffer.putInt(8) // Unknown
        buffer.putInt(1) // Unknown
        buffer.putInt(1) // Unknown
        buffer.putInt(0) // Unknown
        return buffer.array()
    }
    
    /**
     * Creates MOBI header (record 0) with EXTH records.
     */
    private fun createMobiHeader(
        metadata: Metadata,
        textLength: Int,
        lastTextRecordIdx: Int,
        firstNonTextRecordIdx: Int
    ): ByteArray {
        val exth = buildExth(metadata)
        val title = metadata.title.toByteArray(StandardCharsets.UTF_8)
        
        // MOBI header structure
        val headerSize = 0xE8 + 16 + exth.size + title.size
        val buffer = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN)
        
        // Compression and basic info (0x00-0x0F)
        buffer.putShort(COMPRESSION_PALMDOC.toShort())
        buffer.putShort(0) // Unused
        buffer.putInt(textLength)
        buffer.putShort(lastTextRecordIdx.toShort())
        buffer.putShort(RECORD_SIZE.toShort())
        buffer.putShort(0) // Unused
        buffer.putShort(0) // Unused
        
        // MOBI identifier (0x10-0x13)
        buffer.put("MOBI".toByteArray(StandardCharsets.ISO_8859_1))
        
        // Header length and type (0x14-0x1B)
        buffer.putInt(0xE8) // Header length
        buffer.putInt(MOBI_TYPE_BOOK) // MOBI type
        buffer.putInt(65001) // Text encoding (UTF-8)
        buffer.putInt(UUID.randomUUID().hashCode()) // UID
        
        // Generator version (0x1C-0x1F)
        buffer.putInt(6) // Version 6
        
        // Unknown (0x20-0x3F)
        buffer.put(ByteArray(32) { 0xFF.toByte() })
        
        // Secondary index record (0x40-0x43)
        buffer.putInt(0xFFFFFFFF.toInt())
        
        // Unknown (0x44-0x5F)
        buffer.put(ByteArray(28) { 0xFF.toByte() })
        
        // First non-text record (0x60-0x63)
        buffer.putInt(firstNonTextRecordIdx)
        
        // Title offset and length (0x64-0x6B)
        val titleOffset = 0xE8 + 16 + exth.size
        buffer.putInt(titleOffset)
        buffer.putInt(title.size)
        
        // Language (0x6C-0x6F)
        val languageCode = getLanguageCode(metadata.language)
        buffer.putInt(languageCode)
        
        // Input/Output language (0x70-0x77)
        buffer.put(ByteArray(8))
        
        // Format version (0x78-0x7B)
        buffer.putInt(6)
        
        // First image record (0x7C-0x7F)
        buffer.putInt(0xFFFFFFFF.toInt())
        
        // EXTH flags (0x80-0x83)
        // Bit 6 (0x40) indicates EXTH header present
        buffer.putInt(0x50) // EXTH flags
        
        // Unknown (0x84-0xE7)
        buffer.put(ByteArray(100) { 0xFF.toByte() })
        
        // EXTH header
        buffer.put(exth)
        
        // Title
        buffer.put(title)
        
        return buffer.array()
    }
    
    /**
     * Builds EXTH (Extended Header) records for metadata.
     */
    private fun buildExth(metadata: Metadata): ByteArray {
        val exth = ByteArrayOutputStream()
        var recordCount = 0
        
        // EXTH header: "EXTH" + length + record count
        val headerSize = 12
        var totalSize = headerSize
        
        val records = mutableListOf<ByteArray>()
        
        // Creator (author)
        if (metadata.authors.isNotEmpty()) {
            val author = metadata.authors.joinToString(", ")
            val data = author.toByteArray(StandardCharsets.UTF_8)
            records.add(createExthRecord(100, data))
            totalSize += 8 + data.size
            recordCount++
        }
        
        // Publisher
        if (metadata.publisher.isNotEmpty()) {
            val data = metadata.publisher.toByteArray(StandardCharsets.UTF_8)
            records.add(createExthRecord(101, data))
            totalSize += 8 + data.size
            recordCount++
        }
        
        // Description
        if (metadata.description.isNotEmpty()) {
            val data = metadata.description.toByteArray(StandardCharsets.UTF_8)
            records.add(createExthRecord(103, data))
            totalSize += 8 + data.size
            recordCount++
        }
        
        // Subject (tags)
        if (metadata.tags.isNotEmpty()) {
            val tags = metadata.tags.joinToString(", ")
            val data = tags.toByteArray(StandardCharsets.UTF_8)
            records.add(createExthRecord(105, data))
            totalSize += 8 + data.size
            recordCount++
        }
        
        // Publication date
        val dateStr = metadata.publicationDate ?: metadata.dateAdded?.toString() ?: ""
        if (dateStr.isNotEmpty()) {
            val data = dateStr.toByteArray(StandardCharsets.UTF_8)
            records.add(createExthRecord(106, data))
            totalSize += 8 + data.size
            recordCount++
        }
        
        // Source (UUID)
        val uuid = UUID.randomUUID().toString()
        val source = "calibre:$uuid".toByteArray(StandardCharsets.UTF_8)
        records.add(createExthRecord(112, source))
        totalSize += 8 + source.size
        recordCount++
        
        // ASIN (UUID)
        val asin = uuid.toByteArray(StandardCharsets.UTF_8)
        records.add(createExthRecord(113, asin))
        totalSize += 8 + asin.size
        recordCount++
        
        // Content type
        records.add(createExthRecord(501, "EBOK".toByteArray(StandardCharsets.ISO_8859_1)))
        totalSize += 12
        recordCount++
        
        // Write EXTH header
        val header = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN)
        header.put("EXTH".toByteArray(StandardCharsets.ISO_8859_1))
        header.putInt(totalSize)
        header.putInt(recordCount)
        exth.write(header.array())
        
        // Write records
        for (record in records) {
            exth.write(record)
        }
        
        return exth.toByteArray()
    }
    
    /**
     * Creates a single EXTH record.
     */
    private fun createExthRecord(code: Int, data: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(8 + data.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(code)
        buffer.putInt(8 + data.size)
        buffer.put(data)
        return buffer.array()
    }
    
    /**
     * Gets language code for MOBI format.
     */
    private fun getLanguageCode(language: String): Int {
        // Simplified: return 0 (unknown) or map common languages
        // Full implementation would use IANA to MOBI language code mapping
        return when (language.lowercase().take(2)) {
            "en" -> 9 // English
            "fr" -> 12 // French
            "de" -> 7 // German
            "es" -> 10 // Spanish
            "it" -> 11 // Italian
            "pt" -> 20 // Portuguese
            "ru" -> 25 // Russian
            "ja" -> 17 // Japanese
            "zh" -> 33 // Chinese
            else -> 0 // Unknown
        }
    }
    
    /**
     * Writes the complete PDB file with header and records.
     */
    private fun writePdbFile(outputFile: File, records: List<ByteArray>, metadata: Metadata) {
        RandomAccessFile(outputFile, "rw").use { raf ->
            // PDB Header (78 bytes)
            val title = metadata.title
                .replace(" ", "_")
                .take(31)
                .padEnd(32, '\u0000')
                .toByteArray(StandardCharsets.ISO_8859_1)
            
            raf.write(title)
            
            // Attributes, version, dates
            raf.writeShort(0) // Attributes
            raf.writeShort(0) // Version
            val now = (System.currentTimeMillis() / 1000).toInt()
            raf.writeInt(now) // Creation date
            raf.writeInt(now) // Modification date
            raf.writeInt(0) // Last backup date
            raf.writeInt(0) // Modification number
            raf.writeInt(0) // App info ID
            raf.writeInt(0) // Sort info ID
            
            // Type and Creator
            raf.write("BOOK".toByteArray(StandardCharsets.ISO_8859_1))
            raf.write("MOBI".toByteArray(StandardCharsets.ISO_8859_1))
            
            // Unique ID seed
            raf.writeInt(UUID.randomUUID().hashCode())
            
            // Next record list
            raf.writeInt(0)
            
            // Number of records
            raf.writeShort(records.size.toShort())
            
            // Record list: offset and attributes for each record
            var currentOffset = raf.filePointer.toInt() + (8 * records.size) + 2
            for (i in records.indices) {
                raf.writeInt(currentOffset)
                raf.writeByte(0) // Attributes
                raf.writeByte(0) // Unused
                raf.writeShort((i * 2).toShort()) // Unique ID
                currentOffset += records[i].size
            }
            
            // End of record list
            raf.writeShort(0)
            
            // Write records
            for (record in records) {
                raf.write(record)
            }
        }
    }
}
