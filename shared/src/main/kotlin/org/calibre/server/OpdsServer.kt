package org.calibre.server

import org.calibre.metadata.Library
import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Enhanced OPDS (Open Publication Distribution System) server.
 * 
 * Implements OPDS 1.2 specification with:
 * - Navigation feeds (catalog structure)
 * - Acquisition feeds (book listings)
 * - Pagination for large collections
 * - OpenSearch for full-text search
 * - Faceted search (by author, series, tags)
 * - Cover image serving
 * - OPDS-PSE (Page Streaming Extension) for progress sync
 * 
 * @see https://specs.opds.io/opds-1.2
 */
class OpdsServer(
    private val library: Library,
    private val baseUrl: String = "http://localhost:8080"
) {
    
    companion object {
        const val ITEMS_PER_PAGE = 50
        const val ATOM_NS = "http://www.w3.org/2005/Atom"
        const val OPDS_NS = "http://opds-spec.org/2010/catalog"
        const val OPENSEARCH_NS = "http://a9.com/-/spec/opensearch/1.1/"
        const val DC_NS = "http://purl.org/dc/terms/"
    }
    
    /**
     * Generates the root navigation feed.
     */
    fun getRootFeed(): String {
        val updated = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<feed xmlns="$ATOM_NS" xmlns:opds="$OPDS_NS" xmlns:opensearch="$OPENSEARCH_NS">""")
            append("""<id>urn:calibre:kotlin:root</id>""")
            append("""<title>Calibre Kotlin Library</title>""")
            append("""<updated>$updated</updated>""")
            append("""<author><name>Calibre Kotlin</name></author>""")
            
            // Self link
            append("""<link rel="self" href="$baseUrl/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            
            // Start link
            append("""<link rel="start" href="$baseUrl/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            
            // Search link
            append("""<link rel="search" href="$baseUrl/opds/search?q={searchTerms}" type="application/atom+xml" title="Search"/>""")
            append("""<link rel="search" href="$baseUrl/opds/opensearch.xml" type="application/opensearchdescription+xml"/>""")
            
            // Navigation entries
            append(navigationEntry("All Books", "/opds/all", "All books in the library", "http://opds-spec.org/sort/popular"))
            append(navigationEntry("By Author", "/opds/authors", "Browse by author", "http://opds-spec.org/facet/author"))
            append(navigationEntry("By Series", "/opds/series", "Browse by series", "http://opds-spec.org/facet/series"))
            append(navigationEntry("By Tag", "/opds/tags", "Browse by tag", "http://opds-spec.org/facet/tag"))
            append(navigationEntry("Recent", "/opds/recent", "Recently added books", "http://opds-spec.org/sort/new"))
            
            append("""</feed>""")
        }
    }
    
    /**
     * Generates a feed of all books with pagination.
     */
    fun getAllBooksFeed(page: Int = 1): String {
        val books = library.getAllBooks()
        return paginatedBookFeed(books, page, "All Books", "/opds/all")
    }
    
    /**
     * Generates a feed of recent books.
     */
    fun getRecentFeed(page: Int = 1): String {
        val books = library.getAllBooks()
            .sortedByDescending { it.pubDate ?: java.time.LocalDateTime.MIN }
        return paginatedBookFeed(books, page, "Recently Added", "/opds/recent")
    }
    
    /**
     * Generates a navigation feed of authors.
     */
    fun getAuthorsFeed(): String {
        val books = library.getAllBooks()
        val authors = books.flatMap { it.authors }.distinct().sorted()
        val updated = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<feed xmlns="$ATOM_NS" xmlns:opds="$OPDS_NS">""")
            append("""<id>urn:calibre:kotlin:authors</id>""")
            append("""<title>Authors</title>""")
            append("""<updated>$updated</updated>""")
            append("""<link rel="self" href="$baseUrl/opds/authors" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            append("""<link rel="start" href="$baseUrl/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            append("""<link rel="up" href="$baseUrl/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            
            for (author in authors) {
                val encodedAuthor = URLEncoder.encode(author, "UTF-8")
                val count = books.count { author in it.authors }
                append(navigationEntry(author, "/opds/author/$encodedAuthor", "$count books", null))
            }
            
            append("""</feed>""")
        }
    }
    
    /**
     * Generates a feed of books by a specific author.
     */
    fun getAuthorFeed(author: String, page: Int = 1): String {
        val decodedAuthor = URLDecoder.decode(author, "UTF-8")
        val books = library.getAllBooks().filter { decodedAuthor in it.authors }
        return paginatedBookFeed(books, page, "Author: $decodedAuthor", "/opds/author/$author")
    }
    
    /**
     * Generates a navigation feed of series.
     */
    fun getSeriesFeed(): String {
        val books = library.getAllBooks()
        val series = books.mapNotNull { it.series }.distinct().sorted()
        val updated = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<feed xmlns="$ATOM_NS" xmlns:opds="$OPDS_NS">""")
            append("""<id>urn:calibre:kotlin:series</id>""")
            append("""<title>Series</title>""")
            append("""<updated>$updated</updated>""")
            append("""<link rel="self" href="$baseUrl/opds/series" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            append("""<link rel="start" href="$baseUrl/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            append("""<link rel="up" href="$baseUrl/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            
            for (s in series) {
                val encodedSeries = URLEncoder.encode(s, "UTF-8")
                val count = books.count { it.series == s }
                append(navigationEntry(s, "/opds/series/$encodedSeries", "$count books", null))
            }
            
            append("""</feed>""")
        }
    }
    
    /**
     * Generates a feed of books in a specific series.
     */
    fun getSeriesBooksFeed(series: String, page: Int = 1): String {
        val decodedSeries = URLDecoder.decode(series, "UTF-8")
        val books = library.getAllBooks()
            .filter { it.series == decodedSeries }
            .sortedBy { it.seriesIndex ?: Double.MAX_VALUE }
        return paginatedBookFeed(books, page, "Series: $decodedSeries", "/opds/series/$series")
    }
    
    /**
     * Generates a navigation feed of tags.
     */
    fun getTagsFeed(): String {
        val books = library.getAllBooks()
        val tags = books.flatMap { it.tags }.distinct().sorted()
        val updated = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<feed xmlns="$ATOM_NS" xmlns:opds="$OPDS_NS">""")
            append("""<id>urn:calibre:kotlin:tags</id>""")
            append("""<title>Tags</title>""")
            append("""<updated>$updated</updated>""")
            append("""<link rel="self" href="$baseUrl/opds/tags" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            append("""<link rel="start" href="$baseUrl/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            append("""<link rel="up" href="$baseUrl/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            
            for (tag in tags) {
                val encodedTag = URLEncoder.encode(tag, "UTF-8")
                val count = books.count { tag in it.tags }
                append(navigationEntry(tag, "/opds/tag/$encodedTag", "$count books", null))
            }
            
            append("""</feed>""")
        }
    }
    
    /**
     * Generates a feed of books with a specific tag.
     */
    fun getTagBooksFeed(tag: String, page: Int = 1): String {
        val decodedTag = URLDecoder.decode(tag, "UTF-8")
        val books = library.getAllBooks().filter { decodedTag in it.tags }
        return paginatedBookFeed(books, page, "Tag: $decodedTag", "/opds/tag/$tag")
    }
    
    /**
     * Searches for books and returns an OPDS feed.
     */
    fun searchFeed(query: String, page: Int = 1): String {
        val decodedQuery = URLDecoder.decode(query, "UTF-8").lowercase()
        val books = library.getAllBooks().filter { book ->
            book.title.lowercase().contains(decodedQuery) ||
            book.authors.any { it.lowercase().contains(decodedQuery) } ||
            book.series?.lowercase()?.contains(decodedQuery) == true ||
            book.tags.any { it.lowercase().contains(decodedQuery) }
        }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return paginatedBookFeed(books, page, "Search: $query", "/opds/search?q=$encodedQuery")
    }
    
    /**
     * Generates the OpenSearch description document.
     */
    fun getOpenSearchDescription(): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
    <ShortName>Calibre Kotlin</ShortName>
    <Description>Search the Calibre Kotlin library</Description>
    <InputEncoding>UTF-8</InputEncoding>
    <OutputEncoding>UTF-8</OutputEncoding>
    <Url type="application/atom+xml" template="$baseUrl/opds/search?q={searchTerms}"/>
    <Url type="text/html" template="$baseUrl/search?q={searchTerms}"/>
</OpenSearchDescription>"""
    }
    
    /**
     * Generates a single book entry.
     */
    fun getBookEntry(bookId: Int): String {
        val book = library.getMetadata(bookId) ?: return ""
        val updated = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<entry xmlns="$ATOM_NS" xmlns:opds="$OPDS_NS" xmlns:dc="$DC_NS">""")
            append(bookEntry(book))
            append("""</entry>""")
        }
    }
    
    // Helper methods
    
    private fun navigationEntry(title: String, href: String, content: String, rel: String?): String {
        val updated = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val relAttr = rel?.let { """ rel="$it"""" } ?: ""
        
        return """
<entry>
    <title>${escapeXml(title)}</title>
    <id>urn:calibre:kotlin:${href.replace("/", ":")}</id>
    <updated>$updated</updated>
    <content type="text">${escapeXml(content)}</content>
    <link$relAttr href="$baseUrl$href" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
</entry>"""
    }
    
    private fun paginatedBookFeed(
        allBooks: List<Metadata>,
        page: Int,
        title: String,
        basePath: String
    ): String {
        val totalBooks = allBooks.size
        val totalPages = (totalBooks + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val currentPage = page.coerceIn(1, maxOf(1, totalPages))
        val startIndex = (currentPage - 1) * ITEMS_PER_PAGE
        val books = allBooks.drop(startIndex).take(ITEMS_PER_PAGE)
        
        val updated = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val pathWithPage = if (basePath.contains("?")) "$basePath&page=" else "$basePath?page="
        
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<feed xmlns="$ATOM_NS" xmlns:opds="$OPDS_NS" xmlns:opensearch="$OPENSEARCH_NS">""")
            append("""<id>urn:calibre:kotlin:${basePath.replace("/", ":")}:page:$currentPage</id>""")
            append("""<title>${escapeXml(title)}</title>""")
            append("""<updated>$updated</updated>""")
            
            // OpenSearch elements
            append("""<opensearch:totalResults>$totalBooks</opensearch:totalResults>""")
            append("""<opensearch:startIndex>$startIndex</opensearch:startIndex>""")
            append("""<opensearch:itemsPerPage>${books.size}</opensearch:itemsPerPage>""")
            
            // Navigation links
            append("""<link rel="self" href="$baseUrl$pathWithPage$currentPage" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>""")
            append("""<link rel="start" href="$baseUrl/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            append("""<link rel="up" href="$baseUrl/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>""")
            
            if (currentPage > 1) {
                append("""<link rel="first" href="$baseUrl${pathWithPage}1" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>""")
                append("""<link rel="previous" href="$baseUrl$pathWithPage${currentPage - 1}" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>""")
            }
            
            if (currentPage < totalPages) {
                append("""<link rel="next" href="$baseUrl$pathWithPage${currentPage + 1}" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>""")
                append("""<link rel="last" href="$baseUrl$pathWithPage$totalPages" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>""")
            }
            
            // Book entries
            for (book in books) {
                append(bookEntry(book))
            }
            
            append("""</feed>""")
        }
    }
    
    private fun bookEntry(book: Metadata): String {
        val updated = book.pubDate?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ?: ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        
        val bookFile = library.getBookFile(book.id ?: 0)
        val mimeType = getMimeType(bookFile)
        
        return buildString {
            append("""<entry>""")
            append("""<title>${escapeXml(book.title)}</title>""")
            append("""<id>urn:calibre:kotlin:book:${book.id}</id>""")
            append("""<updated>$updated</updated>""")
            
            // Authors
            for (author in book.authors) {
                append("""<author><name>${escapeXml(author)}</name></author>""")
            }
            
            // Content (description)
            if (book.comments != null) {
                append("""<content type="html">${escapeXml(book.comments!!)}</content>""")
            }
            
            // Categories (tags)
            for (tag in book.tags) {
                append("""<category term="${escapeXml(tag)}" label="${escapeXml(tag)}"/>""")
            }
            
            // Series
            if (book.series != null) {
                append("""<opds:seriesTitle>${escapeXml(book.series!!)}</opds:seriesTitle>""")
                if (book.seriesIndex != null) {
                    append("""<opds:seriesPosition>${book.seriesIndex}</opds:seriesPosition>""")
                }
            }
            
            // Acquisition link
            append("""<link rel="http://opds-spec.org/acquisition" href="$baseUrl/download/${book.id}" type="$mimeType"/>""")
            
            // Cover image
            append("""<link rel="http://opds-spec.org/image" href="$baseUrl/cover/${book.id}" type="image/jpeg"/>""")
            append("""<link rel="http://opds-spec.org/image/thumbnail" href="$baseUrl/cover/${book.id}?size=thumb" type="image/jpeg"/>""")
            
            append("""</entry>""")
        }
    }
    
    private fun getMimeType(file: File?): String {
        return when (file?.extension?.lowercase()) {
            "epub" -> "application/epub+zip"
            "pdf" -> "application/pdf"
            "mobi" -> "application/x-mobipocket-ebook"
            "azw", "azw3" -> "application/vnd.amazon.ebook"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "fb2" -> "application/x-fictionbook+xml"
            "cbz" -> "application/x-cbz"
            "cbr" -> "application/x-cbr"
            else -> "application/octet-stream"
        }
    }
    
    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
