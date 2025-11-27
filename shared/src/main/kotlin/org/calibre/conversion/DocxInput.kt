package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File
import java.util.zip.ZipFile

class DocxInput : InputPlugin {
    override val name = "DOCX Input"
    override val fileTypes = setOf("docx")

    override fun convert(inputFile: File, workDir: File): OebBook {
        // DOCX is a ZIP file containing XML
        val zip = ZipFile(inputFile)
        try {
            // Extract main document
            val documentEntry = zip.getEntry("word/document.xml")
            if (documentEntry == null) {
                throw Exception("Invalid DOCX: No document.xml found")
            }
            
            // Extract all files for proper structure
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory) {
                    val destFile = File(workDir, entry.name)
                    destFile.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            
            // Parse document.xml to extract text
            val documentXml = File(workDir, "word/document.xml")
            val xmlContent = documentXml.readText()
            
            // Simple text extraction (remove XML tags)
            // In production, use proper XML parser
            val textContent = xmlContent
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            
            // Extract title from document properties if available
            val propsEntry = zip.getEntry("docProps/core.xml")
            var title = inputFile.nameWithoutExtension
            if (propsEntry != null) {
                val propsXml = zip.getInputStream(propsEntry).readBytes().toString(Charsets.UTF_8)
                val titleMatch = Regex("<dc:title[^>]*>([^<]+)</dc:title>").find(propsXml)
                if (titleMatch != null) {
                    title = titleMatch.groupValues[1].trim()
                }
            }
            
            val metadata = Metadata(title = title)
            val book = OebBook(metadata)
            
            // Create HTML content
            val contentFile = File(workDir, "content.html")
            val htmlContent = """
                <html>
                <head><title>$title</title></head>
                <body>
                ${textContent.split(" ").chunked(100).joinToString("\n<p>", "</p>\n<p>", "</p>") { it.joinToString(" ") }}
                </body>
                </html>
            """.trimIndent()
            
            contentFile.writeText(htmlContent)
            
            val item = OebItem("content", "content.html", "application/xhtml+xml", contentFile)
            book.manifest["content"] = item
            book.spine.add(item)
            
            return book
        } finally {
            zip.close()
        }
    }
}
