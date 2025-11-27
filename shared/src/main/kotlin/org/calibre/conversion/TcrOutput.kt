package org.calibre.conversion

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * # TCR (PalmDOC Compressed) Output Plugin
 * 
 * Writes TCR format files (PalmDOC compressed text).
 * 
 * ## TCR Format
 * 
 * TCR is a compressed text format:
 * - **Compression**: Uses PalmDOC compression algorithm
 * - **Text format**: Plain text content
 * - **Legacy format**: Used on older Palm devices
 * 
 * ## Implementation
 * 
 * This plugin:
 * 1. Extracts text content from the OEB book
 * 2. Compresses the text using PalmDoc compression
 * 3. Writes the compressed data to the output file
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see OutputPlugin
 * @see OebBook
 * @see PalmDocCompression
 */
class TcrOutput : OutputPlugin {
    override val name = "TCR Output"
    override val fileType = "tcr"
    
    override fun convert(book: OebBook, outputFile: File) {
        // Extract text content
        val textOutput = TextOutput()
        val tempTextFile = File.createTempFile("tcr_temp", ".txt")
        
        try {
            // Use TextOutput to get plain text
            textOutput.convert(book, tempTextFile)
            
            // Read text and compress
            val text = tempTextFile.readText(StandardCharsets.UTF_8)
            val textBytes = text.toByteArray(StandardCharsets.UTF_8)
            val compressed = PalmDocCompression.compress(textBytes)
            
            // Write compressed data
            outputFile.writeBytes(compressed)
            
        } finally {
            tempTextFile.delete()
        }
    }
}
