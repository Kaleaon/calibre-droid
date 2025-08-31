package com.calibre.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an author of a book.
 *
 * This data class is a Room Entity based on the `authors` table in the original Calibre schema.
 * Authors have a many-to-many relationship with Books.
 * The `name` is indexed to speed up searches.
 *
 * See `src/calibre/db/tables.py` in the original source for more details on the `AuthorsTable`.
 */
@Entity(
    tableName = "authors",
    indices = [Index(value = ["name"], unique = true)]
)
data class Author(
    /**
     * The unique identifier for the author.
     * Corresponds to the `id` column in the `authors` table.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The author's full name.
     * Corresponds to the `name` column in the `authors` table.
     */
    val name: String,

    /**
     * A sortable version of the author's name.
     * For example, "John Ronald Reuel Tolkien" might be sorted as "Tolkien, John Ronald Reuel".
     * Corresponds to the `sort` column in the `authors` table.
     */
    @ColumnInfo(name = "sort_name")
    val sortName: String?
)
