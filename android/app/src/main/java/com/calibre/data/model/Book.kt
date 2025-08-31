package com.calibre.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single book in the Calibre library.
 *
 * This data class is designed as a Room Entity to be stored in the local Android database.
 * The structure is based on the `books` table and related one-to-one tables
 * from the original Calibre Python application's database schema.
 *
 * See original schema details in `src/calibre/db/tables.py` and the initial database
 * creation in `src/calibre/library/sqlite.py`.
 */
@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["series_id"])]
)
data class Book(
    /**
     * The unique identifier for the book. Corresponds to the `id` column in the `books` table.
     * This is the primary key for the table.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The title of the book.
     * Corresponds to the `title` column in the `books` table.
     */
    val title: String,

    /**
     * A sortable version of the title. For example, "The Lord of the Rings" might have
     * a sort title of "Lord of the Rings, The".
     * Corresponds to the `sort` column in the `books` table.
     */
    @ColumnInfo(name = "sort_title")
    val sortTitle: String?,

    /**
     * The timestamp of the last modification to this book's record.
     * Stored as milliseconds since the epoch.
     * Corresponds to the `timestamp` column in the `books` table.
     */
    @ColumnInfo(name = "last_modified")
    val lastModified: Long,

    /**
     * The original publication date of the book.
     * Stored as milliseconds since the epoch.
     * Corresponds to the `pubdate` column in the `books` table.
     */
    @ColumnInfo(name = "publication_date")
    val publicationDate: Long,

    /**
     * Foreign key for the series this book belongs to.
     * A null value indicates the book is not in a series.
     * Corresponds to the `series` column in the `books_series_link` table.
     */
    @ColumnInfo(name = "series_id")
    val seriesId: Long?,

    /**
     * The index of the book within a series, if it belongs to one.
     * Corresponds to the `series_index` column in the `books` table.
     */
    @ColumnInfo(name = "series_index")
    val seriesIndex: Double = 1.0,

    /**
     * A sortable version of the primary author's name.
     * Corresponds to the `author_sort` column in the `books` table.
     */
    @ColumnInfo(name = "author_sort")
    val authorSort: String?,

    /**
     * The International Standard Book Number.
     * Corresponds to the `isbn` column in the `books` table.
     */
    val isbn: String?,

    /**
     * A unique identifier for the book, often used for syncing.
     * Corresponds to the `uuid` column in the `identifiers` table linked to the book.
     */
    val uuid: String?,

    /**
     * A flag indicating whether the book has a cover image.
     * In the original schema, this is stored in the `has_cover` column.
     */
    @ColumnInfo(name = "has_cover")
    val hasCover: Boolean = false,

    /**
     * The relative path to the book's directory within the Calibre library folder structure.
     * Corresponds to the `path` column in the `books` table.
     */
    val path: String
)
