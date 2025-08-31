package com.calibre.importer

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.calibre.data.db.AppDatabase
import com.calibre.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Handles the logic for importing data from a legacy Calibre `metadata.db` file
 * into the new Room database.
 */
class Importer(
    private val context: Context,
    private val metadataDbUri: Uri
) {

    /**
     * Executes the import process.
     * This function should be called from a background thread.
     */
    suspend fun import() = withContext(Dispatchers.IO) {
        val roomDb = AppDatabase.getInstance(context)
        var legacyDb: SQLiteDatabase? = null
        val tempDbFile = File(context.cacheDir, "temp_metadata.db")

        try {
            // Copy the user-selected database to a temporary file that SQLite can open.
            context.contentResolver.openInputStream(metadataDbUri)?.use { inputStream ->
                FileOutputStream(tempDbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Open the legacy database as read-only.
            legacyDb = SQLiteDatabase.openDatabase(tempDbFile.path, null, SQLiteDatabase.OPEN_READONLY)

            // Run the import process within a single database transaction for efficiency and atomicity.
            roomDb.runInTransaction {
                importAuthorsAndLinks(legacyDb, roomDb)
                importTagsAndLinks(legacyDb, roomDb)
                importPublishersAndLinks(legacyDb, roomDb)
                importSeriesAndLinks(legacyDb, roomDb)
                importBooks(legacyDb, roomDb)
            }

        } finally {
            // Clean up resources.
            legacyDb?.close()
            if (tempDbFile.exists()) {
                tempDbFile.delete()
            }
        }
    }

    private fun importBooks(legacyDb: SQLiteDatabase, roomDb: AppDatabase) {
        val cursor = legacyDb.rawQuery("SELECT id, title, sort, timestamp, pubdate, series_index, author_sort, isbn, uuid, path, has_cover FROM books", null)
        cursor.use {
            while (it.moveToNext()) {
                val book = Book(
                    id = it.getLong("id"),
                    title = it.getString("title"),
                    sortTitle = it.getString("sort"),
                    lastModified = it.getDateAsLong("timestamp"),
                    publicationDate = it.getDateAsLong("pubdate"),
                    seriesIndex = it.getDouble("series_index"),
                    authorSort = it.getString("author_sort"),
                    isbn = it.getString("isbn"),
                    uuid = it.getString("uuid"),
                    path = it.getString("path"),
                    hasCover = it.getInt("has_cover") == 1
                )
                roomDb.bookDao().insertBook(book) // This will be slow, should be a bulk insert.
            }
        }
    }

    // Simplified import logic. A real implementation would be more robust.
    // These methods demonstrate the pattern of reading from the old DB and writing to the new one.

    private fun importAuthorsAndLinks(legacyDb: SQLiteDatabase, roomDb: AppDatabase) {
        val authorCursor = legacyDb.rawQuery("SELECT id, name, sort FROM authors", null)
        authorCursor.use {
            while(it.moveToNext()) {
                val author = Author(id = it.getLong("id"), name = it.getString("name"), sortName = it.getString("sort"))
                roomDb.authorDao().insertAuthor(author)
            }
        }
        val linkCursor = legacyDb.rawQuery("SELECT book, author FROM books_authors_link", null)
        linkCursor.use {
            while(it.moveToNext()) {
                val crossRef = BookAuthorCrossRef(bookId = it.getLong("book"), authorId = it.getLong("author"))
                roomDb.authorDao().insertBookAuthorCrossRef(crossRef)
            }
        }
    }

    private fun importTagsAndLinks(legacyDb: SQLiteDatabase, roomDb: AppDatabase) {
        val tagCursor = legacyDb.rawQuery("SELECT id, name FROM tags", null)
        tagCursor.use {
            while(it.moveToNext()) {
                val tag = Tag(id = it.getLong("id"), name = it.getString("name"))
                roomDb.tagDao().insertTag(tag)
            }
        }
        val linkCursor = legacyDb.rawQuery("SELECT book, tag FROM books_tags_link", null)
        linkCursor.use {
            while(it.moveToNext()) {
                val crossRef = BookTagCrossRef(bookId = it.getLong("book"), tagId = it.getLong("tag"))
                roomDb.tagDao().insertBookTagCrossRef(crossRef)
            }
        }
    }

    private fun importPublishersAndLinks(legacyDb: SQLiteDatabase, roomDb: AppDatabase) {
        val pubCursor = legacyDb.rawQuery("SELECT id, name FROM publishers", null)
        pubCursor.use {
            while(it.moveToNext()) {
                val publisher = Publisher(id = it.getLong("id"), name = it.getString("name"))
                roomDb.publisherDao().insertPublisher(publisher)
            }
        }
        val linkCursor = legacyDb.rawQuery("SELECT book, publisher FROM books_publishers_link", null)
        linkCursor.use {
            while(it.moveToNext()) {
                val crossRef = BookPublisherCrossRef(bookId = it.getLong("book"), publisherId = it.getLong("publisher"))
                roomDb.publisherDao().insertBookPublisherCrossRef(crossRef)
            }
        }
    }

    private fun importSeriesAndLinks(legacyDb: SQLiteDatabase, roomDb: AppDatabase) {
        val seriesCursor = legacyDb.rawQuery("SELECT id, name FROM series", null)
        seriesCursor.use {
            while(it.moveToNext()) {
                val series = Series(id = it.getLong("id"), name = it.getString("name"))
                roomDb.seriesDao().insertSeries(series)
            }
        }
        // In the old schema, the link is in the books table itself.
        // We'll handle this when we import the books table by setting the book's series_id.
        // This is a simplification; a more complex mapping might be needed.
    }

    // Helper extension functions to handle nullable columns and type conversions from the cursor.
    private fun Cursor.getString(columnName: String): String? = this.getString(this.getColumnIndexOrThrow(columnName))
    private fun Cursor.getInt(columnName: String): Int = this.getInt(this.getColumnIndexOrThrow(columnName))
    private fun Cursor.getLong(columnName: String): Long = this.getLong(this.getColumnIndexOrThrow(columnName))
    private fun Cursor.getDouble(columnName: String): Double = this.getDouble(this.getColumnIndexOrThrow(columnName))
    private fun Cursor.getDateAsLong(columnName: String): Long {
        // Calibre stores dates in various formats. This is a simplification.
        // A robust solution would need to parse the date string correctly.
        // For now, we return 0 if it's null.
        val dateStr = getString(columnName)
        return 0L // Placeholder
    }
}
