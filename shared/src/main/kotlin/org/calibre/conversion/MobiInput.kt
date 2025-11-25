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
        
        // Create HTML from extracted text
        // In a real converter, we would need to handle the markup inside the text 
        // (MOBI can contain raw HTML-like tags).
        // We wrap it in a basic HTML structure.
        
        val contentFile = File(workDir, "content.html")
        val htmlContent = """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>${metadata.title}</title></head>
            <body>
            $textContent
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
