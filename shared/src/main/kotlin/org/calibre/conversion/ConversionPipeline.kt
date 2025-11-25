package org.calibre.conversion

import java.io.File

class ConversionPipeline {
    private val inputPlugins = listOf(EpubInput(), MobiInput(), Azw3Input(), TextInput())
    private val outputPlugins = listOf(TextOutput(), HtmlOutput())
    
    fun convert(inputFile: File, outputFormat: String, outputFile: File) {
        val inputPlugin = inputPlugins.find { it.fileTypes.contains(inputFile.extension.lowercase()) }
            ?: throw Exception("No input plugin found for ${inputFile.extension}")
            
        val outputPlugin = outputPlugins.find { it.fileType == outputFormat.lowercase() }
            ?: throw Exception("No output plugin found for $outputFormat")
            
        val workDir = java.nio.file.Files.createTempDirectory("calibre_conversion").toFile()
        try {
            println("Converting ${inputFile.name} using ${inputPlugin.name} -> OEB -> ${outputPlugin.name}...")
            val book = inputPlugin.convert(inputFile, workDir)
            outputPlugin.convert(book, outputFile)
            println("Conversion successful: ${outputFile.absolutePath}")
        } finally {
            workDir.deleteRecursively()
        }
    }
}
