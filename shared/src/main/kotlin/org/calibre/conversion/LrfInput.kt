package org.calibre.conversion

import org.calibre.formats.lrf.LrfParser
import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.File

/**
 * Sony LRF (Librie/Reader Format) input plugin.
 * 
 * LRF is an ebook format used by Sony e-readers. This plugin
 * extracts text content, images, and metadata from LRF files.
 */
class LrfInput : InputPlugin {
    override val name = "LRF Input"
    override val fileTypes = setOf("lrf")

    override fun convert(inputFile: File, workDir: File): OebBook {
        Logger.info("Converting LRF file: ${inputFile.name}")
        
        val data = inputFile.readBytes()
        val parser = LrfParser(data)
        
        // Extract metadata
        val lrfMetadata = parser.getMetadata()
        val authorName = lrfMetadata["author"]?.takeIf { it.isNotEmpty() } 
            ?: parser.getAuthor().takeIf { it != "Unknown Author" }
        val metadata = Metadata(
            title = lrfMetadata["title"] ?: parser.getTitle(),
            authors = if (authorName != null) mutableListOf(authorName) else mutableListOf(),
            publisher = lrfMetadata["publisher"]
        )
        
        Logger.info("LRF title: ${metadata.title}, author: ${metadata.authors.joinToString(", ")}")
        
        // Extract HTML content
        val htmlContent = parser.getHtmlContent()
        val contentFile = File(workDir, "content.html")
        contentFile.writeText(htmlContent, Charsets.UTF_8)
        
        // Create OEB book
        val book = OebBook(metadata)
        
        // Add main content
        val contentItem = OebItem(
            id = "content",
            href = "content.html",
            mediaType = "application/xhtml+xml",
            file = contentFile
        )
        book.manifest["content"] = contentItem
        book.spine.add(contentItem)
        
        // Extract and add images
        val imageDir = File(workDir, "images")
        imageDir.mkdirs()
        
        for ((name, imageData) in parser.getImages()) {
            // Sanitize filename to prevent path traversal attacks
            val safeName = name.substringAfterLast('/').substringAfterLast('\\')
                .replace("..", "_").replace(":", "_")
            val imageFile = File(imageDir, safeName)
            imageFile.writeBytes(imageData)
            
            val ext = safeName.substringAfterLast('.').lowercase()
            val mediaType = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                else -> "image/jpeg"
            }
            
            val imageId = safeName.replace(".", "_")
            book.manifest[imageId] = OebItem(
                id = imageId,
                href = "images/$safeName",
                mediaType = mediaType,
                file = imageFile
            )
        }
        
        Logger.info("Successfully converted LRF file with ${book.manifest.size} items")
        return book
    }
}
