package org.calibre.formats.lit

import org.calibre.utils.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * Microsoft LIT (Literature) format parser.
 * 
 * LIT is an encrypted format used by Microsoft Reader. It's based on 
 * Microsoft's compressed HTML (CHM) format with additional DRM layers.
 * 
 * Structure:
 * - LIT header with magic bytes "ITOLITLS"
 * - Directory containing file entries
 * - Content files (HTML, CSS, images, OPF)
 * - DRM data (for protected content)
 * 
 * This parser supports unprotected LIT files only.
 */
class LitParser(private val data: ByteArray) {
    
    private val header: LitHeader
    private val files = mutableMapOf<String, ByteArray>()
    private var opfContent: String? = null
    
    init {
        if (data.size < 80) {
            throw IllegalArgumentException("File too small to be a valid LIT file")
        }
        
        header = LitHeader.read(data)
        
        if (!header.isValid) {
            throw IllegalArgumentException("Invalid LIT file: magic bytes don't match")
        }
        
        parseDirectory()
    }
    
    private fun parseDirectory() {
        try {
            // Find directory offset from header
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(header.directoryOffset.toInt())
            
            // Read directory entries
            val numEntries = header.entryCount
            
            for (i in 0 until numEntries) {
                // Need at least 16 bytes for 4 int32 values (nameOffset, dataOffset, dataSize, section)
                if (buffer.position() + 16 > data.size) break
                
                try {
                    // Read entry (simplified - actual format is more complex)
                    val nameOffset = buffer.int.toLong() and 0xFFFFFFFFL
                    val dataOffset = buffer.int.toLong() and 0xFFFFFFFFL
                    val dataSize = buffer.int.toLong() and 0xFFFFFFFFL
                    val section = buffer.int
                    
                    if (nameOffset > 0 && nameOffset < data.size.toLong() &&
                        dataOffset > 0 && dataOffset < data.size.toLong() &&
                        dataSize > 0 && dataSize < data.size.toLong()) {
                        
                        // Try to extract name and data
                        val name = extractName(nameOffset.toInt())
                        if (name.isNotEmpty()) {
                            val fileData = extractFileData(dataOffset.toInt(), dataSize.toInt(), section)
                            if (fileData != null) {
                                files[name] = fileData
                                
                                if (name.endsWith(".opf", ignoreCase = true)) {
                                    opfContent = String(fileData, Charsets.UTF_8)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.debug("Error parsing LIT directory entry: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.warn("Error parsing LIT directory: ${e.message}")
        }
        
        // If we couldn't parse the directory, try to find content by scanning
        if (files.isEmpty()) {
            scanForContent()
        }
    }
    
    private fun extractName(offset: Int): String {
        if (offset < 0 || offset >= data.size) return ""
        
        val endPos = data.indexOf(0.toByte(), offset)
        val actualEnd = if (endPos > offset) endPos else minOf(offset + 256, data.size)
        
        return try {
            String(data.copyOfRange(offset, actualEnd), Charsets.UTF_8)
                .filter { it.isLetterOrDigit() || it in "._-/" }
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun extractFileData(offset: Int, size: Int, section: Int): ByteArray? {
        if (offset < 0 || offset >= data.size || size <= 0) return null
        
        val actualSize = minOf(size, data.size - offset)
        val rawData = data.copyOfRange(offset, offset + actualSize)
        
        // Try to decompress if it looks compressed
        return try {
            if (section and 1 != 0) {
                // Might be compressed with LZXD or zlib
                tryDecompress(rawData)
            } else {
                rawData
            }
        } catch (e: Exception) {
            rawData
        }
    }
    
    private fun tryDecompress(data: ByteArray): ByteArray {
        // Try zlib decompression
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
            // Not zlib compressed, return original
            data
        }
    }
    
    private fun scanForContent() {
        // Scan for HTML content by looking for HTML tags
        val htmlStart = findSequence(data, "<html".toByteArray())
        if (htmlStart >= 0) {
            val htmlEnd = findSequence(data, "</html>".toByteArray(), htmlStart)
            if (htmlEnd > htmlStart) {
                val htmlData = data.copyOfRange(htmlStart, htmlEnd + 7)
                files["content.html"] = htmlData
            }
        }
        
        // Scan for OPF content
        val opfStart = findSequence(data, "<package".toByteArray())
        if (opfStart >= 0) {
            val opfEnd = findSequence(data, "</package>".toByteArray(), opfStart)
            if (opfEnd > opfStart) {
                val opfData = data.copyOfRange(opfStart, opfEnd + 10)
                files["content.opf"] = opfData
                opfContent = String(opfData, Charsets.UTF_8)
            }
        }
    }
    
    private fun findSequence(data: ByteArray, sequence: ByteArray, startFrom: Int = 0): Int {
        outer@ for (i in startFrom until data.size - sequence.size) {
            for (j in sequence.indices) {
                if (data[i + j] != sequence[j]) continue@outer
            }
            return i
        }
        return -1
    }
    
    /**
     * Gets the book title from metadata.
     */
    fun getTitle(): String {
        opfContent?.let { opf ->
            val titleMatch = Regex("<dc:title[^>]*>([^<]+)</dc:title>", RegexOption.IGNORE_CASE)
                .find(opf)
            if (titleMatch != null) {
                return titleMatch.groupValues[1].trim()
            }
        }
        return "Unknown Title"
    }
    
    /**
     * Gets the book author from metadata.
     */
    fun getAuthors(): List<String> {
        opfContent?.let { opf ->
            val authorMatches = Regex("<dc:creator[^>]*>([^<]+)</dc:creator>", RegexOption.IGNORE_CASE)
                .findAll(opf)
            return authorMatches.map { it.groupValues[1].trim() }.toList()
        }
        return emptyList()
    }
    
    /**
     * Gets all metadata from the OPF file.
     */
    fun getMetadata(): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        
        opfContent?.let { opf ->
            // Parse Dublin Core metadata
            val dcElements = listOf("title", "creator", "subject", "description", 
                "publisher", "date", "type", "format", "identifier", "language", "rights")
            
            for (element in dcElements) {
                val regex = Regex("<dc:$element[^>]*>([^<]+)</dc:$element>", RegexOption.IGNORE_CASE)
                regex.find(opf)?.let { match ->
                    metadata[element] = match.groupValues[1].trim()
                }
            }
        }
        
        return metadata
    }
    
    /**
     * Gets the list of files in the LIT archive.
     */
    fun getFileList(): List<String> = files.keys.toList()
    
    /**
     * Gets the content of a specific file.
     */
    fun getFile(path: String): ByteArray? = files[path]
    
    /**
     * Gets all HTML content from the book.
     */
    fun getHtmlContent(): String {
        val htmlFiles = files.filter { 
            it.key.endsWith(".html", ignoreCase = true) || 
            it.key.endsWith(".htm", ignoreCase = true) ||
            it.key.endsWith(".xhtml", ignoreCase = true)
        }
        
        if (htmlFiles.isEmpty()) {
            // Generate from any text content
            return generateHtmlFromText()
        }
        
        // Combine HTML files
        val combined = StringBuilder()
        combined.append("<!DOCTYPE html>\n<html><head>")
        combined.append("<meta charset=\"UTF-8\">")
        combined.append("<title>${getTitle()}</title></head><body>\n")
        
        for ((_, content) in htmlFiles) {
            val text = String(content, Charsets.UTF_8)
            // Extract body content if present
            val bodyMatch = Regex("<body[^>]*>([\\s\\S]*)</body>", RegexOption.IGNORE_CASE).find(text)
            if (bodyMatch != null) {
                combined.append(bodyMatch.groupValues[1])
            } else {
                combined.append(text)
            }
        }
        
        combined.append("</body></html>")
        return combined.toString()
    }
    
    private fun generateHtmlFromText(): String {
        // Try to extract text from binary content
        val textBuilder = StringBuilder()
        textBuilder.append("<!DOCTYPE html>\n<html><head>")
        textBuilder.append("<meta charset=\"UTF-8\">")
        textBuilder.append("<title>${getTitle()}</title></head><body>\n")
        
        // Look for text-like content in files
        for ((_, content) in files) {
            val text = String(content, Charsets.UTF_8)
            if (text.any { it.isLetter() }) {
                textBuilder.append("<p>${escapeHtml(text)}</p>\n")
            }
        }
        
        textBuilder.append("</body></html>")
        return textBuilder.toString()
    }
    
    /**
     * Gets image files from the book.
     */
    fun getImages(): Map<String, ByteArray> {
        return files.filter { entry ->
            val ext = entry.key.substringAfterLast('.').lowercase()
            ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "svg")
        }
    }
    
    /**
     * Gets CSS files from the book.
     */
    fun getCssFiles(): Map<String, ByteArray> {
        return files.filter { it.key.endsWith(".css", ignoreCase = true) }
    }
    
    /**
     * Extracts all files to a directory.
     */
    fun extractTo(outputDir: File) {
        outputDir.mkdirs()
        
        for ((name, content) in files) {
            val outputFile = File(outputDir, name.replace("/", File.separator))
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(content)
        }
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}

/**
 * LIT file header.
 */
data class LitHeader(
    val magic: String,
    val version: Int,
    val headerLength: Int,
    val entryCount: Int,
    val directoryOffset: Long
) {
    val isValid: Boolean get() = magic == "ITOLITLS"
    
    companion object {
        fun read(data: ByteArray): LitHeader {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            
            // Magic bytes (8 bytes)
            val magicBytes = ByteArray(8)
            buffer.get(magicBytes)
            val magic = String(magicBytes, Charsets.US_ASCII)
            
            val version = buffer.int
            val headerLength = buffer.int
            val entryCount = buffer.int
            
            buffer.position(0x40)
            val directoryOffset = buffer.long
            
            return LitHeader(
                magic = magic,
                version = version,
                headerLength = headerLength,
                entryCount = entryCount,
                directoryOffset = directoryOffset
            )
        }
    }
}
