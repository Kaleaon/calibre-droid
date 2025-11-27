package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater

/**
 * MOBI Output plugin.
 * Creates MOBI/AZW format files from OEB books.
 * 
 * Note: This is a simplified implementation. Full MOBI support requires
 * implementing Palm Database format, MOBI header, EXTH records, and compression.
 */
class MobiOutput : OutputPlugin {
    override val name = "MOBI Output"
    override val fileType = "mobi"
    
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
     * Creates a simplified MOBI file.
     * This is a basic implementation - full MOBI requires:
     * - Palm Database (PDB) format wrapper
     * - MOBI header with EXTH records
     * - PalmDoc compression or Huff/CDIC compression
     * - Text encoding handling
     */
    private fun createMobiFile(htmlContent: String, metadata: Metadata, outputFile: File) {
        // For now, create a simplified MOBI-like format
        // In a full implementation, we would:
        // 1. Create PDB header
        // 2. Add MOBI header with metadata
        // 3. Compress HTML content (PalmDoc or Huff/CDIC)
        // 4. Write records
        
        // Simplified: Write HTML as-is (not a valid MOBI, but placeholder)
        // Real implementation would require full MOBI format specification
        
        RandomAccessFile(outputFile, "rw").use { raf ->
            // Write a basic header (simplified)
            val htmlBytes = htmlContent.toByteArray(StandardCharsets.UTF_8)
            
            // PDB Header (simplified)
            val name = metadata.title.take(32).padEnd(32, '\u0000')
            raf.write(name.toByteArray(StandardCharsets.ISO_8859_1))
            
            // Attributes
            raf.writeShort(0) // Attributes
            raf.writeShort(0) // Version
            raf.writeInt(0) // Creation date
            raf.writeInt(0) // Modification date
            raf.writeInt(0) // Last backup date
            raf.writeInt(0) // Modification number
            raf.writeInt(0) // App info ID
            raf.writeInt(0) // Sort info ID
            raf.write(ByteArray(4)) // Type: BOOK
            raf.write("MOBI".toByteArray(StandardCharsets.ISO_8859_1)) // Creator: MOBI
            raf.writeInt(0) // Unique ID seed
            raf.writeInt(0) // Next record list
            raf.writeShort(0) // Number of records
            
            // Note: Full MOBI implementation requires:
            // - Proper record structure
            // - MOBI header (EXTH records for metadata)
            // - Compression (PalmDoc or Huff/CDIC)
            // - Text encoding conversion
            
            // For now, write HTML content as a placeholder
            // This file will NOT be readable as a MOBI, but demonstrates structure
            raf.write(htmlBytes)
        }
        
        // Log warning that this is a simplified implementation
        org.calibre.utils.Logger.warn(
            "MOBI output created with simplified format. Full MOBI support requires " +
            "Palm Database format, MOBI headers, and compression."
        )
    }
}
