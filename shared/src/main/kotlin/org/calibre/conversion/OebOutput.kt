package org.calibre.conversion

import org.calibre.metadata.Metadata
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * # OEB (Open Ebook) Output Plugin
 * 
 * Writes the OEB intermediate format to a directory structure.
 * 
 * OEB (Open Ebook) is the intermediate format used internally by the conversion
 * system. This plugin allows exporting books in the OEB format, which is useful
 * for debugging, manual editing, or as an intermediate step in complex workflows.
 * 
 * ## OEB Format Structure
 * 
 * OEB format consists of:
 * - **OPF file** (content.opf): Contains metadata, manifest, and spine
 * - **NCX file** (toc.ncx): Navigation/table of contents
 * - **Content files**: HTML/XHTML files in reading order
 * - **Resources**: Images, CSS, fonts, etc.
 * 
 * ## Output Structure
 * 
 * ```
 * output_directory/
 * ├── content.opf (metadata, manifest, spine)
 * ├── toc.ncx (navigation)
 * ├── *.html (content files)
 * ├── images/ (image resources)
 * ├── styles/ (CSS files)
 * └── fonts/ (font files, if any)
 * ```
 * 
 * ## Features
 * 
 * - Preserves all metadata from the OEB book
 * - Maintains resource structure and relative paths
 * - Creates proper OPF and NCX files
 * - Handles all resource types (images, CSS, fonts)
 * 
 * ## Usage
 * 
 * The output path should be a directory. All files will be written to this directory
 * with their relative paths preserved from the OEB book structure.
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see OebBook
 * @see InputPlugin
 * @see OutputPlugin
 */
class OebOutput : OutputPlugin {
    /**
     * Human-readable name of this plugin.
     */
    override val name = "OEB Output"
    
    /**
     * File extension this plugin produces.
     * Note: OEB is actually a directory, but we use "oeb" as the identifier.
     */
    override val fileType = "oeb"
    
    /**
     * Converts an OEB book to OEB format (directory structure).
     * 
     * This method:
     * 1. Creates the output directory if it doesn't exist
     * 2. Generates content.opf with metadata, manifest, and spine
     * 3. Generates toc.ncx for navigation
     * 4. Copies all content files maintaining their structure
     * 5. Copies all resources (images, CSS, fonts) to appropriate directories
     * 
     * @param book The OEB book to convert
     * @param outputFile The output directory path. Will be created if it doesn't exist.
     *                   Note: Even though this is a File, it represents a directory.
     * 
     * @throws Exception if the directory cannot be created or files cannot be written
     */
    override fun convert(book: OebBook, outputFile: File) {
        // Ensure output directory exists
        if (!outputFile.exists()) {
            outputFile.mkdirs()
        }
        if (!outputFile.isDirectory) {
            throw Exception("OEB output requires a directory, not a file: ${outputFile.absolutePath}")
        }
        
        org.calibre.utils.Logger.info("Writing OEB format to directory: ${outputFile.absolutePath}")
        
        // Create OPF file
        val opfFile = File(outputFile, "content.opf")
        createOpf(book, opfFile)
        
        // Create NCX file (table of contents)
        val ncxFile = File(outputFile, "toc.ncx")
        createNcx(book, ncxFile)
        
        // Copy all manifest items to output directory
        for ((id, item) in book.manifest) {
            val destFile = File(outputFile, item.href)
            destFile.parentFile?.mkdirs()
            
            if (item.file.exists()) {
                item.file.copyTo(destFile, overwrite = true)
                org.calibre.utils.Logger.debug("Copied resource: ${item.href}")
            } else {
                org.calibre.utils.Logger.warn("Resource file not found: ${item.file.absolutePath}")
            }
        }
        
        org.calibre.utils.Logger.info("OEB format written successfully to ${outputFile.absolutePath}")
    }
    
    /**
     * Creates the OPF (Open Packaging Format) file.
     * 
     * The OPF file contains:
     * - Metadata (title, authors, etc.)
     * - Manifest (list of all resources)
     * - Spine (reading order)
     * 
     * @param book The OEB book
     * @param opfFile The OPF file to create
     */
    private fun createOpf(book: OebBook, opfFile: File) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        // Package element
        val packageEl = doc.createElementNS("http://www.idpf.org/2007/opf", "package")
        packageEl.setAttribute("version", "2.0")
        packageEl.setAttribute("unique-identifier", "book-id")
        doc.appendChild(packageEl)
        
        // Metadata
        val metadata = doc.createElementNS("http://www.idpf.org/2007/opf", "metadata")
        packageEl.appendChild(metadata)
        
        val dcNs = "http://purl.org/dc/elements/1.1/"
        addDcElement(doc, metadata, dcNs, "title", book.metadata.title)
        book.metadata.authors.forEach { author ->
            addDcElement(doc, metadata, dcNs, "creator", author)
        }
        book.metadata.publisher?.takeIf { it.isNotBlank() }?.let {
            addDcElement(doc, metadata, dcNs, "publisher", it)
        }
        book.metadata.comments?.takeIf { it.isNotBlank() }?.let {
            addDcElement(doc, metadata, dcNs, "description", it)
        }
        val lang = book.metadata.languages.firstOrNull()?.takeIf { it.isNotBlank() }
        if (lang != null) {
            addDcElement(doc, metadata, dcNs, "language", lang)
        }
        val pubDate = book.metadata.pubDate ?: book.metadata.dateAdded
        if (pubDate != null) {
            addDcElement(doc, metadata, dcNs, "date", pubDate.toString())
        }
        
        // Identifier
        val identifier = doc.createElementNS(dcNs, "dc:identifier")
        identifier.setAttribute("id", "book-id")
        identifier.textContent = book.metadata.title // Use title as identifier if no ISBN
        metadata.appendChild(identifier)
        
        // Manifest
        val manifest = doc.createElementNS("http://www.idpf.org/2007/opf", "manifest")
        packageEl.appendChild(manifest)
        
        for ((id, item) in book.manifest) {
            val itemEl = doc.createElementNS("http://www.idpf.org/2007/opf", "item")
            itemEl.setAttribute("id", id)
            itemEl.setAttribute("href", item.href)
            itemEl.setAttribute("media-type", item.mediaType)
            manifest.appendChild(itemEl)
        }
        
        // Spine
        val spine = doc.createElementNS("http://www.idpf.org/2007/opf", "spine")
        spine.setAttribute("toc", "ncx")
        packageEl.appendChild(spine)
        
        for (item in book.spine) {
            val itemref = doc.createElementNS("http://www.idpf.org/2007/opf", "itemref")
            itemref.setAttribute("idref", item.id)
            spine.appendChild(itemref)
        }
        
        // Guide (optional)
        val guide = doc.createElementNS("http://www.idpf.org/2007/opf", "guide")
        packageEl.appendChild(guide)
        
        // Write OPF file
        writeXml(doc, opfFile)
    }
    
    /**
     * Creates the NCX (Navigation Control XML) file for table of contents.
     * 
     * @param book The OEB book
     * @param ncxFile The NCX file to create
     */
    private fun createNcx(book: OebBook, ncxFile: File) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val ncx = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "ncx")
        ncx.setAttribute("version", "2005-1")
        doc.appendChild(ncx)
        
        // Head
        val head = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "head")
        ncx.appendChild(head)
        
        val metaTitle = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "meta")
        metaTitle.setAttribute("name", "dtb:uid")
        metaTitle.setAttribute("content", book.metadata.title)
        head.appendChild(metaTitle)
        
        val metaDepth = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "meta")
        metaDepth.setAttribute("name", "dtb:depth")
        metaDepth.setAttribute("content", "1")
        head.appendChild(metaDepth)
        
        // DocTitle
        val docTitle = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "docTitle")
        ncx.appendChild(docTitle)
        val text = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "text")
        text.textContent = book.metadata.title
        docTitle.appendChild(text)
        
        // NavMap
        val navMap = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "navMap")
        ncx.appendChild(navMap)
        
        // Create nav points for each spine item
        book.spine.forEachIndexed { index, item ->
            val navPoint = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "navPoint")
            navPoint.setAttribute("id", "navpoint-${index + 1}")
            navPoint.setAttribute("playOrder", "${index + 1}")
            navMap.appendChild(navPoint)
            
            val navLabel = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "navLabel")
            navPoint.appendChild(navLabel)
            val navText = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "text")
            navText.textContent = "Chapter ${index + 1}"
            navLabel.appendChild(navText)
            
            val content = doc.createElementNS("http://www.daisy.org/z3986/2005/ncx/", "content")
            content.setAttribute("src", item.href)
            navPoint.appendChild(content)
        }
        
        // Write NCX file
        writeXml(doc, ncxFile)
    }
    
    /**
     * Adds a Dublin Core metadata element.
     */
    private fun addDcElement(doc: Document, parent: Element, ns: String, name: String, value: String) {
        if (value.isNotEmpty()) {
            val element = doc.createElementNS(ns, "dc:$name")
            element.textContent = value
            parent.appendChild(element)
        }
    }
    
    /**
     * Writes an XML document to a file.
     */
    private fun writeXml(doc: Document, file: File) {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty("indent", "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        transformer.transform(DOMSource(doc), StreamResult(file))
    }
}
