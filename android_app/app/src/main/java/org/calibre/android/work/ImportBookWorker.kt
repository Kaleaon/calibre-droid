package org.calibre.android.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.calibre.android.AndroidLibrary
import java.io.File
import java.io.FileOutputStream

class ImportBookWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriString = inputData.getString(KEY_URI) ?: return@withContext Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME)

        val uri = Uri.parse(uriString)
        val ext = guessExtension(displayName, inputData.getString(KEY_MIME_TYPE))

        val tmp = File(applicationContext.cacheDir, "import_${System.currentTimeMillis()}.$ext")
        try {
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure()

            val lib = AndroidLibrary(applicationContext)
            val id = lib.importBook(tmp, originalFileName = displayName)
            tmp.delete()

            Result.success(Data.Builder().putInt(KEY_IMPORTED_ID, id).build())
        } catch (_: Exception) {
            tmp.delete()
            Result.failure()
        }
    }

    private fun guessExtension(displayName: String?, mime: String?): String {
        val fromName = displayName?.substringAfterLast('.', "")?.trim()?.lowercase()
        if (!fromName.isNullOrBlank()) return fromName

        return when (mime?.lowercase()) {
            "application/epub+zip" -> "epub"
            "application/x-mobipocket-ebook" -> "mobi"
            "application/pdf" -> "pdf"
            "text/html" -> "html"
            "text/plain" -> "txt"
            else -> "bin"
        }
    }

    companion object {
        const val KEY_URI = "uri"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_MIME_TYPE = "mimeType"
        const val KEY_IMPORTED_ID = "importedId"
    }
}

