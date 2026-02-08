package org.calibre.conversion

import org.calibre.metadata.Metadata
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * FB2 (FictionBook) Input plugin.
 * FB2 is a popular e-book format in Russia and Eastern Europe.
 */
class Fb2Input : InputPlugin {
    override val name = "FB2 Input"
    override val fileTypes = setOf("fb2")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        val doc = parseFb2(inputFile)
        val root = doc.documentElement
        
        // Extract metadata
        val titleInfo = root.getElementsByTagName("title-info").item(0) as? Element
            ?: throw Exception("FB2: title-info not found")
        
        val title = getTextContent(titleInfo, "book-title") ?: "Unknown"
        val authors = mutableListOf<String>()
        val authorNodes = titleInfo.getElementsByTagName("author")
        for (i in 0 until authorNodes.length) {
            val author = authorNodes.item(i) as? Element ?: continue
            val firstName = getTextContent(author, "first-name") ?: ""
            val lastName = getTextContent(author, "last-name") ?: ""
            val middleName = getTextContent(author, "middle-name") ?: ""
            val authorName = listOf(firstName, middleName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
            if (authorName.isNotEmpty()) {
                authors.add(authorName)
            }
        }
        
        val genre = getTextContent(titleInfo, "genre")
        val annotation = getTextContent(titleInfo, "annotation")
        val date = getTextContent(titleInfo, "date")
        val publisher = getTextContent(titleInfo, "publisher")
        val isbn = getTextContent(titleInfo, "isbn")
        val language = titleInfo.getAttribute("xml:lang").takeIf { it.isNotEmpty() } ?: "ru"
        
        val metadata = Metadata(
            title = title,
            authors = authors,
            tags = if (genre != null) mutableListOf(genre) else mutableListOf(),
            comments = annotation,
            publisher = publisher,
            isbn = isbn,
            languages = mutableListOf(language)
        )
        
        val book = OebBook(metadata = metadata)
        
        // Extract body sections
        val bodyNodes = root.getElementsByTagName("body")
        val sectionsDir = File(workDir, "sections")
        sectionsDir.mkdirs()
        
        var sectionIndex = 0
        for (i in 0 until bodyNodes.length) {
            val body = bodyNodes.item(i) as? Element ?: continue
            val bodyName = body.getAttribute("name").takeIf { it.isNotEmpty() } ?: "body"
            
            // Process sections within body
            val sections = body.getElementsByTagName("section")
            if (sections.length == 0) {
                // No sections, treat entire body as one chapter
                val html = convertFb2ElementToHtml(body)
                val htmlFile = File(sectionsDir, "section_${sectionIndex++}.xhtml")
                htmlFile.writeText(html)
                
                val item = OebItem(
                    id = "section_${sectionIndex - 1}",
                    href = htmlFile.name,
                    mediaType = "application/xhtml+xml",
                    file = htmlFile
                )
                book.manifest[item.id] = item
                book.spine.add(item)
            } else {
                // Process each section
                for (j in 0 until sections.length) {
                    val section = sections.item(j) as? Element ?: continue
                    val html = convertFb2ElementToHtml(section)
                    val htmlFile = File(sectionsDir, "section_${sectionIndex++}.xhtml")
                    htmlFile.writeText(html)
                    
                    val item = OebItem(
                        id = "section_${sectionIndex - 1}",
                        href = htmlFile.name,
                        mediaType = "application/xhtml+xml",
                        file = htmlFile
                    )
                    book.manifest[item.id] = item
                    book.spine.add(item)
                }
            }
        }
        
        // Extract images
        val binaryNodes = root.getElementsByTagName("binary")
        val imagesDir = File(workDir, "images")
        imagesDir.mkdirs()
        
        for (i in 0 until binaryNodes.length) {
            val binary = binaryNodes.item(i) as? Element ?: continue
            val id = binary.getAttribute("id")
            val contentType = binary.getAttribute("content-type")
            val content = binary.textContent
            
            if (id.isNotEmpty() && contentType.startsWith("image/")) {
                val imageBytes = java.util.Base64.getDecoder().decode(content)
                val extension = when (contentType) {
                    "image/jpeg", "image/jpg" -> "jpg"
                    "image/png" -> "png"
                    "image/gif" -> "gif"
                    else -> "img"
                }
                val imageFile = File(imagesDir, "$id.$extension")
                imageFile.writeBytes(imageBytes)
                
                val item = OebItem(
                    id = id,
                    href = "images/${imageFile.name}",
                    mediaType = contentType,
                    file = imageFile
                )
                book.manifest[id] = item
            }
        }
        
        return book
    }
    
    private fun parseFb2(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(file)
    }
    
    private fun getTextContent(element: Element, tagName: String): String? {
        val nodes = element.getElementsByTagName(tagName)
        return if (nodes.length > 0) {
            nodes.item(0)?.textContent?.trim()
        } else null
    }
    
    private fun convertFb2ElementToHtml(element: Element): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Section</title></head>
<body>
""")
        
        convertFb2NodeToHtml(element, sb)
        
        sb.append("</body></html>")
        return sb.toString()
    }
    
    private fun convertFb2NodeToHtml(node: Node, sb: StringBuilder) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                val element = node as Element
                when (element.tagName) {
                    "title" -> {
                        sb.append("<h1>")
                        processChildren(element, sb)
                        sb.append("</h1>")
                    }
                    "p" -> {
                        sb.append("<p>")
                        processChildren(element, sb)
                        sb.append("</p>")
                    }
                    "emphasis" -> {
                        sb.append("<em>")
                        processChildren(element, sb)
                        sb.append("</em>")
                    }
                    "strong" -> {
                        sb.append("<strong>")
                        processChildren(element, sb)
                        sb.append("</strong>")
                    }
                    "image" -> {
                        val href = element.getAttribute("l:href") ?: element.getAttribute("href")
                        if (href.startsWith("#")) {
                            val id = href.substring(1)
                            sb.append("""<img src="images/$id.jpg" alt="Image"/>""")
                        }
                    }
                    "section" -> {
                        sb.append("<div class='section'>")
                        processChildren(element, sb)
                        sb.append("</div>")
                    }
                    else -> {
                        processChildren(element, sb)
                    }
                }
            }
            Node.TEXT_NODE -> {
                sb.append(escapeHtml(node.textContent))
            }
        }
    }
    
    private fun processChildren(element: Element, sb: StringBuilder) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            convertFb2NodeToHtml(children.item(i), sb)
        }
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
