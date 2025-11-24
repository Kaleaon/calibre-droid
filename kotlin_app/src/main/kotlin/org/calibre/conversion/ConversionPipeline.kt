package org.calibre.conversion

import java.io.File

class ConversionPipeline {
    private val inputPlugins = listOf(EpubInput())
    private val outputPlugins = listOf(TextOutput(), HtmlOutput())
    
    fun convert(inputFile: File, outputFormat: String, outputFile: File) {
        val inputPlugin = inputPlugins.find { it.fileTypes.contains(inputFile.extension.lowercase()) }
            ?: throw Exception("No input plugin found for ${inputFile.extension}")
            
        val outputPlugin = outputPlugins.find { it.fileType == outputFormat.lowercase() }
            ?: throw Exception("No output plugin found for $outputFormat")
            
        // Create temporary working directory
        val workDir = java.nio.file.Files.createTempDirectory("calibre_conversion").toFile()
        try {
            println("Converting ${inputFile.name} using ${inputPlugin.name} -> OEB -> ${outputPlugin.name}...")
            
            // 1. Input -> OEB
            val book = inputPlugin.convert(inputFile, workDir)
            
            // 2. OEB -> Output
            outputPlugin.convert(book, outputFile)
            
            println("Conversion successful: ${outputFile.absolutePath}")
            
        } finally {
            workDir.deleteRecursively()
        }
    }
}
