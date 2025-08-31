package com.calibre.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Represents the many-to-many relationship between Books and Authors.
 *
 * This is a Room Entity that serves as a cross-reference (or link) table.
 * It is based on the `books_authors_link` table from the original Calibre schema.
 *
 * The primary key is a composite of `book_id` and `author_id` to ensure
 * that each book-author pair is unique.
 */
@Entity(
    tableName = "book_author_cross_ref",
    primaryKeys = ["book_id", "author_id"],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Author::class,
            parentColumns = ["id"],
            childColumns = ["author_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["author_id"])
    ]
)
data class BookAuthorCrossRef(
    /**
     * The foreign key referencing the book's ID.
     * Corresponds to the `book` column in `books_authors_link`.
     */
    @ColumnInfo(name = "book_id")
    val bookId: Long,

    /**
     * The foreign key referencing the author's ID.
     * Corresponds to the `author` column in `books_authors_link`.
     */
    @ColumnInfo(name = "author_id")
    val authorId: Long
)
