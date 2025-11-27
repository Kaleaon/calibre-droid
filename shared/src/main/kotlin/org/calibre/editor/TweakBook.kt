package org.calibre.editor

import org.calibre.conversion.OebBook
import org.calibre.conversion.OebItem
import org.calibre.metadata.EpubParser
import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Tweak Book editor - allows editing EPUB content.
 * Unpacks EPUB, allows editing HTML/CSS, and repacks.
 */
class TweakBook(private val epubFile: File) {
    val workDir: File
    private val oebpsDir: File
    private val metaInfDir: File
    
    init {
        if (!epubFile.exists()) {
            throw Exception("EPUB file not found: ${epubFile.absolutePath}")
        }
        workDir = File.createTempFile("tweak_", "_${epubFile.nameWithoutExtension}")
        workDir.delete()
        workDir.mkdirs()
        oebpsDir = File(workDir, "OEBPS")
        oebpsDir.mkdirs()
        metaInfDir = File(workDir, "META-INF")
        metaInfDir.mkdirs()
        
        unpack()
    }
    
    /**
     * Unpack EPUB to work directory.
     */
    private fun unpack() {
        ZipFile(epubFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val targetFile = File(workDir, entry.name)
                
                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        Logger.info("Unpacked EPUB to: ${workDir.absolutePath}")
    }
    
    /**
     * Get the OEB book representation.
     */
    fun getBook(): OebBook {
        val parser = EpubParser()
        val metadata = parser.parseMetadata(epubFile)
        
        // Parse content.opf
        val opfFile = File(oebpsDir, "content.opf")
        if (!opfFile.exists()) {
            // Try to find it
            val opfFiles = workDir.walkTopDown().filter { it.name == "content.opf" }
            val found = opfFiles.firstOrNull()
            if (found != null) {
                return parseBookFromOpf(found, metadata)
            }
            throw Exception("content.opf not found")
        }
        
        return parseBookFromOpf(opfFile, metadata)
    }
    
    private fun parseBookFromOpf(opfFile: File, metadata: Metadata): OebBook {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(opfFile)
        
        val book = OebBook(metadata = metadata)
        val manifest = doc.getElementsByTagName("manifest").item(0) as? Element
        val spine = doc.getElementsByTagName("spine").item(0) as? Element
        
        // Parse manifest
        manifest?.let { mf ->
            val items = mf.getElementsByTagName("item")
            for (i in 0 until items.length) {
                val item = items.item(i) as? Element
                val id = item.getAttribute("id")
                val href = item.getAttribute("href")
                val mediaType = item.getAttribute("media-type")
                
                val hrefFile = File(opfFile.parentFile, href)
                if (hrefFile.exists()) {
                    val oebItem = OebItem(
                        id = id,
                        href = href,
                        mediaType = mediaType,
                        file = hrefFile
                    )
                    book.manifest[id] = oebItem
                }
            }
        }
        
        // Parse spine
        spine?.let { sp ->
            val itemrefs = sp.getElementsByTagName("itemref")
            for (i in 0 until itemrefs.length) {
                val itemref = itemrefs.item(i) as? Element
                val idref = itemref.getAttribute("idref")
                book.manifest[idref]?.let { item ->
                    book.spine.add(item)
                }
            }
        }
        
        return book
    }
    
    /**
     * Edit an HTML file.
     */
    fun editHtml(href: String, editor: (String) -> String) {
        val item = findItemByHref(href) ?: throw Exception("Item not found: $href")
        if (!item.file.exists()) {
            throw Exception("File not found: ${item.file.absolutePath}")
        }
        
        val content = item.file.readText()
        val edited = editor(content)
        item.file.writeText(edited)
        Logger.info("Edited: $href")
    }
    
    /**
     * Edit a CSS file.
     */
    fun editCss(href: String, editor: (String) -> String) {
        val item = findItemByHref(href) ?: throw Exception("Item not found: $href")
        if (!item.file.exists()) {
            throw Exception("File not found: ${item.file.absolutePath}")
        }
        
        val content = item.file.readText()
        val edited = editor(content)
        item.file.writeText(edited)
        Logger.info("Edited CSS: $href")
    }
    
    /**
     * Add a new HTML file to the book.
     */
    fun addHtmlFile(id: String, href: String, content: String) {
        val htmlFile = File(oebpsDir, href)
        htmlFile.parentFile.mkdirs()
        htmlFile.writeText(content)
        
        // Update manifest and spine in content.opf
        updateOpfManifest(id, href, "application/xhtml+xml")
        Logger.info("Added HTML file: $href")
    }
    
    /**
     * Remove a file from the book.
     */
    fun removeFile(href: String) {
        val item = findItemByHref(href)
        if (item != null) {
            item.file.delete()
            // Remove from manifest in content.opf
            removeFromOpfManifest(item.id)
            Logger.info("Removed file: $href")
        }
    }
    
    /**
     * Repack EPUB to output file.
     */
    fun repack(outputFile: File) {
        // Update content.opf if needed
        updateContentOpf()
        
        // Create EPUB (ZIP with specific structure)
        ZipOutputStream(outputFile.outputStream()).use { zos ->
            // META-INF/container.xml
            val containerFile = File(metaInfDir, "container.xml")
            if (!containerFile.exists()) {
                createContainer(containerFile)
            }
            addFileToZip(zos, containerFile, "META-INF/container.xml")
            
            // META-INF files
            metaInfDir.listFiles()?.forEach { file ->
                if (file.name != "container.xml") {
                    addFileToZip(zos, file, "META-INF/${file.name}")
                }
            }
            
            // OEBPS files
            addDirectoryToZip(zos, oebpsDir, "OEBPS")
            
            // Root files (mimetype, etc.)
            workDir.listFiles()?.forEach { file ->
                if (file.name != "META-INF" && file.name != "OEBPS") {
                    if (file.isFile) {
                        addFileToZip(zos, file, file.name)
                    }
                }
            }
        }
        
        Logger.info("Repacked EPUB to: ${outputFile.absolutePath}")
    }
    
    /**
     * Clean up temporary files.
     */
    fun cleanup() {
        workDir.deleteRecursively()
    }
    
    private fun findItemByHref(href: String): OebItem? {
        val book = getBook()
        return book.manifest.values.firstOrNull { it.href == href }
    }
    
    private fun updateOpfManifest(id: String, href: String, mediaType: String) {
        val opfFile = File(oebpsDir, "content.opf")
        if (!opfFile.exists()) return
        
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(opfFile)
        
        val manifest = doc.getElementsByTagName("manifest").item(0) as? Element
            ?: return
        
        val item = doc.createElement("item")
        item.setAttribute("id", id)
        item.setAttribute("href", href)
        item.setAttribute("media-type", mediaType)
        manifest.appendChild(item)
        
        saveOpf(doc, opfFile)
    }
    
    private fun removeFromOpfManifest(id: String) {
        val opfFile = File(oebpsDir, "content.opf")
        if (!opfFile.exists()) return
        
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(opfFile)
        
        val manifest = doc.getElementsByTagName("manifest").item(0) as? Element
            ?: return
        
        val items = manifest.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element
            if (item.getAttribute("id") == id) {
                manifest.removeChild(item)
                break
            }
        }
        
        saveOpf(doc, opfFile)
    }
    
    private fun updateContentOpf() {
        // Ensure content.opf is up to date
        // This could update metadata, etc.
    }
    
    private fun createContainer(containerFile: File) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val container = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:container", "container")
        container.setAttribute("version", "1.0")
        doc.appendChild(container)
        
        val rootfiles = doc.createElement("rootfiles")
        container.appendChild(rootfiles)
        
        val rootfile = doc.createElement("rootfile")
        rootfile.setAttribute("full-path", "OEBPS/content.opf")
        rootfile.setAttribute("media-type", "application/oebps-package+xml")
        rootfiles.appendChild(rootfile)
        
        saveOpf(doc, containerFile)
    }
    
    private fun saveOpf(doc: Document, file: File) {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty("indent", "yes")
        transformer.transform(DOMSource(doc), StreamResult(file))
    }
    
    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        zos.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { input ->
            input.copyTo(zos)
        }
        zos.closeEntry()
    }
    
    private fun addDirectoryToZip(zos: ZipOutputStream, dir: File, basePath: String) {
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(dir).path.replace("\\", "/")
                val entryName = "$basePath/$relativePath"
                addFileToZip(zos, file, entryName)
            }
        }
    }
}
