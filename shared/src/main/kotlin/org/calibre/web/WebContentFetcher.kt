package org.calibre.web

import org.calibre.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

/**
 * Base class for fetching web content.
 * Provides common HTTP functionality for all web downloaders.
 */
open class WebContentFetcher {
    
    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val DEFAULT_TIMEOUT = 30000
        const val MAX_RETRIES = 3
        const val RETRY_DELAY = 2000L
    }
    
    protected var userAgent = DEFAULT_USER_AGENT
    protected var timeout = DEFAULT_TIMEOUT
    protected var cookies = mutableMapOf<String, String>()
    protected var headers = mutableMapOf<String, String>()
    
    /**
     * Fetches HTML content from a URL.
     */
    fun fetchHtml(url: String): String? {
        return fetch(url, "text/html,application/xhtml+xml")
    }
    
    /**
     * Fetches JSON content from a URL.
     */
    fun fetchJson(url: String): String? {
        return fetch(url, "application/json")
    }
    
    /**
     * Fetches binary content (images, etc.) from a URL.
     */
    fun fetchBinary(url: String): ByteArray? {
        var lastException: Exception? = null
        
        for (attempt in 1..MAX_RETRIES) {
            try {
                val connection = createConnection(url)
                connection.setRequestProperty("Accept", "*/*")
                
                if (connection.responseCode == 200) {
                    return connection.inputStream.use { it.readBytes() }
                } else if (connection.responseCode in 301..303 || connection.responseCode == 307) {
                    val redirect = connection.getHeaderField("Location")
                    if (redirect != null) {
                        return fetchBinary(redirect)
                    }
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(RETRY_DELAY * attempt)
                }
            }
        }
        
        Logger.error("Failed to fetch binary from $url: ${lastException?.message}")
        return null
    }
    
    /**
     * Main fetch method with retry logic.
     */
    protected fun fetch(url: String, accept: String): String? {
        var lastException: Exception? = null
        
        for (attempt in 1..MAX_RETRIES) {
            try {
                val connection = createConnection(url)
                connection.setRequestProperty("Accept", accept)
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                
                val responseCode = connection.responseCode
                
                if (responseCode == 200) {
                    val encoding = connection.contentEncoding
                    val inputStream = if (encoding == "gzip") {
                        GZIPInputStream(connection.inputStream)
                    } else {
                        connection.inputStream
                    }
                    
                    val charset = getCharset(connection.contentType)
                    return BufferedReader(InputStreamReader(inputStream, charset)).use { 
                        it.readText() 
                    }
                } else if (responseCode in 301..303 || responseCode == 307) {
                    val redirect = connection.getHeaderField("Location")
                    if (redirect != null) {
                        return fetch(redirect, accept)
                    }
                } else if (responseCode == 429) {
                    // Rate limited - wait longer
                    Thread.sleep(RETRY_DELAY * attempt * 2)
                    continue
                }
                
                Logger.warn("HTTP $responseCode for $url")
                return null
                
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(RETRY_DELAY * attempt)
                }
            }
        }
        
        Logger.error("Failed to fetch $url: ${lastException?.message}")
        return null
    }
    
    protected fun createConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = timeout
        connection.readTimeout = timeout
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        
        // Add custom headers
        for ((key, value) in headers) {
            connection.setRequestProperty(key, value)
        }
        
        // Add cookies
        if (cookies.isNotEmpty()) {
            val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            connection.setRequestProperty("Cookie", cookieString)
        }
        
        return connection
    }
    
    protected fun getCharset(contentType: String?): Charset {
        if (contentType != null) {
            val charsetMatch = Regex("charset=([^;\\s]+)").find(contentType)
            if (charsetMatch != null) {
                try {
                    return Charset.forName(charsetMatch.groupValues[1])
                } catch (e: Exception) {}
            }
        }
        return Charsets.UTF_8
    }
    
    /**
     * Extracts text content between HTML tags.
     */
    protected fun extractText(html: String, startTag: String, endTag: String): String? {
        val startIndex = html.indexOf(startTag)
        if (startIndex < 0) return null
        
        val contentStart = startIndex + startTag.length
        val endIndex = html.indexOf(endTag, contentStart)
        if (endIndex < 0) return null
        
        return html.substring(contentStart, endIndex)
    }
    
    /**
     * Extracts all matches of a pattern.
     */
    protected fun extractAll(html: String, pattern: Regex): List<String> {
        return pattern.findAll(html).map { it.groupValues.getOrElse(1) { it.value } }.toList()
    }
    
    /**
     * Cleans HTML to plain text.
     */
    protected fun htmlToText(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<br[^>]*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { 
                val code = it.groupValues[1].toIntOrNull() ?: 32
                Char(code).toString()
            }
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * Escapes text for HTML output.
     */
    protected fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
    
    /**
     * URL encodes a string.
     */
    protected fun urlEncode(text: String): String {
        return URLEncoder.encode(text, "UTF-8")
    }
}

/**
 * Result of a web content download.
 */
data class WebContent(
    val title: String,
    val author: String?,
    val description: String?,
    val chapters: List<WebChapter>,
    val coverUrl: String?,
    val sourceUrl: String,
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    val totalWords: Int get() = chapters.sumOf { it.wordCount }
}

/**
 * A chapter of web content.
 */
data class WebChapter(
    val title: String,
    val content: String,
    val url: String,
    val index: Int,
    val wordCount: Int = content.split(Regex("\\s+")).size
)
