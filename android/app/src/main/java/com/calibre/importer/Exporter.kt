package com.calibre.importer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.calibre.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Handles the logic for exporting the app's Room database to a Calibre-compatible
 * `metadata.db` file.
 */
class Exporter(
    private val context: Context,
    private val outputDirectoryUri: Uri
) {

    /**
     * Executes the export process.
     * This function should be called from a background thread.
     */
    suspend fun export() = withContext(Dispatchers.IO) {
        val roomDb = AppDatabase.getInstance(context)
        var exportDb: SQLiteDatabase? = null
        val parentDirectory = DocumentFile.fromTreeUri(context, outputDirectoryUri)
            ?: throw IOException("Could not access the output directory.")

        // Create the new metadata.db file.
        val metadataFile = parentDirectory.createFile("application/x-sqlite3", "metadata.db")
            ?: throw IOException("Could not create metadata.db file.")

        try {
            // It's not possible to get a direct file path from a DocumentFile URI.
            // A common workaround is to copy to a temporary local file, operate on it,
            // and then stream it back.
            val tempDbFile = File(context.cacheDir, "export_metadata.db")
            if (tempDbFile.exists()) {
                tempDbFile.delete()
            }

            exportDb = SQLiteDatabase.openOrCreateDatabase(tempDbFile, null)

            // Step 1: Create the original Calibre schema.
            createCalibreSchema(exportDb)

            // Step 2: Fetch data from Room and insert into the new database.
            // This is a simplified example. A full implementation would need to handle all tables.
            val books = roomDb.bookDao().getAllBooksWithDetails().first() // Using .first() for simplicity
            exportDb.beginTransaction()
            try {
                for (bookWithDetails in books) {
                    val book = bookWithDetails.book
                    // Insert into books table
                    exportDb.execSQL(
                        "INSERT INTO books (id, title, sort, path, has_cover) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(book.id, book.title, book.sortTitle, book.path, if (book.hasCover) 1 else 0)
                    )
                    // ... and so on for authors, tags, links, etc.
                }
                exportDb.setTransactionSuccessful()
            } finally {
                exportDb.endTransaction()
            }

            // Step 3: Copy the temporary database back to the user-selected location.
            tempDbFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(metadataFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }

        } finally {
            exportDb?.close()
        }
    }

    private fun createCalibreSchema(db: SQLiteDatabase) {
        // All CREATE statements from resources/metadata_sqlite.sql would go here.
        // This is a sample. A full implementation would parse and execute all statements from the file.
        val statements = listOf(
            """CREATE TABLE authors ( id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, sort TEXT COLLATE NOCASE, link TEXT NOT NULL DEFAULT '', UNIQUE(name) )""",
            """CREATE TABLE books ( id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT 'Unknown' COLLATE NOCASE, sort TEXT COLLATE NOCASE, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, pubdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP, series_index REAL NOT NULL DEFAULT 1.0, author_sort TEXT COLLATE NOCASE, isbn TEXT DEFAULT '' COLLATE NOCASE, lccn TEXT DEFAULT '' COLLATE NOCASE, path TEXT NOT NULL DEFAULT '', flags INTEGER NOT NULL DEFAULT 1, uuid TEXT, has_cover BOOL DEFAULT 0, last_modified TIMESTAMP NOT NULL DEFAULT '2000-01-01 00:00:00+00:00')""",
            """CREATE TABLE books_authors_link ( id INTEGER PRIMARY KEY, book INTEGER NOT NULL, author INTEGER NOT NULL, UNIQUE(book, author) )""",
            """CREATE TABLE publishers ( id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, sort TEXT COLLATE NOCASE, link TEXT NOT NULL DEFAULT '', UNIQUE(name) )""",
            """CREATE TABLE books_publishers_link ( id INTEGER PRIMARY KEY, book INTEGER NOT NULL, publisher INTEGER NOT NULL, UNIQUE(book) )""",
            """CREATE TABLE series ( id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, sort TEXT COLLATE NOCASE, link TEXT NOT NULL DEFAULT '', UNIQUE (name) )""",
            """CREATE TABLE books_series_link ( id INTEGER PRIMARY KEY, book INTEGER NOT NULL, series INTEGER NOT NULL, UNIQUE(book) )""",
            """CREATE TABLE tags ( id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, link TEXT NOT NULL DEFAULT '', UNIQUE (name) )""",
            """CREATE TABLE books_tags_link ( id INTEGER PRIMARY KEY, book INTEGER NOT NULL, tag INTEGER NOT NULL, UNIQUE(book, tag) )"""
            // ... and all other CREATE TABLE, CREATE VIEW, CREATE INDEX, CREATE TRIGGER statements.
        )

        db.beginTransaction()
        try {
            statements.forEach { db.execSQL(it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
// We need to get the Flow's value, which is tricky in a non-composable context.
// This is a placeholder for how one might get the data.
// In a real app, this would be handled more gracefully.
suspend fun <T> kotlinx.coroutines.flow.Flow<T>.first(): T {
    var result: T? = null
    kotlinx.coroutines.flow.collect {
        result = it
        return@collect
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
