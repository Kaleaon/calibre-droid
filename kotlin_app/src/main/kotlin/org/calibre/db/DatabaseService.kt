package org.calibre.db

import org.calibre.metadata.Metadata
import java.io.File
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DatabaseService(private val dbFile: File) {

    private fun getConnection() = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")

    fun loadMetadata(): List<Metadata> {
        val books = mutableListOf<Metadata>()
        if (!dbFile.exists()) return books

        val conn = getConnection()
        try {
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery("SELECT id, title, sort, pubdate, series_index FROM books")

            while (rs.next()) {
                val id = rs.getInt("id")
                val title = rs.getString("title")
                val pubDateStr = rs.getString("pubdate") // Often stored as string or timestamp
                val seriesIndex = rs.getDouble("series_index")

                val book = Metadata(
                    id = id,
                    title = title ?: "Unknown",
                    seriesIndex = seriesIndex
                )
                
                // Parse authors
                book.authors = getAuthorsForBook(conn, id).toMutableList()
                // Parse Series
                book.series = getSeriesForBook(conn, id)
                
                books.add(book)
            }
        } catch (e: Exception) {
            println("Error reading database: ${e.message}")
            e.printStackTrace()
        } finally {
            conn.close()
        }
        return books
    }

    private fun getAuthorsForBook(conn: java.sql.Connection, bookId: Int): List<String> {
        val authors = mutableListOf<String>()
        // books_authors_link (book, author) -> authors(id, name)
        val sql = """
            SELECT a.name 
            FROM authors a 
            JOIN books_authors_link bal ON a.id = bal.author 
            WHERE bal.book = ?
        """.trimIndent()
        
        val pstmt = conn.prepareStatement(sql)
        pstmt.setInt(1, bookId)
        val rs = pstmt.executeQuery()
        while (rs.next()) {
            authors.add(rs.getString("name"))
        }
        return authors
    }
    
    private fun getSeriesForBook(conn: java.sql.Connection, bookId: Int): String? {
        val sql = """
            SELECT s.name 
            FROM series s 
            JOIN books_series_link bsl ON s.id = bsl.series 
            WHERE bsl.book = ?
        """.trimIndent()
        
        val pstmt = conn.prepareStatement(sql)
        pstmt.setInt(1, bookId)
        val rs = pstmt.executeQuery()
        if (rs.next()) {
            return rs.getString("name")
        }
        return null
    }
    
    fun importToLibrary(library: org.calibre.metadata.Library) {
        val books = loadMetadata()
        var imported = 0
        for (book in books) {
            // We are only importing metadata here. 
            // To fully import, we'd need to copy files from the original library folder structure.
            // Calibre structure: Author/Title/Title - Author.epub
            // We can infer path from 'books.path' column if we add it to query.
            
            library.addBook(book)
            imported++
        }
        println("Imported $imported books from SQLite database.")
    }
}
