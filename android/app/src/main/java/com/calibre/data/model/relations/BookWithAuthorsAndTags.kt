package com.calibre.data.model.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.calibre.data.model.*

/**
 * Represents a [Book] with all its associated [Author]s, [Tag]s, and [Publisher]s.
 *
 * This is a relational data class used by Room to query a book and its many-to-many
 * relationships in a single operation. It simplifies data retrieval by bundling
 * related entities together.
 */
data class BookWithAuthorsAndTags(
    @Embedded val book: Book,

    @Relation(
        parentColumn = "series_id",
        entityColumn = "id"
    )
    val series: Series?,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookAuthorCrossRef::class,
            parentColumn = "book_id",
            entityColumn = "author_id"
        )
    )
    val authors: List<Author>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookTagCrossRef::class,
            parentColumn = "book_id",
            entityColumn = "tag_id"
        )
    )
    val tags: List<Tag>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookPublisherCrossRef::class,
            parentColumn = "book_id",
            entityColumn = "publisher_id"
        )
    )
    val publishers: List<Publisher>
)
