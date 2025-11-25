package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File

class TextInput : InputPlugin {
    override val name = "Text Input"
    override val fileTypes = setOf("txt", "text")

    override fun convert(inputFile: File, workDir: File): OebBook {
        val metadata = Metadata(title = inputFile.nameWithoutExtension)
        val book = OebBook(metadata)
        
        // Convert text to basic HTML
        val text = inputFile.readText()
        val lines = text.lines()
        
        val sb = StringBuilder()
        sb.append("<html><head><title>${metadata.title}</title></head><body>")
        for (line in lines) {
            if (line.isBlank()) {
                sb.append("<br/>")
            } else {
                sb.append("<p>${line}</p>")
            }
        }
        sb.append("</body></html>")
        
        val contentFile = File(workDir, "content.html")
        contentFile.writeText(sb.toString())
        
        val item = OebItem("content", "content.html", "application/xhtml+xml", contentFile)
        book.manifest["content"] = item
        book.spine.add(item)
        
        return book
    }
}
