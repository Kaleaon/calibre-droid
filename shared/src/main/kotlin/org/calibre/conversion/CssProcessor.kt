package org.calibre.conversion

import java.io.File

interface CssProcessor {
    fun process(cssContent: String, baseUrl: String): String
    fun flatten(cssFiles: List<File>): String
}

class BasicCssProcessor : CssProcessor {
    override fun process(cssContent: String, baseUrl: String): String {
        // Placeholder for url() re-writing or prefixing
        return cssContent
    }

    override fun flatten(cssFiles: List<File>): String {
        val sb = StringBuilder()
        for (file in cssFiles) {
            if (file.exists()) {
                sb.append("/* File: ${file.name} */\n")
                sb.append(file.readText())
                sb.append("\n\n")
            }
        }
        return sb.toString()
    }
}
