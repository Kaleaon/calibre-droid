package org.calibre.metadata

import java.time.LocalDateTime

data class Metadata(
    var title: String = "Unknown",
    var authors: MutableList<String> = mutableListOf("Unknown"),
    var tags: MutableList<String> = mutableListOf(),
    var comments: String? = null,
    var series: String? = null,
    var seriesIndex: Double? = null,
    var rating: Double? = null,
    var publisher: String? = null,
    var pubDate: LocalDateTime? = null,
    var languages: MutableList<String> = mutableListOf("und"),
    var userMetadata: MutableMap<String, Any?> = mutableMapOf()
) {
    fun isNull(field: String): Boolean {
        return when (field) {
            "title" -> title == "Unknown"
            "authors" -> authors.size == 1 && authors[0] == "Unknown"
            "tags" -> tags.isEmpty()
            "comments" -> comments == null
            "series" -> series == null
            "rating" -> rating == null
            "publisher" -> publisher == null
            "languages" -> languages.isEmpty() || (languages.size == 1 && languages[0] == "und")
            else -> userMetadata[field] == null
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
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
