package org.calibre.conversion

import java.io.File

/**
 * # Conversion Plugin Architecture
 * 
 * The conversion system uses a plugin-based architecture where input and output formats
 * are handled by separate plugins. All conversion goes through an intermediate OEB
 * (Open Ebook) format, which provides a normalized representation of book content.
 * 
 * ## Architecture Overview
 * 
 * ```
 * Input File (Format A) 
 *     → InputPlugin.convert() 
 *     → OebBook (intermediate format)
 *     → OutputPlugin.convert() 
 *     → Output File (Format B)
 * ```
 * 
 * This design allows any input format to be converted to any output format by
 * implementing the appropriate plugins.
 * 
 * ## OEB (Open Ebook) Format
 * 
 * The OEB format is an intermediate representation that includes:
 * - **Metadata**: Title, authors, publication date, etc.
 * - **Manifest**: All resources (HTML files, images, CSS, etc.) with their locations
 * - **Spine**: Ordered list of content items that form the reading order
 * 
 * ## Plugin Development
 * 
 * To add support for a new format:
 * 1. Implement `InputPlugin` for reading the format
 * 2. Implement `OutputPlugin` for writing the format
 * 3. Register the plugins in `ConversionPipeline`
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 */

/**
 * Interface for input format plugins.
 * 
 * Input plugins are responsible for reading a specific e-book format and converting
 * it into the OEB intermediate format. This allows any input format to be converted
 * to any output format.
 * 
 * ## Implementation Requirements
 * 
 * 1. **File Type Support**: Declare all file extensions this plugin can handle
 * 2. **Metadata Extraction**: Extract title, authors, and other metadata from the input
 * 3. **Content Extraction**: Extract text content and convert to HTML/XHTML
 * 4. **Resource Handling**: Extract and preserve images, CSS, fonts, and other resources
 * 5. **Spine Construction**: Determine the reading order of content items
 * 
 * ## Example
 * 
 * ```kotlin
 * class MyFormatInput : InputPlugin {
 *     override val name = "My Format Input"
 *     override val fileTypes = setOf("myf", "myformat")
 *     
 *     override fun convert(inputFile: File, workDir: File): OebBook {
 *         // Parse input file
 *         // Extract metadata
 *         // Extract content and resources
 *         // Build OebBook with manifest and spine
 *         return book
 *     }
 * }
 * ```
 * 
 * ## Error Handling
 * 
 * - Throw `UnsupportedOperationException` if the format requires features not yet implemented
 * - Throw `Exception` with descriptive messages for parsing errors
 * - Log warnings for recoverable issues (missing images, etc.)
 * 
 * @property name Human-readable name of the plugin (e.g., "EPUB Input")
 * @property fileTypes Set of file extensions this plugin handles (e.g., setOf("epub"))
 * 
 * @see OutputPlugin
 * @see OebBook
 * @see ConversionPipeline
 */
interface InputPlugin {
    /**
     * Human-readable name of this input plugin.
     * Used in logging and error messages.
     */
    val name: String
    
    /**
     * Set of file extensions (without dot) that this plugin can handle.
     * Extensions should be lowercase (e.g., "epub", "mobi", "pdf").
     */
    val fileTypes: Set<String>
    
    /**
     * Converts the input file into an OEB intermediate representation.
     * 
     * This method should:
     * 1. Parse the input file format
     * 2. Extract metadata (title, authors, etc.)
     * 3. Extract content and convert to HTML/XHTML
     * 4. Extract resources (images, CSS, fonts) to workDir
     * 5. Build the manifest mapping resource IDs to OebItems
     * 6. Build the spine (ordered list of content items)
     * 
     * @param inputFile The source file to convert. Must exist and be readable.
     * @param workDir Temporary directory for extracted resources. Plugin should create
     *                subdirectories as needed (e.g., "images/", "styles/"). Files in
     *                this directory will be cleaned up after conversion.
     * @return OebBook containing metadata, manifest, and spine
     * @throws Exception if the file cannot be parsed or is invalid
     * @throws UnsupportedOperationException if required features are not implemented
     */
    fun convert(inputFile: File, workDir: File): OebBook
}

/**
 * Interface for output format plugins.
 * 
 * Output plugins are responsible for converting the OEB intermediate format into
 * a specific e-book format. They read from the OebBook structure and write the
 * final output file.
 * 
 * ## Implementation Requirements
 * 
 * 1. **Format Specification**: Understand the target format's structure and requirements
 * 2. **Metadata Writing**: Write metadata to format-specific locations
 * 3. **Content Conversion**: Convert HTML/XHTML from OEB to format-specific markup
 * 4. **Resource Embedding**: Embed or reference images, CSS, and other resources
 * 5. **File Structure**: Create the proper file structure (ZIP for EPUB, binary for MOBI, etc.)
 * 
 * ## Example
 * 
 * ```kotlin
 * class MyFormatOutput : OutputPlugin {
 *     override val name = "My Format Output"
 *     override val fileType = "myf"
 *     
 *     override fun convert(book: OebBook, outputFile: File) {
 *         // Write format header
 *         // Write metadata
 *         // Convert and write content
 *         // Embed resources
 *         // Finalize file structure
 *     }
 * }
 * ```
 * 
 * ## Error Handling
 * 
 * - Throw `Exception` with descriptive messages for format errors
 * - Ensure output file is valid even if some resources are missing
 * - Log warnings for non-critical issues
 * 
 * @property name Human-readable name of the plugin (e.g., "EPUB Output")
 * @property fileType Single file extension (without dot) this plugin produces (e.g., "epub")
 * 
 * @see InputPlugin
 * @see OebBook
 * @see ConversionPipeline
 */
interface OutputPlugin {
    /**
     * Human-readable name of this output plugin.
     * Used in logging and error messages.
     */
    val name: String
    
    /**
     * Single file extension (without dot) that this plugin produces.
     * Should be lowercase (e.g., "epub", "mobi", "pdf").
     */
    val fileType: String
    
    /**
     * Converts the OEB book into the target format.
     * 
     * This method should:
     * 1. Read metadata from book.metadata
     * 2. Process content items from book.spine in order
     * 3. Reference resources from book.manifest as needed
     * 4. Write the complete output file to outputFile
     * 
     * The output file's parent directory will be created if it doesn't exist.
     * 
     * @param book The OEB book to convert, containing metadata, spine, and manifest
     * @param outputFile The destination file. Parent directory will be created if needed.
     * @throws Exception if conversion fails or the format cannot be written
     */
    fun convert(book: OebBook, outputFile: File)
}
