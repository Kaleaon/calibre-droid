package org.calibre.conversion

import org.calibre.formats.djvu.DjvuParser
import org.calibre.metadata.Metadata
import org.calibre.oeb.OebBook
import org.calibre.oeb.OebItem
import java.io.File

/**
 * Input plugin for DjVu format.
 * 
 * Extracts text content from DjVu files and converts
 * to the OEB intermediate format.
 */
class DjvuInput : InputPlugin {
    
    override val name = "DjVu Input"
    override val author = "Calibre Kotlin"
    override val description = "Converts DjVu scanned documents to ebooks"
    override val fileTypes = setOf("djvu", "djv")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        val data = inputFile.readBytes()
        val parser = DjvuParser(data)
        
        val book = OebBook()
        
        // Set metadata
        val metadata = Metadata(
            title = parser.getTitle()
        )
        book.metadata = metadata
        
        // Create content directory
        val oebpsDir = File(workDir, "OEBPS")
        oebpsDir.mkdirs()
        
        // Write HTML content
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
        
        // Add page images if available
        val pages = parser.getPages()
        for ((index, page) in pages.withIndex()) {
            page.imageData?.let { imageData ->
                val imageName = "page_${index + 1}.jpg"
                val imageFile = File(oebpsDir, imageName)
                imageFile.writeBytes(imageData)
                
                val imageItem = OebItem(
                    id = "page_$index",
                    href = imageName,
                    mediaType = "image/jpeg",
                    file = imageFile
                )
                book.manifest[imageItem.id] = imageItem
            }
        }
        
        return book
    }
}
