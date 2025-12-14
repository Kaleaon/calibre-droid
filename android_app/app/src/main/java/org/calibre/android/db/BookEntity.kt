package org.calibre.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val title: String,
    val authors: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val series: String? = null,
    val seriesIndex: Double? = null,
    val comments: String? = null,
    val rating: Double? = null,

    val fileExtension: String? = null,
    val originalFileName: String? = null,

    val dateAdded: LocalDateTime? = null,
    val dateModified: LocalDateTime? = null,

    // Reading progress (simple fields; keep it compatible with existing UI)
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val lastReadPosition: String? = null,
    val lastReadDate: LocalDateTime? = null,
    val readingTimeMinutes: Int = 0,

    // Bookmarks serialized as JSON in a single column (keeps schema simple)
    val bookmarksJson: String = "[]"
)

