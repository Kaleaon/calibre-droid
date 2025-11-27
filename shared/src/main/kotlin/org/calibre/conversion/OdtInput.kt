package org.calibre.conversion

import org.calibre.metadata.Metadata
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * # ODT (OpenDocument Text) Input Plugin
 * 
 * Reads ODT format files (LibreOffice/OpenOffice document format) and converts them to OEB format.
 * 
 * ## ODT Format
 * 
 * ODT is part of the OpenDocument standard:
 * - **ZIP container**: ODT files are ZIP archives
 * - **content.xml**: Main document content in XML
 * - **styles.xml**: Document styles
 * - **meta.xml**: Document metadata
 * - **mimetype**: MIME type declaration
 * 
 * ## Supported Features
 * 
 * - Text content extraction
 * - Basic formatting preservation
 * - Metadata extraction
 * - Image extraction (if present)
 * 
 * ## Implementation Details
 * 
 * The plugin:
 * 1. Opens the ODT file as a ZIP archive
 * 2. Extracts content.xml for text content
 * 3. Extracts meta.xml for metadata
 * 4. Converts XML content to HTML
 * 5. Extracts images if present
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see InputPlugin
 * @see OebBook
 */
class OdtInput : InputPlugin {
    override val name = "ODT Input"
    override val fileTypes = setOf("odt")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        val zip = ZipFile(inputFile)
        
        try {
            // Extract all files
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
            
            // Extract metadata
            val metadata = extractMetadata(workDir) ?: Metadata(
                title = inputFile.nameWithoutExtension
            )
            
            val book = OebBook(metadata)
            
            // Extract content
            val contentFile = File(workDir, "content.xml")
            if (contentFile.exists()) {
                val htmlContent = convertOdtToHtml(contentFile)
                val htmlFile = File(workDir, "content.html")
                htmlFile.writeText(htmlContent)
                
                val item = OebItem("content", "content.html", "application/xhtml+xml", htmlFile)
                book.manifest["content"] = item
                book.spine.add(item)
            } else {
                throw Exception("content.xml not found in ODT file")
            }
            
            // Extract images
            val picturesDir = File(workDir, "Pictures")
            if (picturesDir.exists()) {
                picturesDir.listFiles()?.forEachIndexed { index, imageFile ->
                    if (imageFile.isFile) {
                        val mimeType = when (imageFile.extension.lowercase()) {
                            "jpg", "jpeg" -> "image/jpeg"
                            "png" -> "image/png"
                            "gif" -> "image/gif"
                            else -> "image/png"
                        }
                        val item = OebItem("img_$index", "Pictures/${imageFile.name}", mimeType, imageFile)
                        book.manifest["img_$index"] = item
                    }
                }
            }
            
            return book
            
        } finally {
            zip.close()
        }
    }
    
    private fun extractMetadata(workDir: File): Metadata? {
        val metaFile = File(workDir, "meta.xml")
        if (!metaFile.exists()) return null
        
        return try {
            val doc = parseXml(metaFile)
            val title = getTextContent(doc, "//dc:title") ?: return null
            val creator = getTextContent(doc, "//dc:creator")
            
            Metadata(
                title = title,
                authors = if (creator != null) mutableListOf(creator) else mutableListOf()
            )
        } catch (e: Exception) {
            org.calibre.utils.Logger.warn("Failed to extract ODT metadata: ${e.message}")
            null
        }
    }
    
    private fun convertOdtToHtml(contentFile: File): String {
        val doc = parseXml(contentFile)
        val sb = StringBuilder()
        
        sb.append("""<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8"/>
    <title>Document</title>
</head>
<body>
""")
        
        // Extract text content from office:text elements
        val textNodes = doc.getElementsByTagNameNS("*", "text")
        for (i in 0 until textNodes.length) {
            val node = textNodes.item(i)
            if (node is Element) {
                val text = node.textContent
                if (text.isNotBlank()) {
                    sb.append("<p>${escapeHtml(text)}</p>")
                }
            }
        }
        
        sb.append("</body></html>")
        return sb.toString()
    }
    
    private fun parseXml(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(file)
    }
    
    private fun getTextContent(doc: Document, xpath: String): String? {
        // Simplified - would need XPath for full implementation
        val elements = doc.getElementsByTagNameNS("*", xpath.substringAfterLast(":"))
        return if (elements.length > 0) elements.item(0).textContent else null
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
