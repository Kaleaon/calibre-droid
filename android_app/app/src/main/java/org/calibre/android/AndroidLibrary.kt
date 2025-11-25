package org.calibre.android

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.calibre.metadata.Metadata
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class AndroidLibrary(private val context: Context) {
    private val storageFile = File(context.filesDir, "library.json")
    private val libraryDir = File(context.filesDir, "library_files")
    
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

    fun getMetadata(id: Int): Metadata? {
        return books[id]
    }
    
    fun getBookFile(id: Int): File? {
        val files = libraryDir.listFiles { dir, name -> name.startsWith("$id.") }
        return files?.firstOrNull()
    }

    fun getAllBooks(): List<Metadata> {
        return books.values.sortedBy { it.id }
    }
    
    fun search(query: String): List<Metadata> {
        val lowerQuery = query.lowercase()
        return books.values.filter { book ->
            book.title.lowercase().contains(lowerQuery) ||
            book.authors.any { it.lowercase().contains(lowerQuery) }
        }.sortedBy { it.id }
    }

    private fun save() {
        try {
            mapper.writeValue(storageFile, books.values.toList())
        } catch (e: Exception) {
            e.printStackTrace()
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
                e.printStackTrace()
            }
        }
    }
}
