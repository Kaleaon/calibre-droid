package org.calibre.formats.pdb

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PDB (Palm Database) header reader and writer.
 * 
 * The PDB format is a container format used by many Palm OS applications.
 * It consists of a header followed by record data. The identity field
 * (type + creator) determines the actual format of the content.
 * 
 * Common PDB identities:
 * - "TEXtREAd" - PalmDOC
 * - "PNPdPPrs" / "PNRdPPrs" - eReader
 * - "DataPlkr" - Plucker
 * - "BOOKMTIT" / "BOOKMTIU" - Haodoo
 * - "BOOKMOBI" - MOBI format
 */
data class PdbHeader(
    val name: String,
    val attributes: Int,
    val version: Int,
    val creationDate: Long,
    val modificationDate: Long,
    val lastBackupDate: Long,
    val modificationNumber: Long,
    val appInfoOffset: Long,
    val sortInfoOffset: Long,
    val type: String,      // 4 bytes
    val creator: String,   // 4 bytes
    val uniqueIdSeed: Long,
    val nextRecordListId: Long,
    val numRecords: Int,
    val recordOffsets: List<RecordInfo>
) {
    /**
     * The identity string combining type and creator (8 bytes).
     * This determines the format of the content.
     */
    val identity: String get() = type + creator
    
    companion object {
        const val HEADER_SIZE = 78
        
        // Known PDB identities
        const val PALMDOC = "TEXtREAd"
        const val EREADER_1 = "PNPdPPrs"
        const val EREADER_2 = "PNRdPPrs"
        const val PLUCKER = "DataPlkr"
        const val HAODOO_1 = "BOOKMTIT"
        const val HAODOO_2 = "BOOKMTIU"
        const val MOBI = "BOOKMOBI"
        
        fun read(stream: InputStream): PdbHeader {
            val headerBytes = ByteArray(HEADER_SIZE)
            val bytesRead = stream.read(headerBytes)
            if (bytesRead < HEADER_SIZE) {
                throw IllegalArgumentException("Invalid PDB file: header too short")
            }
            
            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
            
            // Read name (32 bytes, null-terminated)
            val nameBytes = ByteArray(32)
            buffer.get(nameBytes)
            val name = String(nameBytes).trimEnd('\u0000').replace(Regex("[^-A-Za-z0-9 ]+"), "_")
            
            val attributes = buffer.short.toInt() and 0xFFFF
            val version = buffer.short.toInt() and 0xFFFF
            val creationDate = buffer.int.toLong() and 0xFFFFFFFFL
            val modificationDate = buffer.int.toLong() and 0xFFFFFFFFL
            val lastBackupDate = buffer.int.toLong() and 0xFFFFFFFFL
            val modificationNumber = buffer.int.toLong() and 0xFFFFFFFFL
            val appInfoOffset = buffer.int.toLong() and 0xFFFFFFFFL
            val sortInfoOffset = buffer.int.toLong() and 0xFFFFFFFFL
            
            // Type (4 bytes)
            val typeBytes = ByteArray(4)
            buffer.get(typeBytes)
            val type = String(typeBytes, Charsets.US_ASCII)
            
            // Creator (4 bytes)
            val creatorBytes = ByteArray(4)
            buffer.get(creatorBytes)
            val creator = String(creatorBytes, Charsets.US_ASCII)
            
            val uniqueIdSeed = buffer.int.toLong() and 0xFFFFFFFFL
            val nextRecordListId = buffer.int.toLong() and 0xFFFFFFFFL
            val numRecords = buffer.short.toInt() and 0xFFFF
            
            // Read record list (8 bytes per record)
            val recordInfoBytes = ByteArray(numRecords * 8)
            stream.read(recordInfoBytes)
            val recordBuffer = ByteBuffer.wrap(recordInfoBytes).order(ByteOrder.BIG_ENDIAN)
            
            val records = mutableListOf<RecordInfo>()
            for (i in 0 until numRecords) {
                val offset = recordBuffer.int.toLong() and 0xFFFFFFFFL
                val attrs = recordBuffer.get().toInt() and 0xFF
                val uniqueId = ((recordBuffer.get().toInt() and 0xFF) shl 16) or
                               ((recordBuffer.get().toInt() and 0xFF) shl 8) or
                               (recordBuffer.get().toInt() and 0xFF)
                records.add(RecordInfo(offset, attrs, uniqueId))
            }
            
            // Skip 2 bytes padding after record list
            stream.skip(2)
            
            return PdbHeader(
                name = name,
                attributes = attributes,
                version = version,
                creationDate = creationDate,
                modificationDate = modificationDate,
                lastBackupDate = lastBackupDate,
                modificationNumber = modificationNumber,
                appInfoOffset = appInfoOffset,
                sortInfoOffset = sortInfoOffset,
                type = type,
                creator = creator,
                uniqueIdSeed = uniqueIdSeed,
                nextRecordListId = nextRecordListId,
                numRecords = numRecords,
                recordOffsets = records
            )
        }
    }
}

/**
 * Information about a single record in the PDB file.
 */
data class RecordInfo(
    val offset: Long,
    val attributes: Int,
    val uniqueId: Int
)

/**
 * PDB header builder for creating new PDB files.
 */
class PdbHeaderBuilder(
    private val identity: String,
    private val title: String
) {
    init {
        require(identity.length == 8) { "Identity must be exactly 8 characters (type + creator)" }
    }
    
    /**
     * Builds the PDB header with the given section lengths.
     * 
     * @param sectionLengths List of lengths for each section
     * @return ByteArray containing the complete header including record list
     */
    fun buildHeader(sectionLengths: List<Int>): ByteArray {
        val numRecords = sectionLengths.size
        val headerSize = HEADER_SIZE + (numRecords * 8) + 2
        val buffer = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN)
        
        // Title (32 bytes, null-terminated)
        val titleBytes = title.replace(Regex("[^-A-Za-z0-9 ]+"), "_")
            .take(31)
            .toByteArray(Charsets.US_ASCII)
            .copyOf(32)
        buffer.put(titleBytes)
        
        // Attributes and version
        buffer.putShort(0)
        buffer.putShort(0)
        
        // Timestamps (current time as Palm epoch - seconds since Jan 1, 1904)
        val now = (System.currentTimeMillis() / 1000) + 2082844800L
        buffer.putInt(now.toInt())
        buffer.putInt(now.toInt())
        buffer.putInt(0) // lastBackupDate
        buffer.putInt(0) // modificationNumber
        buffer.putInt(0) // appInfoOffset
        buffer.putInt(0) // sortInfoOffset
        
        // Identity (type + creator, 8 bytes)
        buffer.put(identity.toByteArray(Charsets.US_ASCII))
        
        // Unique ID seed and next record list ID
        buffer.putInt(numRecords) // uniqueIdSeed
        buffer.putInt(0)          // nextRecordListId
        
        // Number of records
        buffer.putShort(numRecords.toShort())
        
        // Record list
        var offset = headerSize
        for ((index, length) in sectionLengths.withIndex()) {
            buffer.putInt(offset)
            buffer.put(0) // attributes
            buffer.put(0) // unique ID byte 1
            buffer.put(0) // unique ID byte 2
            buffer.put(0) // unique ID byte 3
            offset += length
        }
        
        // Padding
        buffer.putShort(0)
        
        return buffer.array()
    }
    
    companion object {
        private const val HEADER_SIZE = 78
    }
}
