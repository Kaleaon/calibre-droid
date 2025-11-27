package org.calibre.metadata

import java.time.LocalDateTime

// Copy of Metadata.kt for Android module
object Constants {
    const val UNKNOWN_TITLE = "Unknown"
    const val UNKNOWN_AUTHOR = "Unknown"
    const val UNDEFINED_LANGUAGE = "und"
}

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
)
