package org.calibre.conversion

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class Fb2Output : OutputPlugin {
    override val name = "FB2 Output"
    override val fileType = "fb2"

    override fun convert(book: OebBook, outputFile: File) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val fb2 = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "FictionBook")
        fb2.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", "http://www.gribuser.ru/xml/fictionbook/2.0")
        fb2.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:l", "http://www.w3.org/1999/xlink")
        doc.appendChild(fb2)
        
        // Description
        val description = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "description")
        fb2.appendChild(description)
        
        val titleInfo = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "title-info")
        description.appendChild(titleInfo)
        
        // Title
        val title = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "book-title")
        title.textContent = book.metadata.title
        titleInfo.appendChild(title)
        
        // Authors
        book.metadata.authors.forEach { author ->
            val authorEl = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "author")
            val firstName = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "first-name")
            val lastName = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "last-name")
            
            // Simple name parsing
            val nameParts = author.split(" ", limit = 2)
            if (nameParts.size >= 2) {
                firstName.textContent = nameParts[0]
                lastName.textContent = nameParts[1]
            } else {
                lastName.textContent = author
            }
            
            authorEl.appendChild(firstName)
            authorEl.appendChild(lastName)
            titleInfo.appendChild(authorEl)
        }
        
        // Language
        val lang = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "lang")
        lang.textContent = book.metadata.languages.firstOrNull() ?: "en"
        titleInfo.appendChild(lang)
        
        // Genres
        book.metadata.tags.forEach { tag ->
            val genre = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "genre")
            genre.textContent = tag
            titleInfo.appendChild(genre)
        }
        
        // Annotation (from comments)
        if (book.metadata.comments != null) {
            val annotation = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "annotation")
            val p = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "p")
            p.textContent = book.metadata.comments
            annotation.appendChild(p)
            titleInfo.appendChild(annotation)
        }
        
        // Body
        val body = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "body")
        fb2.appendChild(body)
        
        val section = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "section")
        body.appendChild(section)
        
        // Convert spine items to FB2
        for (item in book.spine) {
            if (item.isXhtml()) {
                convertHtmlToFb2(doc, item.file, section)
            }
        }
        
        // Binary (images)
        val binarySection = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "binary")
        var imageId = 1
        book.manifest.values.filter { it.isImage() }.forEach { item ->
            val binary = doc.createElementNS("http://www.gribuser.ru/xml/fictionbook/2.0", "binary")
            binary.setAttribute("id", "image$imageId")
            binary.setAttribute("content-type", item.mediaType)
            val imageData = item.file.readBytes()
            val base64 = java.util.Base64.getEncoder().encodeToString(imageData)
            binary.textContent = base64
            fb2.appendChild(binary)
            imageId++
        }
        
        // Write XML
        writeXml(doc, outputFile)
    }
    
    private fun convertHtmlToFb2(doc: Document, htmlFile: File, parent: Element) {
        val htmlContent = htmlFile.readText()
        val htmlDoc = parseHtml(htmlContent)
        
        val bodyElements = htmlDoc.getElementsByTagName("body")
        if (bodyElements.length > 0) {
            val htmlBody = bodyElements.item(0) as Element
            convertElement(doc, htmlBody, parent)
        }
    }
    
    private fun convertElement(doc: Document, source: Element, parent: Element) {
        val ns = "http://www.gribuser.ru/xml/fictionbook/2.0"
        
        when (source.tagName.lowercase()) {
            "p" -> {
                val p = doc.createElementNS(ns, "p")
                convertChildren(doc, source, p)
                parent.appendChild(p)
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val subtitle = doc.createElementNS(ns, "subtitle")
                subtitle.textContent = source.textContent
                parent.appendChild(subtitle)
            }
            "strong", "b" -> {
                val strong = doc.createElementNS(ns, "strong")
                strong.textContent = source.textContent
                parent.appendChild(strong)
            }
            "em", "i" -> {
                val emphasis = doc.createElementNS(ns, "emphasis")
                emphasis.textContent = source.textContent
                parent.appendChild(emphasis)
            }
            "br" -> {
                val emptyLine = doc.createElementNS(ns, "empty-line")
                parent.appendChild(emptyLine)
            }
            "img" -> {
                val image = doc.createElementNS(ns, "image")
                val href = source.getAttribute("src")
                if (href.isNotEmpty()) {
                    image.setAttributeNS("http://www.w3.org/1999/xlink", "l:href", "#$href")
                }
                parent.appendChild(image)
            }
            else -> {
                convertChildren(doc, source, parent)
            }
        }
    }
    
    private fun convertChildren(doc: Document, source: Element, parent: Element) {
        val children = source.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when {
                child.nodeType == org.w3c.dom.Node.TEXT_NODE -> {
                    val text = child.textContent
                    if (text.isNotBlank()) {
                        // Add text directly to parent if it's a text node
                        if (parent.childNodes.length > 0 && parent.lastChild.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                            parent.lastChild.textContent += text
                        } else {
                            parent.appendChild(doc.createTextNode(text))
                        }
                    }
                }
                child.nodeType == org.w3c.dom.Node.ELEMENT_NODE -> {
                    convertElement(doc, child as Element, parent)
                }
            }
        }
    }
    
    private fun parseHtml(html: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isValidating = false
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        return builder.parse(java.io.ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    }
    
    private fun writeXml(doc: Document, file: File) {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        file.outputStream().use { out ->
            transformer.transform(DOMSource(doc), StreamResult(out))
        }
    }
}
