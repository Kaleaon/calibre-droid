package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

/**
 * PDF Output plugin that converts OEBBook to PDF using HTML rendering.
 * Note: This requires a desktop environment with OpenHTMLToPDF.
 * For Android, a different approach would be needed.
 */
class PdfOutput : OutputPlugin {
    override val name = "PDF Output"
    override val fileType = "pdf"
    
    private val cssProcessor: CssProcessor = BasicCssProcessor()
    
    override fun convert(book: OebBook, outputFile: File) {
        // First, convert OEBBook to a complete HTML document
        val htmlContent = convertToHtml(book)
        
        // Write HTML to temp file
        val tempHtml = File.createTempFile("calibre_pdf_", ".html")
        try {
            tempHtml.writeText(htmlContent)
            
            // Render HTML to PDF using OpenHTMLToPDF
            renderHtmlToPdf(tempHtml, outputFile, book.metadata)
        } finally {
            tempHtml.delete()
        }
    }
    
    private fun convertToHtml(book: OebBook): String {
        val sb = StringBuilder()
        sb.append("""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${escapeHtml(book.metadata.title)}</title>
    <style>
""")
        
        // Inline CSS
        val cssFiles = book.manifest.values.filter { it.isCss() }.map { it.file }
        val flattenedCss = cssProcessor.flatten(cssFiles)
        sb.append(flattenedCss)
        
        // Add PDF-specific CSS
        sb.append("""
        @page {
            margin: 72pt;
        }
        body {
            font-family: serif;
            line-height: 1.6;
        }
        img {
            max-width: 100%;
            height: auto;
        }
""")
        sb.append("    </style>")
        sb.append("</head>")
        sb.append("<body>")
        
        // Add cover if available
        val coverItem = book.manifest.values.firstOrNull { 
            it.isImage() && (it.href.contains("cover", ignoreCase = true) || 
                            it.id.contains("cover", ignoreCase = true))
        }
        if (coverItem != null && coverItem.file.exists()) {
            val bytes = coverItem.file.readBytes()
            val base64 = Base64.getEncoder().encodeToString(bytes)
            val mime = coverItem.mediaType
            sb.append("""<div style="text-align: center; page-break-after: always;">
                <img src="data:$mime;base64,$base64" alt="Cover" style="max-height: 600pt;"/>
            </div>""")
        }
        
        // Add title page
        sb.append("""<div style="text-align: center; page-break-after: always; padding-top: 200pt;">
            <h1>${escapeHtml(book.metadata.title)}</h1>
            <h2>${escapeHtml(book.metadata.authors.joinToString(", "))}</h2>
        </div>""")
        
        // Process images to Base64
        val imageMap = mutableMapOf<String, String>()
        book.manifest.values.filter { it.isImage() }.forEach { item ->
            if (item.file.exists()) {
                val bytes = item.file.readBytes()
                val base64 = Base64.getEncoder().encodeToString(bytes)
                val mime = item.mediaType
                imageMap[item.href] = "data:$mime;base64,$base64"
                imageMap[item.file.name] = "data:$mime;base64,$base64"
                // Also match relative paths
                val relativePath = item.href.replace("\\", "/")
                imageMap[relativePath] = "data:$mime;base64,$base64"
            }
        }
        
        // Add content from spine
        for (item in book.spine) {
            if (item.isXhtml()) {
                if (item.file.exists()) {
                    var bodyContent = extractBody(item.file)
                    
                    // Replace image sources with Base64 data URIs
                    val regex = Regex("""src=["']([^"']+)["']""")
                    bodyContent = regex.replace(bodyContent) { matchResult ->
                        val src = matchResult.groupValues[1]
                        val filename = File(src).name
                        val normalizedSrc = src.replace("\\", "/")
                        
                        val dataUri = imageMap[src] ?: imageMap[filename] ?: imageMap[normalizedSrc]
                        if (dataUri != null) {
                            "src=\"$dataUri\""
                        } else {
                            matchResult.value
                        }
                    }
                    
                    sb.append("<div class='chapter' style='page-break-before: always;'>")
                    sb.append(bodyContent)
                    sb.append("</div>")
                }
            }
        }
        
        sb.append("</body></html>")
        return sb.toString()
    }
    
    private fun extractBody(file: File): String {
        val content = file.readText()
        val bodyStart = content.indexOf("<body", ignoreCase = true)
        if (bodyStart == -1) return content
        
        val actualStart = content.indexOf(">", bodyStart) + 1
        val bodyEnd = content.lastIndexOf("</body>", ignoreCase = true)
        
        if (actualStart > 0 && bodyEnd > actualStart) {
            return content.substring(actualStart, bodyEnd)
        }
        if (actualStart > 0) return content.substring(actualStart)
        
        return content
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    private fun renderHtmlToPdf(htmlFile: File, outputFile: File, metadata: Metadata) {
        try {
            // Use reflection to check if OpenHTMLToPDF is available
            val rendererClass = Class.forName("com.openhtmltopdf.pdfboxout.PdfRendererBuilder")
            val builder = rendererClass.getDeclaredConstructor().newInstance()
            
            // Set HTML file
            val setHtmlFromFile = rendererClass.getMethod("withHtmlFile", File::class.java)
            setHtmlFromFile.invoke(builder, htmlFile)
            
            // Set output
            val setOutput = rendererClass.getMethod("toStream", java.io.OutputStream::class.java)
            FileOutputStream(outputFile).use { out ->
                setOutput.invoke(builder, out)
            }
            
            // Run renderer
            val runMethod = rendererClass.getMethod("run")
            runMethod.invoke(builder)
        } catch (e: ClassNotFoundException) {
            // Fallback: If OpenHTMLToPDF is not available, create a simple text-based PDF using PDFBox
            createSimplePdf(outputFile, metadata)
        } catch (e: Exception) {
            throw Exception("Failed to render PDF: ${e.message}", e)
        }
    }
    
    private fun createSimplePdf(outputFile: File, metadata: Metadata) {
        // Fallback implementation using PDFBox directly
        try {
            val documentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument")
            val doc = documentClass.getDeclaredConstructor().newInstance()
            
            val pageClass = Class.forName("org.apache.pdfbox.pdmodel.PDPage")
            val page = pageClass.getDeclaredConstructor().newInstance()
            
            val addPageMethod = documentClass.getMethod("addPage", pageClass)
            addPageMethod.invoke(doc, page)
            
            val contentStreamClass = Class.forName("org.apache.pdfbox.pdmodel.PDPageContentStream")
            val contentStream = contentStreamClass.getConstructor(documentClass, pageClass)
                .newInstance(doc, page)
            
            val beginTextMethod = contentStreamClass.getMethod("beginText")
            val setFontMethod = contentStreamClass.getMethod("setFont", 
                Class.forName("org.apache.pdfbox.pdmodel.font.PDFont"), Float::class.java)
            val setLeadingMethod = contentStreamClass.getMethod("setLeading", Float::class.java)
            val newLineAtOffsetMethod = contentStreamClass.getMethod("newLineAtOffset", Float::class.java, Float::class.java)
            val showTextMethod = contentStreamClass.getMethod("showText", String::class.java)
            val newLineMethod = contentStreamClass.getMethod("newLine")
            val endTextMethod = contentStreamClass.getMethod("endText")
            val closeMethod = contentStreamClass.getMethod("close")
            
            val fontClass = Class.forName("org.apache.pdfbox.pdmodel.font.PDType1Font")
            val helvetica = fontClass.getField("HELVETICA").get(null)
            val helveticaBold = fontClass.getField("HELVETICA_BOLD").get(null)
            
            beginTextMethod.invoke(contentStream)
            setFontMethod.invoke(contentStream, helveticaBold, 16f)
            setLeadingMethod.invoke(contentStream, 20f)
            newLineAtOffsetMethod.invoke(contentStream, 50f, 750f)
            showTextMethod.invoke(contentStream, metadata.title)
            newLineMethod.invoke(contentStream)
            setFontMethod.invoke(contentStream, helvetica, 12f)
            showTextMethod.invoke(contentStream, "Author: ${metadata.authors.joinToString(", ")}")
            newLineMethod.invoke(contentStream)
            showTextMethod.invoke(contentStream, "Note: Full PDF rendering requires OpenHTMLToPDF library.")
            endTextMethod.invoke(contentStream)
            closeMethod.invoke(contentStream)
            
            val saveMethod = documentClass.getMethod("save", String::class.java)
            saveMethod.invoke(doc, outputFile.absolutePath)
            
            val closeDocMethod = documentClass.getMethod("close")
            closeDocMethod.invoke(doc)
        } catch (e: Exception) {
            throw Exception("Failed to create PDF: ${e.message}", e)
        }
    }
}
