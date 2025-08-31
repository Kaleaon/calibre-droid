package com.calibre.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a book series.
 *
 * This is a Room Entity based on the `series` table in the original Calibre schema.
 * In the original schema, this is a many-to-one relationship (many books can
 * belong to one series).
 *
 * See `src/calibre/db/tables.py` for details on `ManyToOneTable`.
 */
@Entity(
    tableName = "series",
    indices = [Index(value = ["name"], unique = true)]
)
data class Series(
    /**
     * The unique identifier for the series.
     * Corresponds to the `id` column in the `series` table.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The name of the series.
     * Corresponds to the `name` column in the `series` table.
     */
    val name: String
)
