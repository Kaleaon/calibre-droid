package org.calibre.devices

import org.calibre.metadata.Metadata
import java.io.File

interface DeviceDriver {
    val name: String
    val description: String
    
    fun isConnected(): Boolean
    fun getBooks(): List<Metadata>
    fun addBook(file: File, metadata: Metadata)
    fun removeBook(id: String) // Use String ID as device IDs vary
}

class LocalFolderDriver(private val folder: File) : DeviceDriver {
    override val name = "Local Folder"
    override val description = "Syncs with a local folder"

    init {
        if (!folder.exists()) folder.mkdirs()
    }

    override fun isConnected() = folder.exists() && folder.isDirectory

    override fun getBooks(): List<Metadata> {
        // Naive implementation: assumes files are named "Title - Author.epub" 
        // or similar, or just lists files. Real implementation needs metadata parsing.
        // For this PoC, we just list files and guess.
        return folder.listFiles()?.filter { it.isFile }?.map { file ->
            Metadata(title = file.nameWithoutExtension) // Simplified
        } ?: emptyList()
    }

    override fun addBook(file: File, metadata: Metadata) {
        val destName = "${metadata.authors.firstOrNull() ?: "Unknown"} - ${metadata.title}.${file.extension}".replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val destFile = File(folder, destName)
        file.copyTo(destFile, overwrite = true)
    }

    override fun removeBook(id: String) {
        // Here ID would be the filename for local folder
        val file = File(folder, id)
        if (file.exists()) file.delete()
    }
}
