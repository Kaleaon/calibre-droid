package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * # HTMLZ Input Plugin
 * 
 * Reads HTMLZ format files (HTML content in a ZIP archive) and converts them to OEB format.
 * 
 * ## HTMLZ Format
 * 
 * HTMLZ is a simple format that packages HTML content in a ZIP archive:
 * - **ZIP container**: Contains HTML files and resources
 * - **Index file**: Typically `index.html` at the top level
 * - **Resources**: Images, CSS, and other files referenced by the HTML
 * - **Optional OPF**: May include metadata.opf for book metadata
 * 
 * ## Supported Features
 * 
 * - Single HTML file extraction (index.html or first HTML file found)
 * - Resource extraction (images, CSS, etc.)
 * - Metadata extraction from optional OPF file
 * - Cover image extraction
 * - Encoding detection
 * 
 * ## File Structure
 * 
 * ```
 * HTMLZ File (ZIP)
 * ├── index.html (main content)
 * ├── images/ (image resources)
 * ├── style.css (optional CSS)
 * ├── metadata.opf (optional metadata)
 * └── cover.jpg (optional cover)
 * ```
 * 
 * ## Limitations
 * 
 * - Only processes the first/top-level HTML file found
 * - Multiple HTML files will trigger a warning
 * - Encoding detection may not always be accurate
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see InputPlugin
 * @see OebBook
 * @see HtmlzOutput
 */
class HtmlzInput : InputPlugin {
    /**
     * Human-readable name of this plugin.
     */
    override val name = "HTMLZ Input"
    
    /**
     * File extensions this plugin handles.
     */
    override val fileTypes = setOf("htmlz")
    
    /**
     * Converts an HTMLZ file to OEB format.
     * 
     * This method:
     * 1. Opens the HTMLZ file as a ZIP archive
     * 2. Extracts all files to the work directory
     * 3. Finds the main HTML file (index.html or first HTML file)
     * 4. Extracts metadata from optional OPF file
     * 5. Processes the HTML file using HtmlInput
     * 6. Builds the OEB book structure
     * 
     * @param inputFile The HTMLZ file to convert
     * @param workDir Temporary directory for extracted files
     * @return OebBook containing the converted content
     * @throws Exception if the file is invalid or no HTML file is found
     */
    override fun convert(inputFile: File, workDir: File): OebBook {
        val zip = ZipFile(inputFile)
        
        try {
            // Extract all files to work directory
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory) {
                    val destFile = File(workDir, entry.name)
                    destFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            
            // Find the main HTML file
            val htmlFile = findHtmlFile(workDir)
                ?: throw Exception("No HTML file found in HTMLZ archive")
            
            // Try to extract metadata from OPF if present
            val metadata = extractMetadataFromOpf(workDir) ?: Metadata(
                title = inputFile.nameWithoutExtension
            )
            
            // Use HtmlInput to process the HTML file
            val htmlInput = HtmlInput()
            val book = htmlInput.convert(htmlFile, workDir)
            
            // Override metadata if we found OPF metadata
            if (metadata.title != inputFile.nameWithoutExtension) {
                book.metadata = metadata
            }
            
            // Try to find and add cover image
            findAndAddCover(workDir, book)
            
            return book
            
        } finally {
            zip.close()
        }
    }
    
    /**
     * Finds the main HTML file in the extracted directory.
     * 
     * Looks for:
     * 1. index.html (preferred)
     * 2. index.xhtml
     * 3. index.htm
     * 4. First HTML file found
     * 
     * @param workDir The work directory containing extracted files
     * @return The main HTML file, or null if not found
     */
    private fun findHtmlFile(workDir: File): File? {
        // Check for index files first
        val indexFiles = listOf("index.html", "index.xhtml", "index.htm")
        for (indexName in indexFiles) {
            val indexFile = File(workDir, indexName)
            if (indexFile.exists()) {
                return indexFile
            }
        }
        
        // Find first HTML file
        workDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in listOf("html", "xhtml", "htm")) {
                    org.calibre.utils.Logger.warn(
                        "No index.html found, using first HTML file: ${file.name}"
                    )
                    return file
                }
            }
        }
        
        return null
    }
    
    /**
     * Extracts metadata from an optional OPF file.
     * 
     * @param workDir The work directory
     * @return Metadata if OPF file found, null otherwise
     */
    private fun extractMetadataFromOpf(workDir: File): Metadata? {
        val opfFiles = workDir.listFiles { _, name ->
            name.endsWith(".opf", ignoreCase = true)
        }
        
        if (opfFiles.isNullOrEmpty()) {
            return null
        }
        
        return try {
            // Use existing EpubParser to parse OPF metadata
            val opfFile = opfFiles.first()
            org.calibre.metadata.EpubParser().parseMetadata(opfFile)
        } catch (e: Exception) {
            org.calibre.utils.Logger.warn("Failed to parse OPF metadata: ${e.message}")
            null
        }
    }
    
    /**
     * Finds and adds cover image to the book if present.
     * 
     * @param workDir The work directory
     * @param book The OEB book to add cover to
     */
    private fun findAndAddCover(workDir: File, book: OebBook) {
        val coverNames = listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.gif")
        
        for (coverName in coverNames) {
            val coverFile = File(workDir, coverName)
            if (coverFile.exists()) {
                val mimeType = when (coverFile.extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }
                
                val item = OebItem("cover", coverName, mimeType, coverFile)
                book.manifest["cover"] = item
                org.calibre.utils.Logger.info("Found cover image: $coverName")
                return
            }
        }
    }
}
