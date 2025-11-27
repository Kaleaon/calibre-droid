package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * # PML (Palm Markup Language) Input Plugin
 * 
 * Reads PML format files and converts them to OEB format.
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
 * - PML parser implementation
 * - PML to HTML conversion
 * - PMLZ extraction support
 * 
 * This is a basic implementation. Full support requires PML-specific parsing.
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see InputPlugin
 * @see OebBook
 */
class PmlInput : InputPlugin {
    override val name = "PML Input"
    override val fileTypes = setOf("pml", "pmlz")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        val metadata = Metadata(title = inputFile.nameWithoutExtension)
        val book = OebBook(metadata)
        
        // Check if it's PMLZ (ZIP) or PML (single file)
        if (inputFile.extension.lowercase() == "pmlz") {
            // Extract ZIP
            val zip = ZipFile(inputFile)
            try {
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory) {
                        val destFile = File(workDir, entry.name)
                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            } finally {
                zip.close()
            }
        } else {
            // Single PML file - copy to work directory
            val pmlFile = File(workDir, inputFile.name)
            inputFile.copyTo(pmlFile, overwrite = true)
        }
        
        // Find PML file(s)
        val pmlFiles = workDir.listFiles { _, name ->
            name.endsWith(".pml", ignoreCase = true)
        } ?: emptyArray()
        
        if (pmlFiles.isEmpty()) {
            throw Exception("No PML file found")
        }
        
        // Convert first PML file to HTML (simplified)
        val pmlFile = pmlFiles.first()
        val htmlContent = convertPmlToHtml(pmlFile)
        val htmlFile = File(workDir, "content.html")
        htmlFile.writeText(htmlContent)
        
        val item = OebItem("content", "content.html", "application/xhtml+xml", htmlFile)
        book.manifest["content"] = item
        book.spine.add(item)
        
        // Extract images if present
        val imgDir = File(workDir, "index_img")
        if (imgDir.exists()) {
            imgDir.listFiles()?.forEachIndexed { index, imageFile ->
                if (imageFile.isFile) {
                    val mimeType = when (imageFile.extension.lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "gif" -> "image/gif"
                        else -> "image/png"
                    }
                    val item = OebItem("img_$index", "index_img/${imageFile.name}", mimeType, imageFile)
                    book.manifest["img_$index"] = item
                }
            }
        }
        
        return book
    }
    
    private fun convertPmlToHtml(pmlFile: File): String {
        // Basic PML to HTML conversion
        // Full implementation would require PML parser
        val pmlContent = pmlFile.readText(Charsets.ISO_8859_1)
        
        // Simple tag conversion (very basic)
        var html = pmlContent
            .replace("<p>", "<p>", ignoreCase = true)
            .replace("</p>", "</p>", ignoreCase = true)
            .replace("<b>", "<strong>", ignoreCase = true)
            .replace("</b>", "</strong>", ignoreCase = true)
            .replace("<i>", "<em>", ignoreCase = true)
            .replace("</i>", "</em>", ignoreCase = true)
        
        return """<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8"/>
    <title>${pmlFile.nameWithoutExtension}</title>
</head>
<body>
$html
</body>
</html>"""
    }
}
