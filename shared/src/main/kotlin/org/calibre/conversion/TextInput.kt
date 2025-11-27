package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class TextInput : InputPlugin {
    override val name = "Text Input"
    override val fileTypes = setOf("txt", "text")

    override fun convert(inputFile: File, workDir: File): OebBook {
        val metadata = Metadata(title = inputFile.nameWithoutExtension)
        val book = OebBook(metadata)
        
        // Try to detect encoding, fallback to UTF-8
        val text = try {
            inputFile.readText(Charset.defaultCharset())
        } catch (e: Exception) {
            try {
                inputFile.readText(StandardCharsets.UTF_8)
            } catch (e2: Exception) {
                inputFile.readText(Charsets.ISO_8859_1)
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
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
