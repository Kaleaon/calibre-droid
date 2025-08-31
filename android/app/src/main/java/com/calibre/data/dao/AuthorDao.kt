package com.calibre.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.calibre.data.model.Author
import com.calibre.data.model.BookAuthorCrossRef

/**
 * Data Access Object (DAO) for the [Author] entity.
 *
 * Defines database operations for authors and their relationships with books.
 */
@Dao
interface AuthorDao {

    /**
     * Inserts an author into the database. If the author already exists (based on name),
     * the insertion is ignored.
     *
     * @param author The author to insert.
     * @return The row ID of the newly inserted author.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAuthor(author: Author): Long

    /**
     * Inserts a cross-reference to link a book and an author.
     * If the link already exists, it is ignored.
     *
     * @param crossRef The book-author cross-reference to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookAuthorCrossRef(crossRef: BookAuthorCrossRef)
}
