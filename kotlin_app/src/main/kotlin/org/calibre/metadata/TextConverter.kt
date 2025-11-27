package org.calibre.metadata

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class TextConverter {

    fun convertEpubToText(epubFile: File, outputFile: File) {
        val zip = ZipFile(epubFile)
        try {
            val containerEntry = zip.getEntry("META-INF/container.xml") ?: throw Exception("Invalid EPUB")
            val containerDoc = parseXml(zip.getInputStream(containerEntry))
            val opfPath = getOpfPath(containerDoc) ?: throw Exception("No OPF found")
            val opfEntry = zip.getEntry(opfPath) ?: throw Exception("OPF file not found")
            val opfDoc = parseXml(zip.getInputStream(opfEntry))

            // 1. Get the Manifest (id -> href)
            val manifest = getManifest(opfDoc)

            // 2. Get the Spine (ordered list of ids)
            val spine = getSpine(opfDoc)

            // 3. Resolve paths and extract text
            val opfDir = File(opfPath).parent ?: ""
            val sb = StringBuilder()

            for (id in spine) {
                val href = manifest[id] ?: continue
                // Construct full path inside zip
                val fullPath = if (opfDir.isEmpty()) href else "$opfDir/$href"
                
                val entry = zip.getEntry(fullPath)
                if (entry != null) {
                    val text = extractTextFromHtml(zip.getInputStream(entry))
                    sb.append(text).append("\n\n")
                }
            }

            outputFile.writeText(sb.toString())

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

    private fun getManifest(opfDoc: Document): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val items = opfDoc.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            map[id] = href
        }
        return map
    }

    private fun getSpine(opfDoc: Document): List<String> {
        val list = mutableListOf<String>()
        val itemrefs = opfDoc.getElementsByTagNameNS("*", "itemref")
        for (i in 0 until itemrefs.length) {
            val itemref = itemrefs.item(i) as Element
            list.add(itemref.getAttribute("idref"))
        }
        return list
    }

    private fun extractTextFromHtml(inputStream: InputStream): String {
        // Very simple HTML text extractor. 
        // For production, use a real parser like Jsoup.
        // Here we'll just strip tags using Regex for the PoC.
        val content = inputStream.reader().readText()
        
        // Remove <style> and <script> blocks first
        var clean = content.replace(Regex("(?i)<script.*?>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
        clean = clean.replace(Regex("(?i)<style.*?>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
        
        // Replace block tags with newlines to preserve some structure
        clean = clean.replace(Regex("(?i)</(p|div|h[1-6]|li|br)>"), "\n")
        
        // Strip all other tags
        clean = clean.replace(Regex("<[^>]*>"), "")
        
        // Decode common entities
        clean = clean.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            
        // Normalize whitespace
        return clean.replace(Regex("\\n\\s*\\n"), "\n\n").trim()
    }
}
