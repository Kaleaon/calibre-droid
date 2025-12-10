package org.calibre.conversion

import org.calibre.formats.lit.LitParser
import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.File

/**
 * Microsoft LIT (Literature) format input plugin.
 * 
 * LIT is an ebook format used by Microsoft Reader. It's based on
 * Microsoft's compressed HTML (CHM) format with additional DRM layers.
 * 
 * This plugin supports unprotected LIT files. DRM-protected files
 * cannot be converted without first removing the protection.
 */
class LitInput : InputPlugin {
    override val name = "LIT Input"
    override val fileTypes = setOf("lit")

    override fun convert(inputFile: File, workDir: File): OebBook {
        Logger.info("Converting LIT file: ${inputFile.name}")
        
        val data = inputFile.readBytes()
        val parser = LitParser(data)
        
        // Extract metadata
        val litMetadata = parser.getMetadata()
        val metadata = Metadata(
            title = litMetadata["title"] ?: parser.getTitle(),
            authors = parser.getAuthors().toMutableList(),
            publisher = litMetadata["publisher"]
        )
        litMetadata["language"]?.let { metadata.languages.add(it) }
        
        Logger.info("LIT title: ${metadata.title}, authors: ${metadata.authors.joinToString(", ")}")
        
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
        
        // Extract and add CSS
        val cssDir = File(workDir, "styles")
        cssDir.mkdirs()
        
        for ((name, cssData) in parser.getCssFiles()) {
            val cssFile = File(cssDir, name.substringAfterLast('/'))
            cssFile.writeBytes(cssData)
            
            val cssId = name.replace(".", "_").replace("/", "_")
            book.manifest[cssId] = OebItem(
                id = cssId,
                href = "styles/${cssFile.name}",
                mediaType = "text/css",
                file = cssFile
            )
        }
        
        // Extract and add images
        val imageDir = File(workDir, "images")
        imageDir.mkdirs()
        
        for ((name, imageData) in parser.getImages()) {
            val imageFile = File(imageDir, name.substringAfterLast('/'))
            imageFile.writeBytes(imageData)
            
            val ext = name.substringAfterLast('.').lowercase()
            val mediaType = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                "svg" -> "image/svg+xml"
                else -> "image/jpeg"
            }
            
            val imageId = name.replace(".", "_").replace("/", "_")
            book.manifest[imageId] = OebItem(
                id = imageId,
                href = "images/${imageFile.name}",
                mediaType = mediaType,
                file = imageFile
            )
        }
        
        Logger.info("Successfully converted LIT file with ${book.manifest.size} items")
        return book
    }
}
