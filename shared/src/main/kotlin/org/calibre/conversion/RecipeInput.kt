package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File

/**
 * # Recipe Input Plugin
 * 
 * Reads recipe files (news feed recipes) and converts them to OEB format.
 * 
 * ## Recipe Format
 * 
 * Recipes are used to fetch news from RSS/Atom feeds:
 * - **Recipe files**: Define how to fetch and process news feeds
 * - **RSS/Atom**: Fetches content from web feeds
 * - **E-book generation**: Converts fetched articles into e-books
 * 
 * ## Implementation Status
 * 
 * **Note**: Recipe support is typically handled by the news fetching system,
 * not as a direct input format. Recipes are used to generate e-books from
 * web content rather than reading existing e-book files.
 * 
 * This is a placeholder implementation. Recipe processing is handled by
 * the news fetching subsystem.
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see InputPlugin
 * @see OebBook
 * @see org.calibre.news.NewsFetcher
 */
class RecipeInput : InputPlugin {
    override val name = "Recipe Input"
    override val fileTypes = setOf("recipe")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        throw UnsupportedOperationException(
            "Recipe input is handled by the news fetching system, not as a direct " +
            "conversion input. Recipes are used to generate e-books from web feeds. " +
            "Use the news fetching API to process recipes."
        )
    }
}
