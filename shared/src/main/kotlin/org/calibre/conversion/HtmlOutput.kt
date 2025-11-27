package org.calibre.conversion

import java.io.File
import java.util.Base64

class HtmlOutput : OutputPlugin {
    override val name = "HTML Output"
    override val fileType = "html"
    
    private val cssProcessor: CssProcessor = BasicCssProcessor()

    override fun convert(book: OebBook, outputFile: File) {
        val sb = StringBuilder()
        sb.append("<html><head><title>${book.metadata.title}</title>")
        
        // Inline CSS
        sb.append("<style>")
        val cssFiles = book.manifest.values.filter { it.isCss() }.map { it.file }
        val flattenedCss = cssProcessor.flatten(cssFiles)
        sb.append(flattenedCss)
        sb.append("</style>")
        sb.append("</head><body>")
        
        sb.append("<h1>${book.metadata.title}</h1>")
        sb.append("<h2>${book.metadata.authors.joinToString(", ")}</h2>")
        sb.append("<hr/>")
        
        // Pre-process images to Base64 data URIs or relative paths?
        // For a single file HTML, Base64 is best.
        val imageMap = mutableMapOf<String, String>()
        book.manifest.values.filter { it.isImage() }.forEach { item ->
            if (item.file.exists()) {
                val bytes = item.file.readBytes()
                val base64 = Base64.getEncoder().encodeToString(bytes)
                val mime = item.mediaType
                imageMap[item.href] = "data:$mime;base64,$base64"
                // Also match just the filename
                imageMap[item.file.name] = "data:$mime;base64,$base64"
            }
        }
        
        for (item in book.spine) {
            if (item.isXhtml()) {
                if (item.file.exists()) {
                    var bodyContent = extractBody(item.file)
                    
                    // Replace image sources
                    // More robust image replacement
                    val regex = Regex("""src=["']([^"']+)["']""")
                    bodyContent = regex.replace(bodyContent) { matchResult ->
                        val src = matchResult.groupValues[1]
                        // check if this src matches any of our images
                        // often src is "Images/foo.jpg" or "../foo.jpg"
                        // we check if the filename part matches
                        val filename = File(src).name
                        val dataUri = imageMap[filename]
                        if (dataUri != null) {
                            "src=\"$dataUri\""
                        } else {
                            matchResult.value
                        }
                    }

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
        val bodyStart = content.indexOf("<body", ignoreCase = true)
        if (bodyStart == -1) return content
        
        val actualStart = content.indexOf(">", bodyStart) + 1
        val bodyEnd = content.lastIndexOf("</body>", ignoreCase = true) // lastIndexOf is safer if nested bodies (unlikely but possible in iframes) or invalid HTML
        
        if (actualStart > 0 && bodyEnd > actualStart) {
            return content.substring(actualStart, bodyEnd)
        }
        // If </body> not found, maybe take until end?
        if (actualStart > 0) return content.substring(actualStart)
        
        return content
    }
    
    // Helper to find case-insensitive substring from end?
    private fun String.lastIndexOf(str: String, ignoreCase: Boolean): Int {
        if (!ignoreCase) return this.lastIndexOf(str)
        return this.lowercase().lastIndexOf(str.lowercase())
    }
}
