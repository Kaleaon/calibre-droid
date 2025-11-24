package org.calibre.metadata

fun main() {
    println("Calibre Kotlin Conversion - Proof of Concept")
    println("------------------------------------------")
    
    val book = Metadata(
        title = "The Hitchhiker's Guide to the Galaxy",
        authors = mutableListOf("Douglas Adams"),
        tags = mutableListOf("Science Fiction", "Humor"),
        comments = "Don't Panic!",
        series = "Hitchhiker's Guide",
        seriesIndex = 1.0
    )
    
    println("Created Book Metadata:")
    println(book)
    
    println("Checking if 'publisher' is null: ${book.isNull("publisher")}")
    
    book.publisher = "Pan Books"
    println("Updated Publisher: ${book.publisher}")
    println("Checking if 'publisher' is null: ${book.isNull("publisher")}")
}
