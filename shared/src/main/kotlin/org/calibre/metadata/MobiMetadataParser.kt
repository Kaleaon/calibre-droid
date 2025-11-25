package org.calibre.metadata

import org.calibre.formats.mobi.MobiParser
import java.io.File

class MobiMetadataParser : MetadataParser {
    override fun canParse(file: File): Boolean {
        return file.extension.equals("mobi", ignoreCase = true) || 
               file.extension.equals("azw3", ignoreCase = true)
    }

    override fun parseMetadata(file: File): Metadata {
        val parser = MobiParser()
        try {
            val mobiMeta = parser.parseMetadata(file)
            return Metadata(
                title = mobiMeta.title,
                authors = if (mobiMeta.author != null) mutableListOf(mobiMeta.author) else mutableListOf("Unknown")
            )
        } catch (e: Exception) {
            return Metadata(title = file.nameWithoutExtension)
        }
    }
}
