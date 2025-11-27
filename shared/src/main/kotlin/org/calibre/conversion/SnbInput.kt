package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File

/**
 * # SNB Input Plugin
 * 
 * Reads SNB (Shanda Bambook) format files and converts them to OEB format.
 * 
 * ## SNB Format
 * 
 * SNB is a proprietary e-book format:
 * - **Proprietary format**: Used by Shanda Bambook e-readers
 * - **Legacy format**: Largely superseded by EPUB
 * 
 * ## Implementation Status
 * 
 * **Note**: Full SNB support requires:
 * - SNB format specification
 * - Binary format parsing
 * - Format-specific decompression
 * 
 * This is a placeholder implementation.
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see InputPlugin
 * @see OebBook
 */
class SnbInput : InputPlugin {
    override val name = "SNB Input"
    override val fileTypes = setOf("snb")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        throw UnsupportedOperationException(
            "SNB input format requires full SNB parser implementation. " +
            "This format is proprietary and legacy. Consider converting to EPUB first."
        )
    }
}
