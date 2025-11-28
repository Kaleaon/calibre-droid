package org.calibre.conversion

import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * # Text Input Plugin
 * 
 * Reads plain text files (.txt, .text) and converts them to OEB format.
 * 
 * ## Features
 * 
 * - Automatic encoding detection with fallback chain:
 *   1. System default charset
 *   2. UTF-8
 *   3. ISO-8859-1 (Latin-1)
 * - Proper HTML escaping of special characters
 * - Paragraph detection (blank lines create paragraph breaks)
 * - Basic HTML structure generation
 * 
 * ## Text Processing
 * 
 * The plugin:
 * 1. Attempts to read the file with multiple encoding strategies
 * 2. Converts text lines to HTML paragraphs
 * 3. Properly escapes HTML special characters (&, <, >, ", ')
 * 4. Groups consecutive non-blank lines into paragraphs
 * 5. Inserts paragraph breaks for blank lines
 * 
 * ## Limitations
 * 
 * - No advanced text formatting detection
 * - No table or list detection
 * - Encoding detection is basic (no BOM detection)
 * - All text is treated as plain text (no markdown, etc.)
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see InputPlugin
 * @see OebBook
 */
class TextInput : InputPlugin {
    override val name = "Text Input"
    override val fileTypes = setOf("txt", "text")

    override fun convert(inputFile: File, workDir: File): OebBook {
        val metadata = Metadata(title = inputFile.nameWithoutExtension)
        val book = OebBook(metadata)
        
        // Try to detect encoding, fallback to UTF-8, then ISO-8859-1
        val text = try {
            inputFile.readText(Charset.defaultCharset())
        } catch (e: Exception) {
            Logger.debug("Failed to read with default charset, trying UTF-8: ${e.message}")
            try {
                inputFile.readText(StandardCharsets.UTF_8)
            } catch (e2: Exception) {
                Logger.debug("Failed to read with UTF-8, trying ISO-8859-1: ${e2.message}")
                try {
                    inputFile.readText(Charsets.ISO_8859_1)
                } catch (e3: Exception) {
                    Logger.warn("Failed to read text file with all encoding attempts, using empty content: ${e3.message}")
                    ""
                }
            }
        }
        
        // Convert text to basic HTML with proper escaping
        val lines = text.lines()
        
        val sb = StringBuilder()
        sb.append("""<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8"/>
    <title>${escapeHtml(metadata.title)}</title>
</head>
<body>""")
        
        // Group consecutive blank lines into single paragraph breaks
        var inParagraph = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (inParagraph) {
                    sb.append("</p>")
                    inParagraph = false
                }
                sb.append("<br/>")
            } else {
                if (!inParagraph) {
                    sb.append("<p>")
                    inParagraph = true
                } else {
                    sb.append(" ")
                }
                sb.append(escapeHtml(trimmed))
            }
        }
        
        if (inParagraph) {
            sb.append("</p>")
        }
        
        sb.append("</body></html>")
        
        val contentFile = File(workDir, "content.html")
        contentFile.writeText(sb.toString())
        
        val item = OebItem("content", "content.html", "application/xhtml+xml", contentFile)
        book.manifest["content"] = item
        book.spine.add(item)
        
        return book
    }
    
    /**
     * Escapes HTML special characters to prevent injection and ensure proper display.
     * 
     * Escapes:
     * - `&` → `&amp;`
     * - `<` → `&lt;`
     * - `>` → `&gt;`
     * - `"` → `&quot;`
     * - `'` → `&#39;`
     * 
     * Note: Must escape `&` first to avoid double-escaping other entities.
     * 
     * @param text The text to escape
     * @return HTML-escaped text safe for embedding in HTML/XHTML
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")  // Must be first to avoid double-escaping
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
