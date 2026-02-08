package org.calibre.android

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class AndroidLibraryBackupTest {
    @Test
    fun exportImportBackupZip_roundTripsBooksAndFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Ensure a clean start for this test run
        context.deleteDatabase("calibre_droid.db")
        File(context.filesDir, "library.json").delete()
        File(context.filesDir, "library_files").deleteRecursively()

        val lib = AndroidLibrary(context, enableFts = false)

        val tmpBook = File(context.cacheDir, "test_book_${System.currentTimeMillis()}.txt")
        tmpBook.writeText("hello world")
        val id = lib.importBook(tmpBook, originalFileName = "hello.txt")

        val exported = ByteArrayOutputStream()
        ZipOutputStream(exported).use { zip -> lib.exportBackupZip(zip) }

        // Wipe and restore
        context.deleteDatabase("calibre_droid.db")
        File(context.filesDir, "library_files").deleteRecursively()

        val lib2 = AndroidLibrary(context, enableFts = false)
        ZipInputStream(ByteArrayInputStream(exported.toByteArray())).use { zip -> lib2.importBackupZip(zip) }

        val restored = lib2.getMetadata(id)
        requireNotNull(restored)
        assertEquals("hello", restored.title) // from filename sans extension
        val restoredFile = lib2.getBookFile(id)
        requireNotNull(restoredFile)
        assertEquals(true, restoredFile.exists())
    }
}

