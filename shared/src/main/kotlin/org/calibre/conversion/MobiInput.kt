package org.calibre.conversion

import org.calibre.formats.mobi.MobiParser
import org.calibre.metadata.Metadata
import java.io.File

class MobiInput : InputPlugin {
    override val name = "MOBI Input"
    override val fileTypes = setOf("mobi", "azw3")

    override fun convert(inputFile: File, workDir: File): OebBook {
        val parser = MobiParser()
        val mobiMeta = parser.parseMetadata(inputFile)
        val textContent = parser.extractText(inputFile)
        
        val metadata = Metadata(
            title = mobiMeta.title,
            authors = if (mobiMeta.author != null) mutableListOf(mobiMeta.author) else mutableListOf("Unknown")
        )
        
        val book = OebBook(metadata)
        
        // Extract images
        val imagesDir = File(workDir, "images")
        imagesDir.mkdirs()
        val images = parser.extractImages(inputFile, imagesDir)
        
        // Add images to manifest
        images.forEachIndexed { index, imageFile ->
            val mimeType = when (imageFile.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                else -> "image/png"
            }
            val item = OebItem("img_$index", "images/${imageFile.name}", mimeType, imageFile)
            book.manifest["img_$index"] = item
        }
        
        // Create HTML from extracted text with image references
        val contentFile = File(workDir, "content.html")
        val imageRefs = images.mapIndexed { index, img ->
            "<img src=\"images/${img.name}\" alt=\"Image $index\" />"
        }.joinToString("\n")
        
        val htmlContent = """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>${metadata.title}</title></head>
            <body>
            $textContent
            $imageRefs
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
