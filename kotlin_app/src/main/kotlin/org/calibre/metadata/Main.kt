package org.calibre.metadata

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
        "add" -> addBook(library, args.drop(1).toTypedArray())
        "search" -> searchBooks(library, args.drop(1).joinToString(" "))
        "remove" -> removeBook(library, args.getOrNull(1))
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

fun addBook(library: Library, args: Array<String>) {
    var title = Constants.UNKNOWN_TITLE
    val authors = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--title" -> {
                if (i + 1 < args.size) {
                    title = args[i+1] // Simple assumption: no spaces unless quoted (which shell handles)
                    // But for interactive, spaces are split.
                    // Since this is a simple PoC, we'll just take the next token or handle simple reconstruction if mostly used from shell.
                    // For better CLI, one should use a library like kotlinx-cli or Picocli.
                    // We'll assume single word or quoted from shell passing.
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
    println("Added book with ID: $id")
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

fun printHelp() {
    println("Commands:")
    println("  list")
    println("  add --title <title> --author <author>")
    println("  search <query>")
    println("  remove <id>")
    println("  exit")
}
