package org.calibre.media

import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified media library for managing all types of media content.
 * 
 * Similar to Plex, Jellyfin, and Emby, this library manages:
 * - Books (EPUB, MOBI, PDF, etc.)
 * - Movies and TV Shows
 * - Music and Audiobooks
 * - Comics and Magazines
 * - Photos and Documents
 * 
 * Features:
 * - SQLite database storage
 * - Metadata from various sources (TMDB, MusicBrainz, ComicVine, etc.)
 * - Library scanning and monitoring
 * - Multi-library support
 * - User data tracking (watched, played, favorites)
 */
class MediaLibrary(
    private val dbPath: File,
    private val name: String = "Media Library"
) {
    private var connection: Connection? = null
    private val idGenerator = AtomicLong(1)
    
    // In-memory caches
    private val books = ConcurrentHashMap<Long, BookItem>()
    private val movies = ConcurrentHashMap<Long, MovieItem>()
    private val tvShows = ConcurrentHashMap<Long, TvShowItem>()
    private val musicTracks = ConcurrentHashMap<Long, MusicTrack>()
    private val musicAlbums = ConcurrentHashMap<Long, MusicAlbum>()
    private val musicArtists = ConcurrentHashMap<Long, MusicArtist>()
    private val comics = ConcurrentHashMap<Long, ComicItem>()
    private val magazines = ConcurrentHashMap<Long, MagazineItem>()
    private val audiobooks = ConcurrentHashMap<Long, AudiobookItem>()
    
    // Library folders for each media type
    private val libraryFolders = ConcurrentHashMap<MediaType, MutableList<File>>()
    
    // Metadata providers
    private val metadataProviders = mutableListOf<MetadataProvider>()
    
    // Change listeners
    private val changeListeners = mutableListOf<MediaChangeListener>()
    
    init {
        initDatabase()
        loadFromDatabase()
    }
    
    private fun initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:${dbPath.absolutePath}")
            
            connection?.createStatement()?.use { stmt ->
                // Media items table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS media_items (
                        id INTEGER PRIMARY KEY,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        file_path TEXT,
                        metadata TEXT,
                        date_added TEXT,
                        date_modified TEXT,
                        play_count INTEGER DEFAULT 0,
                        last_played TEXT,
                        user_rating REAL,
                        is_favorite INTEGER DEFAULT 0
                    )
                """)
                
                // Library folders table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS library_folders (
                        id INTEGER PRIMARY KEY,
                        media_type TEXT NOT NULL,
                        path TEXT NOT NULL,
                        enabled INTEGER DEFAULT 1
                    )
                """)
                
                // User progress table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_progress (
                        item_id INTEGER PRIMARY KEY,
                        progress TEXT,
                        last_updated TEXT
                    )
                """)
                
                // Collections table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS collections (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        type TEXT,
                        description TEXT,
                        cover_path TEXT
                    )
                """)
                
                // Collection items table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS collection_items (
                        collection_id INTEGER,
                        item_id INTEGER,
                        position INTEGER,
                        PRIMARY KEY (collection_id, item_id)
                    )
                """)
                
                // Indexes
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_media_type ON media_items(type)")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_media_title ON media_items(title)")
            }
            
            Logger.info("Database initialized: ${dbPath.absolutePath}")
        } catch (e: Exception) {
            Logger.error("Failed to initialize database: ${e.message}")
        }
    }
    
    private fun loadFromDatabase() {
        try {
            connection?.createStatement()?.use { stmt ->
                // Load library folders
                stmt.executeQuery("SELECT media_type, path FROM library_folders WHERE enabled = 1")
                    .use { rs ->
                        while (rs.next()) {
                            val type = MediaType.valueOf(rs.getString("media_type"))
                            val path = File(rs.getString("path"))
                            libraryFolders.getOrPut(type) { mutableListOf() }.add(path)
                        }
                    }
                
                // Find max ID
                stmt.executeQuery("SELECT MAX(id) as max_id FROM media_items").use { rs ->
                    if (rs.next()) {
                        idGenerator.set(rs.getLong("max_id") + 1)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Failed to load from database: ${e.message}")
        }
    }
    
    /**
     * Register a metadata provider.
     */
    fun registerMetadataProvider(provider: MetadataProvider) {
        metadataProviders.add(provider)
    }
    
    /**
     * Add a library folder for a specific media type.
     */
    fun addLibraryFolder(type: MediaType, folder: File) {
        if (!folder.exists() || !folder.isDirectory) {
            Logger.warn("Invalid library folder: ${folder.absolutePath}")
            return
        }
        
        libraryFolders.getOrPut(type) { mutableListOf() }.add(folder)
        
        try {
            connection?.prepareStatement(
                "INSERT INTO library_folders (media_type, path) VALUES (?, ?)"
            )?.use { stmt ->
                stmt.setString(1, type.name)
                stmt.setString(2, folder.absolutePath)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            Logger.error("Failed to save library folder: ${e.message}")
        }
        
        Logger.info("Added library folder for $type: ${folder.absolutePath}")
    }
    
    /**
     * Scan all library folders for new media.
     */
    fun scanLibraries(progressCallback: ((String, Int, Int) -> Unit)? = null) {
        var scanned = 0
        var total = 0
        
        for ((type, folders) in libraryFolders) {
            for (folder in folders) {
                val files = scanFolder(folder, type)
                total += files.size
                
                for (file in files) {
                    progressCallback?.invoke(file.name, scanned, total)
                    addMediaFile(file, type)
                    scanned++
                }
            }
        }
        
        Logger.info("Scan complete: $scanned items processed")
        notifyLibraryChanged()
    }
    
    private fun scanFolder(folder: File, type: MediaType): List<File> {
        val files = mutableListOf<File>()
        
        folder.walkTopDown().forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in type.extensions) {
                    files.add(file)
                }
            }
        }
        
        return files
    }
    
    /**
     * Add a media file to the library.
     */
    fun addMediaFile(file: File, type: MediaType? = null): MediaItem? {
        val mediaType = type ?: MediaType.fromExtension(file.extension)
        
        return when (mediaType) {
            MediaType.EBOOK -> addBook(file)
            MediaType.MOVIE -> addMovie(file)
            MediaType.TV_SHOW -> addTvEpisode(file)
            MediaType.MUSIC -> addMusicTrack(file)
            MediaType.AUDIOBOOK -> addAudiobook(file)
            MediaType.COMIC -> addComic(file)
            MediaType.MAGAZINE -> addMagazine(file)
            else -> {
                Logger.warn("Unsupported media type: $mediaType for ${file.name}")
                null
            }
        }
    }
    
    /**
     * Add a book to the library.
     */
    fun addBook(file: File): BookItem {
        val id = idGenerator.getAndIncrement()
        
        // Extract basic metadata from filename
        val title = file.nameWithoutExtension
            .replace(Regex("[_-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        val book = BookItem(
            id = id,
            title = title,
            file = file,
            format = file.extension.uppercase()
        )
        
        books[id] = book
        saveToDatabase(book)
        
        // Fetch metadata asynchronously
        fetchMetadataAsync(book)
        
        notifyItemAdded(book)
        return book
    }
    
    /**
     * Add a movie to the library.
     */
    fun addMovie(file: File): MovieItem {
        val id = idGenerator.getAndIncrement()
        
        // Parse movie info from filename (supports common naming patterns)
        val (title, year) = parseMovieFilename(file.nameWithoutExtension)
        
        val movie = MovieItem(
            id = id,
            title = title,
            file = file,
            year = year,
            fileSize = file.length()
        )
        
        movies[id] = movie
        saveToDatabase(movie)
        
        fetchMetadataAsync(movie)
        
        notifyItemAdded(movie)
        return movie
    }
    
    private fun parseMovieFilename(filename: String): Pair<String, Int?> {
        // Common patterns: "Movie Name (2020)", "Movie.Name.2020.1080p", etc.
        val patterns = listOf(
            Regex("(.+?)\\s*\\((\\d{4})\\)"),
            Regex("(.+?)\\s+(\\d{4})(?:\\s|\\.|$)"),
            Regex("(.+?)\\.(\\d{4})\\.(?:1080p|720p|2160p|4k|BluRay|WEB|HDRip)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(filename)
            if (match != null) {
                val title = match.groupValues[1]
                    .replace(".", " ")
                    .replace("_", " ")
                    .trim()
                val year = match.groupValues[2].toIntOrNull()
                return Pair(title, year)
            }
        }
        
        // Fallback: just clean up the filename
        val title = filename
            .replace(Regex("\\.(1080p|720p|2160p|4k|BluRay|WEB|HDRip|BRRip|DVDRip|x264|x265|HEVC).*", RegexOption.IGNORE_CASE), "")
            .replace(".", " ")
            .replace("_", " ")
            .trim()
        
        return Pair(title, null)
    }
    
    /**
     * Add a TV episode (auto-creates show/season if needed).
     */
    fun addTvEpisode(file: File): TvEpisode? {
        val parsed = parseTvEpisodeFilename(file.nameWithoutExtension)
        if (parsed == null) {
            Logger.warn("Could not parse TV episode: ${file.name}")
            return null
        }
        
        val (showName, seasonNum, episodeNum, episodeTitle) = parsed
        
        // Find or create TV show
        var show = tvShows.values.find { 
            it.title.equals(showName, ignoreCase = true) 
        }
        
        if (show == null) {
            val showId = idGenerator.getAndIncrement()
            show = TvShowItem(
                id = showId,
                title = showName
            )
            tvShows[showId] = show
            saveToDatabase(show)
            fetchMetadataAsync(show)
        }
        
        // Find or create season
        var season = show.seasons.find { it.seasonNumber == seasonNum }
        if (season == null) {
            season = TvSeason(
                id = idGenerator.getAndIncrement(),
                showId = show.id,
                seasonNumber = seasonNum
            )
            show.seasons.add(season)
        }
        
        // Create episode
        val episode = TvEpisode(
            id = idGenerator.getAndIncrement(),
            showId = show.id,
            seasonNumber = seasonNum,
            episodeNumber = episodeNum,
            title = episodeTitle ?: "Episode $episodeNum",
            file = file
        )
        
        season.episodes.add(episode)
        
        // Update show stats
        val updatedShow = show.copy(
            totalEpisodes = show.seasons.sumOf { it.episodes.size }
        )
        tvShows[show.id] = updatedShow
        
        notifyItemAdded(updatedShow)
        return episode
    }
    
    private fun parseTvEpisodeFilename(filename: String): TvEpisodeParsed? {
        // Common patterns: "Show Name S01E02", "Show.Name.1x02", etc.
        val patterns = listOf(
            Regex("(.+?)[\\s._-]+[Ss](\\d+)[Ee](\\d+)(?:[\\s._-]+(.+))?"),
            Regex("(.+?)[\\s._-]+(\\d+)x(\\d+)(?:[\\s._-]+(.+))?"),
            Regex("(.+?)[\\s._-]+Season\\s*(\\d+)[\\s._-]+Episode\\s*(\\d+)(?:[\\s._-]+(.+))?", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(filename)
            if (match != null) {
                val showName = match.groupValues[1]
                    .replace(".", " ")
                    .replace("_", " ")
                    .trim()
                val season = match.groupValues[2].toIntOrNull() ?: 1
                val episode = match.groupValues[3].toIntOrNull() ?: 1
                val episodeTitle = match.groupValues.getOrNull(4)
                    ?.replace(".", " ")
                    ?.replace("_", " ")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                
                return TvEpisodeParsed(showName, season, episode, episodeTitle)
            }
        }
        
        return null
    }
    
    /**
     * Add a music track to the library.
     */
    fun addMusicTrack(file: File): MusicTrack {
        val id = idGenerator.getAndIncrement()
        
        val title = file.nameWithoutExtension
        
        val track = MusicTrack(
            id = id,
            title = title,
            file = file
        )
        
        musicTracks[id] = track
        saveToDatabase(track)
        
        fetchMetadataAsync(track)
        
        notifyItemAdded(track)
        return track
    }
    
    /**
     * Add a comic to the library.
     */
    fun addComic(file: File): ComicItem {
        val id = idGenerator.getAndIncrement()
        
        val (series, issue) = parseComicFilename(file.nameWithoutExtension)
        
        val comic = ComicItem(
            id = id,
            title = if (issue != null) "$series #$issue" else series,
            file = file,
            series = series,
            issueNumber = issue
        )
        
        comics[id] = comic
        saveToDatabase(comic)
        
        fetchMetadataAsync(comic)
        
        notifyItemAdded(comic)
        return comic
    }
    
    private fun parseComicFilename(filename: String): Pair<String, Double?> {
        // Patterns: "Series Name #123", "Series Name 123", "Series Name v2 #5"
        val patterns = listOf(
            Regex("(.+?)\\s*#(\\d+(?:\\.\\d+)?)"),
            Regex("(.+?)\\s+(?:v\\d+\\s+)?#?(\\d+)$"),
            Regex("(.+?)\\s*\\((\\d+)\\)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(filename)
            if (match != null) {
                val series = match.groupValues[1].trim()
                val issue = match.groupValues[2].toDoubleOrNull()
                return Pair(series, issue)
            }
        }
        
        return Pair(filename.trim(), null)
    }
    
    /**
     * Add a magazine to the library.
     */
    fun addMagazine(file: File): MagazineItem {
        val id = idGenerator.getAndIncrement()
        
        val (publication, issueNum) = parseMagazineFilename(file.nameWithoutExtension)
        
        val magazine = MagazineItem(
            id = id,
            title = if (issueNum != null) "$publication - $issueNum" else publication,
            file = file,
            publication = publication,
            issueNumber = issueNum
        )
        
        magazines[id] = magazine
        saveToDatabase(magazine)
        
        notifyItemAdded(magazine)
        return magazine
    }
    
    private fun parseMagazineFilename(filename: String): Pair<String, String?> {
        // Patterns: "Magazine Name - Issue 123", "Magazine Name (January 2020)"
        val patterns = listOf(
            Regex("(.+?)\\s*-\\s*(?:Issue\\s*)?(\\d+)"),
            Regex("(.+?)\\s*\\(([A-Za-z]+\\s*\\d{4})\\)"),
            Regex("(.+?)\\s*-\\s*(.+)$")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(filename)
            if (match != null) {
                return Pair(match.groupValues[1].trim(), match.groupValues[2].trim())
            }
        }
        
        return Pair(filename.trim(), null)
    }
    
    /**
     * Add an audiobook to the library.
     */
    fun addAudiobook(file: File): AudiobookItem {
        val id = idGenerator.getAndIncrement()
        
        val title = file.nameWithoutExtension
            .replace(Regex("[_-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        val audiobook = AudiobookItem(
            id = id,
            title = title,
            file = file
        )
        
        audiobooks[id] = audiobook
        saveToDatabase(audiobook)
        
        fetchMetadataAsync(audiobook)
        
        notifyItemAdded(audiobook)
        return audiobook
    }
    
    // Query methods
    
    fun getAllBooks(): List<BookItem> = books.values.toList()
    fun getAllMovies(): List<MovieItem> = movies.values.toList()
    fun getAllTvShows(): List<TvShowItem> = tvShows.values.toList()
    fun getAllMusic(): List<MusicTrack> = musicTracks.values.toList()
    fun getAllAlbums(): List<MusicAlbum> = musicAlbums.values.toList()
    fun getAllArtists(): List<MusicArtist> = musicArtists.values.toList()
    fun getAllComics(): List<ComicItem> = comics.values.toList()
    fun getAllMagazines(): List<MagazineItem> = magazines.values.toList()
    fun getAllAudiobooks(): List<AudiobookItem> = audiobooks.values.toList()
    
    fun getBook(id: Long): BookItem? = books[id]
    fun getMovie(id: Long): MovieItem? = movies[id]
    fun getTvShow(id: Long): TvShowItem? = tvShows[id]
    fun getMusicTrack(id: Long): MusicTrack? = musicTracks[id]
    fun getComic(id: Long): ComicItem? = comics[id]
    fun getAudiobook(id: Long): AudiobookItem? = audiobooks[id]
    
    /**
     * Search across all media types.
     */
    fun search(query: String, types: List<MediaType>? = null): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        val q = query.lowercase()
        
        val searchTypes = types ?: MediaType.values().toList()
        
        if (MediaType.EBOOK in searchTypes) {
            results.addAll(books.values.filter { it.title.lowercase().contains(q) })
        }
        if (MediaType.MOVIE in searchTypes) {
            results.addAll(movies.values.filter { it.title.lowercase().contains(q) })
        }
        if (MediaType.TV_SHOW in searchTypes) {
            results.addAll(tvShows.values.filter { it.title.lowercase().contains(q) })
        }
        if (MediaType.MUSIC in searchTypes) {
            results.addAll(musicTracks.values.filter { 
                it.title.lowercase().contains(q) || 
                it.artists.any { a -> a.lowercase().contains(q) }
            })
        }
        if (MediaType.COMIC in searchTypes) {
            results.addAll(comics.values.filter { it.title.lowercase().contains(q) })
        }
        if (MediaType.MAGAZINE in searchTypes) {
            results.addAll(magazines.values.filter { it.title.lowercase().contains(q) })
        }
        if (MediaType.AUDIOBOOK in searchTypes) {
            results.addAll(audiobooks.values.filter { it.title.lowercase().contains(q) })
        }
        
        return results.sortedBy { it.title }
    }
    
    /**
     * Get recently added items.
     */
    fun getRecentlyAdded(limit: Int = 20): List<MediaItem> {
        val allItems = mutableListOf<MediaItem>()
        allItems.addAll(books.values)
        allItems.addAll(movies.values)
        allItems.addAll(tvShows.values)
        allItems.addAll(comics.values)
        allItems.addAll(audiobooks.values)
        
        return allItems
            .sortedByDescending { it.dateAdded }
            .take(limit)
    }
    
    /**
     * Get in-progress items (continue watching/reading).
     */
    fun getInProgress(): List<MediaItem> {
        val inProgress = mutableListOf<MediaItem>()
        
        inProgress.addAll(books.values.filter { it.readingProgress > 0 && it.readingProgress < 100 })
        inProgress.addAll(movies.values.filter { !it.watchProgress.isZero && it.playCount == 0 })
        inProgress.addAll(tvShows.values.filter { it.watchProgress > 0 && !it.isFullyWatched })
        inProgress.addAll(audiobooks.values.filter { it.progressPercent > 0 && it.progressPercent < 100 })
        
        return inProgress.sortedByDescending { it.lastPlayed ?: it.dateModified }
    }
    
    // Database operations
    
    private fun saveToDatabase(item: MediaItem) {
        try {
            connection?.prepareStatement("""
                INSERT OR REPLACE INTO media_items 
                (id, type, title, file_path, date_added, date_modified, play_count, user_rating, is_favorite)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)?.use { stmt ->
                stmt.setLong(1, item.id)
                stmt.setString(2, item.type.name)
                stmt.setString(3, item.title)
                stmt.setString(4, item.file?.absolutePath)
                stmt.setString(5, item.dateAdded.toString())
                stmt.setString(6, item.dateModified.toString())
                stmt.setInt(7, item.playCount)
                stmt.setObject(8, item.userRating)
                stmt.setInt(9, if (item.isFavorite) 1 else 0)
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            Logger.error("Failed to save media item: ${e.message}")
        }
    }
    
    // Metadata fetching
    
    private fun fetchMetadataAsync(item: MediaItem) {
        Thread {
            for (provider in metadataProviders) {
                if (provider.supports(item.type)) {
                    try {
                        val metadata = provider.fetchMetadata(item)
                        if (metadata != null) {
                            applyMetadata(item, metadata)
                            break
                        }
                    } catch (e: Exception) {
                        Logger.debug("Metadata provider ${provider.name} failed: ${e.message}")
                    }
                }
            }
        }.start()
    }
    
    private fun applyMetadata(item: MediaItem, metadata: Map<String, Any>) {
        // Update item with fetched metadata
        when (item) {
            is MovieItem -> {
                val updated = item.copy(
                    description = metadata["description"] as? String ?: item.description,
                    rating = metadata["rating"] as? Double ?: item.rating,
                    runtime = metadata["runtime"] as? java.time.Duration ?: item.runtime,
                    releaseDate = metadata["releaseDate"] as? java.time.LocalDate ?: item.releaseDate,
                    tmdbId = metadata["tmdbId"] as? Int ?: item.tmdbId,
                    imdbId = metadata["imdbId"] as? String ?: item.imdbId,
                    genres = (metadata["genres"] as? List<*>)?.filterIsInstance<String>() ?: item.genres
                )
                movies[item.id] = updated
                saveToDatabase(updated)
                notifyItemUpdated(updated)
            }
            is BookItem -> {
                val updated = item.copy(
                    description = metadata["description"] as? String ?: item.description,
                    rating = metadata["rating"] as? Double ?: item.rating,
                    isbn = metadata["isbn"] as? String ?: item.isbn,
                    publisher = metadata["publisher"] as? String ?: item.publisher,
                    authors = (metadata["authors"] as? List<*>)?.filterIsInstance<String>() ?: item.authors
                )
                books[item.id] = updated
                saveToDatabase(updated)
                notifyItemUpdated(updated)
            }
            // Add more cases as needed
        }
    }
    
    // Change notifications
    
    fun addChangeListener(listener: MediaChangeListener) {
        changeListeners.add(listener)
    }
    
    fun removeChangeListener(listener: MediaChangeListener) {
        changeListeners.remove(listener)
    }
    
    private fun notifyItemAdded(item: MediaItem) {
        changeListeners.forEach { it.onItemAdded(item) }
    }
    
    private fun notifyItemUpdated(item: MediaItem) {
        changeListeners.forEach { it.onItemUpdated(item) }
    }
    
    private fun notifyLibraryChanged() {
        changeListeners.forEach { it.onLibraryChanged() }
    }
    
    /**
     * Close the library and release resources.
     */
    fun close() {
        try {
            connection?.close()
        } catch (e: Exception) {
            Logger.error("Error closing database: ${e.message}")
        }
    }
    
    /**
     * Get library statistics.
     */
    fun getStats(): MediaLibraryStats {
        return MediaLibraryStats(
            totalBooks = books.size,
            totalMovies = movies.size,
            totalTvShows = tvShows.size,
            totalEpisodes = tvShows.values.sumOf { it.totalEpisodes },
            totalMusicTracks = musicTracks.size,
            totalAlbums = musicAlbums.size,
            totalArtists = musicArtists.size,
            totalComics = comics.size,
            totalMagazines = magazines.size,
            totalAudiobooks = audiobooks.size
        )
    }
}

/**
 * Parsed TV episode information.
 */
data class TvEpisodeParsed(
    val showName: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String?
)

/**
 * Library statistics.
 */
data class MediaLibraryStats(
    val totalBooks: Int,
    val totalMovies: Int,
    val totalTvShows: Int,
    val totalEpisodes: Int,
    val totalMusicTracks: Int,
    val totalAlbums: Int,
    val totalArtists: Int,
    val totalComics: Int,
    val totalMagazines: Int,
    val totalAudiobooks: Int
) {
    val totalItems: Int get() = totalBooks + totalMovies + totalEpisodes + 
        totalMusicTracks + totalComics + totalMagazines + totalAudiobooks
}

/**
 * Interface for metadata providers.
 */
interface MetadataProvider {
    val name: String
    fun supports(type: MediaType): Boolean
    fun fetchMetadata(item: MediaItem): Map<String, Any>?
    fun search(query: String, type: MediaType): List<Map<String, Any>>
}

/**
 * Interface for library change listeners.
 */
interface MediaChangeListener {
    fun onItemAdded(item: MediaItem)
    fun onItemUpdated(item: MediaItem)
    fun onItemRemoved(itemId: Long)
    fun onLibraryChanged()
}
