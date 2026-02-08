package org.calibre.android

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.calibre.android.db.AppDatabase
import org.calibre.android.db.BookEntity
import org.calibre.metadata.Bookmark
import org.calibre.metadata.EpubParser
import org.calibre.metadata.Metadata
import org.calibre.metadata.MetadataParser
import org.calibre.metadata.MobiMetadataParser
import org.calibre.metadata.ReadingProgress
import org.calibre.metadata.ReadingStatistics
import org.calibre.search.FullTextSearch
import org.calibre.utils.Logger
import java.io.File
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android-specific Library implementation backed by Room + internal file storage.
 *
 * This replaces the JSON-only persistence model and enables safe backups/restores.
 */
class AndroidLibrary(
    private val context: Context,
    private val extraParsers: List<MetadataParser> = emptyList(),
    enableFts: Boolean = true
) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.bookDao()
    private val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .enable(SerializationFeature.INDENT_OUTPUT)

    private val parsers = listOf(EpubParser(), MobiMetadataParser()) + extraParsers

    private val libraryDir: File = File(context.filesDir, "library_files")
    private val legacyStorageFile: File = File(context.filesDir, "library.json")

    private val fts: FullTextSearch? = if (enableFts) FullTextSearch(libraryDir) else null

    init {
        if (!libraryDir.exists()) libraryDir.mkdirs()
        migrateLegacyJsonIfNeeded()
    }

    private fun migrateLegacyJsonIfNeeded() {
        if (!legacyStorageFile.exists()) return
        runBlocking {
            val existing = withContext(Dispatchers.IO) { dao.getAll() }
            if (existing.isNotEmpty()) return@runBlocking

            try {
                val importedBooks: List<Metadata> = mapper.readValue(legacyStorageFile)
                withContext(Dispatchers.IO) {
                    importedBooks.forEach { meta ->
                        val id = meta.id ?: 0
                        if (id <= 0) return@forEach
                        val bookFile = getBookFile(id)
                        val ext = bookFile?.extension
                        dao.insert(
                            metaToEntity(meta, fileExtension = ext, originalFileName = null)
                        )
                        // Index if possible
                        if (bookFile != null && bookFile.exists()) {
                            fts?.indexBook(meta, bookFile)
                        }
                    }
                }
                Logger.info("Migrated legacy JSON library into Room database")
            } catch (e: Exception) {
                Logger.error("Failed to migrate legacy library.json: ${e.message}", e)
            }
        }
    }

    fun getAllBooks(): List<Metadata> = runBlocking {
        withContext(Dispatchers.IO) { dao.getAll().map { entityToMeta(it) } }
    }

    fun getMetadata(id: Int): Metadata? = runBlocking {
        withContext(Dispatchers.IO) { dao.getById(id)?.let { entityToMeta(it) } }
    }

    fun getBookFile(id: Int): File? {
        val files = libraryDir.listFiles { _, name -> name.startsWith("$id.") }
        return files?.firstOrNull()
    }

    /**
     * Import a book from a local file. The file contents will be copied into internal storage.
     * Returns the new book ID.
     */
    fun importBook(file: File, originalFileName: String? = null): Int {
        if (!file.exists()) throw Exception("File not found: ${file.absolutePath}")

        val fallbackTitle = (originalFileName?.substringBeforeLast('.', originalFileName) ?: file.nameWithoutExtension)

        val parser = parsers.find { it.canParse(file) }
        val metadata = if (parser != null) {
            try {
                parser.parseMetadata(file)
            } catch (e: Exception) {
                Logger.warn("Could not parse metadata from ${file.name} (${e.message}). Using default.", e)
                Metadata(title = fallbackTitle)
            }
        } else {
            Logger.warn("No parser found for ${file.extension}. Using filename as title.")
            Metadata(title = fallbackTitle)
        }

        val now = LocalDateTime.now()
        metadata.dateAdded = now
        metadata.dateModified = now

        val extension = file.extension.ifBlank { "bin" }

        val newId = runBlocking {
            withContext(Dispatchers.IO) {
                val rowId = dao.insert(
                    metaToEntity(
                        metadata,
                        fileExtension = extension,
                        originalFileName = originalFileName ?: file.name
                    ).copy(id = 0)
                )
                rowId.toInt()
            }
        }

        val destFile = File(libraryDir, "$newId.$extension")
        file.copyTo(destFile, overwrite = true)

        // Index for full-text search
        fts?.indexBook(metadata.copy(id = newId), destFile)

        return newId
    }

    fun removeBook(id: Int): Boolean = runBlocking {
        withContext(Dispatchers.IO) {
            val deleted = dao.deleteById(id) > 0
            if (deleted) {
                fts?.removeBook(id)
                getBookFile(id)?.delete()
            }
            deleted
        }
    }

    fun fullTextSearch(query: String, maxResults: Int = 50): List<Metadata> {
        val localFts = fts ?: return emptyList()
        val results = localFts.search(query, maxResults)
        val ids = results.map { it.bookId }.toSet()
        return getAllBooks().filter { it.id != null && ids.contains(it.id) }
    }

    fun search(query: String): List<Metadata> {
        val lowerQuery = query.trim().lowercase()
        if (lowerQuery.isBlank()) return getAllBooks()

        // Keep the same "field:value" behavior as the legacy Library
        if (lowerQuery.contains(":")) {
            val parts = lowerQuery.split(":", limit = 2)
            val field = parts[0].trim()
            val value = parts[1].trim()
            val all = getAllBooks()
            return when (field) {
                "title" -> all.filter { it.title.lowercase().contains(value) }
                "author", "authors" -> all.filter { book -> book.authors.any { it.lowercase().contains(value) } }
                "tag", "tags" -> all.filter { book -> book.tags.any { it.lowercase().contains(value) } }
                "series" -> all.filter { it.series?.lowercase()?.contains(value) == true }
                "rating" -> {
                    val ratingValue = value.toDoubleOrNull()
                    if (ratingValue != null) all.filter { it.rating == ratingValue } else emptyList()
                }
                "read" -> when (value) {
                    "true", "yes", "1" -> all.filter { it.readingProgress.currentPage > 0 }
                    "false", "no", "0" -> all.filter { it.readingProgress.currentPage == 0 }
                    else -> emptyList()
                }
                else -> emptyList()
            }.sortedBy { it.id }
        }

        return runBlocking {
            withContext(Dispatchers.IO) {
                dao.searchSimple(lowerQuery).map { entityToMeta(it) }
            }
        }
    }

    fun updateReadingProgress(id: Int, currentPage: Int, totalPages: Int = 0, position: String? = null) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val entity = dao.getById(id) ?: return@withContext
                val updated = entity.copy(
                    currentPage = currentPage,
                    totalPages = if (totalPages > 0) totalPages else entity.totalPages,
                    lastReadPosition = position,
                    lastReadDate = LocalDateTime.now(),
                    dateModified = LocalDateTime.now()
                )
                dao.update(updated)
            }
        }
    }

    fun setRating(id: Int, rating: Double) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val entity = dao.getById(id) ?: return@withContext
                dao.update(entity.copy(rating = rating, dateModified = LocalDateTime.now()))
            }
        }
    }

    fun addReadingTimeMinutes(id: Int, deltaMinutes: Int) {
        if (deltaMinutes <= 0) return
        runBlocking {
            withContext(Dispatchers.IO) {
                val entity = dao.getById(id) ?: return@withContext
                dao.update(
                    entity.copy(
                        readingTimeMinutes = entity.readingTimeMinutes + deltaMinutes,
                        dateModified = LocalDateTime.now()
                    )
                )
            }
        }
    }

    fun addTag(id: Int, tag: String) {
        val t = tag.trim()
        if (t.isEmpty()) return
        runBlocking {
            withContext(Dispatchers.IO) {
                val entity = dao.getById(id) ?: return@withContext
                if (entity.tags.any { it.equals(t, ignoreCase = true) }) return@withContext
                dao.update(entity.copy(tags = (entity.tags + t), dateModified = LocalDateTime.now()))
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
        runBlocking {
            withContext(Dispatchers.IO) {
                val entity = dao.getById(id) ?: throw Exception("Book not found")
                val list = readBookmarks(entity.bookmarksJson).toMutableList()
                list.add(bookmark)
                dao.update(entity.copy(bookmarksJson = mapper.writeValueAsString(list), dateModified = LocalDateTime.now()))
            }
        }
        return bookmark
    }

    fun getRecentlyRead(limit: Int = 10): List<Metadata> =
        getAllBooks()
            .filter { it.readingProgress.lastReadDate != null }
            .sortedByDescending { it.readingProgress.lastReadDate }
            .take(limit)

    fun getReadingStatistics(): ReadingStatistics {
        val all = getAllBooks()
        val totalBooks = all.size
        val readBooks = all.count { it.readingProgress.currentPage > 0 }
        val totalReadingTime = all.sumOf { it.readingProgress.readingTimeMinutes }
        val totalBookmarks = all.sumOf { it.bookmarks.size }
        val averageRating = all.mapNotNull { it.rating }.average().takeIf { !it.isNaN() } ?: 0.0
        return ReadingStatistics(
            totalBooks = totalBooks,
            readBooks = readBooks,
            unreadBooks = totalBooks - readBooks,
            totalReadingTimeMinutes = totalReadingTime,
            totalBookmarks = totalBookmarks,
            averageRating = averageRating
        )
    }

    /**
     * Portable backup: ZIP containing library.json + all book files under books/.
     */
    fun exportBackupZip(out: ZipOutputStream) {
        val all = getAllBooks()
        val json = mapper.writeValueAsBytes(all)

        out.putNextEntry(ZipEntry("library.json"))
        out.write(json)
        out.closeEntry()

        all.forEach { book ->
            val id = book.id ?: return@forEach
            val file = getBookFile(id) ?: return@forEach
            if (!file.exists()) return@forEach
            out.putNextEntry(ZipEntry("books/${file.name}"))
            file.inputStream().use { it.copyTo(out) }
            out.closeEntry()
        }
    }

    fun importBackupZip(input: ZipInputStream) {
        // Read all entries into memory/light temp files
        val extractedFiles = mutableMapOf<String, File>() // destName -> tmpFile
        var libraryJsonBytes: ByteArray? = null

        while (true) {
            val entry = input.nextEntry ?: break
            if (entry.isDirectory) continue
            when (entry.name) {
                "library.json" -> {
                    libraryJsonBytes = input.readBytes()
                }
                else -> {
                    if (entry.name.startsWith("books/")) {
                        val destName = entry.name.removePrefix("books/").substringAfterLast('/')
                        val tmp = File(context.cacheDir, "import_${System.currentTimeMillis()}_$destName")
                        tmp.outputStream().use { input.copyTo(it) }
                        extractedFiles[destName] = tmp
                    }
                }
            }
            input.closeEntry()
        }

        val metas: List<Metadata> =
            libraryJsonBytes?.let { mapper.readValue(it) } ?: throw Exception("Backup missing library.json")

        // Destructive restore (safe because user initiated restore)
        runBlocking {
            withContext(Dispatchers.IO) {
                dao.deleteAll()
            }
        }

        // Copy files first, then insert metadata preserving IDs where possible
        if (!libraryDir.exists()) libraryDir.mkdirs()
        libraryDir.listFiles()?.forEach { it.delete() }
        extractedFiles.forEach { (destName, tmp) ->
            val dest = File(libraryDir, destName)
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }

        runBlocking {
            withContext(Dispatchers.IO) {
                metas.forEach { meta ->
                    val id = meta.id ?: 0
                    val file = if (id > 0) getBookFile(id) else null
                    val ext = file?.extension
                    dao.insert(metaToEntity(meta, fileExtension = ext, originalFileName = null))
                    if (id > 0 && file != null && file.exists()) {
                        fts?.indexBook(meta, file)
                    }
                }
            }
        }
    }

    private fun readBookmarks(json: String): List<Bookmark> =
        try {
            mapper.readValue(json)
        } catch (_: Exception) {
            emptyList()
        }

    private fun entityToMeta(e: BookEntity): Metadata {
        val bookmarks: MutableList<Bookmark> =
            readBookmarks(e.bookmarksJson).toMutableList()
        val progress = ReadingProgress(
            currentPage = e.currentPage,
            totalPages = e.totalPages,
            lastReadPosition = e.lastReadPosition,
            lastReadDate = e.lastReadDate,
            readingTimeMinutes = e.readingTimeMinutes
        )
        return Metadata(
            id = e.id,
            title = e.title,
            authors = e.authors.toMutableList().ifEmpty { mutableListOf("Unknown") },
            tags = e.tags.toMutableList(),
            comments = e.comments,
            series = e.series,
            seriesIndex = e.seriesIndex,
            rating = e.rating,
            readingProgress = progress,
            bookmarks = bookmarks,
            dateAdded = e.dateAdded ?: LocalDateTime.now(),
            dateModified = e.dateModified ?: LocalDateTime.now()
        )
    }

    private fun metaToEntity(meta: Metadata, fileExtension: String?, originalFileName: String?): BookEntity {
        return BookEntity(
            id = meta.id ?: 0,
            title = meta.title,
            authors = meta.authors.toList(),
            tags = meta.tags.toList(),
            series = meta.series,
            seriesIndex = meta.seriesIndex,
            comments = meta.comments,
            rating = meta.rating,
            fileExtension = fileExtension,
            originalFileName = originalFileName,
            dateAdded = meta.dateAdded,
            dateModified = meta.dateModified,
            currentPage = meta.readingProgress.currentPage,
            totalPages = meta.readingProgress.totalPages,
            lastReadPosition = meta.readingProgress.lastReadPosition,
            lastReadDate = meta.readingProgress.lastReadDate,
            readingTimeMinutes = meta.readingProgress.readingTimeMinutes,
            bookmarksJson = mapper.writeValueAsString(meta.bookmarks)
        )
    }
}

