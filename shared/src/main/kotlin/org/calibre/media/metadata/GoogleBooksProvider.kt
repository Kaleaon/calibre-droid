package org.calibre.media.metadata

import org.calibre.media.*
import org.calibre.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Google Books API metadata provider for ebooks.
 * 
 * Provides metadata for books including:
 * - Title, authors, publisher
 * - ISBN, description, categories
 * - Page count, publication date
 * - Cover images
 * - Ratings and reviews
 * 
 * API documentation: https://developers.google.com/books/docs/v1/using
 */
class GoogleBooksProvider(
    private val apiKey: String = ""  // Optional API key for higher rate limits
) : MetadataProvider {
    
    override val name = "Google Books"
    
    companion object {
        private const val API_BASE = "https://www.googleapis.com/books/v1"
    }
    
    override fun supports(type: MediaType): Boolean {
        return type == MediaType.EBOOK
    }
    
    override fun fetchMetadata(item: MediaItem): Map<String, Any>? {
        return when (item) {
            is BookItem -> fetchBookMetadata(item)
            else -> null
        }
    }
    
    private fun fetchBookMetadata(book: BookItem): Map<String, Any>? {
        // Try searching by ISBN first if available
        if (book.isbn != null || book.isbn13 != null) {
            val isbn = book.isbn13 ?: book.isbn
            val searchResults = searchByIsbn(isbn!!)
            if (searchResults.isNotEmpty()) {
                val volumeId = searchResults.first()["id"] as? String
                if (volumeId != null) {
                    return fetchVolumeDetails(volumeId)
                }
            }
        }
        
        // Search by title and author
        val query = buildString {
            append(book.title)
            if (book.authors.isNotEmpty()) {
                append(" inauthor:${book.authors.first()}")
            }
        }
        
        val searchResults = searchBooks(query)
        if (searchResults.isEmpty()) return null
        
        val volumeId = searchResults.first()["id"] as? String ?: return null
        return fetchVolumeDetails(volumeId)
    }
    
    private fun fetchVolumeDetails(volumeId: String): Map<String, Any>? {
        var url = "$API_BASE/volumes/$volumeId"
        if (apiKey.isNotEmpty()) {
            url += "?key=$apiKey"
        }
        
        val response = httpGet(url) ?: return null
        return parseVolumeResponse(response)
    }
    
    private fun parseVolumeResponse(json: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        result["googleBooksId"] = extractString(json, "\"id\"") ?: ""
        
        // Volume info section
        val volumeInfo = extractSection(json, "\"volumeInfo\"")
        if (volumeInfo != null) {
            result["title"] = extractString(volumeInfo, "\"title\"") ?: ""
            result["subtitle"] = extractString(volumeInfo, "\"subtitle\"")
            result["authors"] = parseStringArray(volumeInfo, "\"authors\"")
            result["publisher"] = extractString(volumeInfo, "\"publisher\"")
            result["publishedDate"] = parseDate(extractString(volumeInfo, "\"publishedDate\""))
            result["description"] = extractString(volumeInfo, "\"description\"")
            result["pageCount"] = extractInt(volumeInfo, "\"pageCount\"")
            result["categories"] = parseStringArray(volumeInfo, "\"categories\"")
            result["averageRating"] = extractDouble(volumeInfo, "\"averageRating\"")
            result["ratingsCount"] = extractInt(volumeInfo, "\"ratingsCount\"")
            result["language"] = extractString(volumeInfo, "\"language\"")
            result["previewLink"] = extractString(volumeInfo, "\"previewLink\"")
            result["infoLink"] = extractString(volumeInfo, "\"infoLink\"")
            
            // ISBN identifiers
            val identifiers = extractSection(volumeInfo, "\"industryIdentifiers\"")
            if (identifiers != null) {
                result["isbn10"] = extractIsbn(identifiers, "ISBN_10")
                result["isbn13"] = extractIsbn(identifiers, "ISBN_13")
            }
            
            // Cover images
            val imageLinks = extractSection(volumeInfo, "\"imageLinks\"")
            if (imageLinks != null) {
                result["thumbnailUrl"] = extractString(imageLinks, "\"thumbnail\"")?.replace("http:", "https:")
                result["smallThumbnailUrl"] = extractString(imageLinks, "\"smallThumbnail\"")?.replace("http:", "https:")
                result["coverUrl"] = extractString(imageLinks, "\"medium\"")?.replace("http:", "https:")
                    ?: extractString(imageLinks, "\"large\"")?.replace("http:", "https:")
                    ?: result["thumbnailUrl"]
            }
        }
        
        // Access info
        val accessInfo = extractSection(json, "\"accessInfo\"")
        if (accessInfo != null) {
            result["country"] = extractString(accessInfo, "\"country\"")
            result["viewability"] = extractString(accessInfo, "\"viewability\"")
            result["embeddable"] = extractBoolean(accessInfo, "\"embeddable\"")
            result["publicDomain"] = extractBoolean(accessInfo, "\"publicDomain\"")
            
            val epub = extractSection(accessInfo, "\"epub\"")
            if (epub != null) {
                result["epubAvailable"] = extractBoolean(epub, "\"isAvailable\"")
            }
            
            val pdf = extractSection(accessInfo, "\"pdf\"")
            if (pdf != null) {
                result["pdfAvailable"] = extractBoolean(pdf, "\"isAvailable\"")
            }
        }
        
        return result
    }
    
    override fun search(query: String, type: MediaType): List<Map<String, Any>> {
        return when (type) {
            MediaType.EBOOK -> searchBooks(query)
            else -> emptyList()
        }
    }
    
    /**
     * Search for books.
     */
    fun searchBooks(query: String, maxResults: Int = 10): List<Map<String, Any>> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        var url = "$API_BASE/volumes?q=$encodedQuery&maxResults=$maxResults"
        if (apiKey.isNotEmpty()) {
            url += "&key=$apiKey"
        }
        
        val response = httpGet(url) ?: return emptyList()
        return parseSearchResults(response)
    }
    
    /**
     * Search by ISBN.
     */
    fun searchByIsbn(isbn: String): List<Map<String, Any>> {
        val cleanIsbn = isbn.replace("-", "").replace(" ", "")
        return searchBooks("isbn:$cleanIsbn", 1)
    }
    
    /**
     * Search by author.
     */
    fun searchByAuthor(author: String, maxResults: Int = 20): List<Map<String, Any>> {
        return searchBooks("inauthor:\"$author\"", maxResults)
    }
    
    /**
     * Search by publisher.
     */
    fun searchByPublisher(publisher: String, maxResults: Int = 20): List<Map<String, Any>> {
        return searchBooks("inpublisher:\"$publisher\"", maxResults)
    }
    
    /**
     * Search by category/subject.
     */
    fun searchByCategory(category: String, maxResults: Int = 20): List<Map<String, Any>> {
        return searchBooks("subject:\"$category\"", maxResults)
    }
    
    private fun parseSearchResults(json: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        
        val totalItems = extractInt(json, "\"totalItems\"") ?: 0
        if (totalItems == 0) return results
        
        val itemsSection = extractSection(json, "\"items\"") ?: return results
        
        var depth = 0
        var objectStart = -1
        
        for (i in itemsSection.indices) {
            when (itemsSection[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = itemsSection.substring(objectStart, i + 1)
                        val item = parseSearchResultItem(obj)
                        results.add(item)
                    }
                }
            }
        }
        
        return results
    }
    
    private fun parseSearchResultItem(json: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        result["id"] = extractString(json, "\"id\"") ?: ""
        
        val volumeInfo = extractSection(json, "\"volumeInfo\"")
        if (volumeInfo != null) {
            result["title"] = extractString(volumeInfo, "\"title\"") ?: ""
            result["subtitle"] = extractString(volumeInfo, "\"subtitle\"")
            result["authors"] = parseStringArray(volumeInfo, "\"authors\"")
            result["publisher"] = extractString(volumeInfo, "\"publisher\"")
            result["publishedDate"] = extractString(volumeInfo, "\"publishedDate\"")
            result["description"] = extractString(volumeInfo, "\"description\"")
            result["pageCount"] = extractInt(volumeInfo, "\"pageCount\"")
            result["averageRating"] = extractDouble(volumeInfo, "\"averageRating\"")
            
            val imageLinks = extractSection(volumeInfo, "\"imageLinks\"")
            if (imageLinks != null) {
                result["thumbnailUrl"] = extractString(imageLinks, "\"thumbnail\"")?.replace("http:", "https:")
            }
            
            val identifiers = extractSection(volumeInfo, "\"industryIdentifiers\"")
            if (identifiers != null) {
                result["isbn10"] = extractIsbn(identifiers, "ISBN_10")
                result["isbn13"] = extractIsbn(identifiers, "ISBN_13")
            }
        }
        
        return result
    }
    
    /**
     * Get books by a specific author.
     */
    fun getAuthorBooks(authorName: String): List<Map<String, Any>> {
        return searchByAuthor(authorName, 40)
    }
    
    /**
     * Get similar books (based on subject/category).
     */
    fun getSimilarBooks(volumeId: String): List<Map<String, Any>> {
        val details = fetchVolumeDetails(volumeId) ?: return emptyList()
        val categories = (details["categories"] as? List<*>)?.filterIsInstance<String>()
        
        return if (categories?.isNotEmpty() == true) {
            searchByCategory(categories.first())
        } else {
            emptyList()
        }
    }
    
    // Helper methods
    
    private fun parseStringArray(json: String, key: String): List<String> {
        val section = extractSection(json, key) ?: return emptyList()
        val results = mutableListOf<String>()
        
        var i = 0
        while (i < section.length) {
            val start = section.indexOf('"', i)
            if (start < 0) break
            
            val end = findStringEnd(section, start + 1)
            if (end > start) {
                results.add(section.substring(start + 1, end))
                i = end + 1
            } else {
                break
            }
        }
        
        return results
    }
    
    private fun extractIsbn(json: String, type: String): String? {
        val typeIndex = json.indexOf("\"$type\"")
        if (typeIndex < 0) return null
        
        // Find the identifier value near this type
        val searchStart = maxOf(0, typeIndex - 50)
        val searchEnd = minOf(json.length, typeIndex + 100)
        val section = json.substring(searchStart, searchEnd)
        
        return extractString(section, "\"identifier\"")
    }
    
    private fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr == null) return null
        
        return try {
            when (dateStr.length) {
                4 -> LocalDate.of(dateStr.toInt(), 1, 1) // Year only
                7 -> LocalDate.parse("$dateStr-01") // Year-Month
                else -> LocalDate.parse(dateStr) // Full date
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun httpGet(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                Logger.warn("Google Books API error: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Logger.error("Google Books request failed: ${e.message}")
            null
        }
    }
    
    // JSON parsing helpers
    
    private fun extractString(json: String, key: String): String? {
        val keyIndex = json.indexOf(key)
        if (keyIndex < 0) return null
        
        val colonIndex = json.indexOf(':', keyIndex)
        if (colonIndex < 0) return null
        
        var valueStart = colonIndex + 1
        while (valueStart < json.length && json[valueStart].isWhitespace()) valueStart++
        
        if (valueStart >= json.length) return null
        
        return if (json[valueStart] == '"') {
            val valueEnd = findStringEnd(json, valueStart + 1)
            if (valueEnd > valueStart) {
                json.substring(valueStart + 1, valueEnd)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\/", "/")
            } else null
        } else null
    }
    
    private fun extractInt(json: String, key: String): Int? {
        val keyIndex = json.indexOf(key)
        if (keyIndex < 0) return null
        
        val colonIndex = json.indexOf(':', keyIndex)
        if (colonIndex < 0) return null
        
        var valueStart = colonIndex + 1
        while (valueStart < json.length && json[valueStart].isWhitespace()) valueStart++
        
        var valueEnd = valueStart
        while (valueEnd < json.length && (json[valueEnd].isDigit() || json[valueEnd] == '-')) valueEnd++
        
        return if (valueEnd > valueStart) {
            json.substring(valueStart, valueEnd).toIntOrNull()
        } else null
    }
    
    private fun extractDouble(json: String, key: String): Double? {
        val keyIndex = json.indexOf(key)
        if (keyIndex < 0) return null
        
        val colonIndex = json.indexOf(':', keyIndex)
        if (colonIndex < 0) return null
        
        var valueStart = colonIndex + 1
        while (valueStart < json.length && json[valueStart].isWhitespace()) valueStart++
        
        var valueEnd = valueStart
        while (valueEnd < json.length && (json[valueEnd].isDigit() || json[valueEnd] == '.' || json[valueEnd] == '-')) valueEnd++
        
        return if (valueEnd > valueStart) {
            json.substring(valueStart, valueEnd).toDoubleOrNull()
        } else null
    }
    
    private fun extractBoolean(json: String, key: String): Boolean {
        val keyIndex = json.indexOf(key)
        if (keyIndex < 0) return false
        
        val colonIndex = json.indexOf(':', keyIndex)
        if (colonIndex < 0) return false
        
        var valueStart = colonIndex + 1
        while (valueStart < json.length && json[valueStart].isWhitespace()) valueStart++
        
        return json.substring(valueStart).startsWith("true")
    }
    
    private fun findStringEnd(json: String, start: Int): Int {
        var i = start
        while (i < json.length) {
            if (json[i] == '"' && (i == 0 || json[i - 1] != '\\')) {
                return i
            }
            i++
        }
        return -1
    }
    
    private fun extractSection(json: String, key: String): String? {
        val keyIndex = json.indexOf(key)
        if (keyIndex < 0) return null
        
        val colonIndex = json.indexOf(':', keyIndex)
        if (colonIndex < 0) return null
        
        var start = colonIndex + 1
        while (start < json.length && json[start].isWhitespace()) start++
        
        if (start >= json.length) return null
        
        val openChar = json[start]
        if (openChar != '[' && openChar != '{') return null
        
        val closeChar = if (openChar == '[') ']' else '}'
        var depth = 1
        var i = start + 1
        var inString = false
        
        while (i < json.length && depth > 0) {
            val c = json[i]
            if (c == '"' && (i == 0 || json[i - 1] != '\\')) {
                inString = !inString
            } else if (!inString) {
                if (c == openChar) depth++
                else if (c == closeChar) depth--
            }
            i++
        }
        
        return if (depth == 0) json.substring(start, i) else null
    }
    
    /**
     * Download cover image.
     */
    fun downloadCover(imageUrl: String): ByteArray? {
        return try {
            // Google Books sometimes returns small images by default
            // Try to get a larger version
            val largerUrl = imageUrl
                .replace("&zoom=1", "&zoom=3")
                .replace("&edge=curl", "")
            
            URL(largerUrl).openStream().use { it.readBytes() }
        } catch (e: Exception) {
            Logger.error("Failed to download cover: ${e.message}")
            null
        }
    }
}
