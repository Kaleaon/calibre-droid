package org.calibre.conversion

import java.io.File

class RtfOutput : OutputPlugin {
    override val name = "RTF Output"
    override val fileType = "rtf"

    override fun convert(book: OebBook, outputFile: File) {
        val sb = StringBuilder()
        
        // RTF header
        sb.append("{\\rtf1\\ansi\\deff0")
        sb.append("{\\fonttbl{\\f0 Times New Roman;}}")
        sb.append("{\\colortbl;\\red0\\green0\\blue0;}")
        sb.append("\\paperw12240\\paperh15840\\margl1440\\margr1440\\margt1440\\margb1440")
        sb.append("\\f0\\fs24")
        
        // Title
        sb.append("\\par\\b\\fs32 ${escapeRtf(book.metadata.title)}\\par\\b0\\fs24\\par")
        
        // Authors
        if (book.metadata.authors.isNotEmpty()) {
            sb.append("\\par ${escapeRtf(book.metadata.authors.joinToString(", "))}\\par\\par")
        }
        
        // Content
        for (item in book.spine) {
            if (item.isXhtml()) {
                convertHtmlToRtf(item.file, sb)
            }
        }
        
        sb.append("}")
        outputFile.writeText(sb.toString())
    }
    
    private fun convertHtmlToRtf(htmlFile: File, sb: StringBuilder) {
        val content = htmlFile.readText()
        
        // Simple HTML to RTF conversion
        var rtf = content
        
        // Remove HTML comments
        rtf = rtf.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        
        // Convert headings
        rtf = rtf.replace(Regex("<h1[^>]*>", RegexOption.IGNORE_CASE), "\\par\\b\\fs28 ")
        rtf = rtf.replace(Regex("</h1>", RegexOption.IGNORE_CASE), "\\par\\b0\\fs24\\par")
        rtf = rtf.replace(Regex("<h2[^>]*>", RegexOption.IGNORE_CASE), "\\par\\b\\fs26 ")
        rtf = rtf.replace(Regex("</h2>", RegexOption.IGNORE_CASE), "\\par\\b0\\fs24\\par")
        rtf = rtf.replace(Regex("<h3[^>]*>", RegexOption.IGNORE_CASE), "\\par\\b\\fs24 ")
        rtf = rtf.replace(Regex("</h3>", RegexOption.IGNORE_CASE), "\\par\\b0\\fs24\\par")
        
        // Convert paragraphs
        rtf = rtf.replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "\\par ")
        rtf = rtf.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\\par")
        
        // Convert line breaks
        rtf = rtf.replace(Regex("<br[^>]*/?>", RegexOption.IGNORE_CASE), "\\par")
        
        // Convert bold
        rtf = rtf.replace(Regex("<b[^>]*>", RegexOption.IGNORE_CASE), "\\b ")
        rtf = rtf.replace(Regex("</b>", RegexOption.IGNORE_CASE), "\\b0 ")
        rtf = rtf.replace(Regex("<strong[^>]*>", RegexOption.IGNORE_CASE), "\\b ")
        rtf = rtf.replace(Regex("</strong>", RegexOption.IGNORE_CASE), "\\b0 ")
        
        // Convert italic
        rtf = rtf.replace(Regex("<i[^>]*>", RegexOption.IGNORE_CASE), "\\i ")
        rtf = rtf.replace(Regex("</i>", RegexOption.IGNORE_CASE), "\\i0 ")
        rtf = rtf.replace(Regex("<em[^>]*>", RegexOption.IGNORE_CASE), "\\i ")
        rtf = rtf.replace(Regex("</em>", RegexOption.IGNORE_CASE), "\\i0 ")
        
        // Remove other HTML tags
        rtf = rtf.replace(Regex("<[^>]+>"), "")
        
        // Decode HTML entities
        rtf = rtf.replace("&nbsp;", " ")
        rtf = rtf.replace("&amp;", "&")
        rtf = rtf.replace("&lt;", "<")
        rtf = rtf.replace("&gt;", ">")
        rtf = rtf.replace("&quot;", "\"")
        rtf = rtf.replace("&apos;", "'")
        
        // Extract body content if present
        val bodyStart = rtf.indexOf("<body", ignoreCase = true)
        if (bodyStart != -1) {
            val actualStart = rtf.indexOf(">", bodyStart) + 1
            val bodyEnd = rtf.indexOf("</body>", ignoreCase = true)
            if (actualStart > 0 && bodyEnd > actualStart) {
                rtf = rtf.substring(actualStart, bodyEnd)
            } else if (actualStart > 0) {
                rtf = rtf.substring(actualStart)
            }
        }
        
        // Escape RTF special characters
        rtf = escapeRtf(rtf)
        
        sb.append(rtf)
        sb.append("\\par\\par")
    }
    
    private fun escapeRtf(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("\n", "\\par ")
            .replace("\r", "")
    }
}
