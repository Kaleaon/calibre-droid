package org.calibre.formats.chm

import org.calibre.utils.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * CHM (Compiled HTML Help) format parser.
 * 
 * CHM files use Microsoft's ITSS (InfoTech Storage System) format:
 * - ITSF header followed by directory structure
 * - LZX or Reset Table compressed content
 * - HTML files, images, CSS, and other resources
 * 
 * This parser extracts content without requiring external libraries.
 */
class ChmParser(private val data: ByteArray) {
    
    private val header: ItsfHeader
    private val directoryHeader: ItspHeader
    private val files = mutableMapOf<String, ByteArray>()
    private val listingEntries = mutableListOf<DirectoryEntry>()
    
    // Content sections
    private var content0Offset: Long = 0
    private var content0Length: Long = 0
    
    init {
        if (data.size < 100) {
            throw IllegalArgumentException("File too small to be a valid CHM file")
        }
        
        header = ItsfHeader.read(data)
        if (!header.isValid) {
            throw IllegalArgumentException("Invalid CHM file: ITSF signature not found")
        }
        
        // Read directory header
        val dirStart = header.directoryOffset.toInt()
        directoryHeader = ItspHeader.read(data, dirStart)
        
        // Parse content sections
        parseContentSections()
        
        // Parse directory listing
        parseDirectoryListing()
        
        // Extract files
        extractFiles()
    }
    
    private fun parseContentSections() {
        // Find ::DataSpace/NameList for content section info
        // For now, use heuristics to find content
        
        // Content section 0 typically starts after headers
        content0Offset = header.dataOffset
        content0Length = data.size.toLong() - content0Offset
    }
    
    private fun parseDirectoryListing() {
        try {
            val dirStart = header.directoryOffset.toInt() + ItspHeader.SIZE
            var offset = dirStart
            
            while (offset < data.size - 10) {
                try {
                    val entry = readDirectoryEntry(offset)
                    if (entry != null) {
                        listingEntries.add(entry)
                        offset += entry.entrySize
                    } else {
                        offset++
                    }
                } catch (e: Exception) {
                    offset++
                }
                
                // Safety limit
                if (listingEntries.size > 10000) break
            }
        } catch (e: Exception) {
            Logger.warn("Error parsing CHM directory: ${e.message}")
        }
    }
    
    private fun readDirectoryEntry(offset: Int): DirectoryEntry? {
        if (offset + 5 >= data.size) return null
        
        val buffer = ByteBuffer.wrap(data, offset, minOf(300, data.size - offset))
            .order(ByteOrder.LITTLE_ENDIAN)
        
        // Read name length (encoded integer)
        val nameLen = readEncodedInt(buffer) ?: return null
        if (nameLen <= 0 || nameLen > 256) return null
        
        // Read name
        if (buffer.remaining() < nameLen) return null
        val nameBytes = ByteArray(nameLen)
        buffer.get(nameBytes)
        val name = String(nameBytes, Charsets.UTF_8)
        
        // Read content section
        val contentSection = readEncodedInt(buffer) ?: return null
        
        // Read offset and length
        val contentOffset = readEncodedInt(buffer)?.toLong() ?: return null
        val contentLength = readEncodedInt(buffer)?.toLong() ?: return null
        
        val entrySize = buffer.position()
        
        return DirectoryEntry(name, contentSection, contentOffset, contentLength, entrySize)
    }
    
    private fun readEncodedInt(buffer: ByteBuffer): Int? {
        if (!buffer.hasRemaining()) return null
        
        var result = 0
        var shift = 0
        
        while (buffer.hasRemaining() && shift < 35) {
            val b = buffer.get().toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) {
                return result
            }
            shift += 7
        }
        
        return null
    }
    
    private fun extractFiles() {
        for (entry in listingEntries) {
            if (entry.name.startsWith("::") || entry.name.startsWith("$")) {
                continue // Skip internal files
            }
            
            if (entry.contentSection == 0 && entry.length > 0) {
                try {
                    val fileOffset = content0Offset + entry.offset
                    if (fileOffset >= 0 && fileOffset + entry.length <= data.size) {
                        val fileData = data.copyOfRange(
                            fileOffset.toInt(),
                            (fileOffset + entry.length).toInt()
                        )
                        files[entry.name] = fileData
                    }
                } catch (e: Exception) {
                    Logger.debug("Error extracting ${entry.name}: ${e.message}")
                }
            }
        }
        
        // If no files extracted, try scanning for HTML content
        if (files.isEmpty()) {
            scanForHtmlContent()
        }
    }
    
    private fun scanForHtmlContent() {
        val htmlPattern = "<html".toByteArray(Charsets.US_ASCII)
        var searchStart = header.dataOffset.toInt()
        
        var count = 0
        while (searchStart < data.size - 100 && count < 100) {
            val htmlStart = findBytes(data, htmlPattern, searchStart)
            if (htmlStart < 0) break
            
            val htmlEnd = findBytes(data, "</html>".toByteArray(), htmlStart)
            if (htmlEnd > htmlStart) {
                val htmlData = data.copyOfRange(htmlStart, htmlEnd + 7)
                files["content_${count}.html"] = htmlData
                count++
                searchStart = htmlEnd + 7
            } else {
                searchStart = htmlStart + 1
            }
        }
    }
    
    private fun findBytes(data: ByteArray, pattern: ByteArray, start: Int): Int {
        outer@ for (i in start until data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }
    
    /**
     * Gets the book title from the CHM file.
     */
    fun getTitle(): String {
        // Try to find title in HHC (HTML Help Contents) or first HTML file
        files.entries.find { it.key.endsWith(".hhc", ignoreCase = true) }?.let { (_, content) ->
            val text = String(content, Charsets.UTF_8)
            val match = Regex("name=\"Name\"\\s+value=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(text)
            if (match != null) return match.groupValues[1]
        }
        
        // Try first HTML file's title
        files.entries.find { it.key.endsWith(".html", ignoreCase = true) || it.key.endsWith(".htm", ignoreCase = true) }?.let { (_, content) ->
            val text = String(content, Charsets.UTF_8)
            val match = Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE).find(text)
            if (match != null) return match.groupValues[1].trim()
        }
        
        return "Unknown Title"
    }
    
    /**
     * Gets all HTML content combined.
     */
    fun getHtmlContent(): String {
        val htmlFiles = files.filter { 
            val ext = it.key.substringAfterLast('.').lowercase()
            ext in listOf("html", "htm", "xhtml")
        }.toSortedMap()
        
        if (htmlFiles.isEmpty()) {
            return "<html><body><p>No content found in CHM file.</p></body></html>"
        }
        
        val combined = StringBuilder()
        combined.append("<!DOCTYPE html>\n<html><head>")
        combined.append("<meta charset=\"UTF-8\">")
        combined.append("<title>${escapeHtml(getTitle())}</title></head><body>\n")
        
        for ((name, content) in htmlFiles) {
            val text = String(content, Charsets.UTF_8)
            // Extract body content
            val bodyMatch = Regex("<body[^>]*>([\\s\\S]*)</body>", RegexOption.IGNORE_CASE).find(text)
            if (bodyMatch != null) {
                combined.append("<!-- File: $name -->\n")
                combined.append(bodyMatch.groupValues[1])
                combined.append("\n<hr/>\n")
            }
        }
        
        combined.append("</body></html>")
        return combined.toString()
    }
    
    /**
     * Gets all files in the CHM.
     */
    fun getFiles(): Map<String, ByteArray> = files.toMap()
    
    /**
     * Gets image files.
     */
    fun getImages(): Map<String, ByteArray> {
        return files.filter { 
            val ext = it.key.substringAfterLast('.').lowercase()
            ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "ico")
        }
    }
    
    /**
     * Gets CSS files.
     */
    fun getCssFiles(): Map<String, ByteArray> {
        return files.filter { it.key.endsWith(".css", ignoreCase = true) }
    }
    
    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}

/**
 * ITSF (InfoTech Storage Format) header.
 */
data class ItsfHeader(
    val signature: String,
    val version: Int,
    val headerLength: Int,
    val unknown1: Int,
    val timestamp: Long,
    val languageId: Int,
    val directoryOffset: Long,
    val dataOffset: Long
) {
    val isValid: Boolean get() = signature == "ITSF"
    
    companion object {
        fun read(data: ByteArray): ItsfHeader {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            
            val sigBytes = ByteArray(4)
            buffer.get(sigBytes)
            val signature = String(sigBytes, Charsets.US_ASCII)
            
            val version = buffer.int
            val headerLength = buffer.int
            val unknown1 = buffer.int
            val timestamp = buffer.long
            val languageId = buffer.int
            
            // Skip to offset fields (position varies by version)
            buffer.position(0x58)
            val dataOffset = buffer.long
            
            buffer.position(0x20)
            val directoryOffset = buffer.long
            
            return ItsfHeader(
                signature = signature,
                version = version,
                headerLength = headerLength,
                unknown1 = unknown1,
                timestamp = timestamp,
                languageId = languageId,
                directoryOffset = directoryOffset,
                dataOffset = dataOffset
            )
        }
    }
}

/**
 * ITSP (InfoTech Storage Protocol) directory header.
 */
data class ItspHeader(
    val signature: String,
    val version: Int,
    val directoryHeaderLength: Int,
    val windowSize: Int,
    val numBlocks: Int
) {
    companion object {
        const val SIZE = 84
        
        fun read(data: ByteArray, offset: Int): ItspHeader {
            val buffer = ByteBuffer.wrap(data, offset, minOf(SIZE, data.size - offset))
                .order(ByteOrder.LITTLE_ENDIAN)
            
            val sigBytes = ByteArray(4)
            buffer.get(sigBytes)
            val signature = String(sigBytes, Charsets.US_ASCII)
            
            val version = buffer.int
            val directoryHeaderLength = buffer.int
            
            buffer.position(buffer.position() + 16) // Skip unknown fields
            val windowSize = buffer.int
            
            buffer.position(buffer.position() + 8)
            val numBlocks = buffer.int
            
            return ItspHeader(
                signature = signature,
                version = version,
                directoryHeaderLength = directoryHeaderLength,
                windowSize = windowSize,
                numBlocks = numBlocks
            )
        }
    }
}

/**
 * Directory entry in CHM file.
 */
data class DirectoryEntry(
    val name: String,
    val contentSection: Int,
    val offset: Long,
    val length: Long,
    val entrySize: Int
)
