package org.calibre.conversion

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class EpubOutput : OutputPlugin {
    override val name = "EPUB Output"
    override val fileType = "epub"

    override fun convert(book: OebBook, outputFile: File) {
        val workDir = outputFile.parentFile ?: File(".")
        val tempDir = File(workDir, "epub_build_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            // Create OEBPS directory
            val oebpsDir = File(tempDir, "OEBPS")
            oebpsDir.mkdirs()
            
            // Copy all manifest files to OEBPS
            val hrefMap = mutableMapOf<String, String>() // old href -> new href
            for ((id, item) in book.manifest) {
                val newHref = "OEBPS/${item.file.name}"
                val destFile = File(tempDir, newHref)
                destFile.parentFile.mkdirs()
                item.file.copyTo(destFile, overwrite = true)
                hrefMap[item.href] = newHref
            }
            
            // Create content.opf
            val opfFile = File(oebpsDir, "content.opf")
            createOpf(book, opfFile, hrefMap)
            
            // Create META-INF/container.xml
            val metaInfDir = File(tempDir, "META-INF")
            metaInfDir.mkdirs()
            val containerFile = File(metaInfDir, "container.xml")
            createContainer(containerFile)
            
            // Create mimetype file (must be first, uncompressed)
            val mimetypeFile = File(tempDir, "mimetype")
            mimetypeFile.writeText("application/epub+zip")
            
            // Package as EPUB (ZIP)
            packageEpub(tempDir, outputFile)
            
        } finally {
            tempDir.deleteRecursively()
        }
    }
    
    private fun createOpf(book: OebBook, opfFile: File, hrefMap: Map<String, String>) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
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
        book.metadata.publisher?.let { publisher ->
            addDcElement(doc, metadata, dcNs, "publisher", publisher)
        }
        book.metadata.comments?.let { comments ->
            addDcElement(doc, metadata, dcNs, "description", comments)
        }
        book.metadata.languages.forEach { lang ->
            addDcElement(doc, metadata, dcNs, "language", lang)
        }
        book.metadata.tags.forEach { tag ->
            addDcElement(doc, metadata, dcNs, "subject", tag)
        }
        
        val identifier = doc.createElementNS(dcNs, "dc:identifier")
        identifier.setAttribute("id", "book-id")
        identifier.textContent = book.metadata.id?.toString() ?: "calibre-kotlin-${System.currentTimeMillis()}"
        metadata.appendChild(identifier)
        
        // Manifest
        val manifest = doc.createElementNS("http://www.idpf.org/2007/opf", "manifest")
        packageEl.appendChild(manifest)
        
        for ((id, item) in book.manifest) {
            val itemEl = doc.createElementNS("http://www.idpf.org/2007/opf", "item")
            itemEl.setAttribute("id", id)
            itemEl.setAttribute("href", hrefMap[item.href] ?: item.href)
            itemEl.setAttribute("media-type", item.mediaType)
            manifest.appendChild(itemEl)
        }
        
        // Spine
        val spine = doc.createElementNS("http://www.idpf.org/2007/opf", "spine")
        packageEl.appendChild(spine)
        
        for (item in book.spine) {
            val itemref = doc.createElementNS("http://www.idpf.org/2007/opf", "itemref")
            itemref.setAttribute("idref", item.id)
            spine.appendChild(itemref)
        }
        
        // Write XML
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.transform(DOMSource(doc), StreamResult(opfFile))
    }
    
    private fun addDcElement(doc: Document, parent: Element, ns: String, name: String, value: String) {
        val element = doc.createElementNS(ns, "dc:$name")
        element.textContent = value
        parent.appendChild(element)
    }
    
    private fun createContainer(containerFile: File) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val container = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:container", "container")
        container.setAttribute("version", "1.0")
        doc.appendChild(container)
        
        val rootfiles = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:container", "rootfiles")
        container.appendChild(rootfiles)
        
        val rootfile = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:container", "rootfile")
        rootfile.setAttribute("full-path", "OEBPS/content.opf")
        rootfile.setAttribute("media-type", "application/oebps-package+xml")
        rootfiles.appendChild(rootfile)
        
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.transform(DOMSource(doc), StreamResult(containerFile))
    }
    
    private fun packageEpub(sourceDir: File, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // mimetype must be first, uncompressed
            val mimetype = File(sourceDir, "mimetype")
            zos.putNextEntry(ZipEntry("mimetype").apply { method = ZipEntry.STORED })
            mimetype.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
            
            // Add all other files
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile && file.name != "mimetype") {
                    val entryName = file.relativeTo(sourceDir).path.replace('\\', '/')
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
}
