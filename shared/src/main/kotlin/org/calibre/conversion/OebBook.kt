package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File

data class OebBook(
    var metadata: Metadata,
    val spine: MutableList<OebItem> = mutableListOf(),
    val manifest: MutableMap<String, OebItem> = mutableMapOf() // id -> Item
)

data class OebItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val file: File // Location of the temporary extracted file
) {
    fun isImage(): Boolean {
        return mediaType.startsWith("image/")
    }

    fun isCss(): Boolean {
        return mediaType == "text/css"
    }

    fun isXhtml(): Boolean {
        return mediaType == "application/xhtml+xml" || mediaType == "text/html"
    }
}
