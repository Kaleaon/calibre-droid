package com.calibre.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.calibre.data.model.BookTagCrossRef
import com.calibre.data.model.Tag

/**
 * Data Access Object (DAO) for the [Tag] entity.
 *
 * Defines database operations for tags and their relationships with books.
 */
@Dao
interface TagDao {

    /**
     * Inserts a tag into the database. If the tag already exists (based on name),
     * the insertion is ignored.
     *
     * @param tag The tag to insert.
     * @return The row ID of the newly inserted tag.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    /**
     * Inserts a cross-reference to link a book and a tag.
     * If the link already exists, it is ignored.
     *
     * @param crossRef The book-tag cross-reference to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookTagCrossRef(crossRef: BookTagCrossRef)
}
