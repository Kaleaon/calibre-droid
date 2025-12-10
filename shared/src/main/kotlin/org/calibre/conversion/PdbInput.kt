package org.calibre.conversion

import org.calibre.formats.pdb.EReaderParser
import org.calibre.formats.pdb.PalmDocDecompressor
import org.calibre.formats.pdb.PalmDocHeader
import org.calibre.formats.pdb.PdbHeader
import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PDB (Palm Database) format input plugin.
 * 
 * Supports multiple PDB sub-formats:
 * - PalmDOC (TEXtREAd)
 * - eReader (PNPdPPrs, PNRdPPrs)
 * - Plucker (DataPlkr)
 * - Haodoo (BOOKMTIT, BOOKMTIU)
 */
class PdbInput : InputPlugin {
    override val name = "PDB Input"
    override val fileTypes = setOf("pdb", "updb")

    override fun convert(inputFile: File, workDir: File): OebBook {
        Logger.info("Converting PDB file: ${inputFile.name}")
        
        val data = inputFile.readBytes()
        val stream = ByteArrayInputStream(data)
        val header = PdbHeader.read(stream)
        
        Logger.info("PDB identity: ${header.identity}, records: ${header.numRecords}")
        
        return when (header.identity) {
            PdbHeader.PALMDOC -> convertPalmDoc(data, header, workDir)
            PdbHeader.EREADER_1, PdbHeader.EREADER_2 -> convertEReader(data, workDir)
            PdbHeader.PLUCKER -> convertPlucker(data, header, workDir)
            PdbHeader.HAODOO_1, PdbHeader.HAODOO_2 -> convertHaodoo(data, header, workDir)
            else -> {
                // Try to detect and convert as plain text PDB
                Logger.warn("Unknown PDB identity: ${header.identity}, attempting plain text conversion")
                convertUnknownPdb(data, header, workDir)
            }
        }
    }
    
    /**
     * Converts PalmDOC format (.pdb with TEXtREAd identity).
     */
    private fun convertPalmDoc(data: ByteArray, header: PdbHeader, workDir: File): OebBook {
        Logger.info("Converting PalmDOC format")
        
        val metadata = Metadata(title = header.name)
        
        // Read first record for PalmDoc header
        val firstRecordOffset = header.recordOffsets[0].offset.toInt()
        val firstRecordEnd = if (header.numRecords > 1) {
            header.recordOffsets[1].offset.toInt()
        } else data.size
        
        val palmDocHeaderData = data.copyOfRange(firstRecordOffset, minOf(firstRecordOffset + 16, firstRecordEnd))
        val palmDocHeader = PalmDocHeader.read(palmDocHeaderData)
        
        Logger.info("PalmDoc compression: ${palmDocHeader.compression}, text length: ${palmDocHeader.textLength}")
        
        // Extract and decompress text records
        val textBuilder = StringBuilder()
        val textRecordCount = palmDocHeader.recordCount
        
        for (i in 1..textRecordCount) {
            if (i >= header.numRecords) break
            
            val recordOffset = header.recordOffsets[i].offset.toInt()
            val recordEnd = if (i + 1 < header.numRecords) {
                header.recordOffsets[i + 1].offset.toInt()
            } else data.size
            
            val recordData = data.copyOfRange(recordOffset, recordEnd)
            
            val decompressed = when (palmDocHeader.compression) {
                PalmDocHeader.COMPRESSION_PALMDOC -> PalmDocDecompressor.decompress(recordData)
                PalmDocHeader.COMPRESSION_NONE -> recordData
                else -> recordData
            }
            
            textBuilder.append(String(decompressed, Charsets.ISO_8859_1))
        }
        
        // Convert plain text to HTML
        val htmlContent = convertTextToHtml(textBuilder.toString(), header.name)
        
        // Create content file
        val contentFile = File(workDir, "content.html")
        contentFile.writeText(htmlContent)
        
        // Build OEB book
        val book = OebBook(metadata)
        val contentItem = OebItem(
            id = "content",
            href = "content.html",
            mediaType = "application/xhtml+xml",
            file = contentFile
        )
        book.manifest["content"] = contentItem
        book.spine.add(contentItem)
        
        return book
    }
    
    /**
     * Converts eReader format (.pdb with PNPd/PNRd identity).
     */
    private fun convertEReader(data: ByteArray, workDir: File): OebBook {
        Logger.info("Converting eReader format")
        
        val parser = EReaderParser(data)
        
        if (parser.isEncrypted()) {
            throw UnsupportedOperationException("Encrypted eReader files are not supported")
        }
        
        val title = parser.getTitle()
        val pdbMetadata = parser.getMetadata()
        
        val metadata = Metadata(
            title = title,
            authors = pdbMetadata["author"]?.let { listOf(it) } ?: emptyList()
        )
        
        // Extract text content
        val htmlContent = parser.extractText()
        val contentFile = File(workDir, "content.html")
        contentFile.writeText(htmlContent)
        
        // Extract images
        val images = parser.extractImages()
        val imageDir = File(workDir, "images")
        imageDir.mkdirs()
        
        val book = OebBook(metadata)
        
        // Add content
        val contentItem = OebItem(
            id = "content",
            href = "content.html",
            mediaType = "application/xhtml+xml",
            file = contentFile
        )
        book.manifest["content"] = contentItem
        book.spine.add(contentItem)
        
        // Add images
        for ((name, imageData) in images) {
            val imageFile = File(imageDir, name)
            imageFile.writeBytes(imageData)
            
            val mediaType = when {
                name.endsWith(".png") -> "image/png"
                name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
                name.endsWith(".gif") -> "image/gif"
                else -> "image/png"
            }
            
            book.manifest[name] = OebItem(
                id = name.replace(".", "_"),
                href = "images/$name",
                mediaType = mediaType,
                file = imageFile
            )
        }
        
        return book
    }
    
    /**
     * Converts Plucker format (.pdb with DataPlkr identity).
     */
    private fun convertPlucker(data: ByteArray, header: PdbHeader, workDir: File): OebBook {
        Logger.info("Converting Plucker format")
        
        // Plucker is a complex format with its own compression
        // This is a simplified implementation that extracts basic text
        val metadata = Metadata(title = header.name)
        
        val textBuilder = StringBuilder()
        textBuilder.append("<!DOCTYPE html>\n<html><head><title>${escapeHtml(header.name)}</title></head><body>\n")
        
        // Read text from records (skip header record)
        for (i in 1 until header.numRecords) {
            val recordOffset = header.recordOffsets[i].offset.toInt()
            val recordEnd = if (i + 1 < header.numRecords) {
                header.recordOffsets[i + 1].offset.toInt()
            } else data.size
            
            val recordData = data.copyOfRange(recordOffset, recordEnd)
            
            // Plucker records have a specific format, but for basic extraction
            // we try to find text content
            try {
                val text = extractPluckerText(recordData)
                if (text.isNotEmpty()) {
                    textBuilder.append("<p>").append(escapeHtml(text)).append("</p>\n")
                }
            } catch (e: Exception) {
                Logger.debug("Error extracting Plucker record $i: ${e.message}")
            }
        }
        
        textBuilder.append("</body></html>")
        
        val contentFile = File(workDir, "content.html")
        contentFile.writeText(textBuilder.toString())
        
        val book = OebBook(metadata)
        val contentItem = OebItem(
            id = "content",
            href = "content.html",
            mediaType = "application/xhtml+xml",
            file = contentFile
        )
        book.manifest["content"] = contentItem
        book.spine.add(contentItem)
        
        return book
    }
    
    private fun extractPluckerText(data: ByteArray): String {
        if (data.size < 8) return ""
        
        // Plucker record header
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val uid = buffer.short.toInt() and 0xFFFF
        val paragraphs = buffer.short.toInt() and 0xFFFF
        val size = buffer.short.toInt() and 0xFFFF
        val type = buffer.get().toInt() and 0xFF
        
        // Type 0 or 1 are text records
        if (type != 0 && type != 1) return ""
        
        // Skip to text content
        val textStart = 8 + (paragraphs * 4)
        if (textStart >= data.size) return ""
        
        return try {
            val textData = data.copyOfRange(textStart, data.size)
            // Try zlib decompression for type 1
            if (type == 1) {
                val inflater = java.util.zip.Inflater()
                inflater.setInput(textData)
                val output = java.io.ByteArrayOutputStream()
                val buf = ByteArray(1024)
                while (!inflater.finished()) {
                    val count = inflater.inflate(buf)
                    if (count == 0) break
                    output.write(buf, 0, count)
                }
                inflater.end()
                String(output.toByteArray(), Charsets.ISO_8859_1)
            } else {
                String(textData, Charsets.ISO_8859_1)
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Converts Haodoo format (.pdb with BOOKMTIT/BOOKMTIU identity).
     * Haodoo is a Chinese ebook format.
     */
    private fun convertHaodoo(data: ByteArray, header: PdbHeader, workDir: File): OebBook {
        Logger.info("Converting Haodoo format")
        
        val metadata = Metadata(title = header.name)
        val textBuilder = StringBuilder()
        textBuilder.append("<!DOCTYPE html>\n<html><head>")
        textBuilder.append("<meta charset=\"UTF-8\">")
        textBuilder.append("<title>${escapeHtml(header.name)}</title></head><body>\n")
        
        // Haodoo format has text in Big5 or UTF-16 encoding
        val encoding = if (header.identity == PdbHeader.HAODOO_2) "UTF-16LE" else "Big5"
        
        for (i in 1 until header.numRecords) {
            val recordOffset = header.recordOffsets[i].offset.toInt()
            val recordEnd = if (i + 1 < header.numRecords) {
                header.recordOffsets[i + 1].offset.toInt()
            } else data.size
            
            val recordData = data.copyOfRange(recordOffset, recordEnd)
            
            try {
                val text = String(recordData, charset(encoding))
                    .trim()
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                
                for (line in text.split("\n")) {
                    if (line.isNotBlank()) {
                        textBuilder.append("<p>").append(escapeHtml(line)).append("</p>\n")
                    }
                }
            } catch (e: Exception) {
                Logger.debug("Error reading Haodoo record $i: ${e.message}")
            }
        }
        
        textBuilder.append("</body></html>")
        
        val contentFile = File(workDir, "content.html")
        contentFile.writeText(textBuilder.toString(), Charsets.UTF_8)
        
        val book = OebBook(metadata)
        val contentItem = OebItem(
            id = "content",
            href = "content.html",
            mediaType = "application/xhtml+xml",
            file = contentFile
        )
        book.manifest["content"] = contentItem
        book.spine.add(contentItem)
        
        return book
    }
    
    /**
     * Attempts to convert an unknown PDB format by extracting raw text.
     */
    private fun convertUnknownPdb(data: ByteArray, header: PdbHeader, workDir: File): OebBook {
        Logger.warn("Attempting to extract text from unknown PDB format: ${header.identity}")
        
        val metadata = Metadata(title = header.name)
        val textBuilder = StringBuilder()
        textBuilder.append("<!DOCTYPE html>\n<html><head><title>${escapeHtml(header.name)}</title></head><body>\n")
        
        // Try to extract text from records
        for (i in 1 until header.numRecords) {
            val recordOffset = header.recordOffsets[i].offset.toInt()
            val recordEnd = if (i + 1 < header.numRecords) {
                header.recordOffsets[i + 1].offset.toInt()
            } else data.size
            
            val recordData = data.copyOfRange(recordOffset, recordEnd)
            
            // Try to interpret as text
            val text = String(recordData, Charsets.ISO_8859_1)
                .filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?;:'\"-()[]" }
            
            if (text.length > 20) { // Only add if seems like actual text
                textBuilder.append("<p>").append(escapeHtml(text)).append("</p>\n")
            }
        }
        
        textBuilder.append("</body></html>")
        
        val contentFile = File(workDir, "content.html")
        contentFile.writeText(textBuilder.toString())
        
        val book = OebBook(metadata)
        val contentItem = OebItem(
            id = "content",
            href = "content.html",
            mediaType = "application/xhtml+xml",
            file = contentFile
        )
        book.manifest["content"] = contentItem
        book.spine.add(contentItem)
        
        return book
    }
    
    private fun convertTextToHtml(text: String, title: String): String {
        val html = StringBuilder()
        html.append("<!DOCTYPE html>\n<html><head><title>${escapeHtml(title)}</title></head><body>\n")
        
        val paragraphs = text.replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n\n")
        
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isNotEmpty()) {
                html.append("<p>").append(escapeHtml(trimmed)).append("</p>\n")
            }
        }
        
        html.append("</body></html>")
        return html.toString()
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
