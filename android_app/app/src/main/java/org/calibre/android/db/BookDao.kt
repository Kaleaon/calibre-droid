package org.calibre.android.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY id ASC")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Int): Int

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    @Query(
        """
        SELECT * FROM books
        WHERE lower(title) LIKE '%' || lower(:query) || '%'
           OR lower(authors) LIKE '%' || lower(:query) || '%'
           OR lower(tags) LIKE '%' || lower(:query) || '%'
           OR lower(coalesce(series,'')) LIKE '%' || lower(:query) || '%'
        ORDER BY id ASC
        """
    )
    suspend fun searchSimple(query: String): List<BookEntity>
}

