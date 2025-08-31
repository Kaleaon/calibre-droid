package com.calibre.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a book publisher.
 *
 * This is a Room Entity based on the `publishers` table in the original Calibre schema.
 * In the original schema, this is a many-to-one relationship, but to allow for
 * co-publishers and greater flexibility, we model it as many-to-many.
 *
 * See `src/calibre/db/tables.py` for details on `ManyToOneTable`.
 */
@Entity(
    tableName = "publishers",
    indices = [Index(value = ["name"], unique = true)]
)
data class Publisher(
    /**
     * The unique identifier for the publisher.
     * Corresponds to the `id` column in the `publishers` table.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The name of the publisher.
     * Corresponds to the `name` column in the `publishers` table.
     */
    val name: String
)
