package org.calibre.conversion

import java.io.File

class TextOutput : OutputPlugin {
    override val name = "Text Output"
    override val fileType = "txt"

    override fun convert(book: OebBook, outputFile: File) {
        val sb = StringBuilder()
        
        // Add Metadata Header
        sb.append("Title: ${book.metadata.title}\n")
        sb.append("Author: ${book.metadata.authors.joinToString(", ")}\n")
        sb.append("--------------------------------------------------\n\n")
        
        for (item in book.spine) {
            // Only process XHTML/HTML
            if (item.mediaType.contains("html") || item.mediaType.contains("xml")) {
                if (item.file.exists()) {
                    val text = extractText(item.file)
                    sb.append(text).append("\n\n")
                }
            }
        }
        
        outputFile.writeText(sb.toString())
    }
    
    private fun extractText(file: File): String {
        // Simple extraction logic reused
        val content = file.readText()
        
        var clean = content.replace(Regex("(?i)<script.*?>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
        clean = clean.replace(Regex("(?i)<style.*?>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
        clean = clean.replace(Regex("(?i)</(p|div|h[1-6]|li|br)>"), "\n")
        clean = clean.replace(Regex("<[^>]*>"), "")
        clean = clean.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            
        return clean.replace(Regex("\\n\\s*\\n"), "\n\n").trim()
    }
}
