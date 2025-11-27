package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File

class RtfInput : InputPlugin {
    override val name = "RTF Input"
    override val fileTypes = setOf("rtf")

    override fun convert(inputFile: File, workDir: File): OebBook {
        // RTF is a text-based format with control words
        // Simple extraction: remove RTF control codes and extract plain text
        val rtfContent = inputFile.readText()
        
        // Basic RTF text extraction (remove control words)
        // RTF format: {\rtf ... \par ... }
        var textContent = rtfContent
            .replace(Regex("\\\\[a-z]+\\d*"), " ") // Remove control words like \par, \b, etc.
            .replace(Regex("\\{[^}]*\\}"), " ") // Remove groups
            .replace(Regex("\\\\'[0-9a-fA-F]{2}"), " ") // Remove hex escapes
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
        
        // Try to extract title from RTF metadata or use filename
        var title = inputFile.nameWithoutExtension
        
        // Look for title in RTF (if present in info group)
        val titleMatch = Regex("(?i)\\\\title\\s+([^\\\\}]+)").find(rtfContent)
        if (titleMatch != null) {
            title = titleMatch.groupValues[1].trim()
        }
        
        val metadata = Metadata(title = title)
        val book = OebBook(metadata)
        
        // Create HTML content
        val contentFile = File(workDir, "content.html")
        val paragraphs = textContent.split("  ").filter { it.isNotBlank() }
        val htmlContent = """
            <html>
            <head><title>$title</title></head>
            <body>
            ${paragraphs.joinToString("\n<p>", "<p>", "</p>") { it.trim() }}
            </body>
            </html>
        """.trimIndent()
        
        contentFile.writeText(htmlContent)
        
        val item = OebItem("content", "content.html", "application/xhtml+xml", contentFile)
        book.manifest["content"] = item
        book.spine.add(item)
        
        return book
    }
}
