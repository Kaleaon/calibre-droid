package org.calibre.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LibraryTest {

    @Test
    fun testAddBook() {
        val tempFile = File.createTempFile("test_lib", ".json")
        val library = Library(storageFile = tempFile)

        val metadata = Metadata(title = "Test Book", authors = mutableListOf("Test Author"))
        val id = library.addBook(metadata)

        assertNotNull(id)
        assertTrue(id > 0)

        val retrieved = library.getMetadata(id)
        assertNotNull(retrieved)
        assertEquals("Test Book", retrieved?.title)
        assertEquals("Test Author", retrieved?.authors?.firstOrNull())

        tempFile.delete()
    }

    @Test
    fun testSearch() {
        val tempFile = File.createTempFile("test_lib", ".json")
        val library = Library(storageFile = tempFile)

        library.addBook(Metadata(title = "1984", authors = mutableListOf("George Orwell")))
        library.addBook(Metadata(title = "Animal Farm", authors = mutableListOf("George Orwell")))
        library.addBook(Metadata(title = "Brave New World", authors = mutableListOf("Aldous Huxley")))

        val results = library.search("Orwell")
        assertEquals(2, results.size)

        val titleResults = library.search("title:1984")
        assertEquals(1, titleResults.size)
        assertEquals("1984", titleResults[0].title)

        tempFile.delete()
    }

    @Test
    fun testLoadLegacyArrayStorageFormat() {
        val tempFile = File.createTempFile("test_lib_legacy", ".json")
        tempFile.writeText(
            """
            [
              {
                "id": 7,
                "title": "Legacy Book",
                "authors": ["Legacy Author"],
                "tags": [],
                "dateAdded": "2026-01-01T10:00:00",
                "dateModified": "2026-01-01T10:00:00"
              }
            ]
            """.trimIndent()
        )

        val library = Library(storageFile = tempFile)
        val loaded = library.getMetadata(7)

        assertNotNull(loaded)
        assertEquals("Legacy Book", loaded?.title)

        tempFile.delete()
    }

    @Test
    fun testPersistVersionedSnapshotFormat() {
        val tempFile = File.createTempFile("test_lib_snapshot", ".json")
        val library = Library(storageFile = tempFile)
        val id = library.addBook(Metadata(title = "Snapshot Book", authors = mutableListOf("Snap Author")))

        // force flush has already happened in addBook; verify wrapper fields
        val content = tempFile.readText()
        assertTrue(content.contains("\"schemaVersion\""))
        assertTrue(content.contains("\"books\""))

        val reloaded = Library(storageFile = tempFile)
        assertEquals("Snapshot Book", reloaded.getMetadata(id)?.title)

        tempFile.delete()
    }
}
