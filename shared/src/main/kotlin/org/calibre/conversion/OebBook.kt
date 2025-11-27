package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File

/**
 * # OEB (Open Ebook) Book - Intermediate Format
 * 
 * The OEB format is the intermediate representation used in the conversion pipeline.
 * It provides a normalized structure that all input plugins produce and all output
 * plugins consume, enabling format-to-format conversion.
 * 
 * ## Structure
 * 
 * - **metadata**: Book metadata (title, authors, publication date, etc.)
 * - **manifest**: Map of all resources (HTML, images, CSS, etc.) by ID
 * - **spine**: Ordered list of content items that form the reading order
 * 
 * ## Usage
 * 
 * Input plugins create an OebBook by:
 * 1. Extracting metadata from the source file
 * 2. Extracting content and resources to temporary files
 * 3. Creating OebItems for each resource
 * 4. Adding items to the manifest
 * 5. Building the spine in reading order
 * 
 * Output plugins consume an OebBook by:
 * 1. Reading metadata for format-specific metadata sections
 * 2. Processing spine items in order for content
 * 3. Referencing manifest items for resources (images, CSS, etc.)
 * 
 * ## Resource Management
 * 
 * All files referenced in OebItems are temporary files in a work directory.
 * The conversion pipeline is responsible for cleanup after conversion completes.
 * 
 * @property metadata Book metadata (title, authors, publication date, etc.)
 * @property spine Ordered list of content items in reading order. Items should be
 *                 HTML/XHTML content files that form the book's main content.
 * @property manifest Map of resource ID to OebItem. Includes all resources:
 *                    - Content files (HTML/XHTML) - also in spine
 *                    - Images (JPEG, PNG, GIF, etc.)
 *                    - Stylesheets (CSS)
 *                    - Fonts (if supported)
 *                    - Other resources (audio, video, etc.)
 * 
 * @see OebItem
 * @see InputPlugin
 * @see OutputPlugin
 * @see ConversionPipeline
 */
data class OebBook(
    /**
     * Book metadata including title, authors, publication date, description, etc.
     * This is extracted from the input file and used by output plugins to populate
     * format-specific metadata sections.
     */
    var metadata: Metadata,
    
    /**
     * Ordered list of content items that form the book's reading order.
     * Items are processed in sequence by output plugins to generate the final content.
     * Typically contains HTML/XHTML files representing chapters or sections.
     * 
     * The spine determines the order in which content appears in the output.
     * Items in the spine should also be present in the manifest.
     */
    val spine: MutableList<OebItem> = mutableListOf(),
    
    /**
     * Map of all resources in the book, keyed by resource ID.
     * 
     * The manifest includes:
     * - All content files (also referenced in spine)
     * - Images referenced in content
     * - Stylesheets (CSS files)
     * - Fonts and other resources
     * 
     * Resources are identified by unique IDs, which are typically used in
     * HTML href/src attributes. The href property provides the relative path
     * to the resource file.
     */
    val manifest: MutableMap<String, OebItem> = mutableMapOf() // id -> Item
)

/**
 * Represents a single resource item in an OEB book.
 * 
 * OebItems can represent:
 * - Content files (HTML/XHTML chapters)
 * - Images (JPEG, PNG, GIF, WebP, etc.)
 * - Stylesheets (CSS files)
 * - Fonts and other media
 * 
 * Each item has:
 * - **id**: Unique identifier used in references
 * - **href**: Relative path/URL to the resource
 * - **mediaType**: MIME type of the resource
 * - **file**: Physical file location in the work directory
 * 
 * @property id Unique identifier for this resource. Used in HTML references
 *              (e.g., href="#chapter1", src="images/cover.jpg"). Should be
 *              a valid XML/HTML ID (alphanumeric, hyphens, underscores).
 * @property href Relative path or URL to this resource. Used in HTML links
 *                and references. Should be relative to the book root.
 * @property mediaType MIME type of the resource (e.g., "application/xhtml+xml",
 *                      "image/jpeg", "text/css"). Used by output plugins to
 *                      determine how to handle the resource.
 * @property file Physical file location in the temporary work directory.
 *                This file exists during conversion and is cleaned up afterward.
 *                The file should match the href path structure.
 * 
 * @see OebBook
 */
data class OebItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val file: File // Location of the temporary extracted file
) {
    /**
     * Checks if this item is an image resource.
     * 
     * @return true if mediaType starts with "image/"
     */
    fun isImage(): Boolean {
        return mediaType.startsWith("image/")
    }

    /**
     * Checks if this item is a CSS stylesheet.
     * 
     * @return true if mediaType is "text/css"
     */
    fun isCss(): Boolean {
        return mediaType == "text/css"
    }

    /**
     * Checks if this item is an HTML/XHTML content file.
     * 
     * @return true if mediaType is "application/xhtml+xml" or "text/html"
     */
    fun isXhtml(): Boolean {
        return mediaType == "application/xhtml+xml" || mediaType == "text/html"
    }
}
