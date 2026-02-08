package org.calibre.android

import android.app.Application

class CalibreApplication : Application() {
    lateinit var library: AndroidLibrary
        private set

    override fun onCreate() {
        super.onCreate()

        // Room-backed library with legacy JSON migration if present
        library = AndroidLibrary(this)
    }
}
