package org.calibre.metadata

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class Library(
    private val storageFile: File = File("library.json"),
    private val libraryDir: File = File("library_files"),
    extraParsers: List<MetadataParser> = emptyList()
) {
    private val books: MutableMap<Int, Metadata> = mutableMapOf()
    private val nextId = AtomicInteger(1)
    private val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
    
    private val parsers = listOf(EpubParser(), MobiMetadataParser()) + extraParsers

    init {
        if (!libraryDir.exists()) {
            libraryDir.mkdirs()
        }
        load()
    }

    fun addBook(metadata: Metadata): Int {
        val id = nextId.getAndIncrement()
        val now = java.time.LocalDateTime.now()
        val newBook = metadata.copy(
            id = id,
            dateAdded = metadata.dateAdded.takeIf { it != java.time.LocalDateTime.MIN } ?: now,
            dateModified = now
        )
        books[id] = newBook
        save()
        return id
    }

    fun importBook(file: File): Int {
        if (!file.exists()) throw Exception("File not found: ${file.absolutePath}")
        
        val parser = parsers.find { it.canParse(file) }
        
        val metadata = if (parser != null) {
            try {
                parser.parseMetadata(file)
            } catch (e: Exception) {
                println("Warning: Could not parse metadata from ${file.name} (${e.message}). Using default.")
                Metadata(title = file.nameWithoutExtension)
            }
        } else {
            println("No parser found for ${file.extension}. Using filename as title.")
            Metadata(title = file.nameWithoutExtension)
        }

        val id = addBook(metadata)
        
        val extension = file.extension
        val destFile = File(libraryDir, "$id.$extension")
        try {
            file.copyTo(destFile, overwrite = true)
            println("Saved book file to: ${destFile.path}")
        } catch (e: Exception) {
            println("Error copying file: ${e.message}")
        }
        
        return id
    }

    fun getMetadata(id: Int): Metadata? {
        return books[id]
    }
    
    fun getBookFile(id: Int): File? {
        val files = libraryDir.listFiles { dir, name -> name.startsWith("$id.") }
        return files?.firstOrNull()
    }
    
    fun exportBook(id: Int, destDir: File) {
        val bookFile = getBookFile(id) ?: throw Exception("Book file for ID $id not found.")
        if (!destDir.exists()) destDir.mkdirs()
        
        val metadata = getMetadata(id)
        val author = metadata?.authors?.firstOrNull() ?: "Unknown"
        val title = metadata?.title ?: "Unknown"
        val extension = bookFile.extension
        
        val safeName = "$author - $title.$extension".replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        
        val destFile = File(destDir, safeName)
        bookFile.copyTo(destFile, overwrite = true)
        println("Exported to: ${destFile.absolutePath}")
    }

    fun removeBook(id: Int): Boolean {
        if (books.remove(id) != null) {
            save()
            val file = getBookFile(id)
            if (file != null && file.exists()) {
                file.delete()
            }
            return true
        }
        return false
    }

    fun search(query: String): List<Metadata> {
        val lowerQuery = query.lowercase()
        
        if (lowerQuery.contains(":")) {
            val parts = lowerQuery.split(":", limit = 2)
            val field = parts[0].trim()
            val value = parts[1].trim()
            
            return when (field) {
                "title" -> books.values.filter { it.title.lowercase().contains(value) }
                "author", "authors" -> books.values.filter { book -> book.authors.any { it.lowercase().contains(value) } }
                "tag", "tags" -> books.values.filter { book -> book.tags.any { it.lowercase().contains(value) } }
                "series" -> books.values.filter { it.series?.lowercase()?.contains(value) == true }
                "rating" -> {
                    val ratingValue = value.toDoubleOrNull()
                    if (ratingValue != null) {
                        books.values.filter { it.rating == ratingValue }
                    } else emptyList()
                }
                "read" -> {
                    when (value) {
                        "true", "yes", "1" -> books.values.filter { it.readingProgress.currentPage > 0 }
                        "false", "no", "0" -> books.values.filter { it.readingProgress.currentPage == 0 }
                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }.sortedBy { it.id }
        }

        return books.values.filter { book ->
            book.title.lowercase().contains(lowerQuery) ||
            book.authors.any { it.lowercase().contains(lowerQuery) } ||
            book.tags.any { it.lowercase().contains(lowerQuery) } ||
            book.series?.lowercase()?.contains(lowerQuery) == true
        }.sortedBy { it.id }
    }
    
    fun advancedSearch(
        title: String? = null,
        author: String? = null,
        series: String? = null,
        tags: List<String>? = null,
        minRating: Double? = null,
        maxRating: Double? = null,
        readStatus: Boolean? = null,
        sortBy: String = "title"
    ): List<Metadata> {
        var results = books.values.toList()
        
        title?.let { 
            val lower = it.lowercase()
            results = results.filter { it.title.lowercase().contains(lower) }
        }
        
        author?.let {
            val lower = it.lowercase()
            results = results.filter { book -> book.authors.any { auth -> auth.lowercase().contains(lower) } }
        }
        
        series?.let {
            val lower = it.lowercase()
            results = results.filter { it.series?.lowercase()?.contains(lower) == true }
        }
        
        tags?.let { tagList ->
            results = results.filter { book -> tagList.any { tag -> book.tags.any { bookTag -> bookTag.lowercase().contains(tag.lowercase()) } } }
        }
        
        minRating?.let { min ->
            results = results.filter { (it.rating ?: 0.0) >= min }
        }
        
        maxRating?.let { max ->
            results = results.filter { (it.rating ?: 0.0) <= max }
        }
        
        readStatus?.let { read ->
            results = if (read) {
                results.filter { it.readingProgress.currentPage > 0 }
            } else {
                results.filter { it.readingProgress.currentPage == 0 }
            }
        }
        
        return when (sortBy.lowercase()) {
            "title" -> results.sortedBy { it.title }
            "author" -> results.sortedBy { it.authors.firstOrNull() ?: "" }
            "date" -> results.sortedByDescending { it.dateAdded }
            "rating" -> results.sortedByDescending { it.rating ?: 0.0 }
            "progress" -> results.sortedByDescending { it.readingProgress.progressPercent }
            else -> results.sortedBy { it.id }
        }
    }
    
    fun updateReadingProgress(id: Int, currentPage: Int, totalPages: Int = 0, position: String? = null) {
        val book = books[id] ?: return
        book.readingProgress.currentPage = currentPage
        if (totalPages > 0) book.readingProgress.totalPages = totalPages
        book.readingProgress.lastReadPosition = position
        book.readingProgress.lastReadDate = java.time.LocalDateTime.now()
        book.dateModified = java.time.LocalDateTime.now()
        save()
    }
    
    fun addBookmark(id: Int, position: String, note: String? = null, color: String? = null): Bookmark {
        val book = books[id] ?: throw Exception("Book not found")
        val bookmark = Bookmark(
            id = "${id}_${System.currentTimeMillis()}",
            position = position,
            note = note,
            color = color
        )
        book.bookmarks.add(bookmark)
        book.dateModified = java.time.LocalDateTime.now()
        save()
        return bookmark
    }
    
    fun removeBookmark(id: Int, bookmarkId: String): Boolean {
        val book = books[id] ?: return false
        val removed = book.bookmarks.removeAll { it.id == bookmarkId }
        if (removed) {
            book.dateModified = java.time.LocalDateTime.now()
            save()
        }
        return removed
    }
    
    fun getBooksByTag(tag: String): List<Metadata> {
        return books.values.filter { it.tags.contains(tag) }
    }
    
    fun getRecentlyRead(limit: Int = 10): List<Metadata> {
        return books.values
            .filter { it.readingProgress.lastReadDate != null }
            .sortedByDescending { it.readingProgress.lastReadDate }
            .take(limit)
    }
    
    fun getReadingStatistics(): ReadingStatistics {
        val totalBooks = books.size
        val readBooks = books.values.count { it.readingProgress.currentPage > 0 }
        val totalReadingTime = books.values.sumOf { it.readingProgress.readingTimeMinutes }
        val totalBookmarks = books.values.sumOf { it.bookmarks.size }
        val averageRating = books.values.mapNotNull { it.rating }.average().takeIf { !it.isNaN() } ?: 0.0
        
        return ReadingStatistics(
            totalBooks = totalBooks,
            readBooks = readBooks,
            unreadBooks = totalBooks - readBooks,
            totalReadingTimeMinutes = totalReadingTime,
            totalBookmarks = totalBookmarks,
            averageRating = averageRating
        )
    }

    fun getAllBooks(): List<Metadata> {
        return books.values.sortedBy { it.id }
    }
    
    fun batchRemove(ids: List<Int>): Int {
        var removed = 0
        ids.forEach { id ->
            if (removeBook(id)) removed++
        }
        return removed
    }
    
    fun batchExport(ids: List<Int>, destDir: File): Int {
        var exported = 0
        ids.forEach { id ->
            try {
                exportBook(id, destDir)
                exported++
            } catch (e: Exception) {
                System.err.println("Failed to export book $id: ${e.message}")
            }
        }
        return exported
    }
    
    fun updateMetadata(id: Int, updateFn: (Metadata) -> Unit): Boolean {
        val book = books[id] ?: return false
        updateFn(book)
        book.dateModified = java.time.LocalDateTime.now()
        save()
        return true
    }
    
    fun setRating(id: Int, rating: Double) {
        updateMetadata(id) { it.rating = rating }
    }
    
    fun addTag(id: Int, tag: String) {
        updateMetadata(id) { 
            if (!it.tags.contains(tag)) {
                it.tags.add(tag)
            }
        }
    }
    
    fun removeTag(id: Int, tag: String) {
        updateMetadata(id) { it.tags.remove(tag) }
    }

    private fun save() {
        try {
            mapper.writeValue(storageFile, books.values.toList())
        } catch (e: Exception) {
            System.err.println("Error saving library: ${e.message}")
        }
    }

    private fun load() {
        if (storageFile.exists()) {
            try {
                val loadedBooks: List<Metadata> = mapper.readValue(storageFile)
                loadedBooks.forEach { book ->
                    val id = book.id ?: nextId.getAndIncrement()
                    if (id >= nextId.get()) {
                        nextId.set(id + 1)
                    }
                    books[id] = book.copy(id = id)
                }
            } catch (e: Exception) {
                System.err.println("Error loading library: ${e.message}")
            }
        }
    }
}
