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
 * A foreground service responsible for exporting the app's library to a
 * Calibre-compatible `metadata.db` file.
 */
class ExporterService : Service() {

    companion object {
        const val ACTION_START_EXPORT = "com.calibre.action.START_EXPORT"
        const val EXTRA_OUTPUT_DIRECTORY_URI = "com.calibre.extra.OUTPUT_DIRECTORY_URI"
        private const val NOTIFICATION_CHANNEL_ID = "ExporterServiceChannel"
        private const val NOTIFICATION_ID = 2 // Must be different from ImporterService
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_EXPORT) {
            val outputDirectoryUri = intent.getParcelableExtra<Uri>(EXTRA_OUTPUT_DIRECTORY_URI)
            if (outputDirectoryUri != null) {
                startForeground(NOTIFICATION_ID, createNotification())
                startExport(outputDirectoryUri)
            }
        }
        return START_STICKY
    }

    /**
     * Starts the export process in a background thread.
     * @param outputDirectoryUri The URI of the directory to export the library to.
     */
    private fun startExport(outputDirectoryUri: Uri) {
        // TODO: Create a coroutine to run the actual export logic from the Exporter class.
        // For now, just stop the service when done.
        stopSelf()
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Library Exporter",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Calibre Library Export")
            .setContentText("Exporting your library...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
