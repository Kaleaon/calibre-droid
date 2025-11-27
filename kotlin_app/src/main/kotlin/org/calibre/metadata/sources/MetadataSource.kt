package org.calibre.metadata.sources

import org.calibre.metadata.Metadata

interface MetadataSource {
    val name: String
    
    /**
     * Search for books by query (title, author, isbn).
     * Returns a list of potential matches.
     */
    fun search(query: String): List<Metadata>
}
