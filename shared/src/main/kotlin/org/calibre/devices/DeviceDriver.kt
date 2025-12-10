package org.calibre.devices

import org.calibre.metadata.Metadata
import java.io.File

/**
 * Interface for device drivers that sync ebooks with external devices.
 * 
 * Device drivers allow Calibre to:
 * - Detect connected devices
 * - List books on the device
 * - Add/remove books from the device
 * - Sync metadata and reading progress
 */
interface DeviceDriver {
    /** Human-readable name of this driver */
    val name: String
    
    /** Description of the device type */
    val description: String
    
    /** Supported file formats */
    val supportedFormats: List<String>
        get() = listOf("epub", "mobi", "pdf", "txt")
    
    /**
     * Checks if a device is connected.
     */
    fun isConnected(): Boolean
    
    /**
     * Gets the list of books on the device.
     */
    fun getBooks(): List<Metadata>
    
    /**
     * Adds a book to the device.
     * 
     * @param file The book file to add
     * @param metadata Metadata for the book
     */
    fun addBook(file: File, metadata: Metadata)
    
    /**
     * Removes a book from the device.
     * 
     * @param id The ID of the book to remove (format depends on device)
     */
    fun removeBook(id: String)
    
    /**
     * Gets the free space on the device in bytes.
     */
    fun getFreeSpace(): Long = -1
    
    /**
     * Gets the total space on the device in bytes.
     */
    fun getTotalSpace(): Long = -1
}

/**
 * Device driver for syncing with a local folder.
 * 
 * This is useful for:
 * - USB mass storage devices
 * - Cloud sync folders (Dropbox, Google Drive, etc.)
 * - Network shares
 */
class LocalFolderDriver(private val folder: File) : DeviceDriver {
    override val name = "Local Folder"
    override val description = "Syncs with a local folder"

    init {
        if (!folder.exists()) folder.mkdirs()
    }

    override fun isConnected() = folder.exists() && folder.isDirectory
    
    override fun getFreeSpace(): Long = folder.usableSpace
    
    override fun getTotalSpace(): Long = folder.totalSpace

    override fun getBooks(): List<Metadata> {
        return folder.walkTopDown()
            .filter { it.isFile && isEbookFile(it.name) }
            .map { file ->
                val name = file.nameWithoutExtension
                val parts = name.split(" - ", limit = 2)
                Metadata(
                    title = parts.getOrElse(1) { name },
                    authors = parts.getOrNull(0)?.let { listOf(it) } ?: emptyList()
                )
            }
            .toList()
    }
    
    private fun isEbookFile(name: String): Boolean {
        val ext = name.substringAfterLast('.').lowercase()
        return ext in supportedFormats
    }

    override fun addBook(file: File, metadata: Metadata) {
        val destName = buildFileName(metadata, file.extension)
        val destFile = File(folder, destName)
        file.copyTo(destFile, overwrite = true)
    }
    
    private fun buildFileName(metadata: Metadata, extension: String): String {
        val author = metadata.authors.firstOrNull() ?: "Unknown"
        val title = metadata.title
        return "${sanitizeFileName(author)} - ${sanitizeFileName(title)}.$extension"
    }
    
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .take(100) // Limit length
    }

    override fun removeBook(id: String) {
        // ID is the filename for local folder
        val file = File(folder, id)
        if (file.exists()) {
            file.delete()
        }
    }
}

/**
 * Device scanner that detects connected devices.
 */
object DeviceScanner {
    
    private val drivers = mutableListOf<() -> DeviceDriver?>()
    
    init {
        // Register default drivers
        registerDriver { MtpDeviceFactory.create() }
    }
    
    /**
     * Registers a device driver factory.
     */
    fun registerDriver(factory: () -> DeviceDriver?) {
        drivers.add(factory)
    }
    
    /**
     * Scans for connected devices.
     */
    fun scan(): List<DeviceDriver> {
        val connectedDevices = mutableListOf<DeviceDriver>()
        
        for (factory in drivers) {
            try {
                val driver = factory()
                if (driver != null && driver.isConnected()) {
                    connectedDevices.add(driver)
                }
            } catch (e: Exception) {
                // Ignore driver initialization errors
            }
        }
        
        return connectedDevices
    }
}
