package com.calibre.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Represents the many-to-many relationship between Books and Publishers.
 *
 * This is a Room Entity that serves as a cross-reference (or link) table.
 * It is based on the `books_publishers_link` table from the original Calibre schema.
 */
@Entity(
    tableName = "book_publisher_cross_ref",
    primaryKeys = ["book_id", "publisher_id"],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Publisher::class,
            parentColumns = ["id"],
            childColumns = ["publisher_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["publisher_id"])
    ]
)
data class BookPublisherCrossRef(
    /**
     * The foreign key referencing the book's ID.
     * Corresponds to the `book` column in `books_publishers_link`.
     */
    @ColumnInfo(name = "book_id")
    val bookId: Long,

    /**
     * The foreign key referencing the publisher's ID.
     * Corresponds to the `publisher` column in `books_publishers_link`.
     */
    @ColumnInfo(name = "publisher_id")
    val publisherId: Long
)
