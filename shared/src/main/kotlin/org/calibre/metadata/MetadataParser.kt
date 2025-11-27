package org.calibre.metadata

import java.io.File

interface MetadataParser {
    fun canParse(file: File): Boolean
    fun parseMetadata(file: File): Metadata
}
