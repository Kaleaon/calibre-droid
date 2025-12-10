package org.calibre.formats.djvu

import org.calibre.utils.Logger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * DjVu format parser.
 * 
 * DjVu is a computer file format designed primarily to store scanned documents.
 * It uses advanced compression techniques optimized for scanned documents containing
 * a combination of text, line drawings, indexed color images, and photographs.
 * 
 * Structure:
 * - IFF85 container format (similar to RIFF)
 * - FORM:DJVU or FORM:DJVM chunks
 * - INFO, INCL, Sjbz, FG44, BG44, TXTz chunks
 * 
 * This parser extracts:
 * - Text layer (TXTz/TXTa chunks)
 * - Page images
 * - Document structure
 */
class DjvuParser(private val data: ByteArray) {
    
    private val pages = mutableListOf<DjvuPage>()
    private var documentType: String = "DJVU"
    private var pageCount: Int = 0
    
    init {
        if (data.size < 16) {
            throw IllegalArgumentException("File too small to be a valid DjVu file")
        }
        
        parseDocument()
    }
    
    private fun parseDocument() {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        
        // Check for AT&T magic
        val magic = ByteArray(4)
        buffer.get(magic)
        val magicStr = String(magic, Charsets.US_ASCII)
        
        if (magicStr != "AT&T") {
            throw IllegalArgumentException("Invalid DjVu file: missing AT&T magic")
        }
        
        // Read FORM chunk
        val formId = ByteArray(4)
        buffer.get(formId)
        if (String(formId, Charsets.US_ASCII) != "FORM") {
            throw IllegalArgumentException("Invalid DjVu file: missing FORM chunk")
        }
        
        val formSize = buffer.int
        
        // Read document type (DJVU, DJVM, etc.)
        val typeBytes = ByteArray(4)
        buffer.get(typeBytes)
        documentType = String(typeBytes, Charsets.US_ASCII)
        
        when (documentType) {
            "DJVU" -> parseSinglePage(buffer, formSize - 4)
            "DJVM" -> parseMultiPage(buffer, formSize - 4)
            "DJVI" -> parseSharedData(buffer, formSize - 4)
            "THUM" -> parseThumbnails(buffer, formSize - 4)
            else -> Logger.warn("Unknown DjVu document type: $documentType")
        }
    }
    
    private fun parseSinglePage(buffer: ByteBuffer, size: Int) {
        val page = parsePage(buffer, size)
        if (page != null) {
            pages.add(page)
        }
        pageCount = 1
    }
    
    private fun parseMultiPage(buffer: ByteBuffer, size: Int) {
        val endPosition = buffer.position() + size
        
        while (buffer.position() < endPosition - 8) {
            val chunkId = ByteArray(4)
            buffer.get(chunkId)
            val chunkName = String(chunkId, Charsets.US_ASCII)
            
            val chunkSize = buffer.int
            val chunkEnd = buffer.position() + chunkSize
            
            when (chunkName) {
                "DIRM" -> parseDirectoryChunk(buffer, chunkSize)
                "FORM" -> {
                    val pageType = ByteArray(4)
                    buffer.get(pageType)
                    if (String(pageType, Charsets.US_ASCII) == "DJVU") {
                        val page = parsePage(buffer, chunkSize - 4)
                        if (page != null) {
                            pages.add(page)
                        }
                    }
                }
                else -> buffer.position(chunkEnd)
            }
            
            // Align to even boundary
            if (chunkSize % 2 == 1 && buffer.position() < endPosition) {
                buffer.get()
            }
        }
        
        pageCount = pages.size
    }
    
    private fun parseDirectoryChunk(buffer: ByteBuffer, size: Int) {
        val startPos = buffer.position()
        
        // Directory flags
        val flags = buffer.get().toInt() and 0xFF
        val isBundled = (flags and 0x80) != 0
        
        // Number of files
        val numFiles = buffer.short.toInt() and 0xFFFF
        pageCount = numFiles
        
        buffer.position(startPos + size)
    }
    
    private fun parsePage(buffer: ByteBuffer, size: Int): DjvuPage? {
        val startPos = buffer.position()
        val endPos = startPos + size
        
        var width = 0
        var height = 0
        var dpi = 300
        var text: String? = null
        var imageData: ByteArray? = null
        
        while (buffer.position() < endPos - 8) {
            val chunkId = ByteArray(4)
            buffer.get(chunkId)
            val chunkName = String(chunkId, Charsets.US_ASCII)
            
            val chunkSize = buffer.int
            val chunkEnd = buffer.position() + chunkSize
            
            try {
                when (chunkName) {
                    "INFO" -> {
                        width = buffer.short.toInt() and 0xFFFF
                        height = buffer.short.toInt() and 0xFFFF
                        buffer.get() // version minor
                        buffer.get() // version major
                        dpi = buffer.short.toInt() and 0xFFFF
                        buffer.get() // gamma
                        buffer.get() // flags
                    }
                    "TXTz" -> {
                        // BZZ-compressed text
                        val compressed = ByteArray(chunkSize)
                        buffer.get(compressed)
                        text = decompressBzz(compressed)
                    }
                    "TXTa" -> {
                        // Uncompressed text
                        val textBytes = ByteArray(chunkSize)
                        buffer.get(textBytes)
                        text = parseTextChunk(textBytes)
                    }
                    "Sjbz" -> {
                        // JB2 compressed mask (we'll skip actual decompression)
                        val jb2Data = ByteArray(chunkSize)
                        buffer.get(jb2Data)
                        // Store raw data for now
                    }
                    "BG44", "FG44" -> {
                        // IW44 wavelet-compressed image
                        val imageBytes = ByteArray(chunkSize)
                        buffer.get(imageBytes)
                        imageData = imageBytes
                    }
                    "BGjp", "FGjp" -> {
                        // JPEG background/foreground
                        val jpegData = ByteArray(chunkSize)
                        buffer.get(jpegData)
                        imageData = jpegData
                    }
                    else -> buffer.position(chunkEnd)
                }
            } catch (e: Exception) {
                Logger.debug("Error parsing chunk $chunkName: ${e.message}")
                buffer.position(minOf(chunkEnd, endPos))
            }
            
            buffer.position(minOf(chunkEnd, endPos))
            
            // Align to even boundary
            if (chunkSize % 2 == 1 && buffer.position() < endPos) {
                buffer.get()
            }
        }
        
        return if (width > 0 && height > 0) {
            DjvuPage(width, height, dpi, text, imageData)
        } else null
    }
    
    private fun parseSharedData(buffer: ByteBuffer, size: Int) {
        // Shared dictionary for multi-page documents
        buffer.position(buffer.position() + size)
    }
    
    private fun parseThumbnails(buffer: ByteBuffer, size: Int) {
        // Thumbnail data
        buffer.position(buffer.position() + size)
    }
    
    private fun decompressBzz(data: ByteArray): String? {
        // BZZ is a specialized compression used in DjVu
        // For now, try to extract any readable text
        return try {
            extractReadableText(data)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseTextChunk(data: ByteArray): String {
        // DjVu text chunks have a specific format:
        // Version (1 byte) + text zone tree
        if (data.isEmpty()) return ""
        
        val version = data[0].toInt() and 0xFF
        
        // Extract text content (simplified - actual format is more complex)
        val textStart = 1
        return extractReadableText(data.copyOfRange(textStart, data.size))
    }
    
    private fun extractReadableText(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            if (b in 0x20..0x7E || b == 0x0A || b == 0x0D || b == 0x09) {
                sb.append(b.toChar())
            } else if (b > 0x7F) {
                // Possible UTF-8 sequence
                try {
                    val remaining = data.size - i
                    if (remaining >= 2 && (b and 0xE0) == 0xC0) {
                        val c = String(data.copyOfRange(i, i + 2), Charsets.UTF_8)
                        sb.append(c)
                        i++
                    } else if (remaining >= 3 && (b and 0xF0) == 0xE0) {
                        val c = String(data.copyOfRange(i, i + 3), Charsets.UTF_8)
                        sb.append(c)
                        i += 2
                    } else if (remaining >= 4 && (b and 0xF8) == 0xF0) {
                        val c = String(data.copyOfRange(i, i + 4), Charsets.UTF_8)
                        sb.append(c)
                        i += 3
                    }
                } catch (e: Exception) {
                    // Skip invalid sequences
                }
            }
            i++
        }
        return sb.toString().trim()
    }
    
    /**
     * Gets the number of pages.
     */
    fun getPageCount(): Int = pageCount
    
    /**
     * Gets all pages.
     */
    fun getPages(): List<DjvuPage> = pages.toList()
    
    /**
     * Gets the document title (from metadata or first page text).
     */
    fun getTitle(): String {
        // Try to find title in text
        for (page in pages) {
            page.text?.let { text ->
                val lines = text.lines().filter { it.isNotBlank() }
                if (lines.isNotEmpty()) {
                    val firstLine = lines.first().take(100)
                    if (firstLine.length > 5) {
                        return firstLine
                    }
                }
            }
        }
        return "DjVu Document"
    }
    
    /**
     * Gets all text content as HTML.
     */
    fun getHtmlContent(): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n<html><head>")
        sb.append("<meta charset=\"UTF-8\">")
        sb.append("<title>${escapeHtml(getTitle())}</title>")
        sb.append("<style>")
        sb.append(".page { page-break-after: always; margin-bottom: 2em; }")
        sb.append(".page-header { color: #666; font-size: 0.9em; margin-bottom: 1em; }")
        sb.append("</style>")
        sb.append("</head><body>\n")
        
        for ((index, page) in pages.withIndex()) {
            sb.append("<div class='page'>")
            sb.append("<div class='page-header'>Page ${index + 1} (${page.width}x${page.height} @ ${page.dpi}dpi)</div>")
            
            if (page.text != null && page.text.isNotBlank()) {
                val paragraphs = page.text.split("\n\n")
                for (para in paragraphs) {
                    if (para.isNotBlank()) {
                        sb.append("<p>${escapeHtml(para.trim())}</p>\n")
                    }
                }
            } else {
                sb.append("<p><em>[Image-only page - no text layer]</em></p>")
            }
            
            sb.append("</div>\n")
        }
        
        if (pages.isEmpty()) {
            sb.append("<p>No text content could be extracted from this DjVu file.</p>")
            sb.append("<p>This document contains ${pageCount} page(s) of scanned images.</p>")
        }
        
        sb.append("</body></html>")
        return sb.toString()
    }
    
    /**
     * Gets all extracted text.
     */
    fun getAllText(): String {
        return pages.mapNotNull { it.text }.joinToString("\n\n---\n\n")
    }
    
    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}

/**
 * Represents a single page in a DjVu document.
 */
data class DjvuPage(
    val width: Int,
    val height: Int,
    val dpi: Int,
    val text: String?,
    val imageData: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DjvuPage) return false
        return width == other.width && height == other.height && 
               dpi == other.dpi && text == other.text
    }
    
    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + dpi
        result = 31 * result + (text?.hashCode() ?: 0)
        return result
    }
}
