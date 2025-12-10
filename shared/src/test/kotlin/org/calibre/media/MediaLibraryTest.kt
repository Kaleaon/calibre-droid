package org.calibre.media

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Comprehensive test suite for the unified media library system.
 */
class MediaLibraryTest {
    
    @TempDir
    lateinit var tempDir: File
    
    private lateinit var library: MediaLibrary
    
    @BeforeEach
    fun setup() {
        val dbFile = File(tempDir, "test_library.db")
        library = MediaLibrary(dbFile, "Test Library")
    }
    
    @AfterEach
    fun teardown() {
        library.close()
    }
    
    // ============ MediaType Tests ============
    
    @Test
    fun `MediaType should detect ebook formats correctly`() {
        assertEquals(MediaType.EBOOK, MediaType.fromExtension("epub"))
        assertEquals(MediaType.EBOOK, MediaType.fromExtension("mobi"))
        assertEquals(MediaType.EBOOK, MediaType.fromExtension("pdf"))
        assertEquals(MediaType.EBOOK, MediaType.fromExtension("azw3"))
        assertEquals(MediaType.EBOOK, MediaType.fromExtension("lit"))
        assertEquals(MediaType.EBOOK, MediaType.fromExtension("lrf"))
    }
    
    @Test
    fun `MediaType should detect video formats correctly`() {
        assertEquals(MediaType.MOVIE, MediaType.fromExtension("mp4"))
        assertEquals(MediaType.MOVIE, MediaType.fromExtension("mkv"))
        assertEquals(MediaType.MOVIE, MediaType.fromExtension("avi"))
        assertEquals(MediaType.MOVIE, MediaType.fromExtension("webm"))
    }
    
    @Test
    fun `MediaType should detect audio formats correctly`() {
        assertEquals(MediaType.MUSIC, MediaType.fromExtension("mp3"))
        assertEquals(MediaType.MUSIC, MediaType.fromExtension("flac"))
        assertEquals(MediaType.MUSIC, MediaType.fromExtension("ogg"))
        assertEquals(MediaType.MUSIC, MediaType.fromExtension("wav"))
    }
    
    @Test
    fun `MediaType should detect comic formats correctly`() {
        assertEquals(MediaType.COMIC, MediaType.fromExtension("cbz"))
        assertEquals(MediaType.COMIC, MediaType.fromExtension("cbr"))
        assertEquals(MediaType.COMIC, MediaType.fromExtension("cb7"))
    }
    
    @Test
    fun `MediaType should return UNKNOWN for unrecognized formats`() {
        assertEquals(MediaType.UNKNOWN, MediaType.fromExtension("xyz"))
        assertEquals(MediaType.UNKNOWN, MediaType.fromExtension(""))
    }
    
    // ============ Book Tests ============
    
    @Test
    fun `should add book to library`() {
        val bookFile = createTempFile("test_book", ".epub")
        val book = library.addBook(bookFile)
        
        assertNotNull(book)
        assertEquals("test_book", book.title)
        assertEquals(MediaType.EBOOK, book.type)
    }
    
    @Test
    fun `should retrieve book by ID`() {
        val bookFile = createTempFile("my_novel", ".epub")
        val addedBook = library.addBook(bookFile)
        
        val retrieved = library.getBook(addedBook.id)
        
        assertNotNull(retrieved)
        assertEquals(addedBook.id, retrieved?.id)
        assertEquals("my_novel", retrieved?.title)
    }
    
    @Test
    fun `should list all books`() {
        createTempFile("book1", ".epub").also { library.addBook(it) }
        createTempFile("book2", ".mobi").also { library.addBook(it) }
        createTempFile("book3", ".pdf").also { library.addBook(it) }
        
        val allBooks = library.getAllBooks()
        
        assertEquals(3, allBooks.size)
    }
    
    // ============ Movie Tests ============
    
    @Test
    fun `should add movie to library`() {
        val movieFile = createTempFile("The.Matrix.1999.1080p.BluRay", ".mkv")
        val movie = library.addMovie(movieFile)
        
        assertNotNull(movie)
        assertEquals("The Matrix", movie.title)
        assertEquals(1999, movie.year)
    }
    
    @Test
    fun `should parse movie filename with parentheses year`() {
        val movieFile = createTempFile("Inception (2010)", ".mp4")
        val movie = library.addMovie(movieFile)
        
        assertEquals("Inception", movie.title)
        assertEquals(2010, movie.year)
    }
    
    @Test
    fun `should parse movie filename with dots`() {
        val movieFile = createTempFile("Avatar.The.Way.of.Water.2022.2160p.WEB-DL", ".mkv")
        val movie = library.addMovie(movieFile)
        
        assertEquals("Avatar The Way of Water", movie.title)
        assertEquals(2022, movie.year)
    }
    
    // ============ TV Show Tests ============
    
    @Test
    fun `should add TV episode and create show`() {
        val episodeFile = createTempFile("Breaking Bad S01E01 Pilot", ".mkv")
        val episode = library.addTvEpisode(episodeFile)
        
        assertNotNull(episode)
        assertEquals("Pilot", episode?.title)
        assertEquals(1, episode?.seasonNumber)
        assertEquals(1, episode?.episodeNumber)
        
        val shows = library.getAllTvShows()
        assertEquals(1, shows.size)
        assertEquals("Breaking Bad", shows.first().title)
    }
    
    @Test
    fun `should parse TV episode with different formats`() {
        // Test S01E02 format
        val ep1 = createTempFile("Game of Thrones S01E02 The Kingsroad", ".mp4")
        library.addTvEpisode(ep1)
        
        // Test 1x03 format
        val ep2 = createTempFile("Game.of.Thrones.1x03.Lord.Snow", ".mkv")
        library.addTvEpisode(ep2)
        
        val shows = library.getAllTvShows()
        assertEquals(1, shows.size)
        assertEquals(2, shows.first().seasons.first().episodes.size)
    }
    
    // ============ Music Tests ============
    
    @Test
    fun `should add music track to library`() {
        val musicFile = createTempFile("01 - Bohemian Rhapsody", ".mp3")
        val track = library.addMusicTrack(musicFile)
        
        assertNotNull(track)
        assertEquals(MediaType.MUSIC, track.type)
    }
    
    @Test
    fun `should list all music tracks`() {
        createTempFile("track1", ".mp3").also { library.addMusicTrack(it) }
        createTempFile("track2", ".flac").also { library.addMusicTrack(it) }
        
        val tracks = library.getAllMusic()
        assertEquals(2, tracks.size)
    }
    
    // ============ Comic Tests ============
    
    @Test
    fun `should add comic to library`() {
        val comicFile = createTempFile("Batman #1", ".cbz")
        val comic = library.addComic(comicFile)
        
        assertNotNull(comic)
        assertEquals("Batman #1", comic.title)
        assertEquals("Batman", comic.series)
        assertEquals(1.0, comic.issueNumber)
    }
    
    @Test
    fun `should parse comic with volume`() {
        val comicFile = createTempFile("Spider-Man v2 #123", ".cbr")
        val comic = library.addComic(comicFile)
        
        assertEquals("Spider-Man v2", comic.series)
        assertEquals(123.0, comic.issueNumber)
    }
    
    // ============ Audiobook Tests ============
    
    @Test
    fun `should add audiobook to library`() {
        val audiobookFile = createTempFile("The Hobbit", ".m4b")
        val audiobook = library.addAudiobook(audiobookFile)
        
        assertNotNull(audiobook)
        assertEquals("The Hobbit", audiobook.title)
        assertEquals(MediaType.AUDIOBOOK, audiobook.type)
    }
    
    // ============ Magazine Tests ============
    
    @Test
    fun `should add magazine to library`() {
        val magazineFile = createTempFile("National Geographic - January 2024", ".pdf")
        val magazine = library.addMagazine(magazineFile)
        
        assertNotNull(magazine)
        assertEquals("National Geographic", magazine.publication)
        assertEquals("January 2024", magazine.issueNumber)
    }
    
    // ============ Search Tests ============
    
    @Test
    fun `should search across all media types`() {
        createTempFile("Harry Potter and the Sorcerer's Stone", ".epub").also { library.addBook(it) }
        createTempFile("Harry Potter and the Chamber of Secrets (2002)", ".mkv").also { library.addMovie(it) }
        
        val results = library.search("Harry Potter")
        
        assertEquals(2, results.size)
    }
    
    @Test
    fun `should search with type filter`() {
        createTempFile("The Lord of the Rings", ".epub").also { library.addBook(it) }
        createTempFile("The Lord of the Rings (2001)", ".mkv").also { library.addMovie(it) }
        
        val bookResults = library.search("Lord of the Rings", listOf(MediaType.EBOOK))
        val movieResults = library.search("Lord of the Rings", listOf(MediaType.MOVIE))
        
        assertEquals(1, bookResults.size)
        assertEquals(1, movieResults.size)
        assertTrue(bookResults.first() is BookItem)
        assertTrue(movieResults.first() is MovieItem)
    }
    
    // ============ Recently Added Tests ============
    
    @Test
    fun `should return recently added items`() {
        createTempFile("book1", ".epub").also { library.addBook(it) }
        Thread.sleep(10)
        createTempFile("movie1", ".mp4").also { library.addMovie(it) }
        Thread.sleep(10)
        createTempFile("book2", ".mobi").also { library.addBook(it) }
        
        val recent = library.getRecentlyAdded(2)
        
        assertEquals(2, recent.size)
    }
    
    // ============ Library Folder Tests ============
    
    @Test
    fun `should add library folder`() {
        val bookFolder = File(tempDir, "books").also { it.mkdirs() }
        library.addLibraryFolder(MediaType.EBOOK, bookFolder)
        
        // Folder should be registered (no exception thrown)
    }
    
    @Test
    fun `should reject invalid library folder`() {
        val nonExistent = File(tempDir, "nonexistent")
        library.addLibraryFolder(MediaType.EBOOK, nonExistent)
        
        // Should not throw, just warn
    }
    
    // ============ Statistics Tests ============
    
    @Test
    fun `should return correct statistics`() {
        createTempFile("book1", ".epub").also { library.addBook(it) }
        createTempFile("book2", ".epub").also { library.addBook(it) }
        createTempFile("movie1", ".mp4").also { library.addMovie(it) }
        createTempFile("comic1", ".cbz").also { library.addComic(it) }
        
        val stats = library.getStats()
        
        assertEquals(2, stats.totalBooks)
        assertEquals(1, stats.totalMovies)
        assertEquals(1, stats.totalComics)
        assertEquals(4, stats.totalItems)
    }
    
    // ============ Helper Methods ============
    
    private fun createTempFile(name: String, extension: String): File {
        val file = File(tempDir, "$name$extension")
        file.writeText("Test content")
        return file
    }
}

/**
 * Tests for MediaItem data classes.
 */
class MediaItemTest {
    
    @Test
    fun `BookItem should have correct defaults`() {
        val book = BookItem(
            id = 1,
            title = "Test Book",
            file = null
        )
        
        assertEquals(0.0, book.readingProgress)
        assertEquals(0, book.currentPage)
        assertTrue(book.authors.isEmpty())
        assertTrue(book.bookmarks.isEmpty())
    }
    
    @Test
    fun `MovieItem should calculate isWatched correctly`() {
        val unwatched = MovieItem(id = 1, title = "Movie", file = null, playCount = 0)
        val watched = MovieItem(id = 2, title = "Movie", file = null, playCount = 1)
        
        assertFalse(unwatched.isWatched)
        assertTrue(watched.isWatched)
    }
    
    @Test
    fun `TvShowItem should calculate watch progress correctly`() {
        val show = TvShowItem(
            id = 1,
            title = "Show",
            totalEpisodes = 10,
            watchedEpisodes = 5
        )
        
        assertEquals(0.5, show.watchProgress)
        assertFalse(show.isFullyWatched)
        
        val completed = show.copy(watchedEpisodes = 10)
        assertTrue(completed.isFullyWatched)
    }
    
    @Test
    fun `AudiobookItem should calculate progress percent`() {
        val audiobook = AudiobookItem(
            id = 1,
            title = "Audiobook",
            file = null,
            duration = Duration.ofHours(10),
            listenProgress = Duration.ofHours(5)
        )
        
        assertEquals(50.0, audiobook.progressPercent)
    }
    
    @Test
    fun `ComicItem should store creator credits`() {
        val comic = ComicItem(
            id = 1,
            title = "Comic #1",
            file = null,
            writers = listOf("Writer 1", "Writer 2"),
            artists = listOf("Artist 1"),
            colorists = listOf("Colorist 1")
        )
        
        assertEquals(2, comic.writers.size)
        assertEquals(1, comic.artists.size)
        assertEquals(1, comic.colorists.size)
    }
}

/**
 * Tests for TV show structure.
 */
class TvShowStructureTest {
    
    @Test
    fun `should create season with episodes`() {
        val season = TvSeason(
            id = 1,
            showId = 100,
            seasonNumber = 1,
            title = "Season 1"
        )
        
        season.episodes.add(TvEpisode(
            id = 10,
            showId = 100,
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Pilot"
        ))
        
        season.episodes.add(TvEpisode(
            id = 11,
            showId = 100,
            seasonNumber = 1,
            episodeNumber = 2,
            title = "Episode 2"
        ))
        
        assertEquals(2, season.episodes.size)
        assertEquals("Pilot", season.episodes[0].title)
    }
    
    @Test
    fun `TvEpisode should track watch status`() {
        val episode = TvEpisode(
            id = 1,
            showId = 100,
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Test",
            runtime = Duration.ofMinutes(45)
        )
        
        assertFalse(episode.isWatched)
        assertEquals(Duration.ZERO, episode.watchProgress)
        
        val watched = episode.copy(isWatched = true, watchProgress = Duration.ofMinutes(45))
        assertTrue(watched.isWatched)
    }
}

/**
 * Tests for music structure.
 */
class MusicStructureTest {
    
    @Test
    fun `should create album with tracks`() {
        val album = MusicAlbum(
            id = 1,
            title = "Abbey Road",
            artists = listOf("The Beatles"),
            year = 1969
        )
        
        album.tracks.add(MusicTrack(
            id = 10,
            title = "Come Together",
            file = null,
            albumId = 1,
            trackNumber = 1
        ))
        
        assertEquals(1, album.tracks.size)
        assertEquals("Come Together", album.tracks[0].title)
    }
    
    @Test
    fun `should create artist with albums`() {
        val artist = MusicArtist(
            id = 1,
            name = "Pink Floyd",
            genres = listOf("Progressive Rock", "Art Rock")
        )
        
        artist.albums.add(MusicAlbum(
            id = 10,
            title = "The Dark Side of the Moon",
            artists = listOf("Pink Floyd"),
            year = 1973
        ))
        
        assertEquals(1, artist.albums.size)
    }
}

/**
 * Tests for audiobook chapter structure.
 */
class AudiobookChapterTest {
    
    @Test
    fun `should calculate chapter duration`() {
        val chapter = AudiobookChapter(
            id = 1,
            audiobookId = 100,
            chapterNumber = 1,
            title = "Chapter 1",
            startTime = Duration.ofMinutes(0),
            endTime = Duration.ofMinutes(30)
        )
        
        assertEquals(Duration.ofMinutes(30), chapter.duration)
    }
    
    @Test
    fun `should handle multi-chapter audiobook`() {
        val chapters = listOf(
            AudiobookChapter(1, 100, 1, "Prologue", Duration.ZERO, Duration.ofMinutes(15)),
            AudiobookChapter(2, 100, 2, "Chapter 1", Duration.ofMinutes(15), Duration.ofMinutes(45)),
            AudiobookChapter(3, 100, 3, "Chapter 2", Duration.ofMinutes(45), Duration.ofHours(1))
        )
        
        val totalDuration = chapters.sumOf { it.duration.toMinutes() }
        assertEquals(60, totalDuration)
    }
}

/**
 * Tests for publication frequency enum.
 */
class PublicationFrequencyTest {
    
    @Test
    fun `should have all expected frequencies`() {
        val frequencies = PublicationFrequency.values()
        
        assertTrue(frequencies.contains(PublicationFrequency.DAILY))
        assertTrue(frequencies.contains(PublicationFrequency.WEEKLY))
        assertTrue(frequencies.contains(PublicationFrequency.MONTHLY))
        assertTrue(frequencies.contains(PublicationFrequency.QUARTERLY))
    }
}

/**
 * Tests for TV show status enum.
 */
class TvShowStatusTest {
    
    @Test
    fun `should have all expected statuses`() {
        val statuses = TvShowStatus.values()
        
        assertTrue(statuses.contains(TvShowStatus.CONTINUING))
        assertTrue(statuses.contains(TvShowStatus.ENDED))
        assertTrue(statuses.contains(TvShowStatus.CANCELLED))
        assertTrue(statuses.contains(TvShowStatus.UNKNOWN))
    }
}
