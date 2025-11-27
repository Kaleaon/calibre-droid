package org.calibre.conversion

import java.io.File
import java.net.URI
import java.nio.file.Paths

interface CssProcessor {
    fun process(cssContent: String, baseUrl: String): String
    fun flatten(cssFiles: List<File>): String
}

class BasicCssProcessor : CssProcessor {
    
    /**
     * Processes CSS content, rewriting relative URLs in url() functions
     * to be relative to the baseUrl.
     */
    override fun process(cssContent: String, baseUrl: String): String {
        if (baseUrl.isEmpty()) return cssContent
        
        // Pattern to match url() functions with various quote styles
        // Matches: url(path), url('path'), url("path"), url( path ), etc.
        val urlPattern = Regex(
            """url\s*\(\s*([^)]+)\s*\)""",
            RegexOption.IGNORE_CASE
        )
        
        return urlPattern.replace(cssContent) { matchResult ->
            val urlContent = matchResult.groupValues[1].trim()
            
            // Remove quotes if present
            val unquotedUrl = when {
                (urlContent.startsWith("'") && urlContent.endsWith("'")) ||
                (urlContent.startsWith("\"") && urlContent.endsWith("\"")) -> {
                    urlContent.substring(1, urlContent.length - 1)
                }
                else -> urlContent
            }
            
            // Skip data URIs, absolute URLs, and hash-only URLs
            if (unquotedUrl.startsWith("data:") ||
                unquotedUrl.startsWith("http://") ||
                unquotedUrl.startsWith("https://") ||
                unquotedUrl.startsWith("file://") ||
                unquotedUrl.startsWith("#") ||
                unquotedUrl.startsWith("mailto:") ||
                unquotedUrl.startsWith("javascript:")) {
                return@replace matchResult.value
            }
            
            // Rewrite relative URLs
            val rewrittenUrl = rewriteUrl(unquotedUrl, baseUrl)
            
            // Preserve original quote style
            val quote = when {
                urlContent.startsWith("'") && urlContent.endsWith("'") -> "'"
                urlContent.startsWith("\"") && urlContent.endsWith("\"") -> "\""
                else -> ""
            }
            
            "url($quote$rewrittenUrl$quote)"
        }
    }
    
    /**
     * Rewrites a relative URL to be relative to the baseUrl.
     * If the URL is already absolute or a data URI, returns it unchanged.
     */
    private fun rewriteUrl(url: String, baseUrl: String): String {
        try {
            // If baseUrl is a file path, resolve relative to it
            if (baseUrl.startsWith("/") || baseUrl.contains(":\\")) {
                val basePath = Paths.get(baseUrl).parent
                if (basePath != null) {
                    val resolved = basePath.resolve(url).normalize()
                    // Return relative path from basePath's parent if possible
                    return basePath.relativize(resolved).toString().replace('\\', '/')
                }
            }
            
            // If baseUrl is a URI, resolve relative to it
            val baseUri = URI(baseUrl)
            val resolvedUri = baseUri.resolve(url)
            
            // Return as relative path if possible, otherwise absolute
            return if (resolvedUri.scheme == baseUri.scheme) {
                // Same scheme, return relative path
                val basePath = baseUri.path.substringBeforeLast('/')
                val resolvedPath = resolvedUri.path
                if (resolvedPath.startsWith(basePath)) {
                    resolvedPath.substring(basePath.length).removePrefix("/")
                } else {
                    resolvedPath.removePrefix("/")
                }
            } else {
                resolvedUri.toString()
            }
        } catch (e: Exception) {
            // If URL rewriting fails, return original
            org.calibre.utils.Logger.warn("Failed to rewrite URL: $url (base: $baseUrl): ${e.message}")
            return url
        }
    }

    override fun flatten(cssFiles: List<File>): String {
        val sb = StringBuilder()
        for (file in cssFiles) {
            if (file.exists()) {
                sb.append("/* File: ${file.name} */\n")
                val content = file.readText()
                // Process URLs in each CSS file relative to its location
                val processed = process(content, file.parent ?: "")
                sb.append(processed)
                sb.append("\n\n")
            }
        }
        return sb.toString()
    }
}
