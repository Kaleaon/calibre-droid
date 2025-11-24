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
    private val libraryDir: File = File("library_files")
) {
    private val books: MutableMap<Int, Metadata> = mutableMapOf()
    private val nextId = AtomicInteger(1)
    private val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)

    init {
        if (!libraryDir.exists()) {
            libraryDir.mkdirs()
        }
        load()
    }

    fun addBook(metadata: Metadata): Int {
        val id = nextId.getAndIncrement()
        val newBook = metadata.copy(id = id)
        books[id] = newBook
        save()
        return id
    }

    fun importBook(file: File): Int {
        if (!file.exists()) throw Exception("File not found: ${file.absolutePath}")
        
        val parser = EpubParser()
        val metadata = try {
            parser.parseMetadata(file)
        } catch (e: Exception) {
            println("Warning: Could not parse metadata from EPUB (${e.message}). Using default.")
            Metadata(title = file.nameWithoutExtension)
        }

        val id = addBook(metadata)
        
        // Simple storage strategy: library_files/ID.epub
        val destFile = File(libraryDir, "$id.epub")
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
        val file = File(libraryDir, "$id.epub")
        return if (file.exists()) file else null
    }
    
    fun exportBook(id: Int, destDir: File) {
        val bookFile = getBookFile(id) ?: throw Exception("Book file for ID $id not found.")
        if (!destDir.exists()) destDir.mkdirs()
        
        val metadata = getMetadata(id)
        // Construct a nice filename: Author - Title.epub
        val author = metadata?.authors?.firstOrNull() ?: "Unknown"
        val title = metadata?.title ?: "Unknown"
        // Sanitize filename
        val safeName = "$author - $title.epub".replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        
        val destFile = File(destDir, safeName)
        bookFile.copyTo(destFile, overwrite = true)
        println("Exported to: ${destFile.absolutePath}")
    }

    fun removeBook(id: Int): Boolean {
        if (books.remove(id) != null) {
            save()
            // Also delete the file
            val file = File(libraryDir, "$id.epub")
            if (file.exists()) {
                file.delete()
            }
            return true
        }
        return false
    }

    fun search(query: String): List<Metadata> {
        val lowerQuery = query.lowercase()
        
        // Check for "field:value" syntax
        if (lowerQuery.contains(":")) {
            val parts = lowerQuery.split(":", limit = 2)
            val field = parts[0].trim()
            val value = parts[1].trim()
            
            return when (field) {
                "title" -> books.values.filter { it.title.lowercase().contains(value) }
                "author", "authors" -> books.values.filter { book -> book.authors.any { it.lowercase().contains(value) } }
                "tag", "tags" -> books.values.filter { book -> book.tags.any { it.lowercase().contains(value) } }
                else -> emptyList() // Unknown field
            }.sortedBy { it.id }
        }

        return books.values.filter { book ->
            book.title.lowercase().contains(lowerQuery) ||
            book.authors.any { it.lowercase().contains(lowerQuery) } ||
            book.tags.any { it.lowercase().contains(lowerQuery) }
        }.sortedBy { it.id }
    }

    fun getAllBooks(): List<Metadata> {
        return books.values.sortedBy { it.id }
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
                    // Ensure we don't overwrite existing IDs if we re-load
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
