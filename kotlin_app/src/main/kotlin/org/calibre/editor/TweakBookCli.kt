package org.calibre.editor

import org.calibre.utils.Logger
import java.io.File

/**
 * CLI interface for Tweak Book editor.
 */
object TweakBookCli {
    
    fun handleTweakBook(args: Array<String>) {
        if (args.isEmpty()) {
            printTweakBookHelp()
            return
        }
        
        val command = args[0]
        
        when (command) {
            "unpack" -> unpackEpub(args.drop(1).toTypedArray())
            "edit" -> editFile(args.drop(1).toTypedArray())
            "add" -> addFile(args.drop(1).toTypedArray())
            "remove" -> removeFile(args.drop(1).toTypedArray())
            "repack" -> repackEpub(args.drop(1).toTypedArray())
            "list" -> listFiles(args.drop(1).toTypedArray())
            else -> {
                println("Unknown command: $command")
                printTweakBookHelp()
            }
        }
    }
    
    private fun unpackEpub(args: Array<String>) {
        if (args.size < 2) {
            println("Usage: tweak unpack <epub-file> <output-dir>")
            return
        }
        
        val epubFile = File(args[0])
        val outputDir = File(args[1])
        
        try {
            val tweak = TweakBook(epubFile)
            // Copy unpacked files to output directory
            tweak.workDir.copyRecursively(outputDir, overwrite = true)
            println("Unpacked EPUB to: ${outputDir.absolutePath}")
            tweak.cleanup()
        } catch (e: Exception) {
            println("Error: ${e.message}")
            Logger.error("Failed to unpack EPUB", e)
        }
    }
    
    private fun editFile(args: Array<String>) {
        if (args.size < 3) {
            println("Usage: tweak edit <epub-file> <href> <new-content-file>")
            return
        }
        
        val epubFile = File(args[0])
        val href = args[1]
        val contentFile = File(args[2])
        
        if (!contentFile.exists()) {
            println("Error: Content file not found: ${contentFile.absolutePath}")
            return
        }
        
        try {
            val tweak = TweakBook(epubFile)
            val newContent = contentFile.readText()
            tweak.editHtml(href) { newContent }
            
            // Repack to same file or new file
            val outputFile = if (args.size > 3) File(args[3]) else epubFile
            tweak.repack(outputFile)
            tweak.cleanup()
            
            println("Edited and repacked: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            println("Error: ${e.message}")
            Logger.error("Failed to edit file", e)
        }
    }
    
    private fun addFile(args: Array<String>) {
        if (args.size < 4) {
            println("Usage: tweak add <epub-file> <id> <href> <content-file>")
            return
        }
        
        val epubFile = File(args[0])
        val id = args[1]
        val href = args[2]
        val contentFile = File(args[3])
        
        if (!contentFile.exists()) {
            println("Error: Content file not found: ${contentFile.absolutePath}")
            return
        }
        
        try {
            val tweak = TweakBook(epubFile)
            val content = contentFile.readText()
            tweak.addHtmlFile(id, href, content)
            
            val outputFile = if (args.size > 4) File(args[4]) else epubFile
            tweak.repack(outputFile)
            tweak.cleanup()
            
            println("Added file and repacked: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            println("Error: ${e.message}")
            Logger.error("Failed to add file", e)
        }
    }
    
    private fun removeFile(args: Array<String>) {
        if (args.size < 2) {
            println("Usage: tweak remove <epub-file> <href>")
            return
        }
        
        val epubFile = File(args[0])
        val href = args[1]
        
        try {
            val tweak = TweakBook(epubFile)
            tweak.removeFile(href)
            
            val outputFile = if (args.size > 2) File(args[2]) else epubFile
            tweak.repack(outputFile)
            tweak.cleanup()
            
            println("Removed file and repacked: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            println("Error: ${e.message}")
            Logger.error("Failed to remove file", e)
        }
    }
    
    private fun repackEpub(args: Array<String>) {
        if (args.size < 2) {
            println("Usage: tweak repack <epub-file> <output-file>")
            return
        }
        
        val epubFile = File(args[0])
        val outputFile = File(args[1])
        
        try {
            val tweak = TweakBook(epubFile)
            tweak.repack(outputFile)
            tweak.cleanup()
            
            println("Repacked EPUB to: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            println("Error: ${e.message}")
            Logger.error("Failed to repack EPUB", e)
        }
    }
    
    private fun listFiles(args: Array<String>) {
        if (args.isEmpty()) {
            println("Usage: tweak list <epub-file>")
            return
        }
        
        val epubFile = File(args[0])
        
        try {
            val tweak = TweakBook(epubFile)
            val book = tweak.getBook()
            
            println("Files in EPUB:")
            println("Spine (reading order):")
            book.spine.forEach { item ->
                println("  - ${item.href} (${item.mediaType})")
            }
            
            println("\nAll files in manifest:")
            book.manifest.values.forEach { item ->
                println("  - ${item.id}: ${item.href} (${item.mediaType})")
            }
            
            tweak.cleanup()
        } catch (e: Exception) {
            println("Error: ${e.message}")
            Logger.error("Failed to list files", e)
        }
    }
    
    private fun printTweakBookHelp() {
        println("""
            Tweak Book Editor - Edit EPUB files
            
            Commands:
              unpack <epub> <output-dir>     - Unpack EPUB to directory
              edit <epub> <href> <content> [output] - Edit an HTML file
              add <epub> <id> <href> <content> [output] - Add a new HTML file
              remove <epub> <href> [output]  - Remove a file
              repack <epub> <output>         - Repack EPUB
              list <epub>                    - List all files in EPUB
            
            Examples:
              tweak list book.epub
              tweak edit book.epub chapter1.xhtml new_content.html book_edited.epub
              tweak add book.epub new_chapter new_chapter.xhtml content.html
        """.trimIndent())
    }
}
