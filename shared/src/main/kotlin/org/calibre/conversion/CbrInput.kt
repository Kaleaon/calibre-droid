package org.calibre.conversion

import org.calibre.formats.rar.RarExtractor
import org.calibre.metadata.Metadata
import java.io.File

/**
 * Input plugin for CBR (Comic Book RAR) format.
 * 
 * Extracts images from RAR archives and creates an ebook
 * with pages displayed as images.
 */
class CbrInput : InputPlugin {
    
    override val name = "CBR Input"
    override val fileTypes = setOf("cbr")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        val data = inputFile.readBytes()
        val extractor = RarExtractor(data)
        
        // Set metadata from filename
        val title = inputFile.nameWithoutExtension
            .replace(Regex("[_\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        val metadata = Metadata(title = title)
        val book = OebBook(metadata)
        
        // Create content directory
        val oebpsDir = File(workDir, "OEBPS")
        oebpsDir.mkdirs()
        
        // Extract and sort image files
        val imageEntries = extractor.getImageFiles()
        val extractedImages = mutableListOf<Pair<String, File>>()
        
        for ((index, entry) in imageEntries.withIndex()) {
            val imageData = extractor.extractEntry(entry)
            if (imageData != null) {
                val ext = entry.name.substringAfterLast('.').lowercase()
                val imageName = "page_${String.format("%04d", index + 1)}.$ext"
                val imageFile = File(oebpsDir, imageName)
                imageFile.writeBytes(imageData)
                extractedImages.add(Pair(imageName, imageFile))
            }
        }
        
        // If RAR compression prevents extraction, note this
        if (extractedImages.isEmpty() && imageEntries.isNotEmpty()) {
            // Create placeholder content
            val htmlContent = buildString {
                append("<!DOCTYPE html>\n<html><head>")
                append("<meta charset=\"UTF-8\">")
                append("<title>${escapeHtml(title)}</title>")
                append("</head><body>")
                append("<h1>${escapeHtml(title)}</h1>")
                append("<p>This CBR file uses RAR compression that requires external tools to decompress.</p>")
                append("<p>The archive contains ${imageEntries.size} image(s):</p>")
                append("<ul>")
                for (entry in imageEntries) {
                    append("<li>${escapeHtml(entry.name)}</li>")
                }
                append("</ul>")
                append("<p>To read this comic, please use a dedicated CBR reader or extract with unrar.</p>")
                append("</body></html>")
            }
            
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
            
            return book
        }
        
        // Create HTML pages for each image
        val pageFiles = mutableListOf<File>()
        
        for ((index, pair) in extractedImages.withIndex()) {
            val (imageName, imageFile) = pair
            val pageNum = index + 1
            val totalPages = extractedImages.size
            
            val htmlContent = buildString {
                append("<!DOCTYPE html>\n<html><head>")
                append("<meta charset=\"UTF-8\">")
                append("<title>Page $pageNum</title>")
                append("<style>")
                append("body { margin: 0; padding: 0; text-align: center; background: #000; }")
                append("img { max-width: 100%; max-height: 100vh; }")
                append(".nav { position: fixed; bottom: 10px; width: 100%; text-align: center; }")
                append(".nav a { color: #fff; margin: 0 20px; text-decoration: none; }")
                append("</style>")
                append("</head><body>")
                append("<img src=\"$imageName\" alt=\"Page $pageNum\"/>")
                append("<div class=\"nav\">")
                if (index > 0) {
                    append("<a href=\"page_${String.format("%04d", pageNum - 1)}.html\">Previous</a>")
                }
                append("<span style=\"color:#666\">$pageNum / $totalPages</span>")
                if (index < extractedImages.size - 1) {
                    append("<a href=\"page_${String.format("%04d", pageNum + 1)}.html\">Next</a>")
                }
                append("</div>")
                append("</body></html>")
            }
            
            val pageFileName = "page_${String.format("%04d", pageNum)}.html"
            val pageFile = File(oebpsDir, pageFileName)
            pageFile.writeText(htmlContent)
            pageFiles.add(pageFile)
            
            // Add page to manifest and spine
            val pageItem = OebItem(
                id = "page_$pageNum",
                href = pageFileName,
                mediaType = "application/xhtml+xml",
                file = pageFile
            )
            book.manifest[pageItem.id] = pageItem
            book.spine.add(pageItem)
            
            // Add image to manifest
            val mediaType = when (imageName.substringAfterLast('.').lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            
            val imageItem = OebItem(
                id = "img_$pageNum",
                href = imageName,
                mediaType = mediaType,
                file = imageFile
            )
            book.manifest[imageItem.id] = imageItem
        }
        
        return book
    }
    
    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
