package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File

/**
 * # TCR (PalmDOC Compressed) Input Plugin
 * 
 * Reads TCR format files (PalmDOC compressed text) and converts them to OEB format.
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
 * 1. Decompresses the TCR file using PalmDoc decompression
 * 2. Treats the result as plain text
 * 3. Uses TextInput logic to convert to HTML
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see InputPlugin
 * @see OebBook
 * @see PalmDocCompression
 */
class TcrInput : InputPlugin {
    override val name = "TCR Input"
    override val fileTypes = setOf("tcr")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        // Read compressed data
        val compressedData = inputFile.readBytes()
        
        // Decompress using PalmDoc algorithm
        val decompressedData = PalmDocCompression.decompress(compressedData)
        val text = String(decompressedData, Charsets.UTF_8)
        
        // Create temporary text file and use TextInput logic
        val tempTextFile = File(workDir, "content.txt")
        tempTextFile.writeText(text, Charsets.UTF_8)
        
        // Use TextInput to process
        val textInput = TextInput()
        return textInput.convert(tempTextFile, workDir)
    }
}
