package org.calibre.media.metadata

import org.calibre.media.*
import org.calibre.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDate

/**
 * Audible metadata provider for audiobooks.
 * 
 * Provides metadata for audiobooks including:
 * - Title, authors, narrators
 * - Duration, chapter information
 * - Series information
 * - Cover images
 * - Ratings and reviews
 * 
 * Uses the unofficial Audible API (for personal use).
 * Note: For production use, consider Audible Partner API.
 */
class AudibleProvider(
    private val locale: String = "us"  // us, uk, de, fr, ca, au, in, it, jp
) : MetadataProvider {
    
    override val name = "Audible"
    
    companion object {
        private val API_DOMAINS = mapOf(
            "us" to "api.audible.com",
            "uk" to "api.audible.co.uk",
            "de" to "api.audible.de",
            "fr" to "api.audible.fr",
            "ca" to "api.audible.ca",
            "au" to "api.audible.com.au",
            "in" to "api.audible.in",
            "it" to "api.audible.it",
            "jp" to "api.audible.co.jp"
        )
        
        private val WEBSITE_DOMAINS = mapOf(
            "us" to "www.audible.com",
            "uk" to "www.audible.co.uk",
            "de" to "www.audible.de",
            "fr" to "www.audible.fr",
            "ca" to "www.audible.ca",
            "au" to "www.audible.com.au",
            "in" to "www.audible.in",
            "it" to "www.audible.it",
            "jp" to "www.audible.co.jp"
        )
    }
    
    override fun supports(type: MediaType): Boolean {
        return type == MediaType.AUDIOBOOK
    }
    
    override fun fetchMetadata(item: MediaItem): Map<String, Any>? {
        return when (item) {
            is AudiobookItem -> fetchAudiobookMetadata(item)
            else -> null
        }
    }
    
    private fun fetchAudiobookMetadata(audiobook: AudiobookItem): Map<String, Any>? {
        // Search by ASIN if available
        if (audiobook.asin != null) {
            val details = fetchByAsin(audiobook.asin)
            if (details != null) return details
        }
        
        // Search by title and author
        val query = buildString {
            append(audiobook.title)
            if (audiobook.authors.isNotEmpty()) {
                append(" ")
                append(audiobook.authors.first())
            }
        }
        
        val searchResults = searchAudiobooks(query)
        if (searchResults.isEmpty()) return null
        
        val asin = searchResults.first()["asin"] as? String ?: return null
        return fetchByAsin(asin)
    }
    
    private fun fetchByAsin(asin: String): Map<String, Any>? {
        // Use web scraping approach since API requires authentication
        val domain = WEBSITE_DOMAINS[locale] ?: WEBSITE_DOMAINS["us"]
        val url = "https://$domain/pd/$asin"
        
        val html = httpGet(url) ?: return null
        return parseAudiobookPage(html, asin)
    }
    
    private fun parseAudiobookPage(html: String, asin: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        result["asin"] = asin
        
        // Extract title
        result["title"] = extractMetaContent(html, "og:title")
            ?: extractBetween(html, "<h1 class=\"bc-heading", "</h1>")
                ?.substringAfter(">")
            ?: ""
        
        // Extract description
        result["description"] = extractMetaContent(html, "og:description")
            ?: extractBetween(html, "publisher-summary", "</div>")
                ?.replace(Regex("<[^>]+>"), "")
                ?.trim()
        
        // Extract cover image
        result["coverUrl"] = extractMetaContent(html, "og:image")
            ?: extractBetween(html, "hero-content", "\"")
                ?.let { s -> 
                    val imgStart = s.indexOf("https://")
                    if (imgStart >= 0) {
                        s.substring(imgStart).substringBefore("\"")
                    } else null
                }
        
        // Extract authors
        val authorSection = extractBetween(html, "authorLabel", "</li>")
        if (authorSection != null) {
            result["authors"] = extractLinks(authorSection)
        }
        
        // Extract narrators
        val narratorSection = extractBetween(html, "narratorLabel", "</li>")
        if (narratorSection != null) {
            result["narrators"] = extractLinks(narratorSection)
        }
        
        // Extract duration
        val runtimeSection = extractBetween(html, "runtimeLabel", "</li>")
        if (runtimeSection != null) {
            val hours = Regex("(\\d+)\\s*hr").find(runtimeSection)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val minutes = Regex("(\\d+)\\s*min").find(runtimeSection)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            result["duration"] = Duration.ofHours(hours.toLong()).plusMinutes(minutes.toLong())
        }
        
        // Extract series info
        val seriesSection = extractBetween(html, "seriesLabel", "</li>")
        if (seriesSection != null) {
            val seriesName = extractBetween(seriesSection, ">", "</a>")
            if (seriesName != null) {
                result["series"] = seriesName
                
                val bookNum = Regex("Book\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(seriesSection)?.groupValues?.get(1)?.toDoubleOrNull()
                if (bookNum != null) {
                    result["seriesIndex"] = bookNum
                }
            }
        }
        
        // Extract release date
        val releaseDateSection = extractBetween(html, "releaseDateLabel", "</li>")
        if (releaseDateSection != null) {
            val dateMatch = Regex("(\\d{1,2})-(\\d{1,2})-(\\d{2,4})").find(releaseDateSection)
            if (dateMatch != null) {
                try {
                    val month = dateMatch.groupValues[1].toInt()
                    val day = dateMatch.groupValues[2].toInt()
                    var year = dateMatch.groupValues[3].toInt()
                    if (year < 100) year += 2000
                    result["releaseDate"] = LocalDate.of(year, month, day)
                } catch (e: Exception) {}
            }
        }
        
        // Extract language
        val languageSection = extractBetween(html, "languageLabel", "</li>")
        if (languageSection != null) {
            result["language"] = languageSection
                .replace(Regex("<[^>]+>"), "")
                .substringAfter(":")
                .trim()
        }
        
        // Extract publisher
        val publisherSection = extractBetween(html, "publisherLabel", "</li>")
        if (publisherSection != null) {
            result["publisher"] = extractBetween(publisherSection, ">", "</a>")
                ?: publisherSection.replace(Regex("<[^>]+>"), "").substringAfter(":").trim()
        }
        
        // Extract rating
        val ratingMatch = Regex("(\\d\\.\\d)\\s*out of 5").find(html)
        if (ratingMatch != null) {
            result["rating"] = ratingMatch.groupValues[1].toDoubleOrNull()
        }
        
        // Extract rating count
        val ratingCountMatch = Regex("([\\d,]+)\\s*ratings?").find(html)
        if (ratingCountMatch != null) {
            result["ratingsCount"] = ratingCountMatch.groupValues[1].replace(",", "").toIntOrNull()
        }
        
        // Extract abridgement status
        result["abridged"] = html.lowercase().contains("abridged") && 
            !html.lowercase().contains("unabridged")
        
        // Extract categories/genres
        val categorySection = extractBetween(html, "categoriesLabel", "</div>")
        if (categorySection != null) {
            result["genres"] = extractLinks(categorySection)
        }
        
        return result
    }
    
    override fun search(query: String, type: MediaType): List<Map<String, Any>> {
        return when (type) {
            MediaType.AUDIOBOOK -> searchAudiobooks(query)
            else -> emptyList()
        }
    }
    
    /**
     * Search for audiobooks.
     */
    fun searchAudiobooks(query: String): List<Map<String, Any>> {
        val domain = WEBSITE_DOMAINS[locale] ?: WEBSITE_DOMAINS["us"]
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://$domain/search?keywords=$encodedQuery"
        
        val html = httpGet(url) ?: return emptyList()
        return parseSearchResults(html)
    }
    
    private fun parseSearchResults(html: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        
        // Find product containers
        var searchStart = 0
        while (true) {
            val productStart = html.indexOf("product-list-flyout", searchStart)
            if (productStart < 0) break
            
            val productEnd = html.indexOf("product-list-flyout", productStart + 20)
            val productSection = if (productEnd > 0) {
                html.substring(productStart, productEnd)
            } else {
                html.substring(productStart, minOf(productStart + 3000, html.length))
            }
            
            val item = parseSearchResultItem(productSection)
            if (item["asin"] != null && item["title"] != null) {
                results.add(item)
            }
            
            searchStart = if (productEnd > 0) productEnd else productStart + 100
            
            if (results.size >= 20) break
        }
        
        return results
    }
    
    private fun parseSearchResultItem(html: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        // Extract ASIN from link
        val asinMatch = Regex("/pd/([A-Z0-9]+)").find(html)
        if (asinMatch != null) {
            result["asin"] = asinMatch.groupValues[1]
        }
        
        // Extract title
        val titleLink = extractBetween(html, "bc-link", "</a>")
        if (titleLink != null) {
            result["title"] = titleLink.substringAfter(">").trim()
        }
        
        // Extract author
        val authorSection = extractBetween(html, "authorLabel", "</li>")
        if (authorSection != null) {
            result["authors"] = extractLinks(authorSection)
        }
        
        // Extract narrator
        val narratorSection = extractBetween(html, "narratorLabel", "</li>")
        if (narratorSection != null) {
            result["narrators"] = extractLinks(narratorSection)
        }
        
        // Extract cover image
        val imgMatch = Regex("src=\"(https://[^\"]+)\"").find(html)
        if (imgMatch != null) {
            result["coverUrl"] = imgMatch.groupValues[1]
        }
        
        // Extract rating
        val ratingMatch = Regex("(\\d\\.\\d)\\s*out of").find(html)
        if (ratingMatch != null) {
            result["rating"] = ratingMatch.groupValues[1].toDoubleOrNull()
        }
        
        // Extract duration
        val durationMatch = Regex("(\\d+)\\s*hr[s]?\\s*(\\d+)?\\s*min").find(html)
        if (durationMatch != null) {
            val hours = durationMatch.groupValues[1].toIntOrNull() ?: 0
            val minutes = durationMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            result["duration"] = Duration.ofHours(hours.toLong()).plusMinutes(minutes.toLong())
        }
        
        return result
    }
    
    /**
     * Search by author.
     */
    fun searchByAuthor(author: String): List<Map<String, Any>> {
        return searchAudiobooks("author:$author")
    }
    
    /**
     * Search by narrator.
     */
    fun searchByNarrator(narrator: String): List<Map<String, Any>> {
        return searchAudiobooks("narrator:$narrator")
    }
    
    /**
     * Get audiobooks in a series.
     */
    fun getSeriesBooks(seriesAsin: String): List<Map<String, Any>> {
        val domain = WEBSITE_DOMAINS[locale] ?: WEBSITE_DOMAINS["us"]
        val url = "https://$domain/series/$seriesAsin"
        
        val html = httpGet(url) ?: return emptyList()
        return parseSearchResults(html)
    }
    
    // Helper methods
    
    private fun extractMetaContent(html: String, property: String): String? {
        val pattern = Regex("property=\"$property\"\\s+content=\"([^\"]+)\"")
        return pattern.find(html)?.groupValues?.get(1)
            ?: Regex("content=\"([^\"]+)\"\\s+property=\"$property\"").find(html)?.groupValues?.get(1)
    }
    
    private fun extractBetween(html: String, start: String, end: String): String? {
        val startIndex = html.indexOf(start)
        if (startIndex < 0) return null
        
        val endIndex = html.indexOf(end, startIndex + start.length)
        if (endIndex < 0) return null
        
        return html.substring(startIndex + start.length, endIndex)
    }
    
    private fun extractLinks(html: String): List<String> {
        val links = mutableListOf<String>()
        val pattern = Regex(">([^<]+)</a>")
        
        pattern.findAll(html).forEach { match ->
            val text = match.groupValues[1].trim()
            if (text.isNotEmpty() && !text.startsWith("<")) {
                links.add(text)
            }
        }
        
        return links
    }
    
    private fun httpGet(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            
            if (connection.responseCode == 200) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                Logger.warn("Audible request failed: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Logger.error("Audible request error: ${e.message}")
            null
        }
    }
    
    /**
     * Download cover image.
     */
    fun downloadCover(coverUrl: String): ByteArray? {
        return try {
            // Try to get larger image
            val largerUrl = coverUrl
                .replace("_SL500_", "_SL1500_")
                .replace("_SS500", "_SS1500")
            
            URL(largerUrl).openStream().use { it.readBytes() }
        } catch (e: Exception) {
            try {
                URL(coverUrl).openStream().use { it.readBytes() }
            } catch (e2: Exception) {
                Logger.error("Failed to download cover: ${e2.message}")
                null
            }
        }
    }
    
    /**
     * Parse chapter information from an M4B file.
     * This is a helper that can be used when adding audiobooks.
     */
    fun parseChaptersFromFile(file: java.io.File): List<AudiobookChapter>? {
        // M4B chapter parsing requires FFmpeg or similar
        // This is a placeholder for the interface
        return null
    }
}
