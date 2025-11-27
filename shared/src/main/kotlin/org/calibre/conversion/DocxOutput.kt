package org.calibre.conversion

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.io.StringWriter

class DocxOutput : OutputPlugin {
    override val name = "DOCX Output"
    override val fileType = "docx"

    override fun convert(book: OebBook, outputFile: File) {
        val tempDir = File(outputFile.parentFile, "docx_build_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            // Create DOCX structure
            val wordDir = File(tempDir, "word")
            wordDir.mkdirs()
            val relsDir = File(tempDir, "_rels")
            relsDir.mkdirs()
            val wordRelsDir = File(wordDir, "_rels")
            wordRelsDir.mkdirs()
            
            // Create document.xml
            val documentFile = File(wordDir, "document.xml")
            createDocumentXml(book, documentFile)
            
            // Create document.xml.rels
            val documentRelsFile = File(wordRelsDir, "document.xml.rels")
            createDocumentRels(book, documentRelsFile)
            
            // Create [Content_Types].xml
            val contentTypesFile = File(tempDir, "[Content_Types].xml")
            createContentTypes(book, contentTypesFile)
            
            // Create _rels/.rels
            val relsFile = File(relsDir, ".rels")
            createRels(relsFile)
            
            // Create styles.xml
            val stylesFile = File(wordDir, "styles.xml")
            createStyles(stylesFile)
            
            // Copy images to word/media/
            val mediaDir = File(wordDir, "media")
            mediaDir.mkdirs()
            val imageMap = mutableMapOf<String, String>() // old href -> new path
            book.manifest.values.filter { it.isImage() }.forEach { item ->
                val mediaFile = File(mediaDir, item.file.name)
                item.file.copyTo(mediaFile, overwrite = true)
                imageMap[item.href] = "media/${item.file.name}"
            }
            
            // Package as DOCX (ZIP)
            packageDocx(tempDir, outputFile)
            
        } finally {
            tempDir.deleteRecursively()
        }
    }
    
    private fun createDocumentXml(book: OebBook, outputFile: File) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val body = doc.createElementNS(w, "w:body")
        doc.appendChild(body)
        
        // Title
        val titlePara = doc.createElementNS(w, "w:p")
        val titleRun = doc.createElementNS(w, "w:r")
        val titleText = doc.createElementNS(w, "w:t")
        titleText.textContent = book.metadata.title
        titleRun.appendChild(titleText)
        titlePara.appendChild(titleRun)
        body.appendChild(titlePara)
        
        // Authors
        if (book.metadata.authors.isNotEmpty()) {
            val authorPara = doc.createElementNS(w, "w:p")
            val authorRun = doc.createElementNS(w, "w:r")
            val authorText = doc.createElementNS(w, "w:t")
            authorText.textContent = book.metadata.authors.joinToString(", ")
            authorRun.appendChild(authorText)
            authorPara.appendChild(authorRun)
            body.appendChild(authorPara)
        }
        
        // Content from spine
        for (item in book.spine) {
            if (item.isXhtml()) {
                convertHtmlToWord(doc, item.file, body, w)
            }
        }
        
        // Add section break at end
        val sectPara = doc.createElementNS(w, "w:p")
        val sectPr = doc.createElementNS(w, "w:sectPr")
        sectPara.appendChild(sectPr)
        body.appendChild(sectPara)
        
        writeXml(doc, outputFile)
    }
    
    private fun convertHtmlToWord(doc: Document, htmlFile: File, body: Element, w: String) {
        val htmlContent = htmlFile.readText()
        val htmlDoc = parseHtml(htmlContent)
        
        val bodyElements = htmlDoc.getElementsByTagName("body")
        if (bodyElements.length > 0) {
            val htmlBody = bodyElements.item(0) as Element
            convertElement(doc, htmlBody, body, w)
        }
    }
    
    private fun convertElement(doc: Document, source: Element, parent: Element, w: String) {
        when (source.tagName.lowercase()) {
            "p" -> {
                val para = doc.createElementNS(w, "w:p")
                convertChildren(doc, source, para, w)
                parent.appendChild(para)
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val para = doc.createElementNS(w, "w:p")
                val run = doc.createElementNS(w, "w:r")
                val text = doc.createElementNS(w, "w:t")
                text.textContent = source.textContent
                run.appendChild(text)
                para.appendChild(run)
                parent.appendChild(para)
            }
            "br" -> {
                val para = doc.createElementNS(w, "w:p")
                val br = doc.createElementNS(w, "w:r")
                val brElement = doc.createElementNS(w, "w:br")
                br.appendChild(brElement)
                para.appendChild(br)
                parent.appendChild(para)
            }
            "img" -> {
                val para = doc.createElementNS(w, "w:p")
                val run = doc.createElementNS(w, "w:r")
                val drawing = doc.createElementNS(w, "w:drawing")
                // Simplified - would need proper image embedding
                run.appendChild(drawing)
                para.appendChild(run)
                parent.appendChild(para)
            }
            "strong", "b" -> {
                val run = doc.createElementNS(w, "w:r")
                val rPr = doc.createElementNS(w, "w:rPr")
                val b = doc.createElementNS(w, "w:b")
                rPr.appendChild(b)
                run.appendChild(rPr)
                val text = doc.createElementNS(w, "w:t")
                text.textContent = source.textContent
                run.appendChild(text)
                val para = doc.createElementNS(w, "w:p")
                para.appendChild(run)
                parent.appendChild(para)
            }
            "em", "i" -> {
                val run = doc.createElementNS(w, "w:r")
                val rPr = doc.createElementNS(w, "w:rPr")
                val i = doc.createElementNS(w, "w:i")
                rPr.appendChild(i)
                run.appendChild(rPr)
                val text = doc.createElementNS(w, "w:t")
                text.textContent = source.textContent
                run.appendChild(text)
                val para = doc.createElementNS(w, "w:p")
                para.appendChild(run)
                parent.appendChild(para)
            }
            else -> {
                convertChildren(doc, source, parent, w)
            }
        }
    }
    
    private fun convertChildren(doc: Document, source: Element, parent: Element, w: String) {
        val children = source.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when {
                child.nodeType == org.w3c.dom.Node.TEXT_NODE -> {
                    val text = child.textContent
                    if (text.isNotBlank()) {
                        val run = doc.createElementNS(w, "w:r")
                        val textNode = doc.createElementNS(w, "w:t")
                        textNode.textContent = text
                        run.appendChild(textNode)
                        val para = doc.createElementNS(w, "w:p")
                        para.appendChild(run)
                        parent.appendChild(para)
                    }
                }
                child.nodeType == org.w3c.dom.Node.ELEMENT_NODE -> {
                    convertElement(doc, child as Element, parent, w)
                }
            }
        }
    }
    
    private fun createDocumentRels(book: OebBook, outputFile: File) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val relationships = doc.createElementNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationships")
        doc.appendChild(relationships)
        
        var relId = 1
        book.manifest.values.filter { it.isImage() }.forEach { item ->
            val rel = doc.createElementNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationship")
            rel.setAttribute("Id", "rId$relId")
            rel.setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image")
            rel.setAttribute("Target", "media/${item.file.name}")
            relationships.appendChild(rel)
            relId++
        }
        
        writeXml(doc, outputFile)
    }
    
    private fun createContentTypes(book: OebBook, outputFile: File) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val types = doc.createElementNS("http://schemas.openxmlformats.org/package/2006/content-types", "Types")
        doc.appendChild(types)
        
        // Defaults
        addOverride(doc, types, "/word/document.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml")
        addOverride(doc, types, "/word/styles.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml")
        
        // Image types
        book.manifest.values.filter { it.isImage() }.forEach { item ->
            val ext = item.file.extension.lowercase()
            val mime = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                else -> "image/png"
            }
            addDefault(doc, types, ".$ext", mime)
        }
        
        writeXml(doc, outputFile)
    }
    
    private fun addOverride(doc: Document, parent: Element, partName: String, contentType: String) {
        val override = doc.createElementNS("http://schemas.openxmlformats.org/package/2006/content-types", "Override")
        override.setAttribute("PartName", partName)
        override.setAttribute("ContentType", contentType)
        parent.appendChild(override)
    }
    
    private fun addDefault(doc: Document, parent: Element, extension: String, contentType: String) {
        val default = doc.createElementNS("http://schemas.openxmlformats.org/package/2006/content-types", "Default")
        default.setAttribute("Extension", extension)
        default.setAttribute("ContentType", contentType)
        parent.appendChild(default)
    }
    
    private fun createRels(outputFile: File) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val relationships = doc.createElementNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationships")
        doc.appendChild(relationships)
        
        val rel = doc.createElementNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationship")
        rel.setAttribute("Id", "rId1")
        rel.setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument")
        rel.setAttribute("Target", "word/document.xml")
        relationships.appendChild(rel)
        
        writeXml(doc, outputFile)
    }
    
    private fun createStyles(outputFile: File) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val styles = doc.createElementNS(w, "w:styles")
        doc.appendChild(styles)
        
        writeXml(doc, outputFile)
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
    
    private fun packageDocx(tempDir: File, outputFile: File) {
        outputFile.outputStream().use { out ->
            ZipOutputStream(out).use { zos ->
                tempDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entryName = tempDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
        }
    }
}
