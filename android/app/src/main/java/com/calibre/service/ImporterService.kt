package com.calibre.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.calibre.R

/**
 * A foreground service responsible for importing an existing Calibre library.
 *
 * This service runs on a background thread and shows a persistent notification
 * to the user, indicating that the import process is underway. This prevents the
 * system from killing the process during a long-running operation.
 */
class ImporterService : Service() {

    companion object {
        const val ACTION_START_IMPORT = "com.calibre.action.START_IMPORT"
        const val EXTRA_DIRECTORY_URI = "com.calibre.extra.DIRECTORY_URI"
        private const val NOTIFICATION_CHANNEL_ID = "ImporterServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_IMPORT) {
            val directoryUri = intent.getParcelableExtra<Uri>(EXTRA_DIRECTORY_URI)
            if (directoryUri != null) {
                startForeground(NOTIFICATION_ID, createNotification())
                startImport(directoryUri)
            }
        }
        // If the service is killed, it will be automatically restarted.
        return START_STICKY
    }

    /**
     * Starts the import process in a background thread.
     * @param directoryUri The URI of the Calibre library directory.
     */
    private fun startImport(directoryUri: Uri) {
        // TODO: Create a coroutine to run the actual import logic from the Importer class.
        // For now, just stop the service when done.
        stopSelf()
    }

    /**
     * Creates the notification channel (for Android O and above) and the notification itself.
     */
    private fun createNotification(): Notification {
        // Create a notification channel for Android 8.0 (Oreo) and higher.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Library Importer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Build the persistent notification.
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Calibre Library Import")
            .setContentText("Importing your library...")
            .setSmallIcon(R.mipmap.ic_launcher) // A default icon is needed.
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This service does not support binding, so return null.
        return null
    }
}
