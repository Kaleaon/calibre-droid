package com.calibre.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a tag that can be applied to a book.
 *
 * This is a Room Entity based on the `tags` table in the original Calibre schema.
 * Tags have a many-to-many relationship with Books.
 * The `name` is indexed to speed up searches.
 *
 * See `src/calibre/db/tables.py` in the original source for more details on `ManyToManyTable`.
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class Tag(
    /**
     * The unique identifier for the tag.
     * Corresponds to the `id` column in the `tags` table.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The name of the tag (e.g., "Fantasy", "Science Fiction").
     * Corresponds to the `name` column in the `tags` table.
     */
    val name: String
)
