package org.calibre.devices

import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * MTP (Media Transfer Protocol) device driver.
 * 
 * MTP is the standard protocol for transferring files to/from Android devices
 * and other media players. This driver provides a common interface for
 * interacting with MTP devices.
 * 
 * On Linux, this uses libmtp or gvfs-mtp.
 * On Windows, this uses WPD (Windows Portable Devices).
 * On macOS, this uses Android File Transfer or similar tools.
 */
interface MtpDevice : DeviceDriver {
    
    /**
     * Storage information for an MTP device.
     */
    data class StorageInfo(
        val id: Long,
        val name: String,
        val description: String,
        val totalSpace: Long,
        val freeSpace: Long,
        val storageType: StorageType
    )
    
    enum class StorageType {
        INTERNAL,
        REMOVABLE,
        UNKNOWN
    }
    
    /**
     * File information on the MTP device.
     */
    data class MtpFile(
        val id: Long,
        val parentId: Long,
        val storageId: Long,
        val name: String,
        val size: Long,
        val modifiedTime: Long,
        val isFolder: Boolean
    )
    
    /**
     * Gets the list of storage areas on the device.
     */
    fun getStorages(): List<StorageInfo>
    
    /**
     * Lists files in a folder.
     */
    fun listFiles(storageId: Long, folderId: Long): List<MtpFile>
    
    /**
     * Gets a file from the device.
     */
    fun getFile(fileId: Long, output: OutputStream)
    
    /**
     * Puts a file on the device.
     */
    fun putFile(storageId: Long, parentId: Long, name: String, input: InputStream, size: Long): MtpFile
    
    /**
     * Deletes a file from the device.
     */
    fun deleteFile(fileId: Long)
    
    /**
     * Creates a folder on the device.
     */
    fun createFolder(storageId: Long, parentId: Long, name: String): MtpFile
}

/**
 * Factory for creating MTP device connections.
 */
object MtpDeviceFactory {
    
    /**
     * Creates an MTP device driver appropriate for the current platform.
     */
    fun create(): MtpDeviceDriver? {
        val osName = System.getProperty("os.name").lowercase()
        
        return when {
            osName.contains("linux") -> LinuxMtpDevice()
            osName.contains("windows") -> WindowsMtpDevice()
            osName.contains("mac") -> MacOsMtpDevice()
            else -> null
        }
    }
    
    /**
     * Scans for connected MTP devices.
     */
    fun scanDevices(): List<MtpDeviceInfo> {
        val driver = create() ?: return emptyList()
        return driver.scanDevices()
    }
}

/**
 * Information about a detected MTP device.
 */
data class MtpDeviceInfo(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val serialNumber: String,
    val vendorId: Int,
    val productId: Int
)

/**
 * Abstract MTP device driver with common functionality.
 */
abstract class MtpDeviceDriver : MtpDevice {
    protected var connectedDevice: MtpDeviceInfo? = null
    protected var isOpen = false
    
    override val name: String get() = "MTP Device"
    override val description: String get() = connectedDevice?.name ?: "MTP Device"
    
    /**
     * Scans for connected MTP devices.
     */
    abstract fun scanDevices(): List<MtpDeviceInfo>
    
    /**
     * Opens a connection to a device.
     */
    abstract fun open(device: MtpDeviceInfo)
    
    /**
     * Closes the device connection.
     */
    abstract fun close()
    
    override fun isConnected(): Boolean = isOpen
    
    override fun getBooks(): List<Metadata> {
        if (!isConnected()) return emptyList()
        
        val books = mutableListOf<Metadata>()
        val storages = getStorages()
        
        for (storage in storages) {
            scanForBooks(storage.id, 0, books)
        }
        
        return books
    }
    
    private fun scanForBooks(storageId: Long, folderId: Long, books: MutableList<Metadata>, depth: Int = 0) {
        if (depth > 10) return // Prevent infinite recursion
        
        try {
            val files = listFiles(storageId, folderId)
            
            for (file in files) {
                if (file.isFolder) {
                    // Recursively scan folders
                    if (!isIgnoredFolder(file.name)) {
                        scanForBooks(storageId, file.id, books, depth + 1)
                    }
                } else if (isEbookFile(file.name)) {
                    books.add(Metadata(
                        title = file.name.substringBeforeLast('.'),
                        id = file.id.toInt()
                    ))
                }
            }
        } catch (e: Exception) {
            Logger.warn("Error scanning folder $folderId: ${e.message}")
        }
    }
    
    private fun isIgnoredFolder(name: String): Boolean {
        val ignored = setOf(
            "android", "dcim", "movies", "music", "notifications",
            "pictures", "ringtones", "samsung", "lost.dir", "video"
        )
        return name.lowercase() in ignored || name.startsWith(".")
    }
    
    private fun isEbookFile(name: String): Boolean {
        val ext = name.substringAfterLast('.').lowercase()
        return ext in setOf("epub", "mobi", "azw", "azw3", "pdf", "fb2", "txt", "html", "cbz", "cbr")
    }
    
    override fun addBook(file: File, metadata: Metadata) {
        if (!isConnected()) return
        
        val storages = getStorages()
        if (storages.isEmpty()) return
        
        // Find or create Books folder
        val storage = storages.first()
        val booksFolder = findOrCreateBooksFolder(storage.id)
        
        // Upload the file
        val fileName = "${metadata.authors.firstOrNull() ?: "Unknown"} - ${metadata.title}.${file.extension}"
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        
        file.inputStream().use { input ->
            putFile(storage.id, booksFolder, fileName, input, file.length())
        }
        
        Logger.info("Uploaded book: $fileName")
    }
    
    private fun findOrCreateBooksFolder(storageId: Long): Long {
        val rootFiles = listFiles(storageId, 0)
        
        // Look for existing Books folder
        val booksFolder = rootFiles.find { 
            it.isFolder && it.name.equals("Books", ignoreCase = true)
        }
        
        if (booksFolder != null) {
            return booksFolder.id
        }
        
        // Create Books folder
        val created = createFolder(storageId, 0, "Books")
        return created.id
    }
    
    override fun removeBook(id: String) {
        try {
            deleteFile(id.toLong())
            Logger.info("Removed book with ID: $id")
        } catch (e: Exception) {
            Logger.error("Failed to remove book: ${e.message}")
        }
    }
}

/**
 * Linux MTP device driver using libmtp.
 */
class LinuxMtpDevice : MtpDeviceDriver() {
    
    private var mtpPath: Path? = null
    
    // Maintain ID→Path mapping to properly resolve folder IDs
    private val idToPath = mutableMapOf<Long, Path>()
    private val pathToId = mutableMapOf<Path, Long>()
    private var nextId = 1L
    
    private fun getOrCreateId(path: Path): Long {
        return pathToId.getOrPut(path) {
            val id = nextId++
            idToPath[id] = path
            id
        }
    }
    
    override fun scanDevices(): List<MtpDeviceInfo> {
        val devices = mutableListOf<MtpDeviceInfo>()
        
        try {
            // Check for gvfs-mtp mount points
            val mtpDir = File("/run/user/${getUid()}/gvfs")
            if (mtpDir.exists()) {
                for (mount in mtpDir.listFiles() ?: emptyArray()) {
                    if (mount.name.startsWith("mtp:")) {
                        val parts = mount.name.split("_")
                        devices.add(MtpDeviceInfo(
                            id = mount.absolutePath,
                            name = mount.name,
                            manufacturer = parts.getOrElse(1) { "Unknown" },
                            model = parts.getOrElse(2) { "Unknown" },
                            serialNumber = "",
                            vendorId = 0,
                            productId = 0
                        ))
                    }
                }
            }
            
            // Check for simple-mtpfs mount points
            val mtpfsDir = File(System.getProperty("user.home"), "mtp")
            if (mtpfsDir.exists() && mtpfsDir.listFiles()?.isNotEmpty() == true) {
                devices.add(MtpDeviceInfo(
                    id = mtpfsDir.absolutePath,
                    name = "MTP Device",
                    manufacturer = "Unknown",
                    model = "Unknown",
                    serialNumber = "",
                    vendorId = 0,
                    productId = 0
                ))
            }
        } catch (e: Exception) {
            Logger.warn("Error scanning for MTP devices: ${e.message}")
        }
        
        return devices
    }
    
    private fun getUid(): Int {
        return try {
            ProcessBuilder("id", "-u").start().inputStream.bufferedReader().readText().trim().toInt()
        } catch (e: Exception) {
            1000
        }
    }
    
    override fun open(device: MtpDeviceInfo) {
        mtpPath = Path.of(device.id)
        connectedDevice = device
        isOpen = Files.exists(mtpPath)
        
        // Clear ID mappings on new connection
        idToPath.clear()
        pathToId.clear()
        nextId = 1L
        
        // Register root path with ID 0
        mtpPath?.let { 
            idToPath[0L] = it
            pathToId[it] = 0L
        }
    }
    
    override fun close() {
        mtpPath = null
        connectedDevice = null
        isOpen = false
        idToPath.clear()
        pathToId.clear()
        nextId = 1L
    }
    
    override fun getStorages(): List<MtpDevice.StorageInfo> {
        val path = mtpPath ?: return emptyList()
        val storages = mutableListOf<MtpDevice.StorageInfo>()
        
        try {
            // Use Files.list with use{} to ensure the stream is closed properly
            Files.list(path).use { stream ->
                stream.filter { Files.isDirectory(it) }.forEach { storage ->
                    try {
                        val storageId = getOrCreateId(storage)
                        val store = Files.getFileStore(storage)
                        storages.add(MtpDevice.StorageInfo(
                            id = storageId,
                            name = storage.fileName.toString(),
                            description = storage.fileName.toString(),
                            totalSpace = store.totalSpace,
                            freeSpace = store.usableSpace,
                            storageType = if (storage.fileName.toString().contains("SD", ignoreCase = true))
                                MtpDevice.StorageType.REMOVABLE else MtpDevice.StorageType.INTERNAL
                        ))
                    } catch (e: Exception) {
                        Logger.debug("Error reading storage info for $storage: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.warn("Error getting storages: ${e.message}")
        }
        
        return storages
    }
    
    override fun listFiles(storageId: Long, folderId: Long): List<MtpDevice.MtpFile> {
        val path = mtpPath ?: return emptyList()
        val files = mutableListOf<MtpDevice.MtpFile>()
        
        try {
            // Resolve folder path from ID mapping
            val dirPath = if (folderId == 0L) path else idToPath[folderId] ?: return emptyList()
            
            // Use Files.list with use{} to ensure the stream is closed properly
            Files.list(dirPath).use { stream ->
                stream.forEach { file ->
                    try {
                        val fileId = getOrCreateId(file)
                        files.add(MtpDevice.MtpFile(
                            id = fileId,
                            parentId = folderId,
                            storageId = storageId,
                            name = file.fileName.toString(),
                            size = if (Files.isDirectory(file)) 0 else Files.size(file),
                            modifiedTime = Files.getLastModifiedTime(file).toMillis(),
                            isFolder = Files.isDirectory(file)
                        ))
                    } catch (e: Exception) {
                        Logger.debug("Error reading file info for $file: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.warn("Error listing files: ${e.message}")
        }
        
        return files
    }
    
    override fun getFile(fileId: Long, output: OutputStream) {
        val filePath = idToPath[fileId]
        if (filePath == null) {
            Logger.warn("getFile: Unknown file ID $fileId")
            return
        }
        
        try {
            Files.copy(filePath, output)
        } catch (e: Exception) {
            Logger.error("Error getting file: ${e.message}")
        }
    }
    
    override fun putFile(storageId: Long, parentId: Long, name: String, input: InputStream, size: Long): MtpDevice.MtpFile {
        // Resolve parent folder from ID mapping
        val parentPath = idToPath[parentId] ?: mtpPath ?: throw IllegalStateException("Device not connected")
        val destPath = parentPath.resolve(name)
        
        Files.copy(input, destPath)
        
        val fileId = getOrCreateId(destPath)
        
        return MtpDevice.MtpFile(
            id = fileId,
            parentId = parentId,
            storageId = storageId,
            name = name,
            size = size,
            modifiedTime = System.currentTimeMillis(),
            isFolder = false
        )
    }
    
    override fun deleteFile(fileId: Long) {
        val filePath = idToPath[fileId]
        if (filePath == null) {
            Logger.warn("deleteFile: Unknown file ID $fileId")
            return
        }
        
        try {
            Files.deleteIfExists(filePath)
            idToPath.remove(fileId)
            pathToId.remove(filePath)
        } catch (e: Exception) {
            Logger.error("Error deleting file: ${e.message}")
        }
    }
    
    override fun createFolder(storageId: Long, parentId: Long, name: String): MtpDevice.MtpFile {
        // Resolve parent folder from ID mapping
        val parentPath = idToPath[parentId] ?: mtpPath ?: throw IllegalStateException("Device not connected")
        val dirPath = parentPath.resolve(name)
        
        Files.createDirectories(dirPath)
        
        val folderId = getOrCreateId(dirPath)
        
        return MtpDevice.MtpFile(
            id = folderId,
            parentId = parentId,
            storageId = storageId,
            name = name,
            size = 0,
            modifiedTime = System.currentTimeMillis(),
            isFolder = true
        )
    }
}

/**
 * Windows MTP device driver using WPD (Windows Portable Devices).
 */
class WindowsMtpDevice : MtpDeviceDriver() {
    
    override fun scanDevices(): List<MtpDeviceInfo> {
        // On Windows, we would use COM/WPD API
        // This is a placeholder implementation
        Logger.info("Windows MTP device scanning not fully implemented")
        return emptyList()
    }
    
    override fun open(device: MtpDeviceInfo) {
        connectedDevice = device
        isOpen = true
    }
    
    override fun close() {
        connectedDevice = null
        isOpen = false
    }
    
    override fun getStorages(): List<MtpDevice.StorageInfo> = emptyList()
    override fun listFiles(storageId: Long, folderId: Long): List<MtpDevice.MtpFile> = emptyList()
    override fun getFile(fileId: Long, output: OutputStream) {}
    override fun putFile(storageId: Long, parentId: Long, name: String, input: InputStream, size: Long): MtpDevice.MtpFile {
        throw UnsupportedOperationException("Windows MTP not implemented")
    }
    override fun deleteFile(fileId: Long) {}
    override fun createFolder(storageId: Long, parentId: Long, name: String): MtpDevice.MtpFile {
        throw UnsupportedOperationException("Windows MTP not implemented")
    }
}

/**
 * macOS MTP device driver.
 */
class MacOsMtpDevice : MtpDeviceDriver() {
    
    override fun scanDevices(): List<MtpDeviceInfo> {
        // On macOS, MTP requires Android File Transfer or similar
        Logger.info("macOS MTP device scanning not fully implemented")
        return emptyList()
    }
    
    override fun open(device: MtpDeviceInfo) {
        connectedDevice = device
        isOpen = true
    }
    
    override fun close() {
        connectedDevice = null
        isOpen = false
    }
    
    override fun getStorages(): List<MtpDevice.StorageInfo> = emptyList()
    override fun listFiles(storageId: Long, folderId: Long): List<MtpDevice.MtpFile> = emptyList()
    override fun getFile(fileId: Long, output: OutputStream) {}
    override fun putFile(storageId: Long, parentId: Long, name: String, input: InputStream, size: Long): MtpDevice.MtpFile {
        throw UnsupportedOperationException("macOS MTP not implemented")
    }
    override fun deleteFile(fileId: Long) {}
    override fun createFolder(storageId: Long, parentId: Long, name: String): MtpDevice.MtpFile {
        throw UnsupportedOperationException("macOS MTP not implemented")
    }
}
