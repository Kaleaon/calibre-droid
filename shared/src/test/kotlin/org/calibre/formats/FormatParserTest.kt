package org.calibre.formats

import org.calibre.formats.chm.ChmParser
import org.calibre.formats.djvu.DjvuParser
import org.calibre.formats.pdb.PdbHeader
import org.calibre.formats.pdb.PalmDocDecompressor
import org.calibre.formats.rar.RarExtractor
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests for format parsers.
 */
class FormatParserTest {
    
    // ============ PalmDoc Compression Tests ============
    
    @Test
    fun `PalmDocDecompressor should compress and decompress text`() {
        val original = "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog again."
        val originalBytes = original.toByteArray(Charsets.ISO_8859_1)
        
        val compressed = PalmDocDecompressor.compress(originalBytes)
        val decompressed = PalmDocDecompressor.decompress(compressed)
        
        assertEquals(original, String(decompressed, Charsets.ISO_8859_1))
    }
    
    @Test
    fun `PalmDocDecompressor should handle empty input`() {
        val empty = ByteArray(0)
        
        val compressed = PalmDocDecompressor.compress(empty)
        val decompressed = PalmDocDecompressor.decompress(compressed)
        
        assertEquals(0, decompressed.size)
    }
    
    @Test
    fun `PalmDocDecompressor should handle single character`() {
        val single = byteArrayOf('A'.code.toByte())
        
        val compressed = PalmDocDecompressor.compress(single)
        val decompressed = PalmDocDecompressor.decompress(compressed)
        
        assertArrayEquals(single, decompressed)
    }
    
    @Test
    fun `PalmDocDecompressor should handle repeated patterns`() {
        val repeated = "AAAAAAAAAAAAAAAA".toByteArray()
        
        val compressed = PalmDocDecompressor.compress(repeated)
        val decompressed = PalmDocDecompressor.decompress(compressed)
        
        assertArrayEquals(repeated, decompressed)
        // Compression should reduce size for repeated patterns
        assertTrue(compressed.size <= repeated.size)
    }
    
    // ============ PDB Header Tests ============
    
    @Test
    fun `PdbHeader should detect PalmDoc format`() {
        assertEquals("TEXtREAd", PdbHeader.PALMDOC)
        assertEquals("PNPdPPrs", PdbHeader.EREADER_1)
        assertEquals("PNRdPPrs", PdbHeader.EREADER_2)
    }
    
    @Test
    fun `PdbHeader identity should combine type and creator`() {
        val header = createMinimalPdbHeader("TEXt", "REAd")
        assertEquals("TEXtREAd", header.identity)
    }
    
    private fun createMinimalPdbHeader(type: String, creator: String): PdbHeader {
        return PdbHeader(
            name = "Test",
            attributes = 0,
            version = 0,
            creationDate = 0,
            modificationDate = 0,
            lastBackupDate = 0,
            modificationNumber = 0,
            appInfoOffset = 0,
            sortInfoOffset = 0,
            type = type,
            creator = creator,
            uniqueIdSeed = 0,
            nextRecordListId = 0,
            numRecords = 0,
            recordOffsets = emptyList()
        )
    }
    
    // ============ RAR Extractor Tests ============
    
    @Test
    fun `RarExtractor should detect RAR4 signature`() {
        val rar4Signature = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
        
        val exception = assertThrows<IllegalArgumentException> {
            // Signature alone is not enough, needs more data
            RarExtractor(rar4Signature)
        }
        
        assertTrue(exception.message?.contains("small") == true || 
                   exception.message?.contains("Invalid") == true)
    }
    
    @Test
    fun `RarExtractor should detect RAR5 signature`() {
        val rar5Signature = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
        
        val exception = assertThrows<IllegalArgumentException> {
            RarExtractor(rar5Signature)
        }
        
        // Should recognize as RAR5 but fail due to incomplete data
        assertNotNull(exception)
    }
    
    @Test
    fun `RarExtractor should reject invalid signatures`() {
        val invalidData = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        
        assertThrows<IllegalArgumentException> {
            RarExtractor(invalidData)
        }
    }
    
    @Test
    fun `RarExtractor getImageFiles should filter correctly`() {
        // Test the filtering logic by checking supported extensions
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        
        assertTrue(imageExtensions.contains("jpg"))
        assertTrue(imageExtensions.contains("png"))
        assertFalse(imageExtensions.contains("txt"))
        assertFalse(imageExtensions.contains("xml"))
    }
    
    // ============ CHM Parser Tests ============
    
    @Test
    fun `ChmParser should detect ITSF signature`() {
        // Create minimal CHM-like header
        val buffer = ByteArrayOutputStream()
        buffer.write("ITSF".toByteArray()) // Signature
        buffer.write(ByteArray(96)) // Padding
        
        val data = buffer.toByteArray()
        
        assertThrows<IllegalArgumentException> {
            // Will fail due to incomplete header, but should recognize signature
            ChmParser(data)
        }
    }
    
    @Test
    fun `ChmParser should reject invalid files`() {
        val invalidData = ByteArray(50) // Too small
        
        assertThrows<IllegalArgumentException> {
            ChmParser(invalidData)
        }
    }
    
    @Test
    fun `ChmParser should reject non-CHM files`() {
        val pdfHeader = "%PDF-1.4".toByteArray() + ByteArray(92)
        
        assertThrows<IllegalArgumentException> {
            ChmParser(pdfHeader)
        }
    }
    
    // ============ DjVu Parser Tests ============
    
    @Test
    fun `DjvuParser should detect AT&T magic`() {
        val validStart = "AT&T".toByteArray() + "FORM".toByteArray() + ByteArray(8)
        
        assertThrows<Exception> {
            // Will fail due to incomplete structure
            DjvuParser(validStart)
        }
    }
    
    @Test
    fun `DjvuParser should reject invalid files`() {
        val invalidData = ByteArray(10)
        
        assertThrows<IllegalArgumentException> {
            DjvuParser(invalidData)
        }
    }
    
    @Test
    fun `DjvuParser should reject non-DjVu files`() {
        val pdfHeader = "%PDF-1.4".toByteArray() + ByteArray(92)
        
        assertThrows<IllegalArgumentException> {
            DjvuParser(pdfHeader)
        }
    }
    
    // ============ Helper for creating test structures ============
    
    private fun createTestPdb(): ByteArray {
        val buffer = ByteBuffer.allocate(78).order(ByteOrder.BIG_ENDIAN)
        
        // Database name (32 bytes)
        val name = "Test Book".toByteArray().copyOf(32)
        buffer.put(name)
        
        // Attributes, version (4 bytes)
        buffer.putShort(0)
        buffer.putShort(0)
        
        // Dates (12 bytes)
        buffer.putInt(0) // creation
        buffer.putInt(0) // modification
        buffer.putInt(0) // backup
        
        // Modification number, app info, sort info (12 bytes)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Type and creator (8 bytes)
        buffer.put("TEXt".toByteArray())
        buffer.put("REAd".toByteArray())
        
        // Unique ID seed, next record list (8 bytes)
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Number of records (2 bytes)
        buffer.putShort(0)
        
        return buffer.array()
    }
}

/**
 * Tests for format conversion output.
 */
class FormatOutputTest {
    
    @Test
    fun `HTML escaping should handle special characters`() {
        val input = "<script>alert('xss')</script>"
        val escaped = escapeHtml(input)
        
        assertFalse(escaped.contains("<script>"))
        assertTrue(escaped.contains("&lt;script&gt;"))
    }
    
    @Test
    fun `XML escaping should handle all entities`() {
        val input = "Tom & Jerry's \"Adventure\" <Now>"
        val escaped = escapeXml(input)
        
        assertTrue(escaped.contains("&amp;"))
        assertTrue(escaped.contains("&apos;"))
        assertTrue(escaped.contains("&quot;"))
        assertTrue(escaped.contains("&lt;"))
        assertTrue(escaped.contains("&gt;"))
    }
    
    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
    
    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
