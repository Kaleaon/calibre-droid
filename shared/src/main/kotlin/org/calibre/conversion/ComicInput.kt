package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File
import java.util.zip.ZipFile

/**
 * # Comic Book Input Plugin
 * 
 * Reads comic book formats (CBZ, CBR) and converts them to OEB format.
 * 
 * ## Comic Formats
 * 
 * - **CBZ**: Comic Book ZIP - ZIP archive containing images
 * - **CBR**: Comic Book RAR - RAR archive containing images
 * 
 * ## Implementation Status
 * 
 * **Note**: Full comic book support requires:
 * - Image sequence handling
 * - Page ordering
 * - Metadata extraction from ComicInfo.xml
 * 
 * This is a basic implementation that extracts images from CBZ files.
 * CBR (RAR) support requires RAR extraction library.
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see InputPlugin
 * @see OebBook
 */
class ComicInput : InputPlugin {
    override val name = "Comic Input"
    override val fileTypes = setOf("cbz", "cbr")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        val metadata = Metadata(title = inputFile.nameWithoutExtension)
        val book = OebBook(metadata)
        
        if (inputFile.extension.lowercase() == "cbz") {
            // CBZ is a ZIP file
            val zip = ZipFile(inputFile)
            try {
                val imagesDir = File(workDir, "images")
                imagesDir.mkdirs()
                
                var imageIndex = 0
                zip.entries().asSequence()
                    .sortedBy { it.name } // Sort by filename for page order
                    .forEach { entry ->
                        if (!entry.isDirectory) {
                            val ext = entry.name.substringAfterLast('.', "").lowercase()
                            if (ext in listOf("jpg", "jpeg", "png", "gif", "webp")) {
                                val destFile = File(imagesDir, "page_${String.format("%04d", imageIndex)}.$ext")
                                zip.getInputStream(entry).use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                
                                val mimeType = when (ext) {
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "png" -> "image/png"
                                    "gif" -> "image/gif"
                                    "webp" -> "image/webp"
                                    else -> "image/jpeg"
                                }
                                
                                val item = OebItem("img_$imageIndex", "images/${destFile.name}", mimeType, destFile)
                                book.manifest["img_$imageIndex"] = item
                                imageIndex++
                            }
                        }
                    }
                
                // Create HTML content with images
                val htmlContent = generateComicHtml(imageIndex)
                val htmlFile = File(workDir, "content.html")
                htmlFile.writeText(htmlContent)
                
                val item = OebItem("content", "content.html", "application/xhtml+xml", htmlFile)
                book.manifest["content"] = item
                book.spine.add(item)
                
            } finally {
                zip.close()
            }
        } else if (inputFile.extension.lowercase() == "cbr") {
            throw UnsupportedOperationException(
                "CBR (RAR) format requires RAR extraction library. " +
                "Use CBZ (ZIP) format instead, or integrate a RAR library."
            )
        }
        
        return book
    }
    
    private fun generateComicHtml(pageCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8"/>
    <title>Comic Book</title>
    <style>
        body { margin: 0; padding: 0; text-align: center; background: #000; }
        img { max-width: 100%; height: auto; display: block; margin: 0 auto; }
        .page { page-break-after: always; }
    </style>
</head>
<body>
""")
        
        for (i in 0 until pageCount) {
            val pageNum = String.format("%04d", i)
            sb.append("<div class='page'>")
            sb.append("<img src='images/page_$pageNum.jpg' alt='Page ${i + 1}' />")
            sb.append("</div>")
        }
        
        sb.append("</body></html>")
        return sb.toString()
    }
}
