package org.calibre.conversion

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * # PML (Palm Markup Language) Output Plugin
 * 
 * Writes PMLZ format files (PML in ZIP archive).
 * 
 * ## PML Format
 * 
 * PML is a markup language used on Palm devices:
 * - **Markup language**: Simple HTML-like tags
 * - **PMLZ format**: ZIP archive containing PML files and images
 * - **Legacy format**: Used on older Palm devices
 * 
 * ## Implementation Status
 * 
 * **Note**: Full PML support requires:
 * - HTML to PML conversion
 * - PML-specific tag generation
 * - Proper PML structure
 * 
 * This is a basic implementation. Full support requires PML-specific conversion.
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see OutputPlugin
 * @see OebBook
 */
class PmlOutput : OutputPlugin {
    override val name = "PML Output"
    override val fileType = "pmlz"
    
    override fun convert(book: OebBook, outputFile: File) {
        val tempDir = java.nio.file.Files.createTempDirectory("pmlz_build_").toFile()
        
        try {
            // Convert HTML to PML (simplified)
            val pmlContent = convertHtmlToPml(book)
            val pmlFile = File(tempDir, "index.pml")
            pmlFile.writeText(pmlContent, Charsets.ISO_8859_1)
            
            // Copy images
            val imgDir = File(tempDir, "index_img")
            imgDir.mkdirs()
            for ((id, item) in book.manifest) {
                if (item.isImage() && item.file.exists()) {
                    val destFile = File(imgDir, item.file.name)
                    item.file.copyTo(destFile, overwrite = true)
                }
            }
            
            // Package as ZIP
            ZipOutputStream(outputFile.outputStream()).use { zos ->
                tempDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(tempDir).path.replace('\\', '/')
                        val entry = ZipEntry(relativePath)
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            
        } finally {
            tempDir.deleteRecursively()
        }
    }
    
    private fun convertHtmlToPml(book: OebBook): String {
        val sb = StringBuilder()
        
        // Basic HTML to PML conversion (simplified)
        for (item in book.spine) {
            if (item.isXhtml() && item.file.exists()) {
                val html = item.file.readText(StandardCharsets.UTF_8)
                val bodyContent = extractBody(html)
                
                // Simple tag conversion
                var pml = bodyContent
                    .replace("<p>", "<p>", ignoreCase = true)
                    .replace("</p>", "</p>", ignoreCase = true)
                    .replace("<strong>", "<b>", ignoreCase = true)
                    .replace("</strong>", "</b>", ignoreCase = true)
                    .replace("<em>", "<i>", ignoreCase = true)
                    .replace("</em>", "</i>", ignoreCase = true)
                    .replace(Regex("<[^>]+>"), "") // Remove other HTML tags
                
                sb.append(pml)
            }
        }
        
        return sb.toString()
    }
    
    private fun extractBody(html: String): String {
        val bodyStart = html.indexOf("<body", ignoreCase = true)
        if (bodyStart == -1) return html
        
        val actualStart = html.indexOf(">", bodyStart) + 1
        val bodyEnd = html.lastIndexOf("</body>", ignoreCase = true)
        
        return if (actualStart > 0 && bodyEnd > actualStart) {
            html.substring(actualStart, bodyEnd)
        } else if (actualStart > 0) {
            html.substring(actualStart)
        } else {
            html
        }
    }
}
