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
 * MusicBrainz metadata provider for music.
 * 
 * Provides metadata for music including:
 * - Artist information
 * - Album/release information
 * - Track listings
 * - Release dates and labels
 * 
 * Also integrates with:
 * - Cover Art Archive for album covers
 * - Last.fm for additional metadata and similar artists
 * 
 * API documentation: https://musicbrainz.org/doc/MusicBrainz_API
 */
class MusicBrainzProvider(
    private val userAgent: String = "CalibreKotlin/1.0 (calibre-kotlin@example.com)"
) : MetadataProvider {
    
    override val name = "MusicBrainz"
    
    companion object {
        private const val API_BASE = "https://musicbrainz.org/ws/2"
        private const val COVER_ART_BASE = "https://coverartarchive.org"
        
        // Rate limiting: MusicBrainz requires max 1 request per second
        private var lastRequestTime = 0L
        private const val MIN_REQUEST_INTERVAL = 1100L // 1.1 seconds
    }
    
    override fun supports(type: MediaType): Boolean {
        return type == MediaType.MUSIC
    }
    
    override fun fetchMetadata(item: MediaItem): Map<String, Any>? {
        return when (item) {
            is MusicTrack -> fetchTrackMetadata(item)
            else -> null
        }
    }
    
    private fun fetchTrackMetadata(track: MusicTrack): Map<String, Any>? {
        // Try to find by recording search
        val searchResults = searchRecordings(track.title, track.artists.firstOrNull())
        if (searchResults.isEmpty()) return null
        
        val recordingId = searchResults.first()["id"] as? String ?: return null
        
        return fetchRecordingDetails(recordingId)
    }
    
    private fun fetchRecordingDetails(recordingId: String): Map<String, Any>? {
        val url = "$API_BASE/recording/$recordingId?inc=artists+releases+tags&fmt=json"
        
        val response = httpGet(url) ?: return null
        return parseRecordingResponse(response)
    }
    
    private fun parseRecordingResponse(json: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        result["musicBrainzId"] = extractString(json, "\"id\"") ?: ""
        result["title"] = extractString(json, "\"title\"") ?: ""
        
        extractInt(json, "\"length\"")?.let { ms ->
            result["duration"] = Duration.ofMillis(ms.toLong())
        }
        
        // Parse artists
        val artistCredits = extractSection(json, "\"artist-credit\"")
        if (artistCredits != null) {
            result["artists"] = parseArtistCredits(artistCredits)
        }
        
        // Parse releases (albums)
        val releases = extractSection(json, "\"releases\"")
        if (releases != null) {
            result["albums"] = parseReleases(releases)
        }
        
        // Parse tags (genres)
        val tags = extractSection(json, "\"tags\"")
        if (tags != null) {
            result["genres"] = parseTags(tags)
        }
        
        return result
    }
    
    override fun search(query: String, type: MediaType): List<Map<String, Any>> {
        return when (type) {
            MediaType.MUSIC -> searchRecordings(query, null)
            else -> emptyList()
        }
    }
    
    /**
     * Search for recordings (songs/tracks).
     */
    fun searchRecordings(title: String, artist: String?): List<Map<String, Any>> {
        var query = "recording:\"${encodeQuery(title)}\""
        if (artist != null) {
            query += " AND artist:\"${encodeQuery(artist)}\""
        }
        
        val url = "$API_BASE/recording?query=$query&limit=10&fmt=json"
        val response = httpGet(url) ?: return emptyList()
        
        return parseRecordingSearchResults(response)
    }
    
    private fun parseRecordingSearchResults(json: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        
        val recordingsSection = extractSection(json, "\"recordings\"") ?: return results
        
        var depth = 0
        var objectStart = -1
        
        for (i in recordingsSection.indices) {
            when (recordingsSection[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = recordingsSection.substring(objectStart, i + 1)
                        val item = mutableMapOf<String, Any>()
                        
                        item["id"] = extractString(obj, "\"id\"") ?: ""
                        item["title"] = extractString(obj, "\"title\"") ?: ""
                        item["score"] = extractInt(obj, "\"score\"") ?: 0
                        
                        extractInt(obj, "\"length\"")?.let { ms ->
                            item["duration"] = Duration.ofMillis(ms.toLong())
                        }
                        
                        val artistCredits = extractSection(obj, "\"artist-credit\"")
                        if (artistCredits != null) {
                            item["artists"] = parseArtistCredits(artistCredits)
                        }
                        
                        results.add(item)
                    }
                }
            }
        }
        
        return results
    }
    
    /**
     * Search for releases (albums).
     */
    fun searchReleases(title: String, artist: String?): List<Map<String, Any>> {
        var query = "release:\"${encodeQuery(title)}\""
        if (artist != null) {
            query += " AND artist:\"${encodeQuery(artist)}\""
        }
        
        val url = "$API_BASE/release?query=$query&limit=10&fmt=json"
        val response = httpGet(url) ?: return emptyList()
        
        return parseReleaseSearchResults(response)
    }
    
    private fun parseReleaseSearchResults(json: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        
        val releasesSection = extractSection(json, "\"releases\"") ?: return results
        
        var depth = 0
        var objectStart = -1
        
        for (i in releasesSection.indices) {
            when (releasesSection[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = releasesSection.substring(objectStart, i + 1)
                        val item = mutableMapOf<String, Any>()
                        
                        item["id"] = extractString(obj, "\"id\"") ?: ""
                        item["title"] = extractString(obj, "\"title\"") ?: ""
                        item["date"] = extractString(obj, "\"date\"")
                        item["country"] = extractString(obj, "\"country\"")
                        item["score"] = extractInt(obj, "\"score\"") ?: 0
                        
                        val artistCredits = extractSection(obj, "\"artist-credit\"")
                        if (artistCredits != null) {
                            item["artists"] = parseArtistCredits(artistCredits)
                        }
                        
                        results.add(item)
                    }
                }
            }
        }
        
        return results
    }
    
    /**
     * Search for artists.
     */
    fun searchArtists(name: String): List<Map<String, Any>> {
        val query = "artist:\"${encodeQuery(name)}\""
        val url = "$API_BASE/artist?query=$query&limit=10&fmt=json"
        val response = httpGet(url) ?: return emptyList()
        
        return parseArtistSearchResults(response)
    }
    
    private fun parseArtistSearchResults(json: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        
        val artistsSection = extractSection(json, "\"artists\"") ?: return results
        
        var depth = 0
        var objectStart = -1
        
        for (i in artistsSection.indices) {
            when (artistsSection[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = artistsSection.substring(objectStart, i + 1)
                        val item = mutableMapOf<String, Any>()
                        
                        item["id"] = extractString(obj, "\"id\"") ?: ""
                        item["name"] = extractString(obj, "\"name\"") ?: ""
                        item["sortName"] = extractString(obj, "\"sort-name\"")
                        item["type"] = extractString(obj, "\"type\"")
                        item["country"] = extractString(obj, "\"country\"")
                        item["score"] = extractInt(obj, "\"score\"") ?: 0
                        
                        results.add(item)
                    }
                }
            }
        }
        
        return results
    }
    
    /**
     * Get album details including track listing.
     */
    fun getAlbumDetails(releaseId: String): Map<String, Any>? {
        val url = "$API_BASE/release/$releaseId?inc=artists+recordings+labels+tags&fmt=json"
        val response = httpGet(url) ?: return null
        
        val result = mutableMapOf<String, Any>()
        
        result["id"] = extractString(response, "\"id\"") ?: ""
        result["title"] = extractString(response, "\"title\"") ?: ""
        result["date"] = extractString(response, "\"date\"")
        result["country"] = extractString(response, "\"country\"")
        result["barcode"] = extractString(response, "\"barcode\"")
        
        // Parse artists
        val artistCredits = extractSection(response, "\"artist-credit\"")
        if (artistCredits != null) {
            result["artists"] = parseArtistCredits(artistCredits)
        }
        
        // Parse media (discs and tracks)
        val media = extractSection(response, "\"media\"")
        if (media != null) {
            result["tracks"] = parseMedia(media)
        }
        
        // Parse label info
        val labelInfo = extractSection(response, "\"label-info\"")
        if (labelInfo != null) {
            result["labels"] = parseLabelInfo(labelInfo)
        }
        
        // Try to get cover art
        result["coverUrl"] = getCoverArtUrl(releaseId)
        
        return result
    }
    
    /**
     * Get artist details.
     */
    fun getArtistDetails(artistId: String): Map<String, Any>? {
        val url = "$API_BASE/artist/$artistId?inc=releases+tags+ratings&fmt=json"
        val response = httpGet(url) ?: return null
        
        val result = mutableMapOf<String, Any>()
        
        result["id"] = extractString(response, "\"id\"") ?: ""
        result["name"] = extractString(response, "\"name\"") ?: ""
        result["sortName"] = extractString(response, "\"sort-name\"")
        result["type"] = extractString(response, "\"type\"")
        result["country"] = extractString(response, "\"country\"")
        result["disambiguation"] = extractString(response, "\"disambiguation\"")
        
        // Life span
        result["beginDate"] = extractString(response, "\"begin\"")
        result["endDate"] = extractString(response, "\"end\"")
        result["ended"] = extractBoolean(response, "\"ended\"")
        
        // Releases
        val releases = extractSection(response, "\"releases\"")
        if (releases != null) {
            result["releases"] = parseReleases(releases)
        }
        
        // Tags
        val tags = extractSection(response, "\"tags\"")
        if (tags != null) {
            result["genres"] = parseTags(tags)
        }
        
        // Rating
        result["rating"] = extractDouble(response, "\"value\"")
        
        return result
    }
    
    /**
     * Get cover art URL from Cover Art Archive.
     */
    fun getCoverArtUrl(releaseId: String): String? {
        val url = "$COVER_ART_BASE/release/$releaseId/front-500"
        
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            
            if (connection.responseCode == 307 || connection.responseCode == 200) {
                connection.getHeaderField("Location") ?: url
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Download cover art.
     */
    fun downloadCoverArt(releaseId: String): ByteArray? {
        val url = getCoverArtUrl(releaseId) ?: return null
        
        return try {
            URL(url).openStream().use { it.readBytes() }
        } catch (e: Exception) {
            Logger.error("Failed to download cover art: ${e.message}")
            null
        }
    }
    
    // Helper parsing methods
    
    private fun parseArtistCredits(json: String): List<String> {
        val artists = mutableListOf<String>()
        
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
                        val artistSection = extractSection(obj, "\"artist\"")
                        if (artistSection != null) {
                            extractString(artistSection, "\"name\"")?.let { artists.add(it) }
                        }
                    }
                }
            }
        }
        
        return artists
    }
    
    private fun parseReleases(json: String): List<Map<String, Any>> {
        val releases = mutableListOf<Map<String, Any>>()
        
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
                        val release = mutableMapOf<String, Any>()
                        
                        release["id"] = extractString(obj, "\"id\"") ?: ""
                        release["title"] = extractString(obj, "\"title\"") ?: ""
                        release["date"] = extractString(obj, "\"date\"")
                        release["status"] = extractString(obj, "\"status\"")
                        
                        releases.add(release)
                    }
                }
            }
        }
        
        return releases
    }
    
    private fun parseTags(json: String): List<String> {
        val tags = mutableListOf<String>()
        
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
                        extractString(obj, "\"name\"")?.let { tags.add(it) }
                    }
                }
            }
        }
        
        return tags
    }
    
    private fun parseMedia(json: String): List<Map<String, Any>> {
        val tracks = mutableListOf<Map<String, Any>>()
        
        // Find tracks in each medium
        val tracksSection = extractSection(json, "\"tracks\"") ?: return tracks
        
        var depth = 0
        var objectStart = -1
        
        for (i in tracksSection.indices) {
            when (tracksSection[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = tracksSection.substring(objectStart, i + 1)
                        val track = mutableMapOf<String, Any>()
                        
                        track["number"] = extractInt(obj, "\"number\"") ?: tracks.size + 1
                        track["title"] = extractString(obj, "\"title\"") ?: ""
                        
                        extractInt(obj, "\"length\"")?.let { ms ->
                            track["duration"] = Duration.ofMillis(ms.toLong())
                        }
                        
                        tracks.add(track)
                    }
                }
            }
        }
        
        return tracks
    }
    
    private fun parseLabelInfo(json: String): List<String> {
        val labels = mutableListOf<String>()
        
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
                        val labelSection = extractSection(obj, "\"label\"")
                        if (labelSection != null) {
                            extractString(labelSection, "\"name\"")?.let { labels.add(it) }
                        }
                    }
                }
            }
        }
        
        return labels
    }
    
    // HTTP and utility methods
    
    private fun httpGet(urlString: String): String? {
        // Rate limiting
        val now = System.currentTimeMillis()
        val timeSinceLastRequest = now - lastRequestTime
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
            Thread.sleep(MIN_REQUEST_INTERVAL - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()
        
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                Logger.warn("MusicBrainz API error: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Logger.error("MusicBrainz request failed: ${e.message}")
            null
        }
    }
    
    private fun encodeQuery(query: String): String {
        return URLEncoder.encode(query, "UTF-8")
    }
    
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
}
