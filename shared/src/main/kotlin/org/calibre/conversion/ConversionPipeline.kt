package org.calibre.conversion

import java.io.File

/**
 * # Conversion Pipeline
 * 
 * The central orchestrator for format-to-format e-book conversion.
 * 
 * The pipeline uses a plugin-based architecture where conversion happens in two stages:
 * 1. **Input Stage**: An `InputPlugin` reads the source format and converts it to OEB
 * 2. **Output Stage**: An `OutputPlugin` reads the OEB format and converts it to the target format
 * 
 * ## Conversion Flow
 * 
 * ```
 * Input File (e.g., EPUB)
 *     ↓
 * InputPlugin.convert() → OebBook (intermediate format)
 *     ↓
 * OutputPlugin.convert() → Output File (e.g., MOBI)
 * ```
 * 
 * This design allows any supported input format to be converted to any supported
 * output format by combining the appropriate plugins.
 * 
 * ## Supported Formats
 * 
 * ### Input Formats
 * - EPUB (.epub)
 * - MOBI/AZW (.mobi, .azw)
 * - AZW3 (.azw3) - KF8 format
 * - TXT (.txt, .text)
 * - DOCX (.docx)
 * - RTF (.rtf)
 * - FB2 (.fb2) - FictionBook 2.0
 * - HTML/OPF (.html, .opf)
 * - LIT (.lit) - Placeholder (requires full parser)
 * - LRF (.lrf) - Placeholder (requires full parser)
 * - PDB (.pdb) - Placeholder (requires full parser)
 * 
 * ### Output Formats
 * - EPUB (.epub)
 * - HTML (.html)
 * - TXT (.txt)
 * - PDF (.pdf)
 * - MOBI (.mobi) - Enhanced with PDB format, EXTH records, PalmDoc compression
 * - DOCX (.docx)
 * - FB2 (.fb2)
 * - RTF (.rtf)
 * 
 * ## Usage Example
 * 
 * ```kotlin
 * val pipeline = ConversionPipeline()
 * 
 * // Convert EPUB to MOBI
 * pipeline.convert(
 *     inputFile = File("book.epub"),
 *     outputFormat = "mobi",
 *     outputFile = File("book.mobi")
 * )
 * 
 * // List supported formats
 * val inputFormats = pipeline.getSupportedInputFormats()
 * val outputFormats = pipeline.getSupportedOutputFormats()
 * ```
 * 
 * ## Error Handling
 * 
 * The pipeline provides detailed error messages:
 * - File not found errors
 * - Unsupported format errors with list of supported formats
 * - Format-specific errors from plugins
 * - Clear messages for placeholder formats that need implementation
 * 
 * ## Resource Management
 * 
 * The pipeline automatically:
 * - Creates temporary work directories for conversion
 * - Cleans up temporary files after conversion (success or failure)
 * - Creates output directories if they don't exist
 * 
 * ## Thread Safety
 * 
 * This class is not thread-safe. Each conversion should use a separate instance
 * or be synchronized if used from multiple threads.
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see InputPlugin
 * @see OutputPlugin
 * @see OebBook
 */
class ConversionPipeline {
    private val inputPlugins = listOf(
        EpubInput(), 
        MobiInput(), 
        Azw3Input(), 
        TextInput(), 
        DocxInput(), 
        RtfInput(), 
        Fb2Input(),
        HtmlInput(),
        // Placeholder plugins (will throw UnsupportedOperationException with helpful message)
        LitInput(),
        LrfInput(),
        PdbInput()
    )
    private val outputPlugins = listOf(
        TextOutput(), 
        HtmlOutput(), 
        EpubOutput(), 
        PdfOutput(), 
        MobiOutput(),
        DocxOutput(),
        Fb2Output(),
        RtfOutput()
    )
    
    /**
     * Returns all file extensions supported as input formats.
     * 
     * This includes extensions from all registered input plugins.
     * Extensions are returned without leading dots and in lowercase.
     * 
     * @return Set of supported input file extensions (e.g., {"epub", "mobi", "pdf", "txt"})
     * 
     * @see getSupportedOutputFormats
     * @see convert
     */
    fun getSupportedInputFormats(): Set<String> {
        return inputPlugins.flatMap { it.fileTypes }.toSet()
    }
    
    /**
     * Returns all file extensions supported as output formats.
     * 
     * This includes extensions from all registered output plugins.
     * Extensions are returned without leading dots and in lowercase.
     * 
     * @return Set of supported output file extensions (e.g., {"epub", "mobi", "pdf", "html"})
     * 
     * @see getSupportedInputFormats
     * @see convert
     */
    fun getSupportedOutputFormats(): Set<String> {
        return outputPlugins.map { it.fileType }.toSet()
    }
    
    /**
     * Converts an e-book file from one format to another.
     * 
     * This is the main entry point for format conversion. The method:
     * 1. Validates the input file exists
     * 2. Finds the appropriate input and output plugins
     * 3. Creates a temporary work directory
     * 4. Executes the conversion through the OEB intermediate format
     * 5. Cleans up temporary files
     * 
     * ## Conversion Process
     * 
     * ```
     * Input File → InputPlugin → OebBook → OutputPlugin → Output File
     * ```
     * 
     * ## Error Handling
     * 
     * The method throws exceptions for:
     * - File not found: If inputFile doesn't exist
     * - Unsupported input format: If no plugin handles the input file extension
     * - Unsupported output format: If no plugin produces the requested format
     * - Format errors: If the input file is invalid or corrupted
     * - Conversion errors: If format-specific conversion fails
     * 
     * Error messages include:
     * - Lists of supported formats when format is unsupported
     * - Detailed error messages from plugins
     * - Clear indication if a format requires additional implementation
     * 
     * ## Resource Management
     * 
     * - Creates output directory if it doesn't exist
     * - Creates temporary work directory for intermediate files
     * - Automatically cleans up temporary files (even on error)
     * - Logs conversion progress and results
     * 
     * ## Example
     * 
     * ```kotlin
     * val pipeline = ConversionPipeline()
     * 
     * try {
     *     pipeline.convert(
     *         inputFile = File("input.epub"),
     *         outputFormat = "mobi",
     *         outputFile = File("output.mobi")
     *     )
     *     println("Conversion successful!")
     * } catch (e: Exception) {
     *     println("Conversion failed: ${e.message}")
     * }
     * ```
     * 
     * @param inputFile The source file to convert. Must exist and be readable.
     *                  The file extension determines which input plugin is used.
     * @param outputFormat The target format as a file extension without dot
     *                     (e.g., "epub", "mobi", "pdf", "html"). Case-insensitive.
     * @param outputFile The destination file path. Parent directory will be created
     *                   if it doesn't exist. Existing files will be overwritten.
     * 
     * @throws Exception if:
     *   - Input file doesn't exist
     *   - Input format is not supported (with list of supported formats)
     *   - Output format is not supported (with list of supported formats)
     *   - Input file is invalid or corrupted
     *   - Conversion fails for format-specific reasons
     *   - Format requires additional implementation (UnsupportedOperationException wrapped)
     * 
     * @see getSupportedInputFormats
     * @see getSupportedOutputFormats
     * @see InputPlugin
     * @see OutputPlugin
     */
    fun convert(inputFile: File, outputFormat: String, outputFile: File) {
        if (!inputFile.exists()) {
            throw Exception("Input file does not exist: ${inputFile.absolutePath}")
        }
        
        val inputExt = inputFile.extension.lowercase()
        val inputPlugin = inputPlugins.find { it.fileTypes.contains(inputExt) }
            ?: throw Exception(
                "No input plugin found for format: $inputExt. " +
                "Supported input formats: ${getSupportedInputFormats().joinToString(", ")}"
            )
            
        val outputFormatLower = outputFormat.lowercase()
        val outputPlugin = outputPlugins.find { it.fileType == outputFormatLower }
            ?: throw Exception(
                "No output plugin found for format: $outputFormatLower. " +
                "Supported output formats: ${getSupportedOutputFormats().joinToString(", ")}"
            )
        
        // Ensure output directory exists
        outputFile.parentFile?.mkdirs()
        
        val workDir = java.nio.file.Files.createTempDirectory("calibre_conversion").toFile()
        try {
            org.calibre.utils.Logger.info(
                "Converting ${inputFile.name} (${inputExt}) using ${inputPlugin.name} -> OEB -> ${outputPlugin.name}..."
            )
            
            val book = try {
                inputPlugin.convert(inputFile, workDir)
            } catch (e: UnsupportedOperationException) {
                throw Exception(
                    "Format not yet fully supported: ${e.message}. " +
                    "This format requires additional implementation work.",
                    e
                )
            }
            
            outputPlugin.convert(book, outputFile)
            
            org.calibre.utils.Logger.info(
                "Conversion successful: ${outputFile.absolutePath} " +
                "(${outputFile.length()} bytes)"
            )
        } catch (e: Exception) {
            org.calibre.utils.Logger.error(
                "Conversion failed: ${e.message}",
                e
            )
            throw e
        } finally {
            // Clean up work directory
            try {
                workDir.deleteRecursively()
            } catch (e: Exception) {
                org.calibre.utils.Logger.warn("Failed to clean up work directory: ${e.message}")
            }
        }
    }
}
