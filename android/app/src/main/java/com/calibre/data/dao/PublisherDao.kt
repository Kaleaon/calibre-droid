package com.calibre.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.calibre.data.model.BookPublisherCrossRef
import com.calibre.data.model.Publisher

/**
 * Data Access Object (DAO) for the [Publisher] entity.
 *
 * Defines database operations for publishers and their relationships with books.
 */
@Dao
interface PublisherDao {

    /**
     * Inserts a publisher into the database. If the publisher already exists (based on name),
     * the insertion is ignored.
     *
     * @param publisher The publisher to insert.
     * @return The row ID of the newly inserted publisher.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPublisher(publisher: Publisher): Long

    /**
     * Inserts a cross-reference to link a book and a publisher.
     * If the link already exists, it is ignored.
     *
     * @param crossRef The book-publisher cross-reference to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookPublisherCrossRef(crossRef: BookPublisherCrossRef)
}
