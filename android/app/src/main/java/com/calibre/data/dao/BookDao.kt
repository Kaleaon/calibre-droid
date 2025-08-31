package com.calibre.data.dao

import androidx.room.*
import com.calibre.data.model.Book
import com.calibre.data.model.relations.BookWithAuthorsAndTags
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the [Book] entity.
 *
 * This interface defines the database operations for books, including
 * insertions, updates, deletions, and queries. It uses Kotlin's `suspend`
 * functions for non-blocking database access and `Flow` for reactive queries.
 *
 * The logic is inspired by the data retrieval and manipulation methods in
 * `calibre/db/cache.py` and `calibre/db/write.py` from the original source.
 */
@Dao
interface BookDao {

    /**
     * Inserts a single book into the database. If the book already exists,
     * the insertion is ignored.
     *
     * @param book The book to insert.
     * @return The row ID of the newly inserted book.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(book: Book): Long

    /**
     * Updates an existing book in the database.
     *
     * @param book The book to update.
     */
    @Update
    suspend fun updateBook(book: Book)

    /**
     * Deletes a book from the database.
     *
     * @param book The book to delete.
     */
    @Delete
    suspend fun deleteBook(book: Book)

    /**
     * Retrieves a single book with all its related authors, tags, and publishers by its ID.
     * This query uses a transaction to ensure atomicity.
     *
     * @param bookId The ID of the book to retrieve.
     * @return A [Flow] emitting the [BookWithAuthorsAndTags] or null if not found.
     */
    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId")
    fun getBookWithDetails(bookId: Long): Flow<BookWithAuthorsAndTags?>

    /**
     * Retrieves all books from the database, each with its full details.
     * The results are ordered by the sortable title.
     *
     * @return A [Flow] emitting a list of all [BookWithAuthorsAndTags].
     */
    @Transaction
    @Query("SELECT * FROM books ORDER BY sort_title ASC")
    fun getAllBooksWithDetails(): Flow<List<BookWithAuthorsAndTags>>
}
