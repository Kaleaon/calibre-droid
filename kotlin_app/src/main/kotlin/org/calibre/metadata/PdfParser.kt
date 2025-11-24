package org.calibre.metadata

import org.apache.pdfbox.pdmodel.PDDocument
import java.io.File
import java.util.Calendar

class PdfParser : MetadataParser {

    override fun canParse(file: File): Boolean {
        return file.extension.equals("pdf", ignoreCase = true)
    }

    override fun parseMetadata(file: File): Metadata {
        val document = PDDocument.load(file)
        val metadata = Metadata()
        try {
            val info = document.documentInformation
            
            val title = info.title
            if (title != null && title.isNotBlank()) {
                metadata.title = title
            }
            
            val author = info.author
            if (author != null && author.isNotBlank()) {
                metadata.authors = mutableListOf(author)
            }
            
            val subject = info.subject
            if (subject != null && subject.isNotBlank()) {
                metadata.comments = subject
            }
            
            val keywords = info.keywords
            if (keywords != null && keywords.isNotBlank()) {
                // Basic comma separation
                metadata.tags = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            }
            
            val producer = info.producer
            if (producer != null && producer.isNotBlank()) {
                metadata.publisher = producer // Often producer is the software, but sometimes used for publisher
            }
            
            // Creation date
            val creationDate = info.creationDate
            if (creationDate != null) {
                val instant = creationDate.toInstant()
                // Convert Calendar to LocalDateTime
                // This is a bit rough due to timezones, but sufficient for PoC
                metadata.pubDate = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
            }

        } finally {
            document.close()
        }
        return metadata
    }
}
