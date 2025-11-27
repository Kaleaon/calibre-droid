package org.calibre.conversion

import java.io.File

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
     * Get all supported input file types.
     */
    fun getSupportedInputFormats(): Set<String> {
        return inputPlugins.flatMap { it.fileTypes }.toSet()
    }
    
    /**
     * Get all supported output formats.
     */
    fun getSupportedOutputFormats(): Set<String> {
        return outputPlugins.map { it.fileType }.toSet()
    }
    
    /**
     * Convert a file from one format to another.
     * 
     * @param inputFile The input file to convert
     * @param outputFormat The desired output format (e.g., "epub", "mobi", "pdf")
     * @param outputFile The output file path
     * @throws Exception if conversion fails or format is not supported
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
