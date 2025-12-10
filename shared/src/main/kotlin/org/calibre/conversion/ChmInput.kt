package org.calibre.conversion

import org.calibre.formats.chm.ChmParser
import org.calibre.metadata.Metadata
import org.calibre.oeb.OebBook
import org.calibre.oeb.OebItem
import java.io.File

/**
 * Input plugin for CHM (Compiled HTML Help) format.
 * 
 * Extracts HTML content, CSS, and images from CHM files
 * and converts them to the OEB intermediate format.
 */
class ChmInput : InputPlugin {
    
    override val name = "CHM Input"
    override val author = "Calibre Kotlin"
    override val description = "Converts CHM (Compiled HTML Help) files to ebooks"
    override val supportedFormats = listOf("chm")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        val data = inputFile.readBytes()
        val parser = ChmParser(data)
        
        val book = OebBook()
        
        // Set metadata
        val metadata = Metadata(
            title = parser.getTitle()
        )
        book.metadata = metadata
        
        // Create content directory
        val oebpsDir = File(workDir, "OEBPS")
        oebpsDir.mkdirs()
        
        // Write main HTML content
        val htmlContent = parser.getHtmlContent()
        val contentFile = File(oebpsDir, "content.html")
        contentFile.writeText(htmlContent)
        
        val contentItem = OebItem(
            id = "content",
            href = "content.html",
            mediaType = "application/xhtml+xml",
            file = contentFile
        )
        book.manifest["content"] = contentItem
        book.spine.add(contentItem)
        
        // Add CSS files
        val cssFiles = parser.getCssFiles()
        for ((name, cssData) in cssFiles) {
            val safeName = sanitizeFilename(name)
            val cssFile = File(oebpsDir, safeName)
            cssFile.parentFile?.mkdirs()
            cssFile.writeBytes(cssData)
            
            val cssItem = OebItem(
                id = "css_${safeName.replace(Regex("[^a-zA-Z0-9]"), "_")}",
                href = safeName,
                mediaType = "text/css",
                file = cssFile
            )
            book.manifest[cssItem.id] = cssItem
        }
        
        // Add images
        val images = parser.getImages()
        for ((name, imageData) in images) {
            val safeName = sanitizeFilename(name)
            val imageFile = File(oebpsDir, safeName)
            imageFile.parentFile?.mkdirs()
            imageFile.writeBytes(imageData)
            
            val mediaType = when (safeName.substringAfterLast('.').lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                "ico" -> "image/x-icon"
                else -> "application/octet-stream"
            }
            
            val imageItem = OebItem(
                id = "img_${safeName.replace(Regex("[^a-zA-Z0-9]"), "_")}",
                href = safeName,
                mediaType = mediaType,
                file = imageFile
            )
            book.manifest[imageItem.id] = imageItem
        }
        
        return book
    }
    
    private fun sanitizeFilename(name: String): String {
        return name.replace("\\", "/")
            .split("/")
            .last()
            .replace(Regex("[<>:\"|?*]"), "_")
    }
}
