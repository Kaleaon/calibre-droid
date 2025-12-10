package org.calibre.devices

import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Device driver for Kobo e-readers.
 * 
 * Supports:
 * - Kobo Clara
 * - Kobo Libra
 * - Kobo Forma
 * - Kobo Sage
 * - Kobo Elipsa
 * - Kobo Nia
 * - Kobo Aura
 * - Kobo Glo
 * - Other Kobo devices
 * 
 * When connected via USB, Kobo devices appear as mass storage.
 * Book metadata is stored in a SQLite database at .kobo/KoboReader.sqlite
 * 
 * Supported formats: EPUB, PDF, MOBI, TXT, HTML, RTF, CBZ, CBR
 */
class KoboDriver : DeviceDriver {
    
    override val name = "Kobo"
    override val description = "Kobo e-reader"
    override val supportedFormats = listOf("epub", "pdf", "mobi", "txt", "html", "rtf", "cbz", "cbr")
    
    private var koboPath: Path? = null
    private var databasePath: Path? = null
    
    init {
        detectDevice()
    }
    
    private fun detectDevice() {
        val possiblePaths = listOf(
            // Linux mount points
            "/media/${System.getProperty("user.name")}/KOBOeReader",
            "/media/KOBOeReader",
            "/mnt/kobo",
            "/run/media/${System.getProperty("user.name")}/KOBOeReader",
            // macOS mount points
            "/Volumes/KOBOeReader",
            // Windows drive letters
            "D:/", "E:/", "F:/", "G:/", "H:/"
        )
        
        for (pathStr in possiblePaths) {
            val path = Path.of(pathStr)
            if (isKoboDevice(path)) {
                koboPath = path
                databasePath = path.resolve(".kobo").resolve("KoboReader.sqlite")
                Logger.info("Kobo detected at: $path")
                break
            }
        }
    }
    
    private fun isKoboDevice(path: Path): Boolean {
        if (!Files.exists(path) || !Files.isDirectory(path)) return false
        
        // Check for Kobo-specific files
        val koboDir = path.resolve(".kobo")
        val database = koboDir.resolve("KoboReader.sqlite")
        
        return Files.exists(koboDir) && Files.exists(database)
    }
    
    override fun isConnected(): Boolean = koboPath != null && Files.exists(koboPath)
    
    override fun getFreeSpace(): Long = koboPath?.let { Files.getFileStore(it).usableSpace } ?: -1
    
    override fun getTotalSpace(): Long = koboPath?.let { Files.getFileStore(it).totalSpace } ?: -1
    
    override fun getBooks(): List<Metadata> {
        val dbPath = databasePath ?: return emptyList()
        if (!Files.exists(dbPath)) return emptyList()
        
        val books = mutableListOf<Metadata>()
        
        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT ContentID, Title, Attribution, Publisher, DateCreated, 
                           Series, SeriesNumber, ISBN, Description
                    FROM content 
                    WHERE ContentType = 6
                    AND Title IS NOT NULL
                    ORDER BY Title
                """
                
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        while (rs.next()) {
                            books.add(Metadata(
                                title = rs.getString("Title") ?: "Unknown",
                                authors = rs.getString("Attribution")?.split("&", ",")?.map { it.trim() } ?: emptyList(),
                                publisher = rs.getString("Publisher"),
                                series = rs.getString("Series"),
                                seriesIndex = rs.getString("SeriesNumber")?.toDoubleOrNull(),
                                isbn = rs.getString("ISBN"),
                                comments = rs.getString("Description"),
                                id = rs.getString("ContentID").hashCode()
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Error reading Kobo database: ${e.message}")
            
            // Fall back to file-based detection
            return getBooksByFileSystem()
        }
        
        return books
    }
    
    private fun getBooksByFileSystem(): List<Metadata> {
        val root = koboPath ?: return emptyList()
        
        return Files.walk(root)
            .filter { Files.isRegularFile(it) }
            .filter { !it.toString().contains(".kobo") }
            .filter { isEbookFile(it.fileName.toString()) }
            .map { file ->
                val name = file.fileName.toString().substringBeforeLast('.')
                Metadata(title = name, id = file.hashCode())
            }
            .toList()
    }
    
    private fun isEbookFile(name: String): Boolean {
        val ext = name.substringAfterLast('.').lowercase()
        return ext in supportedFormats
    }
    
    private fun getConnection(): Connection {
        val dbPath = databasePath ?: throw IllegalStateException("Kobo not connected")
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }
    
    override fun addBook(file: File, metadata: Metadata) {
        val kobo = koboPath ?: throw IllegalStateException("Kobo not connected")
        
        // Books go in root or organized by kepub folder structure
        val ext = file.extension.lowercase()
        val fileName = "${sanitizePath(metadata.title)}.${if (ext == "epub") "kepub.epub" else ext}"
        val destPath = kobo.resolve(fileName)
        
        // Copy file
        Files.copy(file.toPath(), destPath)
        Logger.info("Added book to Kobo: $fileName")
        
        // Update database
        updateDatabase(destPath, metadata)
    }
    
    private fun sanitizePath(name: String): String {
        return name
            .replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
    }
    
    private fun updateDatabase(bookPath: Path, metadata: Metadata) {
        try {
            getConnection().use { conn ->
                val relativePath = koboPath?.relativize(bookPath)?.toString() ?: return
                
                val sql = """
                    INSERT OR REPLACE INTO content 
                    (ContentID, ContentType, MimeType, Title, Attribution, Publisher, 
                     Series, SeriesNumber, ISBN, Description, DateCreated)
                    VALUES (?, 6, 'application/epub+zip', ?, ?, ?, ?, ?, ?, ?, datetime('now'))
                """
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, relativePath)
                    stmt.setString(2, metadata.title)
                    stmt.setString(3, metadata.authors.joinToString(", "))
                    stmt.setString(4, metadata.publisher)
                    stmt.setString(5, metadata.series)
                    stmt.setString(6, metadata.seriesIndex?.toString())
                    stmt.setString(7, metadata.isbn)
                    stmt.setString(8, metadata.comments)
                    
                    stmt.executeUpdate()
                }
                
                Logger.debug("Updated Kobo database for: ${metadata.title}")
            }
        } catch (e: Exception) {
            Logger.warn("Failed to update Kobo database: ${e.message}")
        }
    }
    
    override fun removeBook(id: String) {
        val kobo = koboPath ?: return
        
        try {
            getConnection().use { conn ->
                // Find the file path from database
                val sql = "SELECT ContentID FROM content WHERE ContentID LIKE ? OR rowid = ?"
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, "%$id%")
                    stmt.setString(2, id)
                    
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val contentId = rs.getString("ContentID")
                            val filePath = kobo.resolve(contentId)
                            
                            // Delete file
                            if (Files.exists(filePath)) {
                                Files.delete(filePath)
                            }
                            
                            // Delete from database
                            conn.createStatement().execute(
                                "DELETE FROM content WHERE ContentID = '$contentId'"
                            )
                            
                            Logger.info("Removed book from Kobo: $contentId")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Error removing book from Kobo: ${e.message}")
        }
    }
    
    /**
     * Gets reading progress for a book.
     */
    fun getReadingProgress(bookId: String): ReadingProgress? {
        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT ___PercentRead, ReadStatus, DateLastRead
                    FROM content
                    WHERE ContentID = ?
                """
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, bookId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return ReadingProgress(
                                percentRead = rs.getDouble("___PercentRead"),
                                isFinished = rs.getInt("ReadStatus") == 2,
                                lastRead = rs.getString("DateLastRead")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.debug("Error getting reading progress: ${e.message}")
        }
        
        return null
    }
    
    /**
     * Gets bookmarks for a book.
     */
    fun getBookmarks(bookId: String): List<Bookmark> {
        val bookmarks = mutableListOf<Bookmark>()
        
        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT BookmarkID, ContentID, Text, Annotation, DateCreated,
                           ChapterProgress, StartContainerPath
                    FROM Bookmark
                    WHERE ContentID LIKE ?
                    ORDER BY DateCreated
                """
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, "%$bookId%")
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            bookmarks.add(Bookmark(
                                id = rs.getString("BookmarkID"),
                                text = rs.getString("Text"),
                                annotation = rs.getString("Annotation"),
                                dateCreated = rs.getString("DateCreated"),
                                progress = rs.getDouble("ChapterProgress")
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.debug("Error getting bookmarks: ${e.message}")
        }
        
        return bookmarks
    }
    
    data class ReadingProgress(
        val percentRead: Double,
        val isFinished: Boolean,
        val lastRead: String?
    )
    
    data class Bookmark(
        val id: String,
        val text: String?,
        val annotation: String?,
        val dateCreated: String?,
        val progress: Double
    )
}
