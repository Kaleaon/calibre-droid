package org.calibre.metadata

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
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
}
