package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.util.*
import kotlin.io.path.exists

class HtmlInput : InputPlugin {
    override val name = "HTML Input"
    override val fileTypes = setOf("html", "htm", "xhtml", "xhtm", "shtml", "shtm", "opf")

    override fun convert(inputFile: File, workDir: File): OebBook {
        if (inputFile.extension.lowercase() == "opf") {
            return convertOpf(inputFile, workDir)
        }
        
        val baseDir = inputFile.parentFile ?: File(".")
        val metadata = extractMetadata(inputFile)
        val book = OebBook(metadata)
        
        val processedFiles = mutableSetOf<String>()
        val fileMap = mutableMapOf<File, String>() // File -> href
        
        // Process the main HTML file
        val mainHref = processHtmlFile(inputFile, baseDir, workDir, book, processedFiles, fileMap, 0, 5)
        if (mainHref != null) {
            val item = book.manifest.values.find { it.href == mainHref }
            if (item != null) {
                book.spine.add(item)
            }
        }
        
        // Generate basic TOC from headings
        generateToc(book)
        
        return book
    }
    
    private fun convertOpf(opfFile: File, workDir: File): OebBook {
        // OPF is similar to EPUB, reuse similar logic
        val opfDir = opfFile.parentFile ?: File(".")
        val opfDoc = parseXml(opfFile.inputStream())
        
        val metadata = extractOpfMetadata(opfDoc)
        val book = OebBook(metadata)
        
        // Parse manifest
        val manifestMap = mutableMapOf<String, String>()
        val items = opfDoc.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            val mediaType = item.getAttribute("media-type")
            
            val sourceFile = File(opfDir, href)
            val destFile = File(workDir, href)
            if (sourceFile.exists()) {
                destFile.parentFile.mkdirs()
                Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            
            val oebItem = OebItem(id, href, mediaType, destFile)
            book.manifest[id] = oebItem
            manifestMap[id] = href
        }
        
        // Parse spine
        val itemrefs = opfDoc.getElementsByTagNameNS("*", "itemref")
        for (i in 0 until itemrefs.length) {
            val itemref = itemrefs.item(i) as Element
            val idref = itemref.getAttribute("idref")
            val item = book.manifest[idref]
            if (item != null) {
                book.spine.add(item)
            }
        }
        
        return book
    }
    
    private fun extractMetadata(htmlFile: File): Metadata {
        val text = htmlFile.readText()
        val titleMatch = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(text)
        val title = titleMatch?.groupValues?.get(1)?.trim() ?: htmlFile.nameWithoutExtension
        
        val authorMatch = Regex("<meta[^>]*name=['\"]author['\"][^>]*content=['\"](.*?)['\"]", RegexOption.IGNORE_CASE).find(text)
        val author = authorMatch?.groupValues?.get(1)?.trim()
        
        return Metadata(
            title = title,
            authors = if (author != null) mutableListOf(author) else mutableListOf()
        )
    }
    
    private fun extractOpfMetadata(opfDoc: Document): Metadata {
        val metadata = Metadata()
        
        val titleNodes = opfDoc.getElementsByTagNameNS("*", "title")
        if (titleNodes.length > 0) {
            metadata.title = titleNodes.item(0).textContent.trim()
        }
        
        val creatorNodes = opfDoc.getElementsByTagNameNS("*", "creator")
        val authors = mutableListOf<String>()
        for (i in 0 until creatorNodes.length) {
            authors.add(creatorNodes.item(i).textContent.trim())
        }
        if (authors.isNotEmpty()) {
            metadata.authors = authors
        }
        
        return metadata
    }
    
    private fun processHtmlFile(
        htmlFile: File,
        baseDir: File,
        workDir: File,
        book: OebBook,
        processedFiles: MutableSet<String>,
        fileMap: MutableMap<File, String>,
        currentLevel: Int,
        maxLevels: Int
    ): String? {
        val normalizedPath = htmlFile.canonicalPath
        if (normalizedPath in processedFiles) {
            return fileMap[htmlFile]
        }
        processedFiles.add(normalizedPath)
        
        val htmlContent = htmlFile.readText()
        val doc = parseHtml(htmlContent)
        
        // Copy file to workDir
        val relativePath = baseDir.toPath().relativize(htmlFile.toPath()).toString()
        val destFile = File(workDir, sanitizeFileName(relativePath))
        destFile.parentFile.mkdirs()
        
        // Process resources (images, CSS, etc.)
        processResources(doc, htmlFile.parentFile, baseDir, workDir, book)
        
        // Rewrite links
        rewriteLinks(doc, htmlFile.parentFile, baseDir, workDir, book, processedFiles, fileMap, currentLevel, maxLevels)
        
        // Save modified HTML
        val modifiedHtml = docToString(doc)
        destFile.writeText(modifiedHtml)
        
        val id = "html_${book.manifest.size}"
        val href = sanitizeFileName(relativePath)
        val item = OebItem(id, href, "text/html", destFile)
        book.manifest[id] = item
        fileMap[htmlFile] = href
        
        return href
    }
    
    private fun processResources(
        doc: Document,
        htmlDir: File,
        baseDir: File,
        workDir: File,
        book: OebBook
    ) {
        // Process images
        val images = doc.getElementsByTagName("img")
        for (i in 0 until images.length) {
            val img = images.item(i) as Element
            val src = img.getAttribute("src") ?: continue
            processResource(src, htmlDir, baseDir, workDir, book, "image")
        }
        
        // Process CSS links
        val links = doc.getElementsByTagName("link")
        for (i in 0 until links.length) {
            val link = links.item(i) as Element
            val rel = link.getAttribute("rel")?.lowercase()
            if (rel == "stylesheet") {
                val href = link.getAttribute("href") ?: continue
                processResource(href, htmlDir, baseDir, workDir, book, "css")
            }
        }
        
        // Process style tags
        val styles = doc.getElementsByTagName("style")
        for (i in 0 until styles.length) {
            val style = styles.item(i) as Element
            processCssResources(style.textContent, htmlDir, baseDir, workDir, book)
        }
    }
    
    private fun processResource(
        url: String,
        htmlDir: File,
        baseDir: File,
        workDir: File,
        book: OebBook,
        type: String
    ) {
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) {
            return // Skip external and data URLs
        }
        
        val resourceFile = resolveFile(url, htmlDir, baseDir) ?: return
        if (!resourceFile.exists()) return
        
        val relativePath = baseDir.toPath().relativize(resourceFile.toPath()).toString()
        val destFile = File(workDir, sanitizeFileName(relativePath))
        destFile.parentFile.mkdirs()
        
        if (!destFile.exists()) {
            Files.copy(resourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        
        val mediaType = when {
            type == "image" -> guessImageType(resourceFile)
            type == "css" -> "text/css"
            else -> "application/octet-stream"
        }
        
        val id = "${type}_${book.manifest.size}"
        val href = sanitizeFileName(relativePath)
        val item = OebItem(id, href, mediaType, destFile)
        book.manifest[id] = item
    }
    
    private fun processCssResources(css: String, htmlDir: File, baseDir: File, workDir: File, book: OebBook) {
        // Simple URL extraction from CSS
        val urlPattern = Regex("url\\(['\"]?([^'\")]+)['\"]?\\)")
        urlPattern.findAll(css).forEach { match ->
            val url = match.groupValues[1]
            processResource(url, htmlDir, baseDir, workDir, book, "image")
        }
    }
    
    private fun rewriteLinks(
        doc: Document,
        htmlDir: File,
        baseDir: File,
        workDir: File,
        book: OebBook,
        processedFiles: MutableSet<String>,
        fileMap: MutableMap<File, String>,
        currentLevel: Int,
        maxLevels: Int
    ) {
        val links = doc.getElementsByTagName("a")
        for (i in 0 until links.length) {
            val link = links.item(i) as Element
            val href = link.getAttribute("href") ?: continue
            
            if (href.startsWith("#") || href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:")) {
                continue
            }
            
            val linkedFile = resolveFile(href, htmlDir, baseDir) ?: continue
            if (!linkedFile.exists() || linkedFile.extension.lowercase() !in setOf("html", "htm", "xhtml")) {
                continue
            }
            
            if (currentLevel < maxLevels) {
                val linkedHref = processHtmlFile(linkedFile, baseDir, workDir, book, processedFiles, fileMap, currentLevel + 1, maxLevels)
                if (linkedHref != null) {
                    link.setAttribute("href", linkedHref)
                }
            }
        }
    }
    
    private fun resolveFile(url: String, htmlDir: File, baseDir: File): File? {
        return try {
            val urlFile = File(url)
            when {
                urlFile.isAbsolute -> urlFile
                else -> {
                    val resolved = File(htmlDir, url)
                    if (resolved.exists()) resolved else File(baseDir, url)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun generateToc(book: OebBook) {
        // Simple TOC generation - could be enhanced
        for (item in book.spine) {
            if (item.isXhtml()) {
                try {
                    val doc = parseHtml(item.file.readText())
                    val titleNodes = doc.getElementsByTagName("title")
                    if (titleNodes.length > 0) {
                        val title = titleNodes.item(0).textContent.trim()
                        // TOC could be stored in book.toc if we add that field
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
    
    private fun parseXml(inputStream: java.io.InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isValidating = false
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        return factory.newDocumentBuilder().parse(inputStream)
    }
    
    private fun parseHtml(html: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isValidating = false
        factory.isNamespaceAware = false
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        val builder = factory.newDocumentBuilder()
        return builder.parse(java.io.ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    }
    
    private fun docToString(doc: Document): String {
        val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
        val writer = java.io.StringWriter()
        transformer.transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(writer))
        return writer.toString()
    }
    
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[?&=;#/\\\\]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
    }
    
    private fun guessImageType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
