package org.calibre.conversion

import java.io.File

class HtmlOutput : OutputPlugin {
    override val name = "HTML Output"
    override val fileType = "html"

    override fun convert(book: OebBook, outputFile: File) {
        // Single file HTML output? Or Zip of HTMLs?
        // Let's do a single monolithic HTML file for simplicity in this plugin
        // by concatenating body contents.
        
        val sb = StringBuilder()
        sb.append("<html><head><title>${book.metadata.title}</title></head><body>")
        
        sb.append("<h1>${book.metadata.title}</h1>")
        sb.append("<h2>${book.metadata.authors.joinToString(", ")}</h2>")
        sb.append("<hr/>")
        
        for (item in book.spine) {
            if (item.mediaType.contains("html") || item.mediaType.contains("xml")) {
                if (item.file.exists()) {
                    val bodyContent = extractBody(item.file)
                    sb.append("<div class='chapter'>")
                    sb.append(bodyContent)
                    sb.append("</div>")
                }
            }
        }
        
        sb.append("</body></html>")
        outputFile.writeText(sb.toString())
    }
    
    private fun extractBody(file: File): String {
        val content = file.readText()
        // Find body tag
        val bodyStart = content.indexOf("<body", ignoreCase = true)
        if (bodyStart == -1) return content // Fallback
        
        val actualStart = content.indexOf(">", bodyStart) + 1
        val bodyEnd = content.indexOf("</body>", ignoreCase = true)
        
        if (actualStart > 0 && bodyEnd > actualStart) {
            return content.substring(actualStart, bodyEnd)
        }
        return content
    }
}
