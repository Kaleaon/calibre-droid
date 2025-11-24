package org.calibre.metadata

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class Library(private val storageFile: File = File("library.json")) {
    private val books: MutableMap<Int, Metadata> = mutableMapOf()
    private val nextId = AtomicInteger(1)
    private val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)

    init {
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

    fun removeBook(id: Int): Boolean {
        if (books.remove(id) != null) {
            save()
            return true
        }
        return false
    }

    fun search(query: String): List<Metadata> {
        val lowerQuery = query.lowercase()
        return books.values.filter { book ->
            book.title.lowercase().contains(lowerQuery) ||
            book.authors.any { it.lowercase().contains(lowerQuery) } ||
            book.tags.any { it.lowercase().contains(lowerQuery) }
        }.toList()
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
