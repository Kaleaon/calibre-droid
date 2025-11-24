package org.calibre.metadata

import java.io.File
import java.util.Scanner

fun main(args: Array<String>) {
    val library = Library()

    if (args.isEmpty()) {
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
        println("Usage: convert <id> <format> (only 'txt' supported)")
        return
    }
    
    val idStr = args[0]
    val format = args[1].lowercase()
    
    if (format != "txt") {
        println("Only 'txt' format is currently supported.")
        return
    }
    
    try {
        val id = idStr.toInt()
        val bookFile = library.getBookFile(id)
        
        if (bookFile == null) {
            println("Book file for ID $id not found.")
            return
        }
        
        val metadata = library.getMetadata(id)
        val title = metadata?.title ?: "converted"
        val outFile = File("${title.replace(" ", "_")}.txt")
        
        println("Converting book $id to TEXT...")
        val converter = TextConverter()
        converter.convertEpubToText(bookFile, outFile)
        
        println("Conversion complete: ${outFile.absolutePath}")
        
    } catch (e: NumberFormatException) {
        println("Invalid ID")
    } catch (e: Exception) {
        println("Conversion failed: ${e.message}")
        e.printStackTrace()
    }
}

fun printHelp() {
    println("Commands:")
    println("  list")
    println("  add <file_path>")
    println("  add --title <title> --author <author>")
    println("  search <query> OR <field>:<value>")
    println("  remove <id>")
    println("  export <id> <destination_directory>")
    println("  convert <id> txt")
    println("  exit")
}
