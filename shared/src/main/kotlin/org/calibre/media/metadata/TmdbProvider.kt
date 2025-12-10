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
 * The Movie Database (TMDB) metadata provider.
 * 
 * Provides metadata for movies and TV shows including:
 * - Title, overview, release date, runtime
 * - Cast and crew information
 * - Genres, ratings, and popularity
 * - Poster and backdrop images
 * - IMDB IDs and other external IDs
 * 
 * API documentation: https://developers.themoviedb.org/3
 */
class TmdbProvider(
    private val apiKey: String = "",  // User must provide their own API key
    private val language: String = "en-US"
) : MetadataProvider {
    
    override val name = "The Movie Database (TMDB)"
    
    companion object {
        private const val API_BASE = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p"
    }
    
    override fun supports(type: MediaType): Boolean {
        return type in listOf(MediaType.MOVIE, MediaType.TV_SHOW)
    }
    
    override fun fetchMetadata(item: MediaItem): Map<String, Any>? {
        return when (item) {
            is MovieItem -> fetchMovieMetadata(item)
            is TvShowItem -> fetchTvShowMetadata(item)
            else -> null
        }
    }
    
    private fun fetchMovieMetadata(movie: MovieItem): Map<String, Any>? {
        // First search for the movie
        val searchResults = searchMovies(movie.title, movie.year)
        if (searchResults.isEmpty()) return null
        
        // Get the best match (first result)
        val tmdbId = searchResults.first()["id"] as? Int ?: return null
        
        // Fetch detailed info
        return fetchMovieDetails(tmdbId)
    }
    
    private fun fetchMovieDetails(tmdbId: Int): Map<String, Any>? {
        val url = "$API_BASE/movie/$tmdbId?api_key=$apiKey&language=$language&append_to_response=credits,external_ids"
        
        val response = httpGet(url) ?: return null
        return parseMovieResponse(response)
    }
    
    private fun parseMovieResponse(json: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        // Parse JSON manually (to avoid external dependencies)
        result["tmdbId"] = extractInt(json, "\"id\"")
        result["title"] = extractString(json, "\"title\"") ?: ""
        result["originalTitle"] = extractString(json, "\"original_title\"")
        result["description"] = extractString(json, "\"overview\"")
        result["rating"] = extractDouble(json, "\"vote_average\"")
        result["popularity"] = extractDouble(json, "\"popularity\"")
        
        extractString(json, "\"release_date\"")?.let { dateStr ->
            try {
                result["releaseDate"] = LocalDate.parse(dateStr)
            } catch (e: Exception) {}
        }
        
        extractInt(json, "\"runtime\"")?.let { minutes ->
            result["runtime"] = Duration.ofMinutes(minutes.toLong())
        }
        
        result["posterPath"] = extractString(json, "\"poster_path\"")?.let { 
            "$IMAGE_BASE/w500$it" 
        }
        result["backdropPath"] = extractString(json, "\"backdrop_path\"")?.let { 
            "$IMAGE_BASE/original$it" 
        }
        
        result["genres"] = extractGenres(json)
        result["imdbId"] = extractString(json, "\"imdb_id\"")
        
        // Parse cast and crew
        val castSection = extractSection(json, "\"cast\"")
        if (castSection != null) {
            result["cast"] = parseCast(castSection)
        }
        
        val crewSection = extractSection(json, "\"crew\"")
        if (crewSection != null) {
            result["directors"] = parseDirectors(crewSection)
            result["writers"] = parseWriters(crewSection)
        }
        
        return result
    }
    
    private fun fetchTvShowMetadata(show: TvShowItem): Map<String, Any>? {
        val searchResults = searchTvShows(show.title)
        if (searchResults.isEmpty()) return null
        
        val tmdbId = searchResults.first()["id"] as? Int ?: return null
        
        return fetchTvShowDetails(tmdbId)
    }
    
    private fun fetchTvShowDetails(tmdbId: Int): Map<String, Any>? {
        val url = "$API_BASE/tv/$tmdbId?api_key=$apiKey&language=$language&append_to_response=credits,external_ids"
        
        val response = httpGet(url) ?: return null
        return parseTvShowResponse(response)
    }
    
    private fun parseTvShowResponse(json: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        result["tmdbId"] = extractInt(json, "\"id\"")
        result["title"] = extractString(json, "\"name\"") ?: ""
        result["originalTitle"] = extractString(json, "\"original_name\"")
        result["description"] = extractString(json, "\"overview\"")
        result["rating"] = extractDouble(json, "\"vote_average\"")
        
        extractString(json, "\"first_air_date\"")?.let { dateStr ->
            try {
                result["firstAirDate"] = LocalDate.parse(dateStr)
            } catch (e: Exception) {}
        }
        
        result["status"] = when (extractString(json, "\"status\"")) {
            "Returning Series" -> TvShowStatus.CONTINUING
            "Ended" -> TvShowStatus.ENDED
            "Canceled" -> TvShowStatus.CANCELLED
            else -> TvShowStatus.UNKNOWN
        }
        
        result["genres"] = extractGenres(json)
        result["network"] = extractString(json, "\"name\"") // First network
        result["posterPath"] = extractString(json, "\"poster_path\"")?.let { 
            "$IMAGE_BASE/w500$it" 
        }
        
        // External IDs
        result["imdbId"] = extractString(json, "\"imdb_id\"")
        result["tvdbId"] = extractInt(json, "\"tvdb_id\"")
        
        return result
    }
    
    override fun search(query: String, type: MediaType): List<Map<String, Any>> {
        return when (type) {
            MediaType.MOVIE -> searchMovies(query, null)
            MediaType.TV_SHOW -> searchTvShows(query)
            else -> emptyList()
        }
    }
    
    private fun searchMovies(query: String, year: Int?): List<Map<String, Any>> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        var url = "$API_BASE/search/movie?api_key=$apiKey&language=$language&query=$encodedQuery"
        if (year != null) {
            url += "&year=$year"
        }
        
        val response = httpGet(url) ?: return emptyList()
        return parseSearchResults(response, "movie")
    }
    
    private fun searchTvShows(query: String): List<Map<String, Any>> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$API_BASE/search/tv?api_key=$apiKey&language=$language&query=$encodedQuery"
        
        val response = httpGet(url) ?: return emptyList()
        return parseSearchResults(response, "tv")
    }
    
    private fun parseSearchResults(json: String, type: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        
        // Find results array
        val resultsStart = json.indexOf("\"results\"")
        if (resultsStart < 0) return results
        
        val arrayStart = json.indexOf('[', resultsStart)
        val arrayEnd = findMatchingBracket(json, arrayStart)
        if (arrayStart < 0 || arrayEnd < 0) return results
        
        val resultsArray = json.substring(arrayStart + 1, arrayEnd)
        
        // Split by object boundaries
        var depth = 0
        var objectStart = -1
        
        for (i in resultsArray.indices) {
            when (resultsArray[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val objectJson = resultsArray.substring(objectStart, i + 1)
                        val item = mutableMapOf<String, Any>()
                        
                        item["id"] = extractInt(objectJson, "\"id\"")
                        
                        if (type == "movie") {
                            item["title"] = extractString(objectJson, "\"title\"") ?: ""
                            item["releaseDate"] = extractString(objectJson, "\"release_date\"")
                        } else {
                            item["title"] = extractString(objectJson, "\"name\"") ?: ""
                            item["firstAirDate"] = extractString(objectJson, "\"first_air_date\"")
                        }
                        
                        item["overview"] = extractString(objectJson, "\"overview\"")
                        item["posterPath"] = extractString(objectJson, "\"poster_path\"")?.let { 
                            "$IMAGE_BASE/w500$it" 
                        }
                        item["rating"] = extractDouble(objectJson, "\"vote_average\"")
                        
                        results.add(item)
                    }
                }
            }
        }
        
        return results
    }
    
    // Helper methods for JSON parsing
    
    private fun extractString(json: String, key: String): String? {
        val keyIndex = json.indexOf(key)
        if (keyIndex < 0) return null
        
        val colonIndex = json.indexOf(':', keyIndex)
        if (colonIndex < 0) return null
        
        // Find the value start
        var valueStart = colonIndex + 1
        while (valueStart < json.length && json[valueStart].isWhitespace()) valueStart++
        
        if (valueStart >= json.length) return null
        
        return if (json[valueStart] == '"') {
            val valueEnd = findStringEnd(json, valueStart + 1)
            if (valueEnd > valueStart) {
                json.substring(valueStart + 1, valueEnd)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
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
        
        var valueEnd = valueStart
        while (valueEnd < json.length && (json[valueEnd].isDigit() || json[valueEnd] == '.' || json[valueEnd] == '-')) valueEnd++
        
        return if (valueEnd > valueStart) {
            json.substring(valueStart, valueEnd).toDoubleOrNull()
        } else null
    }
    
    private fun findStringEnd(json: String, start: Int): Int {
        var i = start
        while (i < json.length) {
            if (json[i] == '"' && json[i - 1] != '\\') {
                return i
            }
            i++
        }
        return -1
    }
    
    private fun findMatchingBracket(json: String, start: Int): Int {
        if (start < 0 || start >= json.length) return -1
        val openChar = json[start]
        val closeChar = when (openChar) {
            '[' -> ']'
            '{' -> '}'
            else -> return -1
        }
        
        var depth = 1
        var i = start + 1
        var inString = false
        
        while (i < json.length && depth > 0) {
            val c = json[i]
            if (c == '"' && json[i - 1] != '\\') {
                inString = !inString
            } else if (!inString) {
                if (c == openChar) depth++
                else if (c == closeChar) depth--
            }
            i++
        }
        
        return if (depth == 0) i - 1 else -1
    }
    
    private fun extractSection(json: String, key: String): String? {
        val keyIndex = json.indexOf(key)
        if (keyIndex < 0) return null
        
        val colonIndex = json.indexOf(':', keyIndex)
        if (colonIndex < 0) return null
        
        var arrayStart = json.indexOf('[', colonIndex)
        if (arrayStart < 0) return null
        
        val arrayEnd = findMatchingBracket(json, arrayStart)
        if (arrayEnd < 0) return null
        
        return json.substring(arrayStart, arrayEnd + 1)
    }
    
    private fun extractGenres(json: String): List<String> {
        val genres = mutableListOf<String>()
        val genresSection = extractSection(json, "\"genres\"") ?: return genres
        
        var i = 0
        while (i < genresSection.length) {
            val nameIndex = genresSection.indexOf("\"name\"", i)
            if (nameIndex < 0) break
            
            val name = extractString(genresSection.substring(nameIndex), "\"name\"")
            if (name != null) {
                genres.add(name)
            }
            i = nameIndex + 1
        }
        
        return genres
    }
    
    private fun parseCast(castJson: String): List<CastMember> {
        val cast = mutableListOf<CastMember>()
        var depth = 0
        var objectStart = -1
        
        for (i in castJson.indices) {
            when (castJson[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = castJson.substring(objectStart, i + 1)
                        val name = extractString(obj, "\"name\"") ?: continue
                        val character = extractString(obj, "\"character\"")
                        val order = extractInt(obj, "\"order\"") ?: cast.size
                        val profilePath = extractString(obj, "\"profile_path\"")?.let {
                            "$IMAGE_BASE/w185$it"
                        }
                        val tmdbId = extractInt(obj, "\"id\"")
                        
                        cast.add(CastMember(name, character, order, profilePath, tmdbId))
                        
                        if (cast.size >= 20) break // Limit cast size
                    }
                }
            }
        }
        
        return cast.sortedBy { it.order }
    }
    
    private fun parseDirectors(crewJson: String): List<String> {
        val directors = mutableListOf<String>()
        var depth = 0
        var objectStart = -1
        
        for (i in crewJson.indices) {
            when (crewJson[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = crewJson.substring(objectStart, i + 1)
                        val job = extractString(obj, "\"job\"")
                        if (job == "Director") {
                            extractString(obj, "\"name\"")?.let { directors.add(it) }
                        }
                    }
                }
            }
        }
        
        return directors
    }
    
    private fun parseWriters(crewJson: String): List<String> {
        val writers = mutableListOf<String>()
        var depth = 0
        var objectStart = -1
        
        for (i in crewJson.indices) {
            when (crewJson[i]) {
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val obj = crewJson.substring(objectStart, i + 1)
                        val job = extractString(obj, "\"job\"")
                        if (job in listOf("Writer", "Screenplay", "Story")) {
                            extractString(obj, "\"name\"")?.let { 
                                if (it !in writers) writers.add(it) 
                            }
                        }
                    }
                }
            }
        }
        
        return writers
    }
    
    private fun httpGet(urlString: String): String? {
        if (apiKey.isEmpty()) {
            Logger.warn("TMDB API key not configured")
            return null
        }
        
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                Logger.warn("TMDB API error: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Logger.error("TMDB request failed: ${e.message}")
            null
        }
    }
    
    /**
     * Fetch episode details for a TV show.
     */
    fun fetchEpisodeDetails(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int): Map<String, Any>? {
        val url = "$API_BASE/tv/$showTmdbId/season/$seasonNumber/episode/$episodeNumber?api_key=$apiKey&language=$language"
        
        val response = httpGet(url) ?: return null
        
        return mapOf(
            "title" to (extractString(response, "\"name\"") ?: ""),
            "description" to extractString(response, "\"overview\""),
            "airDate" to extractString(response, "\"air_date\"")?.let { 
                try { LocalDate.parse(it) } catch (e: Exception) { null }
            },
            "rating" to extractDouble(response, "\"vote_average\""),
            "stillPath" to extractString(response, "\"still_path\"")?.let { "$IMAGE_BASE/w500$it" }
        )
    }
    
    /**
     * Get popular movies.
     */
    fun getPopularMovies(page: Int = 1): List<Map<String, Any>> {
        val url = "$API_BASE/movie/popular?api_key=$apiKey&language=$language&page=$page"
        val response = httpGet(url) ?: return emptyList()
        return parseSearchResults(response, "movie")
    }
    
    /**
     * Get popular TV shows.
     */
    fun getPopularTvShows(page: Int = 1): List<Map<String, Any>> {
        val url = "$API_BASE/tv/popular?api_key=$apiKey&language=$language&page=$page"
        val response = httpGet(url) ?: return emptyList()
        return parseSearchResults(response, "tv")
    }
    
    /**
     * Download an image and return the bytes.
     */
    fun downloadImage(imagePath: String): ByteArray? {
        return try {
            val url = URL(imagePath)
            url.openStream().use { it.readBytes() }
        } catch (e: Exception) {
            Logger.error("Failed to download image: ${e.message}")
            null
        }
    }
}
