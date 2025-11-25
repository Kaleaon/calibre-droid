package org.calibre.metadata

import java.time.LocalDateTime

data class Metadata(
    var title: String = Constants.UNKNOWN_TITLE,
    var authors: MutableList<String> = mutableListOf(Constants.UNKNOWN_AUTHOR),
    var tags: MutableList<String> = mutableListOf(),
    var comments: String? = null,
    var series: String? = null,
    var seriesIndex: Double? = null,
    var rating: Double? = null,
    var publisher: String? = null,
    var pubDate: LocalDateTime? = null,
    var languages: MutableList<String> = mutableListOf(Constants.UNDEFINED_LANGUAGE),
    var userMetadata: MutableMap<String, Any?> = mutableMapOf(),
    var id: Int? = null
) {
    fun isNull(field: String): Boolean {
        return when (field) {
            "title" -> title == Constants.UNKNOWN_TITLE
            "authors" -> authors.size == 1 && authors[0] == Constants.UNKNOWN_AUTHOR
            "tags" -> tags.isEmpty()
            "comments" -> comments == null
            "series" -> series == null
            "rating" -> rating == null
            "publisher" -> publisher == null
            "languages" -> languages.isEmpty() || (languages.size == 1 && languages[0] == Constants.UNDEFINED_LANGUAGE)
            else -> userMetadata[field] == null
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        if (id != null) sb.append("ID: $id\n")
        sb.append("Title: $title\n")
        sb.append("Authors: ${authors.joinToString(", ")}\n")
        if (series != null) {
            sb.append("Series: $series #${seriesIndex ?: 1.0}\n")
        }
        if (tags.isNotEmpty()) {
            sb.append("Tags: ${tags.joinToString(", ")}\n")
        }
        if (comments != null) {
            sb.append("Comments: $comments\n")
        }
        if (publisher != null) {
             sb.append("Publisher: $publisher\n")
        }
        return sb.toString()
    }
}
