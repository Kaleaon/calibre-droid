package org.calibre.devices

import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Device driver for Amazon Kindle e-readers.
 * 
 * Supports:
 * - Kindle Paperwhite (all generations)
 * - Kindle Oasis
 * - Kindle Basic
 * - Kindle Scribe
 * - Kindle Fire tablets (when connected as USB storage)
 * 
 * When connected via USB, Kindles appear as mass storage devices.
 * Books should be placed in the "documents" folder.
 * 
 * Supported formats: MOBI, AZW, AZW3, PDF, TXT
 */
class KindleDriver : DeviceDriver {
    
    override val name = "Kindle"
    override val description = "Amazon Kindle e-reader"
    override val supportedFormats = listOf("mobi", "azw", "azw3", "pdf", "txt")
    
    private var kindlePath: Path? = null
    private var documentsPath: Path? = null
    
    init {
        detectDevice()
    }
    
    private fun detectDevice() {
        // Look for Kindle mount points
        val possiblePaths = listOf(
            // Linux mount points
            "/media/${System.getProperty("user.name")}/Kindle",
            "/media/Kindle",
            "/mnt/Kindle",
            "/run/media/${System.getProperty("user.name")}/Kindle",
            // macOS mount points
            "/Volumes/Kindle",
            // Windows drive letters
            "D:/", "E:/", "F:/", "G:/", "H:/"
        )
        
        for (pathStr in possiblePaths) {
            val path = Path.of(pathStr)
            if (isKindleDevice(path)) {
                kindlePath = path
                documentsPath = path.resolve("documents")
                Logger.info("Kindle detected at: $path")
                break
            }
        }
    }
    
    private fun isKindleDevice(path: Path): Boolean {
        if (!Files.exists(path) || !Files.isDirectory(path)) return false
        
        // Check for Kindle-specific directories
        val systemDir = path.resolve("system")
        val documentsDir = path.resolve("documents")
        
        return Files.exists(documentsDir) && 
               (Files.exists(systemDir) || Files.exists(path.resolve("audible")))
    }
    
    override fun isConnected(): Boolean = kindlePath != null && Files.exists(kindlePath)
    
    override fun getFreeSpace(): Long = kindlePath?.let { Files.getFileStore(it).usableSpace } ?: -1
    
    override fun getTotalSpace(): Long = kindlePath?.let { Files.getFileStore(it).totalSpace } ?: -1
    
    override fun getBooks(): List<Metadata> {
        val docs = documentsPath ?: return emptyList()
        if (!Files.exists(docs)) return emptyList()
        
        return Files.walk(docs)
            .filter { Files.isRegularFile(it) }
            .filter { isEbookFile(it.fileName.toString()) }
            .map { file ->
                parseKindleMetadata(file)
            }
            .toList()
    }
    
    private fun isEbookFile(name: String): Boolean {
        val ext = name.substringAfterLast('.').lowercase()
        return ext in supportedFormats
    }
    
    private fun parseKindleMetadata(file: Path): Metadata {
        val name = file.fileName.toString()
        val baseName = name.substringBeforeLast('.')
        
        // Try to parse "Author - Title" format
        val parts = baseName.split(" - ", limit = 2)
        
        return if (parts.size == 2) {
            Metadata(
                title = parts[1].trim(),
                authors = listOf(parts[0].trim()),
                id = file.hashCode()
            )
        } else {
            Metadata(
                title = baseName,
                id = file.hashCode()
            )
        }
    }
    
    override fun addBook(file: File, metadata: Metadata) {
        val docs = documentsPath ?: throw IllegalStateException("Kindle not connected")
        
        // Create author subfolder (optional, but helps organization)
        val author = metadata.authors.firstOrNull()?.let { sanitizePath(it) } ?: "Unknown"
        val authorDir = docs.resolve(author)
        Files.createDirectories(authorDir)
        
        // Build filename
        val ext = file.extension.lowercase()
        val fileName = "${sanitizePath(metadata.title)}.$ext"
        val destPath = authorDir.resolve(fileName)
        
        // Copy file
        Files.copy(file.toPath(), destPath)
        Logger.info("Added book to Kindle: $fileName")
        
        // Generate APNX file for page number support (for MOBI/AZW3)
        if (ext in listOf("mobi", "azw", "azw3")) {
            try {
                generateApnx(destPath, metadata)
            } catch (e: Exception) {
                Logger.warn("Failed to generate APNX: ${e.message}")
            }
        }
    }
    
    private fun sanitizePath(name: String): String {
        return name
            .replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)
    }
    
    /**
     * Generates an APNX file for page number display on Kindle.
     */
    private fun generateApnx(bookPath: Path, metadata: Metadata) {
        val sdrDir = Path.of(bookPath.toString().substringBeforeLast('.') + ".sdr")
        Files.createDirectories(sdrDir)
        
        val apnxPath = sdrDir.resolve(bookPath.fileName.toString().substringBeforeLast('.') + ".apnx")
        
        // Simple APNX generation (placeholder - real implementation would analyze book content)
        val pageCount = estimatePageCount(bookPath)
        val apnxContent = generateApnxContent(bookPath, pageCount)
        
        Files.write(apnxPath, apnxContent)
        Logger.debug("Generated APNX with $pageCount pages")
    }
    
    private fun estimatePageCount(bookPath: Path): Int {
        val size = Files.size(bookPath)
        // Rough estimate: ~2KB per page
        return maxOf(1, (size / 2048).toInt())
    }
    
    private fun generateApnxContent(bookPath: Path, pageCount: Int): ByteArray {
        // APNX file format (simplified)
        val output = java.io.ByteArrayOutputStream()
        val data = java.io.DataOutputStream(output)
        
        // Magic number
        data.writeInt(65537) // 0x00010001
        
        // Content start offset
        data.writeInt(12)
        
        // Page count
        data.writeInt(pageCount)
        
        // Placeholder page positions (real implementation would calculate actual positions)
        val interval = 4096 // Approximate bytes per page
        for (i in 0 until pageCount) {
            data.writeInt(i * interval)
        }
        
        return output.toByteArray()
    }
    
    override fun removeBook(id: String) {
        val docs = documentsPath ?: return
        
        // Search for the file by ID (hash)
        Files.walk(docs)
            .filter { Files.isRegularFile(it) && it.hashCode().toString() == id }
            .findFirst()
            .ifPresent { file ->
                Files.deleteIfExists(file)
                
                // Also delete .sdr folder if exists
                val sdrDir = Path.of(file.toString().substringBeforeLast('.') + ".sdr")
                if (Files.exists(sdrDir)) {
                    Files.walk(sdrDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach { Files.deleteIfExists(it) }
                }
                
                Logger.info("Removed book from Kindle: ${file.fileName}")
            }
    }
    
    /**
     * Syncs thumbnails for cover display on Kindle.
     */
    fun syncThumbnails(books: List<Metadata>) {
        val kindle = kindlePath ?: return
        val thumbnailDir = kindle.resolve("system").resolve("thumbnails")
        Files.createDirectories(thumbnailDir)
        
        for (book in books) {
            book.coverData?.let { coverData ->
                try {
                    val thumbName = "thumbnail_${book.id}_EBOK_portrait.jpg"
                    val thumbPath = thumbnailDir.resolve(thumbName)
                    Files.write(thumbPath, coverData)
                } catch (e: Exception) {
                    Logger.debug("Failed to sync thumbnail: ${e.message}")
                }
            }
        }
    }
}
