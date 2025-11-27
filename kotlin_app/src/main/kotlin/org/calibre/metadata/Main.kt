package org.calibre.metadata

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Scanner

fun main(args: Array<String>) {
    val library = Library(extraParsers = listOf(DesktopPdfParser()))

    if (args.isNotEmpty() && args[0] == "gui") {
        try {
            System.setProperty("java.awt.headless", "false")
            val gui = org.calibre.gui.DesktopGui(library)
            gui.isVisible = true
        } catch (e: java.awt.HeadlessException) {
            println("Cannot start GUI: Headless environment detected.")
        } catch (e: Exception) {
            println("Error starting GUI: ${e.message}")
            e.printStackTrace()
        }
    } else if (args.isEmpty()) {
        runInteractiveMode(library)
    } else {
        runCommand(library, args)
    }
}

fun runCommand(library: Library, args: Array<String>) {
    val command = args[0]
    when (command) {
        "list" -> listBooks(library)
        "add" -> handleAdd(library, args.drop(1).toTypedArray())
        "search" -> searchBooks(library, args.drop(1).joinToString(" "))
        "remove" -> removeBook(library, args.getOrNull(1))
        "export" -> exportBook(library, args.drop(1).toTypedArray())
        "convert" -> convertBook(library, args.drop(1).toTypedArray())
        "server" -> startServer(library, args.drop(1).toTypedArray())
        "device" -> handleDevice(library, args.drop(1).toTypedArray())
        "import-db" -> importDatabase(library, args.drop(1).toTypedArray())
        "fetch-meta" -> fetchMetadata(args.drop(1).joinToString(" "))
        "opds" -> testOpds(args.drop(1).toTypedArray())
        "stats" -> showStatistics(library)
        "bookmark" -> handleBookmark(library, args.drop(1).toTypedArray())
        "rating" -> setRating(library, args.drop(1).toTypedArray())
        "tag" -> handleTag(library, args.drop(1).toTypedArray())
        "batch" -> handleBatch(library, args.drop(1).toTypedArray())
        "export-library" -> exportLibrary(library, args.drop(1).toTypedArray())
        "import-library" -> importLibrary(library, args.drop(1).toTypedArray())
        "collections" -> showCollections(library, args.drop(1).toTypedArray())
        "help" -> printHelp()
        else -> println("Unknown command: $command. Try 'help'.")
    }
}

fun runInteractiveMode(library: Library) {
    val scanner = Scanner(System.`in`)
    println("Calibre Kotlin CLI")
    println("Type 'help' for commands, 'exit' to quit.")

    while (true) {
        print("> ")
        if (!scanner.hasNextLine()) break
        val line = scanner.nextLine().trim()
        if (line.isEmpty()) continue
        
        val parts = line.split("\\s+".toRegex()).toTypedArray()
        if (parts[0] == "exit") break
        
        runCommand(library, parts)
    }
}

fun testOpds(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toIntOrNull() ?: 8080 else 8080
    val url = "http://localhost:$port/opds"
    println("Testing OPDS feed at $url...")
    try {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
            println("Success! Feed content preview:")
            println(response.body().take(500) + "...")
        } else {
            println("Failed with status code: ${response.statusCode()}")
            if (response.statusCode() == 404) {
                println("Is the server running?")
            }
        }
    } catch (e: java.net.ConnectException) {
        println("Connection refused. Make sure the server is running using 'server' command in another terminal.")
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}

fun fetchMetadata(query: String) {
    if (query.isBlank()) {
        println("Usage: fetch-meta <title/author>")
        return
    }
    println("Searching Google Books for '$query'...")
    val client = org.calibre.metadata.sources.GoogleBooksClient()
    val results = client.search(query)
    
    if (results.isEmpty()) {
        println("No results found.")
    } else {
        results.forEachIndexed { index, meta ->
            println("[${index + 1}] ${meta.title} by ${meta.authors.joinToString(", ")}")
            if (meta.publisher != null) println("    Publisher: ${meta.publisher}")
            println("")
        }
    }
}

fun startServer(library: Library, args: Array<String>) {
    var port = 8080
    if (args.isNotEmpty()) {
        port = args[0].toIntOrNull() ?: 8080
    }
    val server = ContentServer(library, port)
    server.start()
    println("Press Enter to stop...")
    System.`in`.read()
}

fun handleDevice(library: Library, args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: device <path_to_folder> [sync <book_id>]")
        return
    }
    
    val folder = File(args[0])
    val driver = org.calibre.devices.LocalFolderDriver(folder)
    
    if (!driver.isConnected()) {
        println("Device folder not found or invalid.")
        return
    }
    
    if (args.size > 1 && args[1] == "sync") {
        if (args.size < 3) {
            println("Usage: device <path> sync <book_id>")
            return
        }
        try {
            val id = args[2].toInt()
            val bookFile = library.getBookFile(id)
            val metadata = library.getMetadata(id)
            
            if (bookFile != null && metadata != null) {
                driver.addBook(bookFile, metadata)
                println("Synced book $id to device folder.")
            } else {
                println("Book not found.")
            }
        } catch (e: Exception) {
            println("Sync failed: ${e.message}")
        }
    } else {
        println("Books on device (${driver.name}):")
        driver.getBooks().forEach { println("- ${it.title}") }
    }
}

fun importDatabase(library: Library, args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: import-db <path_to_metadata.db>")
        return
    }
    val dbFile = File(args[0])
    if (!dbFile.exists()) {
        println("Database file not found.")
        return
    }
    
    val service = org.calibre.db.DatabaseService(dbFile)
    service.importToLibrary(library)
}

fun listBooks(library: Library) {
    val books = library.getAllBooks()
    if (books.isEmpty()) {
        println("Library is empty.")
    } else {
        books.forEach { println(it) }
    }
}

fun handleAdd(library: Library, args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: add <file_path> OR add --title <title> --author <author>")
        return
    }

    val possibleFile = File(args[0])
    if (possibleFile.exists() && possibleFile.isFile) {
        println("Importing file: ${possibleFile.path}")
        try {
            val id = library.importBook(possibleFile)
            println("Successfully imported book with ID: $id")
            val book = library.getMetadata(id)
            if (book != null) println(book)
        } catch (e: Exception) {
            println("Error importing book: ${e.message}")
            e.printStackTrace()
        }
    } else {
        addBookManual(library, args)
    }
}

fun addBookManual(library: Library, args: Array<String>) {
    var title = Constants.UNKNOWN_TITLE
    val authors = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--title" -> {
                if (i + 1 < args.size) {
                    title = args[i+1]
                    i++
                }
            }
            "--author" -> {
                 if (i + 1 < args.size) {
                    authors.add(args[i+1])
                    i++
                }
            }
        }
        i++
    }
    
    if (authors.isEmpty()) authors.add(Constants.UNKNOWN_AUTHOR)
    
    val book = Metadata(title = title, authors = authors)
    val id = library.addBook(book)
    println("Added book manually with ID: $id")
}

fun searchBooks(library: Library, query: String) {
    val results = library.search(query)
    if (results.isEmpty()) {
        println("No results found.")
    } else {
        results.forEach { println(it) }
    }
}

fun removeBook(library: Library, idStr: String?) {
    if (idStr == null) {
        println("Usage: remove <id>")
        return
    }
    try {
        val id = idStr.toInt()
        if (library.removeBook(id)) {
            println("Removed book $id")
        } else {
            println("Book $id not found")
        }
    } catch (e: NumberFormatException) {
        println("Invalid ID format")
    }
}

fun exportBook(library: Library, args: Array<String>) {
    if (args.size < 2) {
        println("Usage: export <id> <destination_directory>")
        return
    }
    try {
        val id = args[0].toInt()
        val destDir = File(args[1])
        library.exportBook(id, destDir)
    } catch (e: NumberFormatException) {
        println("Invalid ID format")
    } catch (e: Exception) {
        println("Export failed: ${e.message}")
    }
}

fun convertBook(library: Library, args: Array<String>) {
    if (args.size < 2) {
        println("Usage: convert <id> <format> (txt, html)")
        return
    }
    
    val idStr = args[0]
    val format = args[1].lowercase()
    
    try {
        val id = idStr.toInt()
        val bookFile = library.getBookFile(id)
        
        if (bookFile == null) {
            println("Book file for ID $id not found.")
            return
        }
        
        val metadata = library.getMetadata(id)
        val title = metadata?.title ?: "converted"
        val safeTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val outFile = File("${safeTitle}.$format")
        
        val pipeline = org.calibre.conversion.ConversionPipeline()
        pipeline.convert(bookFile, format, outFile)
        
    } catch (e: NumberFormatException) {
        println("Invalid ID")
    } catch (e: Exception) {
        println("Conversion failed: ${e.message}")
        e.printStackTrace()
    }
}

fun showStatistics(library: Library) {
    val stats = library.getReadingStatistics()
    println("=== Reading Statistics ===")
    println("Total Books: ${stats.totalBooks}")
    println("Read: ${stats.readBooks} (${String.format("%.1f", stats.readPercentage)}%)")
    println("Unread: ${stats.unreadBooks}")
    println("Total Reading Time: ${String.format("%.1f", stats.totalReadingTimeHours)} hours")
    println("Total Bookmarks: ${stats.totalBookmarks}")
    println("Average Rating: ${String.format("%.1f", stats.averageRating)}/5.0")
    
    val recent = library.getRecentlyRead(5)
    if (recent.isNotEmpty()) {
        println("\nRecently Read:")
        recent.forEach { book ->
            println("  - ${book.title} (${book.readingProgress.lastReadDate})")
        }
    }
}

fun handleBookmark(library: Library, args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: bookmark <id> [add <position> [note]] | list | remove <id> <bookmark_id>")
        return
    }
    
    when (args[0]) {
        "add" -> {
            if (args.size < 3) {
                println("Usage: bookmark add <id> <position> [note]")
                return
            }
            val id = args[1].toIntOrNull() ?: return println("Invalid ID")
            val position = args[2]
            val note = if (args.size > 3) args.drop(3).joinToString(" ") else null
            try {
                val bookmark = library.addBookmark(id, position, note)
                println("Bookmark added: ${bookmark.id}")
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }
        "list" -> {
            if (args.size < 2) {
                println("Usage: bookmark list <id>")
                return
            }
            val id = args[1].toIntOrNull() ?: return println("Invalid ID")
            val book = library.getMetadata(id)
            if (book != null) {
                if (book.bookmarks.isEmpty()) {
                    println("No bookmarks")
                } else {
                    book.bookmarks.forEach { bm ->
                        println("  ${bm.id}: ${bm.position} - ${bm.note ?: ""}")
                    }
                }
            }
        }
        "remove" -> {
            if (args.size < 3) {
                println("Usage: bookmark remove <id> <bookmark_id>")
                return
            }
            val id = args[1].toIntOrNull() ?: return println("Invalid ID")
            val bookmarkId = args[2]
            if (library.removeBookmark(id, bookmarkId)) {
                println("Bookmark removed")
            } else {
                println("Bookmark not found")
            }
        }
    }
}

fun setRating(library: Library, args: Array<String>) {
    if (args.size < 2) {
        println("Usage: rating <id> <0-5>")
        return
    }
    val id = args[0].toIntOrNull() ?: return println("Invalid ID")
    val rating = args[1].toDoubleOrNull()?.coerceIn(0.0, 5.0) ?: return println("Invalid rating (0-5)")
    library.setRating(id, rating)
    println("Rating set to $rating")
}

fun handleTag(library: Library, args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: tag <add|remove|list> <id> [tag_name]")
        return
    }
    
    when (args[0]) {
        "add" -> {
            if (args.size < 3) {
                println("Usage: tag add <id> <tag>")
                return
            }
            val id = args[1].toIntOrNull() ?: return println("Invalid ID")
            library.addTag(id, args[2])
            println("Tag added")
        }
        "remove" -> {
            if (args.size < 3) {
                println("Usage: tag remove <id> <tag>")
                return
            }
            val id = args[1].toIntOrNull() ?: return println("Invalid ID")
            library.removeTag(id, args[2])
            println("Tag removed")
        }
        "list" -> {
            if (args.size < 2) {
                println("Usage: tag list <id>")
                return
            }
            val id = args[1].toIntOrNull() ?: return println("Invalid ID")
            val book = library.getMetadata(id)
            if (book != null) {
                if (book.tags.isEmpty()) {
                    println("No tags")
                } else {
                    println("Tags: ${book.tags.joinToString(", ")}")
                }
            }
        }
    }
}

fun handleBatch(library: Library, args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: batch <remove|export> <id1,id2,...> [destination]")
        return
    }
    
    when (args[0]) {
        "remove" -> {
            if (args.size < 2) {
                println("Usage: batch remove <id1,id2,...>")
                return
            }
            val ids = args[1].split(",").mapNotNull { it.trim().toIntOrNull() }
            val removed = library.batchRemove(ids)
            println("Removed $removed books")
        }
        "export" -> {
            if (args.size < 3) {
                println("Usage: batch export <id1,id2,...> <destination>")
                return
            }
            val ids = args[1].split(",").mapNotNull { it.trim().toIntOrNull() }
            val destDir = File(args[2])
            val exported = library.batchExport(ids, destDir)
            println("Exported $exported books")
        }
    }
}

fun exportLibrary(library: Library, args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: export-library <output_file.json>")
        return
    }
    try {
        val destFile = File(args[0])
        library.exportLibrary(destFile)
        println("Library exported to: ${destFile.absolutePath}")
    } catch (e: Exception) {
        println("Export failed: ${e.message}")
    }
}

fun importLibrary(library: Library, args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: import-library <library_file.json>")
        return
    }
    try {
        val sourceFile = File(args[0])
        if (!sourceFile.exists()) {
            println("Library file not found")
            return
        }
        library.importLibrary(sourceFile)
        println("Library imported successfully")
    } catch (e: Exception) {
        println("Import failed: ${e.message}")
    }
}

fun showCollections(library: Library, args: Array<String>) {
    val tags = library.getAllTags()
    if (tags.isEmpty()) {
        println("No tags/collections found")
    } else {
        println("Tags/Collections:")
        tags.sorted().forEach { tag ->
            val count = library.getBooksByTag(tag).size
            println("  $tag ($count books)")
        }
    }
}

fun printHelp() {
    println("Commands:")
    println("  gui")
    println("  list")
    println("  add <file_path>")
    println("  add --title <title> --author <author>")
    println("  search <query> OR <field>:<value>")
    println("  remove <id>")
    println("  export <id> <destination_directory>")
    println("  convert <id> <format> (txt, html, epub)")
    println("  server [port]")
    println("  opds [port] (test client)")
    println("  device <folder> [sync <id>]")
    println("  import-db <metadata.db>")
    println("  fetch-meta <query>")
    println("  stats (show reading statistics)")
    println("  bookmark <add|list|remove> <id> [args]")
    println("  rating <id> <0-5>")
    println("  tag <add|remove|list> <id> [tag]")
    println("  batch <remove|export> <id1,id2,...> [destination]")
    println("  export-library <file.json>")
    println("  import-library <file.json>")
    println("  collections (list all tags/collections)")
    println("  exit")
}
