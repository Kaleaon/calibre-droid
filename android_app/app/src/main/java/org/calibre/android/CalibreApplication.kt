package org.calibre.android

import android.app.Application
import org.calibre.metadata.Library
import java.io.File

class CalibreApplication : Application() {
    lateinit var library: Library
        private set

    override fun onCreate() {
        super.onCreate()
        
        val libraryDir = File(filesDir, "library_files")
        val storageFile = File(filesDir, "library.json")
        
        // We can inject Android-specific parsers here if needed in the future
        library = Library(storageFile, libraryDir)
    }
}
