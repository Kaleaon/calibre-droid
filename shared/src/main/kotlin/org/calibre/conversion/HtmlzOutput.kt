package org.calibre.conversion

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * # HTMLZ Output Plugin
 * 
 * Writes HTMLZ format files (HTML content packaged in a ZIP archive).
 * 
 * ## HTMLZ Format
 * 
 * HTMLZ is a simple format that packages HTML content in a ZIP archive:
 * - **ZIP container**: Contains HTML files and resources
 * - **Index file**: `index.html` at the top level (or title-based filename)
 * - **Resources**: Images, CSS, and other files in subdirectories
 * - **Optional OPF**: May include metadata.opf for book metadata
 * 
 * ## Output Structure
 * 
 * ```
 * HTMLZ File (ZIP)
 * ├── index.html (or title.html) - main content
 * ├── style.css (if CSS is external)
 * ├── images/ (image resources)
 * ├── fonts/ (font files, if any)
 * ├── metadata.opf (book metadata)
 * └── cover.jpg (cover image, if present)
 * ```
 * 
 * ## Features
 * 
 * - Single HTML file output with embedded or external CSS
 * - Image resources in images/ directory
 * - Font resources in fonts/ directory (if present)
 * - Optional metadata.opf file
 * - Cover image extraction
 * 
 * ## CSS Handling
 * 
 * CSS can be:
 * - **External**: Separate style.css file (default for class-based CSS)
 * - **Inline**: Embedded in `<style>` tag in HTML
 * - **Inline attributes**: Style attributes on elements
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see OutputPlugin
 * @see OebBook
 * @see HtmlzInput
 */
class HtmlzOutput : OutputPlugin {
    /**
     * Human-readable name of this plugin.
     */
    override val name = "HTMLZ Output"
    
    /**
     * File extension this plugin produces.
     */
    override val fileType = "htmlz"
    
    /**
     * Converts an OEB book to HTMLZ format.
     * 
     * This method:
     * 1. Combines all spine items into a single HTML file
     * 2. Extracts CSS (external or inline based on options)
     * 3. Copies images to images/ directory
     * 4. Copies fonts to fonts/ directory (if present)
     * 5. Creates metadata.opf file
     * 6. Packages everything into a ZIP file
     * 
     * @param book The OEB book to convert
     * @param outputFile The output HTMLZ file path
     * @throws Exception if conversion fails
     */
    override fun convert(book: OebBook, outputFile: File) {
        org.calibre.utils.Logger.info("Creating HTMLZ file: ${outputFile.absolutePath}")
        
        // Create temporary directory for building HTMLZ
        val tempDir = java.nio.file.Files.createTempDirectory("htmlz_build_").toFile()
        
        try {
            // Generate HTML content
            val htmlContent = generateHtml(book)
            val htmlFileName = sanitizeFileName("${book.metadata.title}.html").take(100)
                .ifEmpty { "index.html" }
            val htmlFile = File(tempDir, htmlFileName)
            htmlFile.writeText(htmlContent, StandardCharsets.UTF_8)
            
            // Extract CSS (external file)
            val cssContent = extractCss(book)
            if (cssContent.isNotEmpty()) {
                val cssFile = File(tempDir, "style.css")
                cssFile.writeText(cssContent, StandardCharsets.UTF_8)
            }
            
            // Copy images
            val imagesDir = File(tempDir, "images")
            imagesDir.mkdirs()
            for ((id, item) in book.manifest) {
                if (item.isImage() && item.file.exists()) {
                    val destFile = File(imagesDir, item.file.name)
                    item.file.copyTo(destFile, overwrite = true)
                }
            }
            
            // Copy fonts (if any)
            val fontsDir = File(tempDir, "fonts")
            var hasFonts = false
            for ((id, item) in book.manifest) {
                if (item.mediaType.startsWith("font/") || 
                    item.mediaType == "application/font-woff" ||
                    item.mediaType == "application/font-woff2") {
                    if (!hasFonts) {
                        fontsDir.mkdirs()
                        hasFonts = true
                    }
                    if (item.file.exists()) {
                        val destFile = File(fontsDir, item.file.name)
                        item.file.copyTo(destFile, overwrite = true)
                    }
                }
            }
            
            // Create metadata.opf
            val opfFile = File(tempDir, "metadata.opf")
            createMetadataOpf(book, opfFile)
            
            // Copy cover image if present
            book.manifest.values.firstOrNull { it.id == "cover" && it.isImage() }?.let { coverItem ->
                if (coverItem.file.exists()) {
                    val coverFile = File(tempDir, "cover.jpg")
                    coverItem.file.copyTo(coverFile, overwrite = true)
                }
            }
            
            // Package as ZIP
            packageHtmlz(tempDir, outputFile)
            
            org.calibre.utils.Logger.info("HTMLZ file created successfully: ${outputFile.absolutePath}")
            
        } finally {
            // Clean up temp directory
            tempDir.deleteRecursively()
        }
    }
    
    /**
     * Generates HTML content from the OEB book.
     * 
     * Combines all spine items into a single HTML document.
     */
    private fun generateHtml(book: OebBook): String {
        val sb = StringBuilder()
        sb.append("""<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8"/>
    <title>${escapeHtml(book.metadata.title)}</title>
    <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
""")
        
        // Add title page
        sb.append("<h1>${escapeHtml(book.metadata.title)}</h1>")
        if (book.metadata.authors.isNotEmpty()) {
            sb.append("<h2>${escapeHtml(book.metadata.authors.joinToString(", "))}</h2>")
        }
        sb.append("<hr/>")
        
        // Add content from spine
        for (item in book.spine) {
            if (item.isXhtml() && item.file.exists()) {
                val content = item.file.readText(StandardCharsets.UTF_8)
                val bodyContent = extractBody(content)
                sb.append("<div class='chapter'>")
                sb.append(bodyContent)
                sb.append("</div>")
            }
        }
        
        sb.append("</body></html>")
        return sb.toString()
    }
    
    /**
     * Extracts CSS content from the book.
     */
    private fun extractCss(book: OebBook): String {
        val cssProcessor = BasicCssProcessor()
        val cssFiles = book.manifest.values
            .filter { it.isCss() }
            .map { it.file }
            .filter { it.exists() }
        
        return cssProcessor.flatten(cssFiles)
    }
    
    /**
     * Creates a simplified metadata.opf file.
     */
    private fun createMetadataOpf(book: OebBook, opfFile: File) {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val packageEl = doc.createElementNS("http://www.idpf.org/2007/opf", "package")
        packageEl.setAttribute("version", "2.0")
        doc.appendChild(packageEl)
        
        val metadata = doc.createElementNS("http://www.idpf.org/2007/opf", "metadata")
        packageEl.appendChild(metadata)
        
        val dcNs = "http://purl.org/dc/elements/1.1/"
        addDcElement(doc, metadata, dcNs, "title", book.metadata.title)
        book.metadata.authors.forEach { author ->
            addDcElement(doc, metadata, dcNs, "creator", author)
        }
        
        val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
        transformer.transform(
            javax.xml.transform.dom.DOMSource(doc),
            javax.xml.transform.stream.StreamResult(opfFile)
        )
    }
    
    private fun addDcElement(doc: org.w3c.dom.Document, parent: org.w3c.dom.Element, ns: String, name: String, value: String) {
        if (value.isNotEmpty()) {
            val element = doc.createElementNS(ns, "dc:$name")
            element.textContent = value
            parent.appendChild(element)
        }
    }
    
    /**
     * Packages the directory contents into a ZIP file.
     */
    private fun packageHtmlz(sourceDir: File, outputFile: File) {
        ZipOutputStream(outputFile.outputStream()).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(sourceDir).path.replace('\\', '/')
                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
    
    private fun extractBody(content: String): String {
        val bodyStart = content.indexOf("<body", ignoreCase = true)
        if (bodyStart == -1) return content
        
        val actualStart = content.indexOf(">", bodyStart) + 1
        val bodyEnd = content.lastIndexOf("</body>", ignoreCase = true)
        
        return if (actualStart > 0 && bodyEnd > actualStart) {
            content.substring(actualStart, bodyEnd)
        } else if (actualStart > 0) {
            content.substring(actualStart)
        } else {
            content
        }
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
