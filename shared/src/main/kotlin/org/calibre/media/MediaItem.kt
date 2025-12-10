package org.calibre.media

import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Base class for all media items in the unified library.
 * 
 * This provides a common structure for books, movies, TV shows,
 * music, comics, and other media types.
 */
abstract class MediaItem {
    abstract val id: Long
    abstract val type: MediaType
    abstract val title: String
    abstract val file: File?
    abstract val dateAdded: LocalDateTime
    abstract val dateModified: LocalDateTime
    
    // Common metadata
    abstract val rating: Double?
    abstract val description: String?
    abstract val genres: List<String>
    abstract val tags: List<String>
    abstract val coverImage: ByteArray?
    abstract val thumbnailPath: String?
    
    // User data
    abstract var playCount: Int
    abstract var lastPlayed: LocalDateTime?
    abstract var userRating: Double?
    abstract var isFavorite: Boolean
}

/**
 * Extended book metadata for the unified library.
 */
data class BookItem(
    override val id: Long,
    override val title: String,
    override val file: File?,
    override val dateAdded: LocalDateTime = LocalDateTime.now(),
    override val dateModified: LocalDateTime = LocalDateTime.now(),
    override val rating: Double? = null,
    override val description: String? = null,
    override val genres: List<String> = emptyList(),
    override val tags: List<String> = emptyList(),
    override val coverImage: ByteArray? = null,
    override val thumbnailPath: String? = null,
    override var playCount: Int = 0,
    override var lastPlayed: LocalDateTime? = null,
    override var userRating: Double? = null,
    override var isFavorite: Boolean = false,
    
    // Book-specific fields
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishDate: LocalDate? = null,
    val isbn: String? = null,
    val isbn13: String? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val languages: List<String> = emptyList(),
    val pageCount: Int? = null,
    val format: String? = null,
    val readingProgress: Double = 0.0,
    val currentPage: Int = 0,
    val bookmarks: List<Bookmark> = emptyList()
) : MediaItem() {
    override val type = MediaType.EBOOK
}

/**
 * Movie metadata for the unified library.
 */
data class MovieItem(
    override val id: Long,
    override val title: String,
    override val file: File?,
    override val dateAdded: LocalDateTime = LocalDateTime.now(),
    override val dateModified: LocalDateTime = LocalDateTime.now(),
    override val rating: Double? = null,
    override val description: String? = null,
    override val genres: List<String> = emptyList(),
    override val tags: List<String> = emptyList(),
    override val coverImage: ByteArray? = null,
    override val thumbnailPath: String? = null,
    override var playCount: Int = 0,
    override var lastPlayed: LocalDateTime? = null,
    override var userRating: Double? = null,
    override var isFavorite: Boolean = false,
    
    // Movie-specific fields
    val originalTitle: String? = null,
    val year: Int? = null,
    val releaseDate: LocalDate? = null,
    val runtime: Duration? = null,
    val directors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val studio: String? = null,
    val contentRating: String? = null, // PG, PG-13, R, etc.
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val trailerUrl: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val resolution: String? = null, // 1080p, 4K, etc.
    val fileSize: Long = 0,
    val watchProgress: Duration = Duration.ZERO,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList()
) : MediaItem() {
    override val type = MediaType.MOVIE
    
    val isWatched: Boolean get() = playCount > 0
}

/**
 * TV Show metadata (series level).
 */
data class TvShowItem(
    override val id: Long,
    override val title: String,
    override val file: File? = null, // May not have a single file
    override val dateAdded: LocalDateTime = LocalDateTime.now(),
    override val dateModified: LocalDateTime = LocalDateTime.now(),
    override val rating: Double? = null,
    override val description: String? = null,
    override val genres: List<String> = emptyList(),
    override val tags: List<String> = emptyList(),
    override val coverImage: ByteArray? = null,
    override val thumbnailPath: String? = null,
    override var playCount: Int = 0,
    override var lastPlayed: LocalDateTime? = null,
    override var userRating: Double? = null,
    override var isFavorite: Boolean = false,
    
    // TV Show-specific fields
    val originalTitle: String? = null,
    val year: Int? = null,
    val endYear: Int? = null,
    val status: TvShowStatus = TvShowStatus.UNKNOWN,
    val network: String? = null,
    val contentRating: String? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val seasons: MutableList<TvSeason> = mutableListOf(),
    val totalEpisodes: Int = 0,
    val watchedEpisodes: Int = 0
) : MediaItem() {
    override val type = MediaType.TV_SHOW
    
    val isFullyWatched: Boolean get() = watchedEpisodes >= totalEpisodes
    val watchProgress: Double get() = if (totalEpisodes > 0) watchedEpisodes.toDouble() / totalEpisodes else 0.0
}

/**
 * TV Season metadata.
 */
data class TvSeason(
    val id: Long,
    val showId: Long,
    val seasonNumber: Int,
    val title: String? = null,
    val description: String? = null,
    val airDate: LocalDate? = null,
    val posterImage: ByteArray? = null,
    val episodes: MutableList<TvEpisode> = mutableListOf()
)

/**
 * TV Episode metadata.
 */
data class TvEpisode(
    val id: Long,
    val showId: Long,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val description: String? = null,
    val airDate: LocalDate? = null,
    val runtime: Duration? = null,
    val file: File? = null,
    val rating: Double? = null,
    val stillImage: ByteArray? = null,
    val directors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val guestStars: List<CastMember> = emptyList(),
    var isWatched: Boolean = false,
    var watchProgress: Duration = Duration.ZERO
)

/**
 * Music track/song metadata.
 */
data class MusicTrack(
    override val id: Long,
    override val title: String,
    override val file: File?,
    override val dateAdded: LocalDateTime = LocalDateTime.now(),
    override val dateModified: LocalDateTime = LocalDateTime.now(),
    override val rating: Double? = null,
    override val description: String? = null,
    override val genres: List<String> = emptyList(),
    override val tags: List<String> = emptyList(),
    override val coverImage: ByteArray? = null,
    override val thumbnailPath: String? = null,
    override var playCount: Int = 0,
    override var lastPlayed: LocalDateTime? = null,
    override var userRating: Double? = null,
    override var isFavorite: Boolean = false,
    
    // Music-specific fields
    val artists: List<String> = emptyList(),
    val albumArtists: List<String> = emptyList(),
    val album: String? = null,
    val albumId: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val duration: Duration? = null,
    val year: Int? = null,
    val composer: String? = null,
    val bpm: Int? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val musicBrainzId: String? = null,
    val lyrics: String? = null,
    val isCompilation: Boolean = false
) : MediaItem() {
    override val type = MediaType.MUSIC
}

/**
 * Music album metadata.
 */
data class MusicAlbum(
    val id: Long,
    val title: String,
    val artists: List<String> = emptyList(),
    val albumArtists: List<String> = emptyList(),
    val year: Int? = null,
    val releaseDate: LocalDate? = null,
    val genres: List<String> = emptyList(),
    val coverImage: ByteArray? = null,
    val musicBrainzId: String? = null,
    val discogId: String? = null,
    val label: String? = null,
    val totalDiscs: Int = 1,
    val totalTracks: Int = 0,
    val tracks: MutableList<MusicTrack> = mutableListOf(),
    val duration: Duration = Duration.ZERO,
    var playCount: Int = 0,
    var userRating: Double? = null,
    var isFavorite: Boolean = false
)

/**
 * Music artist metadata.
 */
data class MusicArtist(
    val id: Long,
    val name: String,
    val sortName: String? = null,
    val biography: String? = null,
    val genres: List<String> = emptyList(),
    val image: ByteArray? = null,
    val musicBrainzId: String? = null,
    val albums: MutableList<MusicAlbum> = mutableListOf(),
    val website: String? = null,
    val formed: Int? = null,
    val disbanded: Int? = null,
    var isFavorite: Boolean = false
)

/**
 * Comic book metadata.
 */
data class ComicItem(
    override val id: Long,
    override val title: String,
    override val file: File?,
    override val dateAdded: LocalDateTime = LocalDateTime.now(),
    override val dateModified: LocalDateTime = LocalDateTime.now(),
    override val rating: Double? = null,
    override val description: String? = null,
    override val genres: List<String> = emptyList(),
    override val tags: List<String> = emptyList(),
    override val coverImage: ByteArray? = null,
    override val thumbnailPath: String? = null,
    override var playCount: Int = 0,
    override var lastPlayed: LocalDateTime? = null,
    override var userRating: Double? = null,
    override var isFavorite: Boolean = false,
    
    // Comic-specific fields
    val series: String? = null,
    val issueNumber: Double? = null,
    val volume: Int? = null,
    val publisher: String? = null,
    val imprint: String? = null,
    val publishDate: LocalDate? = null,
    val writers: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val colorists: List<String> = emptyList(),
    val letterers: List<String> = emptyList(),
    val editors: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val teams: List<String> = emptyList(),
    val storyArcs: List<String> = emptyList(),
    val pageCount: Int = 0,
    val comicVineId: Int? = null,
    val readingProgress: Double = 0.0,
    val currentPage: Int = 0,
    val ageRating: String? = null
) : MediaItem() {
    override val type = MediaType.COMIC
}

/**
 * Magazine/Periodical metadata.
 */
data class MagazineItem(
    override val id: Long,
    override val title: String,
    override val file: File?,
    override val dateAdded: LocalDateTime = LocalDateTime.now(),
    override val dateModified: LocalDateTime = LocalDateTime.now(),
    override val rating: Double? = null,
    override val description: String? = null,
    override val genres: List<String> = emptyList(),
    override val tags: List<String> = emptyList(),
    override val coverImage: ByteArray? = null,
    override val thumbnailPath: String? = null,
    override var playCount: Int = 0,
    override var lastPlayed: LocalDateTime? = null,
    override var userRating: Double? = null,
    override var isFavorite: Boolean = false,
    
    // Magazine-specific fields
    val publication: String,
    val issueNumber: String? = null,
    val issueDate: LocalDate? = null,
    val volume: Int? = null,
    val frequency: PublicationFrequency = PublicationFrequency.UNKNOWN,
    val publisher: String? = null,
    val language: String? = null,
    val pageCount: Int = 0,
    val issn: String? = null,
    val readingProgress: Double = 0.0,
    val currentPage: Int = 0
) : MediaItem() {
    override val type = MediaType.MAGAZINE
}

/**
 * Audiobook metadata.
 */
data class AudiobookItem(
    override val id: Long,
    override val title: String,
    override val file: File?,
    override val dateAdded: LocalDateTime = LocalDateTime.now(),
    override val dateModified: LocalDateTime = LocalDateTime.now(),
    override val rating: Double? = null,
    override val description: String? = null,
    override val genres: List<String> = emptyList(),
    override val tags: List<String> = emptyList(),
    override val coverImage: ByteArray? = null,
    override val thumbnailPath: String? = null,
    override var playCount: Int = 0,
    override var lastPlayed: LocalDateTime? = null,
    override var userRating: Double? = null,
    override var isFavorite: Boolean = false,
    
    // Audiobook-specific fields
    val authors: List<String> = emptyList(),
    val narrators: List<String> = emptyList(),
    val publisher: String? = null,
    val publishDate: LocalDate? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val duration: Duration? = null,
    val chapters: List<AudiobookChapter> = emptyList(),
    val abridged: Boolean = false,
    val asin: String? = null, // Audible ASIN
    val audibleId: String? = null,
    val isbn: String? = null,
    val languages: List<String> = emptyList(),
    val listenProgress: Duration = Duration.ZERO,
    val currentChapter: Int = 0,
    val playbackSpeed: Float = 1.0f
) : MediaItem() {
    override val type = MediaType.AUDIOBOOK
    
    val progressPercent: Double get() = duration?.let { 
        if (it.isZero) 0.0 else listenProgress.toMillis().toDouble() / it.toMillis() * 100 
    } ?: 0.0
}

/**
 * Audiobook chapter metadata.
 */
data class AudiobookChapter(
    val id: Long,
    val audiobookId: Long,
    val chapterNumber: Int,
    val title: String,
    val startTime: Duration,
    val endTime: Duration,
    val file: File? = null // For multi-file audiobooks
) {
    val duration: Duration get() = endTime.minus(startTime)
}

// Supporting classes

data class CastMember(
    val name: String,
    val character: String? = null,
    val order: Int = 0,
    val image: String? = null,
    val tmdbId: Int? = null
)

data class SubtitleTrack(
    val language: String,
    val file: File? = null,
    val isEmbedded: Boolean = true,
    val isForced: Boolean = false,
    val isDefault: Boolean = false,
    val codec: String? = null
)

data class AudioTrack(
    val language: String,
    val codec: String? = null,
    val channels: Int = 2,
    val bitrate: Int? = null,
    val isDefault: Boolean = false
)

data class Bookmark(
    val id: Long,
    val position: Any, // Page number, timestamp, etc.
    val label: String? = null,
    val note: String? = null,
    val dateCreated: LocalDateTime = LocalDateTime.now()
)

enum class TvShowStatus {
    CONTINUING, ENDED, CANCELLED, UNKNOWN
}

enum class PublicationFrequency {
    DAILY, WEEKLY, BIWEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, YEARLY, UNKNOWN
}
