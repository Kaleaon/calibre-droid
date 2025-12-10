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
 * Comic Vine metadata provider for comics.
 * 
 * Provides metadata for comics including:
 * - Issue and series information
 * - Cover images
 * - Credits (writers, artists, colorists, etc.)
 * - Character and team appearances
 * - Story arcs
 * 
 * API documentation: https://comicvine.gamespot.com/api/documentation
 */
class ComicVineProvider(
    private val apiKey: String = ""  // User must provide their own API key
) : MetadataProvider {
    
    override val name = "Comic Vine"
    
    companion object {
        private const val API_BASE = "https://comicvine.gamespot.com/api"
    }
    
    override fun supports(type: MediaType): Boolean {
        return type == MediaType.COMIC
    }
    
    override fun fetchMetadata(item: MediaItem): Map<String, Any>? {
        return when (item) {
            is ComicItem -> fetchComicMetadata(item)
            else -> null
        }
    }
    
    private fun fetchComicMetadata(comic: ComicItem): Map<String, Any>? {
        // Search for the issue
        val searchResults = if (comic.series != null && comic.issueNumber != null) {
            searchIssues(comic.series, comic.issueNumber.toInt())
        } else {
            searchIssues(comic.title, null)
        }
        
        if (searchResults.isEmpty()) return null
        
        val issueId = searchResults.first()["id"] as? Int ?: return null
        
        return fetchIssueDetails(issueId)
    }
    
    private fun fetchIssueDetails(issueId: Int): Map<String, Any>? {
        val url = "$API_BASE/issue/4000-$issueId/?api_key=$apiKey&format=json&field_list=" +
            "id,name,issue_number,description,cover_date,store_date,image," +
            "volume,person_credits,character_credits,team_credits,story_arc_credits"
        
        val response = httpGet(url) ?: return null
        return parseIssueResponse(response)
    }
    
    private fun parseIssueResponse(json: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        // Extract from "results" object
        val resultsSection = extractSection(json, "\"results\"") ?: return result
        
        result["comicVineId"] = extractInt(resultsSection, "\"id\"")
        result["title"] = extractString(resultsSection, "\"name\"") ?: ""
        result["issueNumber"] = extractDouble(resultsSection, "\"issue_number\"")
        result["description"] = cleanHtml(extractString(resultsSection, "\"description\""))
        
        extractString(resultsSection, "\"cover_date\"")?.let { dateStr ->
            try {
                result["coverDate"] = LocalDate.parse(dateStr)
            } catch (e: Exception) {
                // Try partial date
                try {
                    val parts = dateStr.split("-")
                    if (parts.size >= 2) {
                        result["coverDate"] = LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1)
                    }
                } catch (e2: Exception) {}
            }
        }
        
        extractString(resultsSection, "\"store_date\"")?.let { dateStr ->
            try {
                result["storeDate"] = LocalDate.parse(dateStr)
            } catch (e: Exception) {}
        }
        
        // Cover image
        val imageSection = extractSection(resultsSection, "\"image\"")
        if (imageSection != null) {
            result["coverUrl"] = extractString(imageSection, "\"medium_url\"")
                ?: extractString(imageSection, "\"small_url\"")
            result["coverUrlLarge"] = extractString(imageSection, "\"super_url\"")
                ?: extractString(imageSection, "\"original_url\"")
        }
        
        // Volume (series) info
        val volumeSection = extractSection(resultsSection, "\"volume\"")
        if (volumeSection != null) {
            result["series"] = extractString(volumeSection, "\"name\"")
            result["volumeId"] = extractInt(volumeSection, "\"id\"")
        }
        
        // Credits
        val credits = extractSection(resultsSection, "\"person_credits\"")
        if (credits != null) {
            val parsedCredits = parseCredits(credits)
            result["writers"] = parsedCredits["writer"] ?: emptyList<String>()
            result["artists"] = parsedCredits["artist"] ?: emptyList<String>()
            result["pencilers"] = parsedCredits["penciler"] ?: emptyList<String>()
            result["inkers"] = parsedCredits["inker"] ?: emptyList<String>()
            result["colorists"] = parsedCredits["colorist"] ?: emptyList<String>()
            result["letterers"] = parsedCredits["letterer"] ?: emptyList<String>()
            result["editors"] = parsedCredits["editor"] ?: emptyList<String>()
            result["coverArtists"] = parsedCredits["cover"] ?: emptyList<String>()
        }
        
        // Characters
        val characters = extractSection(resultsSection, "\"character_credits\"")
        if (characters != null) {
            result["characters"] = parseNameList(characters)
        }
        
        // Teams
        val teams = extractSection(resultsSection, "\"team_credits\"")
        if (teams != null) {
            result["teams"] = parseNameList(teams)
        }
        
        // Story arcs
        val storyArcs = extractSection(resultsSection, "\"story_arc_credits\"")
        if (storyArcs != null) {
            result["storyArcs"] = parseNameList(storyArcs)
        }
        
        return result
    }
    
    override fun search(query: String, type: MediaType): List<Map<String, Any>> {
        return when (type) {
            MediaType.COMIC -> searchIssues(query, null)
            else -> emptyList()
        }
    }
    
    /**
     * Search for comic issues.
     */
    fun searchIssues(query: String, issueNumber: Int?): List<Map<String, Any>> {
        val searchQuery = if (issueNumber != null) {
            "$query #$issueNumber"
        } else {
            query
        }
        
        val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
        val url = "$API_BASE/search/?api_key=$apiKey&format=json&resources=issue&query=$encodedQuery&limit=10"
        
        val response = httpGet(url) ?: return emptyList()
        return parseSearchResults(response)
    }
    
    /**
     * Search for comic series/volumes.
     */
    fun searchVolumes(query: String): List<Map<String, Any>> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$API_BASE/search/?api_key=$apiKey&format=json&resources=volume&query=$encodedQuery&limit=10"
        
        val response = httpGet(url) ?: return emptyList()
        return parseVolumeSearchResults(response)
    }
    
    private fun parseSearchResults(json: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        
        val resultsSection = extractSection(json, "\"results\"") ?: return results
        
        var depth = 0
        var objectStart = -1
        
        for (i in resultsSection.indices) {
            when (resultsSection[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = resultsSection.substring(objectStart, i + 1)
                        val item = mutableMapOf<String, Any>()
                        
                        item["id"] = extractInt(obj, "\"id\"")
                        item["title"] = extractString(obj, "\"name\"") ?: ""
                        item["issueNumber"] = extractDouble(obj, "\"issue_number\"")
                        item["description"] = cleanHtml(extractString(obj, "\"deck\""))
                        
                        val volumeSection = extractSection(obj, "\"volume\"")
                        if (volumeSection != null) {
                            item["series"] = extractString(volumeSection, "\"name\"")
                        }
                        
                        val imageSection = extractSection(obj, "\"image\"")
                        if (imageSection != null) {
                            item["coverUrl"] = extractString(imageSection, "\"medium_url\"")
                        }
                        
                        results.add(item)
                    }
                }
            }
        }
        
        return results
    }
    
    private fun parseVolumeSearchResults(json: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        
        val resultsSection = extractSection(json, "\"results\"") ?: return results
        
        var depth = 0
        var objectStart = -1
        
        for (i in resultsSection.indices) {
            when (resultsSection[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = resultsSection.substring(objectStart, i + 1)
                        val item = mutableMapOf<String, Any>()
                        
                        item["id"] = extractInt(obj, "\"id\"")
                        item["name"] = extractString(obj, "\"name\"") ?: ""
                        item["startYear"] = extractInt(obj, "\"start_year\"")
                        item["issueCount"] = extractInt(obj, "\"count_of_issues\"")
                        item["description"] = cleanHtml(extractString(obj, "\"deck\""))
                        item["publisher"] = extractString(obj, "\"publisher\"")
                        
                        val imageSection = extractSection(obj, "\"image\"")
                        if (imageSection != null) {
                            item["coverUrl"] = extractString(imageSection, "\"medium_url\"")
                        }
                        
                        results.add(item)
                    }
                }
            }
        }
        
        return results
    }
    
    /**
     * Get all issues in a volume/series.
     */
    fun getVolumeIssues(volumeId: Int): List<Map<String, Any>> {
        val url = "$API_BASE/issues/?api_key=$apiKey&format=json&filter=volume:$volumeId&sort=issue_number:asc"
        val response = httpGet(url) ?: return emptyList()
        return parseSearchResults(response)
    }
    
    /**
     * Search for characters.
     */
    fun searchCharacters(query: String): List<Map<String, Any>> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$API_BASE/search/?api_key=$apiKey&format=json&resources=character&query=$encodedQuery&limit=10"
        
        val response = httpGet(url) ?: return emptyList()
        return parseCharacterResults(response)
    }
    
    private fun parseCharacterResults(json: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        
        val resultsSection = extractSection(json, "\"results\"") ?: return results
        
        var depth = 0
        var objectStart = -1
        
        for (i in resultsSection.indices) {
            when (resultsSection[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = resultsSection.substring(objectStart, i + 1)
                        val item = mutableMapOf<String, Any>()
                        
                        item["id"] = extractInt(obj, "\"id\"")
                        item["name"] = extractString(obj, "\"name\"") ?: ""
                        item["realName"] = extractString(obj, "\"real_name\"")
                        item["description"] = cleanHtml(extractString(obj, "\"deck\""))
                        item["publisher"] = extractString(obj, "\"publisher\"")
                        item["aliases"] = extractString(obj, "\"aliases\"")?.split("\n") ?: emptyList<String>()
                        
                        val imageSection = extractSection(obj, "\"image\"")
                        if (imageSection != null) {
                            item["imageUrl"] = extractString(imageSection, "\"medium_url\"")
                        }
                        
                        results.add(item)
                    }
                }
            }
        }
        
        return results
    }
    
    // Helper methods
    
    private fun parseCredits(json: String): Map<String, List<String>> {
        val credits = mutableMapOf<String, MutableList<String>>()
        
        var depth = 0
        var objectStart = -1
        
        for (i in json.indices) {
            when (json[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = json.substring(objectStart, i + 1)
                        val name = extractString(obj, "\"name\"") ?: continue
                        val role = extractString(obj, "\"role\"")?.lowercase() ?: continue
                        
                        credits.getOrPut(role) { mutableListOf() }.add(name)
                    }
                }
            }
        }
        
        return credits
    }
    
    private fun parseNameList(json: String): List<String> {
        val names = mutableListOf<String>()
        
        var depth = 0
        var objectStart = -1
        
        for (i in json.indices) {
            when (json[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = json.substring(objectStart, i + 1)
                        extractString(obj, "\"name\"")?.let { names.add(it) }
                    }
                }
            }
        }
        
        return names
    }
    
    private fun cleanHtml(html: String?): String? {
        if (html == null) return null
        
        return html
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    private fun httpGet(urlString: String): String? {
        if (apiKey.isEmpty()) {
            Logger.warn("Comic Vine API key not configured")
            return null
        }
        
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "CalibreKotlin/1.0")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                Logger.warn("Comic Vine API error: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Logger.error("Comic Vine request failed: ${e.message}")
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
        } else if (json.substring(valueStart).startsWith("null")) {
            null
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
        
        // Handle string numbers
        if (json[valueStart] == '"') {
            val valueEnd = findStringEnd(json, valueStart + 1)
            return if (valueEnd > valueStart) {
                json.substring(valueStart + 1, valueEnd).toDoubleOrNull()
            } else null
        }
        
        var valueEnd = valueStart
        while (valueEnd < json.length && (json[valueEnd].isDigit() || json[valueEnd] == '.' || json[valueEnd] == '-')) valueEnd++
        
        return if (valueEnd > valueStart) {
            json.substring(valueStart, valueEnd).toDoubleOrNull()
        } else null
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
    fun downloadCover(coverUrl: String): ByteArray? {
        return try {
            URL(coverUrl).openStream().use { it.readBytes() }
        } catch (e: Exception) {
            Logger.error("Failed to download cover: ${e.message}")
            null
        }
    }
}
