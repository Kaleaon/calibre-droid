package org.calibre.conversion

import org.calibre.formats.mobi.MobiParser
import org.calibre.metadata.Metadata
import java.io.File

/**
 * AZW3 (KF8) Input plugin.
 * 
 * AZW3 is Amazon's KF8 (Kindle Format 8) format, which is an evolution of MOBI.
 * It uses the same Palm Database container but with enhanced features:
 * - Better HTML/CSS support
 * - Improved typography
 * - Enhanced image handling
 * 
 * This implementation uses MobiParser for basic extraction. Full KF8 support would
 * require parsing the KF8-specific indices (FDST, SKEL, DIVI) and flow structure.
 */
class Azw3Input : InputPlugin {
    override val name = "AZW3 Input"
    override val fileTypes = setOf("azw3")

    override fun convert(inputFile: File, workDir: File): OebBook {
        val parser = MobiParser()
        val mobiMeta = parser.parseMetadata(inputFile)
        
        // Extract text content (MobiParser handles both MOBI 6 and basic KF8)
        val textContent = try {
            parser.extractText(inputFile)
        } catch (e: Exception) {
            org.calibre.utils.Logger.warn(
                "Failed to extract text from AZW3 file using standard method: ${e.message}. " +
                "KF8 format may require specialized parsing."
            )
            "<p>Content extraction from KF8 format requires specialized parsing.</p>"
        }
        
        val metadata = Metadata(
            title = mobiMeta.title,
            authors = if (mobiMeta.author != null) mutableListOf(mobiMeta.author) else mutableListOf("Unknown")
        )
        
        val book = OebBook(metadata)
        
        // Extract images if possible
        val imagesDir = File(workDir, "images")
        imagesDir.mkdirs()
        val images = try {
            parser.extractImages(inputFile, imagesDir)
        } catch (e: Exception) {
            org.calibre.utils.Logger.warn("Failed to extract images from AZW3: ${e.message}")
            emptyList<File>()
        }
        
        // Add images to manifest
        images.forEachIndexed { index, imageFile ->
            val mimeType = when (imageFile.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "image/png"
            }
            val item = OebItem("img_$index", "images/${imageFile.name}", mimeType, imageFile)
            book.manifest["img_$index"] = item
        }
        
        // Create HTML content
        val contentFile = File(workDir, "content.html")
        val imageRefs = if (images.isNotEmpty()) {
            images.mapIndexed { index, img ->
                "<img src=\"images/${img.name}\" alt=\"Image ${index + 1}\" />"
            }.joinToString("\n")
        } else {
            ""
        }
        
        // Try to parse text content as HTML, or wrap it if it's plain text
        val htmlContent = if (textContent.trim().startsWith("<")) {
            // Already HTML-like, use as-is but ensure proper structure
            wrapHtmlContent(textContent, metadata.title, imageRefs)
        } else {
            // Plain text, convert to HTML
            convertTextToHtml(textContent, metadata.title, imageRefs)
        }
        
        contentFile.writeText(htmlContent)
        
        val item = OebItem("content", "content.html", "application/xhtml+xml", contentFile)
        book.manifest["content"] = item
        book.spine.add(item)
        
        return book
    }
    
    /**
     * Wraps HTML content in a proper HTML document structure.
     */
    private fun wrapHtmlContent(content: String, title: String, imageRefs: String): String {
        // Extract body content if present
        val bodyStart = content.indexOf("<body", ignoreCase = true)
        val bodyEnd = content.lastIndexOf("</body>", ignoreCase = true)
        
        val bodyContent = if (bodyStart >= 0 && bodyEnd > bodyStart) {
            val actualStart = content.indexOf(">", bodyStart) + 1
            content.substring(actualStart, bodyEnd)
        } else {
            content
        }
        
        return """<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8"/>
    <title>${escapeHtml(title)}</title>
</head>
<body>
$bodyContent
$imageRefs
</body>
</html>"""
    }
    
    /**
     * Converts plain text to HTML.
     */
    private fun convertTextToHtml(text: String, title: String, imageRefs: String): String {
        val paragraphs = text.split("\n\n", "\r\n\r\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { "<p>${escapeHtml(it)}</p>" }
        
        return """<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8"/>
    <title>${escapeHtml(title)}</title>
</head>
<body>
<h1>${escapeHtml(title)}</h1>
$paragraphs
$imageRefs
</body>
</html>"""
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
