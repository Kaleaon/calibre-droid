package org.calibre.metadata

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubParser : MetadataParser {

    override fun canParse(file: File): Boolean {
        return file.extension.equals("epub", ignoreCase = true)
    }

    override fun parseMetadata(file: File): Metadata {
        val zip = ZipFile(file)
        try {
            val containerEntry = zip.getEntry("META-INF/container.xml") ?: throw Exception("Invalid EPUB: No container.xml")
            val containerDoc = parseXml(zip.getInputStream(containerEntry))
            val opfPath = getOpfPath(containerDoc) ?: throw Exception("Invalid EPUB: No rootfile found in container.xml")

            val opfEntry = zip.getEntry(opfPath) ?: throw Exception("Invalid EPUB: OPF file not found at $opfPath")
            val opfDoc = parseXml(zip.getInputStream(opfEntry))

            return extractMetadata(opfDoc)
        } finally {
            zip.close()
        }
    }

    private fun parseXml(inputStream: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isValidating = false
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        val builder = factory.newDocumentBuilder()
        return builder.parse(inputStream)
    }

    private fun getOpfPath(containerDoc: Document): String? {
        val rootfiles = containerDoc.getElementsByTagName("rootfile")
        for (i in 0 until rootfiles.length) {
            val element = rootfiles.item(i) as Element
            val mediaType = element.getAttribute("media-type")
            if (mediaType == "application/oebps-package+xml") {
                return element.getAttribute("full-path")
            }
        }
        if (rootfiles.length > 0) {
            return (rootfiles.item(0) as Element).getAttribute("full-path")
        }
        return null
    }

    private fun extractMetadata(opfDoc: Document): Metadata {
        val metadataElement = opfDoc.getElementsByTagNameNS("*", "metadata").item(0) as? Element
            ?: throw Exception("No metadata element found in OPF")

        val metadata = Metadata()
        
        fun getDcContent(localName: String): List<String> {
            val dcUri = "http://purl.org/dc/elements/1.1/"
            var elements = metadataElement.getElementsByTagNameNS(dcUri, localName)
            if (elements.length == 0) {
                 elements = metadataElement.getElementsByTagName(localName)
            }
            
            val results = mutableListOf<String>()
            for (i in 0 until elements.length) {
                val node = elements.item(i)
                if (node.localName == localName || node.nodeName.endsWith(":$localName")) {
                     results.add(node.textContent.trim())
                }
            }
            return results
        }

        val titles = getDcContent("title")
        if (titles.isNotEmpty()) metadata.title = titles[0]

        val creators = getDcContent("creator")
        if (creators.isNotEmpty()) {
            metadata.authors = creators.toMutableList()
        }

        val descriptions = getDcContent("description")
        if (descriptions.isNotEmpty()) metadata.comments = descriptions[0]

        val publishers = getDcContent("publisher")
        if (publishers.isNotEmpty()) metadata.publisher = publishers[0]

        val langs = getDcContent("language")
        if (langs.isNotEmpty()) metadata.languages = langs.toMutableList()

        val subjects = getDcContent("subject")
        if (subjects.isNotEmpty()) metadata.tags = subjects.toMutableList()

        val metas = metadataElement.getElementsByTagName("meta")
        for (i in 0 until metas.length) {
            val meta = metas.item(i) as Element
            val name = meta.getAttribute("name")
            val content = meta.getAttribute("content")
            
            if (name == "calibre:series") {
                metadata.series = content
            } else if (name == "calibre:series_index") {
                metadata.seriesIndex = content.toDoubleOrNull()
            } else if (name == "calibre:rating") {
                metadata.rating = content.toDoubleOrNull()
            }
            
            val property = meta.getAttribute("property")
            if (property != null) {
                 if (property == "calibre:series") metadata.series = meta.textContent.trim()
                 if (property == "calibre:series_index") metadata.seriesIndex = meta.textContent.trim().toDoubleOrNull()
                 if (property == "calibre:rating") metadata.rating = meta.textContent.trim().toDoubleOrNull()
            }
        }

        return metadata
    }
}
