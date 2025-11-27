package org.calibre.conversion

import org.calibre.metadata.Metadata
import java.io.File

class LitInput : InputPlugin {
    override val name = "LIT Input"
    override val fileTypes = setOf("lit")

    override fun convert(inputFile: File, workDir: File): OebBook {
        // LIT format is complex (Microsoft Reader format)
        // This is a basic stub - full implementation would require LIT parser
        val metadata = Metadata(title = inputFile.nameWithoutExtension)
        val book = OebBook(metadata)
        
        // For now, throw an exception indicating this needs full implementation
        throw UnsupportedOperationException("LIT input format requires full LIT parser implementation. This is a placeholder.")
    }
}
