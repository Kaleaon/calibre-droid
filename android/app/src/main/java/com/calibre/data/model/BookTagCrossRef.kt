package com.calibre.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Represents the many-to-many relationship between Books and Tags.
 *
 * This is a Room Entity that serves as a cross-reference (or link) table.
 * It is based on the `books_tags_link` table from the original Calibre schema.
 */
@Entity(
    tableName = "book_tag_cross_ref",
    primaryKeys = ["book_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["tag_id"])
    ]
)
data class BookTagCrossRef(
    /**
     * The foreign key referencing the book's ID.
     * Corresponds to the `book` column in `books_tags_link`.
     */
    @ColumnInfo(name = "book_id")
    val bookId: Long,

    /**
     * The foreign key referencing the tag's ID.
     * Corresponds to the `tag` column in `books_tags_link`.
     */
    @ColumnInfo(name = "tag_id")
    val tagId: Long
)
