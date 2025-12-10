package org.calibre.conversion

import org.calibre.oeb.OebBook
import org.calibre.utils.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Deflater

/**
 * Output plugin for LIT (Microsoft Reader) format.
 * 
 * Creates LIT files from the OEB intermediate format.
 * LIT uses Microsoft's ITSS (InfoTech Storage System) format
 * with specialized compression and directory structure.
 */
class LitOutput : OutputPlugin {
    
    override val name = "LIT Output"
    override val author = "Calibre Kotlin"
    override val description = "Converts ebooks to LIT (Microsoft Reader) format"
    override val outputFormat = "lit"
    
    companion object {
        private const val ITSF_SIGNATURE = "ITSF"
        private const val ITSP_SIGNATURE = "ITSP"
        private val LIT_GUID = byteArrayOf(
            0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
    }
    
    override fun convert(book: OebBook, outputFile: File, workDir: File) {
        val output = ByteArrayOutputStream()
        
        // Collect content files
        val files = mutableMapOf<String, ByteArray>()
        
        // Add OPF file
        files["/content.opf"] = generateOpf(book).toByteArray(Charsets.UTF_8)
        
        // Add manifest items
        for ((id, item) in book.manifest) {
            if (item.file?.exists() == true) {
                files["/${item.href}"] = item.file.readBytes()
            }
        }
        
        // Generate LIT structure
        val litData = buildLitFile(files, book)
        
        outputFile.writeBytes(litData)
        Logger.info("Created LIT file: ${outputFile.absolutePath}")
    }
    
    private fun buildLitFile(files: Map<String, ByteArray>, book: OebBook): ByteArray {
        val output = ByteArrayOutputStream()
        
        // Calculate content sizes
        val compressedFiles = mutableMapOf<String, ByteArray>()
        var totalContentSize = 0
        
        for ((path, data) in files) {
            val compressed = compress(data)
            compressedFiles[path] = compressed
            totalContentSize += compressed.size
        }
        
        // Build directory listing
        val directoryData = buildDirectory(compressedFiles)
        
        // Calculate offsets
        val headerSize = 0x60
        val directoryOffset = headerSize
        val contentOffset = directoryOffset + directoryData.size + 100
        
        // Write ITSF header
        output.write(ITSF_SIGNATURE.toByteArray(Charsets.US_ASCII))
        output.write(intToBytes(3)) // Version
        output.write(intToBytes(headerSize)) // Total header length
        output.write(intToBytes(1)) // Unknown
        output.write(longToBytes(System.currentTimeMillis() / 1000)) // Timestamp
        output.write(intToBytes(0x409)) // Language ID (English)
        output.write(LIT_GUID)
        output.write(LIT_GUID)
        output.write(longToBytes(directoryOffset.toLong())) // Directory offset
        output.write(longToBytes(directoryData.size.toLong())) // Directory length
        output.write(longToBytes(contentOffset.toLong())) // Content offset
        
        // Pad header to size
        while (output.size() < headerSize) {
            output.write(0)
        }
        
        // Write directory
        output.write(ITSP_SIGNATURE.toByteArray(Charsets.US_ASCII))
        output.write(intToBytes(1)) // Version
        output.write(intToBytes(directoryData.size + 84)) // Directory header length
        output.write(intToBytes(0)) // Unknown
        output.write(intToBytes(0x1000)) // Chunk size
        output.write(intToBytes(2)) // Density
        output.write(intToBytes(0)) // Index depth
        output.write(intToBytes(-1)) // Root index chunk
        output.write(intToBytes(0)) // First PMGL chunk
        output.write(intToBytes(0)) // Last PMGL chunk
        output.write(intToBytes(-1)) // Unknown
        output.write(intToBytes(compressedFiles.size)) // Number of directory chunks
        output.write(intToBytes(0x409)) // Language
        output.write(LIT_GUID)
        output.write(intToBytes(84)) // Header length
        output.write(intToBytes(-1)) // Unknown
        output.write(intToBytes(-1)) // Unknown
        output.write(intToBytes(-1)) // Unknown
        
        output.write(directoryData)
        
        // Pad to content offset
        while (output.size() < contentOffset) {
            output.write(0)
        }
        
        // Write content
        var currentOffset = 0
        for ((path, data) in compressedFiles) {
            output.write(data)
            currentOffset += data.size
        }
        
        return output.toByteArray()
    }
    
    private fun buildDirectory(files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        
        var offset = 0
        for ((path, data) in files) {
            val nameBytes = path.toByteArray(Charsets.UTF_8)
            
            // Write name length (encoded)
            writeEncodedInt(output, nameBytes.size)
            // Write name
            output.write(nameBytes)
            // Write content section (0)
            writeEncodedInt(output, 0)
            // Write offset
            writeEncodedInt(output, offset)
            // Write length
            writeEncodedInt(output, data.size)
            
            offset += data.size
        }
        
        return output.toByteArray()
    }
    
    private fun writeEncodedInt(output: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v >= 0x80) {
            output.write((v and 0x7F) or 0x80)
            v = v shr 7
        }
        output.write(v and 0x7F)
    }
    
    private fun generateOpf(book: OebBook): String {
        val metadata = book.metadata
        val sb = StringBuilder()
        
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"uuid\">\n")
        sb.append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
        sb.append("    <dc:title>${escapeXml(metadata.title)}</dc:title>\n")
        
        for (author in metadata.authors) {
            sb.append("    <dc:creator>${escapeXml(author)}</dc:creator>\n")
        }
        
        metadata.publisher?.let {
            sb.append("    <dc:publisher>${escapeXml(it)}</dc:publisher>\n")
        }
        
        for (lang in metadata.languages) {
            sb.append("    <dc:language>$lang</dc:language>\n")
        }
        
        sb.append("  </metadata>\n")
        
        sb.append("  <manifest>\n")
        for ((id, item) in book.manifest) {
            sb.append("    <item id=\"$id\" href=\"${item.href}\" media-type=\"${item.mediaType}\"/>\n")
        }
        sb.append("  </manifest>\n")
        
        sb.append("  <spine>\n")
        for (item in book.spine) {
            sb.append("    <itemref idref=\"${item.id}\"/>\n")
        }
        sb.append("  </spine>\n")
        
        sb.append("</package>\n")
        
        return sb.toString()
    }
    
    private fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        
        deflater.end()
        
        // Only use compressed if smaller
        val compressed = output.toByteArray()
        return if (compressed.size < data.size) compressed else data
    }
    
    private fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }
    
    private fun longToBytes(value: Long): ByteArray {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
    }
    
    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
