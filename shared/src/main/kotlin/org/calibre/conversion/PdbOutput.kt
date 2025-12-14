package org.calibre.conversion

import org.calibre.formats.pdb.PalmDocDecompressor
import org.calibre.utils.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Output plugin for PDB (Palm Database) format.
 * 
 * Creates PDB files in PalmDoc format from the OEB intermediate format.
 * This is compatible with older Palm devices and many ebook readers.
 */
class PdbOutput : OutputPlugin {
    
    override val name = "PDB Output"
    override val fileType = "pdb"
    
    companion object {
        private const val MAX_RECORD_SIZE = 4096
        private const val COMPRESSION_PALMDOC = 2
        private const val COMPRESSION_NONE = 1
    }
    
    override fun convert(book: OebBook, outputFile: File) {
        // Extract text content
        val textContent = extractTextContent(book)
        val metadata = book.metadata
        
        // Compress text into records
        val records = createTextRecords(textContent)
        
        // Build PDB file
        val pdbData = buildPdbFile(metadata.title, records, textContent.length)
        
        outputFile.writeBytes(pdbData)
        Logger.info("Created PDB file: ${outputFile.absolutePath}")
    }
    
    private fun extractTextContent(book: OebBook): String {
        val sb = StringBuilder()
        
        for (item in book.spine) {
            if (item.file.exists() && item.mediaType.contains("html")) {
                val html = item.file.readText()
                
                // Convert HTML to plain text
                val text = htmlToText(html)
                if (text.isNotBlank()) {
                    sb.append(text).append("\n\n")
                }
            }
        }
        
        return sb.toString()
    }
    
    private fun htmlToText(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<br[^>]*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")
            .replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<h[1-6][^>]*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { 
                val code = it.groupValues[1].toIntOrNull() ?: 32
                if (code in 32..126) code.toChar().toString() else " "
            }
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
    
    private fun createTextRecords(text: String): List<ByteArray> {
        val records = mutableListOf<ByteArray>()
        val textBytes = text.toByteArray(Charsets.ISO_8859_1)
        
        var offset = 0
        while (offset < textBytes.size) {
            val chunkSize = minOf(MAX_RECORD_SIZE, textBytes.size - offset)
            val chunk = textBytes.copyOfRange(offset, offset + chunkSize)
            
            // Compress using PalmDoc LZ77
            val compressed = PalmDocDecompressor.compress(chunk)
            records.add(compressed)
            
            offset += chunkSize
        }
        
        return records
    }
    
    private fun buildPdbFile(title: String, records: List<ByteArray>, textLength: Int): ByteArray {
        val output = ByteArrayOutputStream()
        
        // Prepare database name (max 31 chars + null)
        val dbName = title.take(31).toByteArray(Charsets.US_ASCII)
        val dbNamePadded = dbName.copyOf(32)
        output.write(dbNamePadded)
        
        // Database attributes (2 bytes)
        output.write(shortToBytes(0))
        
        // Version (2 bytes)
        output.write(shortToBytes(0))
        
        // Creation date (4 bytes) - seconds since 1904
        val palmEpochOffset = 2082844800L // Seconds between 1904 and 1970
        val createTime = (System.currentTimeMillis() / 1000 + palmEpochOffset).toInt()
        output.write(intToBytes(createTime))
        
        // Modification date (4 bytes)
        output.write(intToBytes(createTime))
        
        // Last backup date (4 bytes)
        output.write(intToBytes(0))
        
        // Modification number (4 bytes)
        output.write(intToBytes(0))
        
        // App info offset (4 bytes)
        output.write(intToBytes(0))
        
        // Sort info offset (4 bytes)
        output.write(intToBytes(0))
        
        // Type (4 bytes) - "TEXt"
        output.write("TEXt".toByteArray(Charsets.US_ASCII))
        
        // Creator (4 bytes) - "REAd"
        output.write("REAd".toByteArray(Charsets.US_ASCII))
        
        // Unique ID seed (4 bytes)
        output.write(intToBytes(0))
        
        // Next record list ID (4 bytes)
        output.write(intToBytes(0))
        
        // Number of records (2 bytes) - +1 for header record
        val numRecords = records.size + 1
        output.write(shortToBytes(numRecords))
        
        // Record info list
        // Calculate offsets
        val headerOffset = 78 + numRecords * 8 + 2 // PDB header + record list + gap
        val docHeaderSize = 16
        var currentOffset = headerOffset
        
        // Doc header record
        output.write(intToBytes(currentOffset))
        output.write(byteArrayOf(0, 0, 0, 0)) // Attributes + unique ID
        currentOffset += docHeaderSize
        
        // Text records
        for (record in records) {
            output.write(intToBytes(currentOffset))
            output.write(byteArrayOf(0, 0, 0, 0)) // Attributes + unique ID
            currentOffset += record.size
        }
        
        // Gap (2 bytes)
        output.write(shortToBytes(0))
        
        // PalmDoc header record
        output.write(shortToBytes(COMPRESSION_PALMDOC)) // Compression
        output.write(shortToBytes(0)) // Unused
        output.write(intToBytes(textLength)) // Text length
        output.write(shortToBytes(records.size)) // Record count
        output.write(shortToBytes(MAX_RECORD_SIZE)) // Record size
        output.write(intToBytes(0)) // Current position
        
        // Write text records
        for (record in records) {
            output.write(record)
        }
        
        return output.toByteArray()
    }
    
    private fun shortToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array()
    }
    
    private fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
    }
}
