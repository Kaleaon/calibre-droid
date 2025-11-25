package org.calibre.conversion

import java.io.File

interface InputPlugin {
    val name: String
    val fileTypes: Set<String>
    
    /**
     * Converts the input file into an OEB intermediate representation.
     * @param inputFile The source file.
     * @param workDir Directory to store extracted/intermediate files.
     */
    fun convert(inputFile: File, workDir: File): OebBook
}

interface OutputPlugin {
    val name: String
    val fileType: String // The extension this plugin produces
    
    /**
     * Converts the OEB book into the target format.
     */
    fun convert(book: OebBook, outputFile: File)
}
