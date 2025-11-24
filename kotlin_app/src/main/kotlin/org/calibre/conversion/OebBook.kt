package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File

/**
 * Intermediate representation of a book in the conversion pipeline.
 * Similar to Calibre's OEB (Open eBook) structure.
 */
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
)
