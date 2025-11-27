package org.calibre.news

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * RSS/Atom feed parser.
 */
data class FeedItem(
    val title: String,
    val link: String,
    val description: String,
    val pubDate: LocalDateTime?,
    val author: String? = null,
    val guid: String? = null
)

class RssParser {
    private val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val dateFormats = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    )
    
    fun parseFeed(feedUrl: String): List<FeedItem> {
        val url = URL(feedUrl)
        url.openStream().use { input ->
            return parseFeed(input)
        }
    }
    
    fun parseFeed(input: InputStream): List<FeedItem> {
        val doc = docBuilder.parse(input)
        val root = doc.documentElement
        
        return when (root.tagName.lowercase()) {
            "rss" -> parseRss(doc)
            "feed" -> parseAtom(doc)
            else -> emptyList()
        }
    }
    
    private fun parseRss(doc: Document): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
        val itemNodes = doc.getElementsByTagName("item")
        
        for (i in 0 until itemNodes.length) {
            val item = itemNodes.item(i) as? Element ?: continue
            val title = getTextContent(item, "title") ?: continue
            val link = getTextContent(item, "link") ?: continue
            val description = getTextContent(item, "description") ?: ""
            val pubDateStr = getTextContent(item, "pubDate")
            val guid = getTextContent(item, "guid")
            val author = getTextContent(item, "author") ?: getTextContent(item, "dc:creator")
            
            val pubDate = pubDateStr?.let { parseDate(it) }
            
            items.add(FeedItem(
                title = title,
                link = link,
                description = description,
                pubDate = pubDate,
                author = author,
                guid = guid
            ))
        }
        
        return items
    }
    
    private fun parseAtom(doc: Document): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
        val entryNodes = doc.getElementsByTagName("entry")
        
        for (i in 0 until entryNodes.length) {
            val entry = entryNodes.item(i) as? Element ?: continue
            val title = getTextContent(entry, "title") ?: continue
            val link = getLinkHref(entry) ?: continue
            val summary = getTextContent(entry, "summary") ?: getTextContent(entry, "content") ?: ""
            val updatedStr = getTextContent(entry, "updated") ?: getTextContent(entry, "published")
            val author = getTextContent(entry, "author/name")
            val id = getTextContent(entry, "id")
            
            val pubDate = updatedStr?.let { parseDate(it) }
            
            items.add(FeedItem(
                title = title,
                link = link,
                description = summary,
                pubDate = pubDate,
                author = author,
                guid = id
            ))
        }
        
        return items
    }
    
    private fun getTextContent(element: Element, tagName: String): String? {
        val nodes = element.getElementsByTagName(tagName)
        return if (nodes.length > 0) {
            nodes.item(0)?.textContent?.trim()
        } else null
    }
    
    private fun getLinkHref(element: Element): String? {
        val links = element.getElementsByTagName("link")
        for (i in 0 until links.length) {
            val link = links.item(i) as? Element
            val href = link?.getAttribute("href")
            if (href != null) return href
        }
        return null
    }
    
    private fun parseDate(dateStr: String): LocalDateTime? {
        for (format in dateFormats) {
            try {
                return LocalDateTime.parse(dateStr, format)
            } catch (e: DateTimeParseException) {
                // Try next format
            }
        }
        return null
    }
}
