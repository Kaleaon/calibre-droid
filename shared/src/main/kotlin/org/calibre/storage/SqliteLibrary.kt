package org.calibre.storage

import org.calibre.metadata.Metadata
import org.calibre.metadata.MetadataParser
import org.calibre.metadata.ReadingProgress
import org.calibre.metadata.Bookmark
import org.calibre.utils.Logger
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * SQLite-based backend for Library storage.
 * Provides better performance for large libraries.
 */
class SqliteLibrary(
    private val dbFile: File = File("library.db"),
    private val libraryDir: File = File("library_files"),
    extraParsers: List<MetadataParser> = emptyList()
) {
    private val parsers = listOf(org.calibre.metadata.EpubParser(), org.calibre.metadata.MobiMetadataParser()) + extraParsers
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    init {
        if (!libraryDir.exists()) {
            libraryDir.mkdirs()
        }
        initializeDatabase()
    }
    
    private fun getConnection(): Connection {
        return DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    }
    
    private fun initializeDatabase() {
        getConnection().use { conn ->
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS books (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    authors TEXT,
                    series TEXT,
                    series_index REAL,
                    rating REAL,
                    tags TEXT,
                    publisher TEXT,
                    isbn TEXT,
                    language TEXT,
                    description TEXT,
                    reading_progress_current_page INTEGER DEFAULT 0,
                    reading_progress_total_pages INTEGER DEFAULT 0,
                    reading_progress_last_read_position TEXT,
                    reading_progress_last_read_date TEXT,
                    reading_progress_reading_time_minutes INTEGER DEFAULT 0,
                    date_added TEXT,
                    date_modified TEXT
                )
            """.trimIndent())
            
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS bookmarks (
                    id TEXT PRIMARY KEY,
                    book_id INTEGER NOT NULL,
                    position TEXT NOT NULL,
                    note TEXT,
                    created_date TEXT,
                    color TEXT,
                    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            conn.createStatement().execute("""
                CREATE INDEX IF NOT EXISTS idx_books_title ON books(title);
                CREATE INDEX IF NOT EXISTS idx_books_authors ON books(authors);
                CREATE INDEX IF NOT EXISTS idx_books_tags ON books(tags);
                CREATE INDEX IF NOT EXISTS idx_bookmarks_book_id ON bookmarks(book_id);
            """.trimIndent())
        }
    }
    
    fun addBook(metadata: Metadata): Int {
        getConnection().use { conn ->
            val sql = """
                INSERT INTO books (title, authors, series, series_index, rating, tags, publisher, isbn, language, description,
                    reading_progress_current_page, reading_progress_total_pages, reading_progress_last_read_position,
                    reading_progress_last_read_date, reading_progress_reading_time_minutes, date_added, date_modified)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            conn.prepareStatement(sql).use { stmt ->
                val now = LocalDateTime.now()
                stmt.setString(1, metadata.title)
                stmt.setString(2, metadata.authors.joinToString(" & "))
                stmt.setString(3, metadata.series)
                stmt.setDouble(4, metadata.seriesIndex ?: 0.0)
                stmt.setObject(5, metadata.rating)
                stmt.setString(6, metadata.tags.joinToString(","))
                stmt.setString(7, metadata.publisher)
                stmt.setString(8, metadata.isbn)
                stmt.setString(9, metadata.language)
                stmt.setString(10, metadata.description)
                stmt.setInt(11, metadata.readingProgress.currentPage)
                stmt.setInt(12, metadata.readingProgress.totalPages)
                stmt.setString(13, metadata.readingProgress.lastReadPosition)
                stmt.setString(14, metadata.readingProgress.lastReadDate?.format(dateFormatter))
                stmt.setInt(15, metadata.readingProgress.readingTimeMinutes)
                stmt.setString(16, (metadata.dateAdded.takeIf { it != LocalDateTime.MIN } ?: now).format(dateFormatter))
                stmt.setString(17, now.format(dateFormatter))
                
                stmt.executeUpdate()
                
                val rs = conn.createStatement().executeQuery("SELECT last_insert_rowid()")
                return if (rs.next()) rs.getInt(1) else throw Exception("Failed to get book ID")
            }
        }
    }
    
    fun importBook(file: File): Int {
        if (!file.exists()) throw Exception("File not found: ${file.absolutePath}")
        
        val parser = parsers.find { it.canParse(file) }
        
        val metadata = if (parser != null) {
            try {
                parser.parseMetadata(file)
            } catch (e: Exception) {
                Logger.warn("Could not parse metadata from ${file.name}: ${e.message}")
                Metadata(title = file.nameWithoutExtension)
            }
        } else {
            Logger.warn("No parser found for ${file.extension}")
            Metadata(title = file.nameWithoutExtension)
        }
        
        val id = addBook(metadata)
        
        val extension = file.extension
        val destFile = File(libraryDir, "$id.$extension")
        try {
            file.copyTo(destFile, overwrite = true)
            Logger.info("Saved book file to: ${destFile.path}")
        } catch (e: Exception) {
            Logger.error("Error copying file: ${e.message}", e)
        }
        
        return id
    }
    
    fun getMetadata(id: Int): Metadata? {
        getConnection().use { conn ->
            conn.prepareStatement("SELECT * FROM books WHERE id = ?").use { stmt ->
                stmt.setInt(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return resultSetToMetadata(rs)
                    }
                }
            }
        }
        return null
    }
    
    fun getBookFile(id: Int): File? {
        val files = libraryDir.listFiles { _, name -> name.startsWith("$id.") }
        return files?.firstOrNull()
    }
    
    fun removeBook(id: Int): Boolean {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM books WHERE id = ?").use { stmt ->
                stmt.setInt(1, id)
                val deleted = stmt.executeUpdate() > 0
                if (deleted) {
                    val file = getBookFile(id)
                    if (file != null && file.exists()) {
                        file.delete()
                    }
                }
                return deleted
            }
        }
    }
    
    fun search(query: String): List<Metadata> {
        val lowerQuery = query.lowercase()
        val results = mutableListOf<Metadata>()
        
        getConnection().use { conn ->
            conn.prepareStatement("""
                SELECT * FROM books 
                WHERE LOWER(title) LIKE ? 
                   OR LOWER(authors) LIKE ? 
                   OR LOWER(tags) LIKE ?
                ORDER BY id
            """.trimIndent()).use { stmt ->
                val pattern = "%$lowerQuery%"
                stmt.setString(1, pattern)
                stmt.setString(2, pattern)
                stmt.setString(3, pattern)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(resultSetToMetadata(rs))
                    }
                }
            }
        }
        return results
    }
    
    fun getAllBooks(): List<Metadata> {
        val results = mutableListOf<Metadata>()
        getConnection().use { conn ->
            conn.createStatement().executeQuery("SELECT * FROM books ORDER BY id").use { rs ->
                while (rs.next()) {
                    results.add(resultSetToMetadata(rs))
                }
            }
        }
        return results
    }
    
    fun updateReadingProgress(id: Int, currentPage: Int, totalPages: Int = 0, position: String? = null) {
        getConnection().use { conn ->
            conn.prepareStatement("""
                UPDATE books 
                SET reading_progress_current_page = ?,
                    reading_progress_total_pages = ?,
                    reading_progress_last_read_position = ?,
                    reading_progress_last_read_date = ?,
                    date_modified = ?
                WHERE id = ?
            """.trimIndent()).use { stmt ->
                stmt.setInt(1, currentPage)
                stmt.setInt(2, totalPages)
                stmt.setString(3, position)
                stmt.setString(4, LocalDateTime.now().format(dateFormatter))
                stmt.setString(5, LocalDateTime.now().format(dateFormatter))
                stmt.setInt(6, id)
                stmt.executeUpdate()
            }
        }
    }
    
    fun addBookmark(id: Int, position: String, note: String? = null, color: String? = null): Bookmark {
        val bookmark = Bookmark(
            id = "${id}_${System.currentTimeMillis()}",
            position = position,
            note = note,
            color = color
        )
        
        getConnection().use { conn ->
            conn.prepareStatement("""
                INSERT INTO bookmarks (id, book_id, position, note, created_date, color)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, bookmark.id)
                stmt.setInt(2, id)
                stmt.setString(3, bookmark.position)
                stmt.setString(4, bookmark.note)
                stmt.setString(5, bookmark.createdDate.format(dateFormatter))
                stmt.setString(6, bookmark.color)
                stmt.executeUpdate()
            }
        }
        return bookmark
    }
    
    fun getBookmarks(id: Int): List<Bookmark> {
        val results = mutableListOf<Bookmark>()
        getConnection().use { conn ->
            conn.prepareStatement("SELECT * FROM bookmarks WHERE book_id = ? ORDER BY created_date").use { stmt ->
                stmt.setInt(1, id)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(Bookmark(
                            id = rs.getString("id"),
                            position = rs.getString("position"),
                            note = rs.getString("note"),
                            createdDate = LocalDateTime.parse(rs.getString("created_date"), dateFormatter),
                            color = rs.getString("color")
                        ))
                    }
                }
            }
        }
        return results
    }
    
    private fun resultSetToMetadata(rs: ResultSet): Metadata {
        val authorsStr = rs.getString("authors") ?: ""
        val tagsStr = rs.getString("tags") ?: ""
        
        val lastReadDateStr = rs.getString("reading_progress_last_read_date")
        val dateAddedStr = rs.getString("date_added")
        val dateModifiedStr = rs.getString("date_modified")
        
        return Metadata(
            id = rs.getInt("id"),
            title = rs.getString("title"),
            authors = if (authorsStr.isNotEmpty()) authorsStr.split(" & ") else mutableListOf(),
            series = rs.getString("series"),
            seriesIndex = rs.getDouble("series_index").takeIf { !rs.wasNull() },
            rating = rs.getDouble("rating").takeIf { !rs.wasNull() },
            tags = if (tagsStr.isNotEmpty()) tagsStr.split(",").toMutableList() else mutableListOf(),
            publisher = rs.getString("publisher"),
            isbn = rs.getString("isbn"),
            language = rs.getString("language"),
            description = rs.getString("description"),
            readingProgress = ReadingProgress(
                currentPage = rs.getInt("reading_progress_current_page"),
                totalPages = rs.getInt("reading_progress_total_pages"),
                lastReadPosition = rs.getString("reading_progress_last_read_position"),
                lastReadDate = lastReadDateStr?.let { LocalDateTime.parse(it, dateFormatter) },
                readingTimeMinutes = rs.getInt("reading_progress_reading_time_minutes")
            ),
            bookmarks = getBookmarks(rs.getInt("id")).toMutableList(),
            dateAdded = dateAddedStr?.let { LocalDateTime.parse(it, dateFormatter) } ?: LocalDateTime.now(),
            dateModified = dateModifiedStr?.let { LocalDateTime.parse(it, dateFormatter) } ?: LocalDateTime.now()
        )
    }
}
