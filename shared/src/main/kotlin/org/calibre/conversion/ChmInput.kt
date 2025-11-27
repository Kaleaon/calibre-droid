package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File

/**
 * # CHM (Compiled HTML Help) Input Plugin
 * 
 * Reads CHM format files and converts them to OEB format.
 * 
 * ## CHM Format
 * 
 * CHM is Microsoft's Compiled HTML Help format:
 * - **ITSS format**: Uses Microsoft's ITSS (InfoTech Storage System)
 * - **HTML content**: Contains HTML files and resources
 * - **Navigation**: Includes table of contents and index
 * 
 * ## Implementation Status
 * 
 * **Note**: Full CHM support requires:
 * - ITSS format parser
 * - CHM decompression
 * - Navigation structure extraction
 * 
 * This is a placeholder implementation. CHM parsing requires specialized libraries.
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 * @see InputPlugin
 * @see OebBook
 */
class ChmInput : InputPlugin {
    override val name = "CHM Input"
    override val fileTypes = setOf("chm")
    
    override fun convert(inputFile: File, workDir: File): OebBook {
        throw UnsupportedOperationException(
            "CHM input format requires full CHM/ITSS parser implementation. " +
            "CHM files use Microsoft's ITSS format which requires specialized parsing. " +
            "Consider using external tools to extract CHM content first."
        )
    }
}
