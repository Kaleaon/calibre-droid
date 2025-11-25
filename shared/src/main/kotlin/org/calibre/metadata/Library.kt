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
        val newBook = metadata.copy(id = id)
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
                else -> emptyList()
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
