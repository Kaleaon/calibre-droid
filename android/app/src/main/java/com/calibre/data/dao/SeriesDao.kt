package com.calibre.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.calibre.data.model.Series

/**
 * Data Access Object (DAO) for the [Series] entity.
 *
 * Defines database operations for series.
 */
@Dao
interface SeriesDao {

    /**
     * Inserts a series into the database. If the series already exists (based on name),
     * the insertion is ignored.
     *
     * @param series The series to insert.
     * @return The row ID of the newly inserted series.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeries(series: Series): Long
}
