package org.calibre.conversion

import org.calibre.metadata.EpubParser
import org.calibre.metadata.Metadata
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubInput : InputPlugin {
    override val name = "EPUB Input"
    override val fileTypes = setOf("epub")

    override fun convert(inputFile: File, workDir: File): OebBook {
        val zip = ZipFile(inputFile)
        try {
            val containerEntry = zip.getEntry("META-INF/container.xml") ?: throw Exception("Invalid EPUB")
            val containerDoc = parseXml(zip.getInputStream(containerEntry))
            val opfPath = getOpfPath(containerDoc) ?: throw Exception("No OPF found")
            val opfEntry = zip.getEntry(opfPath) ?: throw Exception("OPF file not found")
            
            // Extract all files to workDir
            // For a real implementation we might be more selective, but extracting all is safer for relative links
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory) {
                    val destFile = File(workDir, entry.name)
                    destFile.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            
            val opfFile = File(workDir, opfPath)
            val opfDoc = parseXml(opfFile.inputStream())
            
            // Reuse existing parser logic for metadata? 
            // We can reuse EpubParser logic if we refactor it or just re-parse for now.
            // For speed, let's use the existing MetadataParser on the original file since we trust it.
            val metadata = EpubParser().parseMetadata(inputFile)
            
            val book = OebBook(metadata)
            
            // Parse Manifest
            val manifestMap = mutableMapOf<String, String>() // id -> href
            val items = opfDoc.getElementsByTagNameNS("*", "item")
            for (i in 0 until items.length) {
                val item = items.item(i) as Element
                val id = item.getAttribute("id")
                val href = item.getAttribute("href")
                val mediaType = item.getAttribute("media-type")
                
                // Resolve href relative to OPF
                val opfDir = File(opfPath).parentFile
                val itemFile = if (opfDir != null) File(workDir, File(opfDir, href).path) else File(workDir, href)
                
                val oebItem = OebItem(id, href, mediaType, itemFile)
                book.manifest[id] = oebItem
                manifestMap[id] = href
            }
            
            // Parse Spine
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
            
        } finally {
            zip.close()
        }
    }

    private fun parseXml(inputStream: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isValidating = false
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        return factory.newDocumentBuilder().parse(inputStream)
    }

    private fun getOpfPath(containerDoc: Document): String? {
        val rootfiles = containerDoc.getElementsByTagName("rootfile")
        if (rootfiles.length > 0) {
            return (rootfiles.item(0) as Element).getAttribute("full-path")
        }
        return null
    }
}
