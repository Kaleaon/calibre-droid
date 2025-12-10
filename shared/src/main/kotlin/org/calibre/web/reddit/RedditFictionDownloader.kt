package org.calibre.web.reddit

import org.calibre.web.WebChapter
import org.calibre.web.WebContent
import org.calibre.web.WebContentFetcher
import org.calibre.utils.Logger

/**
 * Reddit fiction downloader for serial stories.
 * 
 * Supports:
 * - r/HFY (Humanity, Fuck Yeah!)
 * - r/nosleep
 * - r/WritingPrompts
 * - r/shortstories
 * - r/redditserials
 * - Any subreddit with serial fiction
 * 
 * Features:
 * - Full series download via wiki links
 * - Author post history search
 * - Thread continuation capture
 * - Markdown to HTML conversion
 */
class RedditFictionDownloader : WebContentFetcher() {
    
    init {
        // Reddit requires a user agent
        userAgent = "CalibreKotlin/1.0 Fiction Downloader"
    }
    
    /**
     * Download a Reddit story or series.
     */
    fun download(url: String, progressCallback: ((Int, Int) -> Unit)? = null): WebContent? {
        return when {
            url.contains("/wiki/") -> downloadFromWiki(url, progressCallback)
            url.contains("/comments/") -> downloadThread(url, progressCallback)
            url.contains("/user/") -> downloadFromAuthor(url, progressCallback)
            url.contains("/r/") && url.contains("/s/") -> 
                downloadFromSearchShare(url, progressCallback)
            else -> {
                Logger.warn("Could not parse Reddit URL: $url")
                null
            }
        }
    }
    
    /**
     * Download a series from a wiki page (common for r/HFY)
     */
    private fun downloadFromWiki(url: String, progressCallback: ((Int, Int) -> Unit)?): WebContent? {
        val jsonUrl = url.replace("reddit.com", "reddit.com") + ".json"
        val json = fetchJson(jsonUrl) ?: return null
        
        // Extract wiki content
        val content = extractJsonString(json, "content_md") ?: return null
        
        // Find links to chapters
        val links = mutableListOf<Pair<String, String>>()
        val linkPattern = Regex("\\[([^\\]]+)\\]\\((https?://[^)]+reddit\\.com/r/[^)]+/comments/[^)]+)\\)")
        
        for (match in linkPattern.findAll(content)) {
            val title = match.groupValues[1]
            val link = match.groupValues[2]
            links.add(Pair(title, link))
        }
        
        if (links.isEmpty()) {
            // Try old reddit format
            val oldLinks = Regex("\\[([^\\]]+)\\]\\(/r/([^/]+)/comments/([^/]+)/[^)]+\\)")
            for (match in oldLinks.findAll(content)) {
                val title = match.groupValues[1]
                val subreddit = match.groupValues[2]
                val postId = match.groupValues[3]
                val link = "https://www.reddit.com/r/$subreddit/comments/$postId/"
                links.add(Pair(title, link))
            }
        }
        
        // Extract title from wiki
        val title = extractJsonString(json, "title") 
            ?: url.substringAfterLast("/wiki/").substringBefore("?")
                .replace("_", " ")
        
        // Download chapters
        val chapters = mutableListOf<WebChapter>()
        
        for ((index, pair) in links.withIndex()) {
            val (chapterTitle, chapterUrl) = pair
            progressCallback?.invoke(index + 1, links.size)
            
            val chapterContent = downloadThreadContent(chapterUrl)
            if (chapterContent != null) {
                chapters.add(WebChapter(
                    title = chapterTitle,
                    content = chapterContent,
                    url = chapterUrl,
                    index = index + 1
                ))
            }
            
            // Rate limiting for Reddit
            Thread.sleep(2000)
        }
        
        return WebContent(
            title = title,
            author = null,
            description = null,
            chapters = chapters,
            coverUrl = null,
            sourceUrl = url,
            tags = listOf("Reddit", "Web Fiction")
        )
    }
    
    /**
     * Download a single thread and optionally find continuations.
     */
    private fun downloadThread(url: String, progressCallback: ((Int, Int) -> Unit)?): WebContent? {
        val jsonUrl = url.trimEnd('/') + ".json"
        val json = fetchJson(jsonUrl) ?: return null
        
        // Parse the main post
        val postData = extractBetween(json, "\"selftext\":", ",\"") ?: ""
        val selftext = extractJsonString(json, "selftext") ?: ""
        val title = extractJsonString(json, "title") ?: "Unknown Post"
        val author = extractJsonString(json, "author") ?: "Unknown"
        val subreddit = extractJsonString(json, "subreddit") ?: ""
        
        val chapters = mutableListOf<WebChapter>()
        
        // Convert markdown to HTML
        val htmlContent = markdownToHtml(selftext)
        
        chapters.add(WebChapter(
            title = title,
            content = htmlContent,
            url = url,
            index = 1
        ))
        
        progressCallback?.invoke(1, 1)
        
        // Look for "next" link in the post
        val nextPattern = Regex("\\[(?:Next|Part \\d+|Chapter \\d+|Continue)\\]\\(([^)]+)\\)", RegexOption.IGNORE_CASE)
        val nextMatch = nextPattern.find(selftext)
        
        if (nextMatch != null) {
            var nextUrl = nextMatch.groupValues[1]
            if (!nextUrl.startsWith("http")) {
                nextUrl = "https://www.reddit.com$nextUrl"
            }
            
            var chapterIndex = 2
            while (nextUrl.isNotEmpty() && chapterIndex <= 200) { // Limit to prevent infinite loops
                progressCallback?.invoke(chapterIndex, chapterIndex)
                
                val nextContent = downloadThreadContent(nextUrl)
                if (nextContent != null) {
                    val nextTitle = "Part $chapterIndex"
                    chapters.add(WebChapter(
                        title = nextTitle,
                        content = nextContent,
                        url = nextUrl,
                        index = chapterIndex
                    ))
                    
                    // Find next link in this chapter
                    val textContent = htmlToText(nextContent)
                    val nextNextMatch = nextPattern.find(textContent)
                    nextUrl = if (nextNextMatch != null) {
                        var url = nextNextMatch.groupValues[1]
                        if (!url.startsWith("http")) {
                            url = "https://www.reddit.com$url"
                        }
                        url
                    } else ""
                    
                    chapterIndex++
                    Thread.sleep(2000)
                } else {
                    break
                }
            }
        }
        
        val tags = mutableListOf("Reddit", "r/$subreddit")
        
        return WebContent(
            title = title,
            author = author,
            description = null,
            chapters = chapters,
            coverUrl = null,
            sourceUrl = url,
            tags = tags
        )
    }
    
    /**
     * Download content from a Reddit thread.
     */
    private fun downloadThreadContent(url: String): String? {
        val jsonUrl = url.trimEnd('/') + ".json"
        val json = fetchJson(jsonUrl) ?: return null
        
        val selftext = extractJsonString(json, "selftext") ?: return null
        return markdownToHtml(selftext)
    }
    
    /**
     * Download all posts from an author in a subreddit.
     */
    private fun downloadFromAuthor(url: String, progressCallback: ((Int, Int) -> Unit)?): WebContent? {
        // Extract username
        val username = Regex("/user/([^/]+)").find(url)?.groupValues?.get(1) ?: return null
        
        // Get user's submissions
        val userUrl = "https://www.reddit.com/user/$username/submitted.json?limit=100"
        val json = fetchJson(userUrl) ?: return null
        
        // Parse posts
        val posts = mutableListOf<RedditPost>()
        val postPattern = Regex("\"data\":\\s*\\{[^}]*\"id\":\\s*\"([^\"]+)\"[^}]*\"title\":\\s*\"([^\"]+)\"[^}]*\"selftext\":\\s*\"([^\"]*(?:\\\\.[^\"]*)*)\"[^}]*\"permalink\":\\s*\"([^\"]+)\"")
        
        for (match in postPattern.findAll(json)) {
            posts.add(RedditPost(
                id = match.groupValues[1],
                title = unescapeJson(match.groupValues[2]),
                selftext = unescapeJson(match.groupValues[3]),
                permalink = "https://www.reddit.com${match.groupValues[4]}"
            ))
        }
        
        // Sort by title to try to get chapter order
        posts.sortBy { it.title }
        
        val chapters = mutableListOf<WebChapter>()
        
        for ((index, post) in posts.withIndex()) {
            progressCallback?.invoke(index + 1, posts.size)
            
            if (post.selftext.isNotBlank()) {
                chapters.add(WebChapter(
                    title = post.title,
                    content = markdownToHtml(post.selftext),
                    url = post.permalink,
                    index = index + 1
                ))
            }
        }
        
        return WebContent(
            title = "Posts by $username",
            author = username,
            description = null,
            chapters = chapters,
            coverUrl = null,
            sourceUrl = url,
            tags = listOf("Reddit", "Author: $username")
        )
    }
    
    /**
     * Handle Reddit's share URLs (/r/subreddit/s/shareId)
     */
    private fun downloadFromSearchShare(url: String, progressCallback: ((Int, Int) -> Unit)?): WebContent? {
        // These redirect to the actual post, so we need to follow the redirect
        val html = fetchHtml(url) ?: return null
        
        // Look for the canonical URL
        val canonicalMatch = Regex("<link[^>]*rel=\"canonical\"[^>]*href=\"([^\"]+)\"").find(html)
        val realUrl = canonicalMatch?.groupValues?.get(1)
        
        return if (realUrl != null) {
            downloadThread(realUrl, progressCallback)
        } else null
    }
    
    /**
     * Search for a series by title in a subreddit.
     */
    fun searchSeries(subreddit: String, query: String): List<RedditSearchResult> {
        val searchUrl = "https://www.reddit.com/r/$subreddit/search.json?q=${urlEncode(query)}&restrict_sr=on&sort=relevance&limit=25"
        val json = fetchJson(searchUrl) ?: return emptyList()
        
        val results = mutableListOf<RedditSearchResult>()
        
        val resultPattern = Regex("\"title\":\\s*\"([^\"]+)\"[^}]*\"author\":\\s*\"([^\"]+)\"[^}]*\"permalink\":\\s*\"([^\"]+)\"[^}]*\"score\":\\s*(\\d+)")
        
        for (match in resultPattern.findAll(json)) {
            results.add(RedditSearchResult(
                title = unescapeJson(match.groupValues[1]),
                author = match.groupValues[2],
                url = "https://www.reddit.com${match.groupValues[3]}",
                score = match.groupValues[4].toIntOrNull() ?: 0
            ))
        }
        
        return results.sortedByDescending { it.score }
    }
    
    /**
     * Get popular series from r/HFY wiki.
     */
    fun getHfyPopularSeries(): List<RedditSearchResult> {
        val wikiUrl = "https://www.reddit.com/r/HFY/wiki/series.json"
        val json = fetchJson(wikiUrl) ?: return emptyList()
        
        val content = extractJsonString(json, "content_md") ?: return emptyList()
        
        val results = mutableListOf<RedditSearchResult>()
        
        // Wiki format typically has links like [Series Name](wiki/series/name)
        val seriesPattern = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")
        
        for (match in seriesPattern.findAll(content)) {
            val title = match.groupValues[1]
            var url = match.groupValues[2]
            
            if (url.startsWith("/r/")) {
                url = "https://www.reddit.com$url"
            } else if (url.startsWith("wiki/")) {
                url = "https://www.reddit.com/r/HFY/$url"
            }
            
            if (url.contains("reddit.com")) {
                results.add(RedditSearchResult(
                    title = title,
                    author = null,
                    url = url,
                    score = 0
                ))
            }
        }
        
        return results
    }
    
    // Helper methods
    
    private fun markdownToHtml(markdown: String): String {
        var html = markdown
        
        // Headers
        html = html.replace(Regex("^######\\s*(.+)$", RegexOption.MULTILINE), "<h6>$1</h6>")
        html = html.replace(Regex("^#####\\s*(.+)$", RegexOption.MULTILINE), "<h5>$1</h5>")
        html = html.replace(Regex("^####\\s*(.+)$", RegexOption.MULTILINE), "<h4>$1</h4>")
        html = html.replace(Regex("^###\\s*(.+)$", RegexOption.MULTILINE), "<h3>$1</h3>")
        html = html.replace(Regex("^##\\s*(.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
        html = html.replace(Regex("^#\\s*(.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")
        
        // Bold and italic
        html = html.replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*"), "<strong><em>$1</em></strong>")
        html = html.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        html = html.replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        html = html.replace(Regex("___(.+?)___"), "<strong><em>$1</em></strong>")
        html = html.replace(Regex("__(.+?)__"), "<strong>$1</strong>")
        html = html.replace(Regex("_(.+?)_"), "<em>$1</em>")
        
        // Strikethrough
        html = html.replace(Regex("~~(.+?)~~"), "<del>$1</del>")
        
        // Links
        html = html.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
        
        // Code blocks
        html = html.replace(Regex("```([^`]+)```"), "<pre><code>$1</code></pre>")
        html = html.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        
        // Blockquotes
        html = html.replace(Regex("^>\\s*(.+)$", RegexOption.MULTILINE), "<blockquote>$1</blockquote>")
        
        // Horizontal rules
        html = html.replace(Regex("^[-*_]{3,}$", RegexOption.MULTILINE), "<hr/>")
        
        // Line breaks and paragraphs
        html = html.replace("\n\n", "</p><p>")
        html = html.replace("\n", "<br/>")
        
        // Wrap in paragraph
        html = "<p>$html</p>"
        
        // Clean up empty paragraphs
        html = html.replace(Regex("<p>\\s*</p>"), "")
        html = html.replace(Regex("<p>\\s*<br/>\\s*</p>"), "")
        
        return html
    }
    
    private fun extractBetween(text: String, start: String, end: String): String? {
        val startIndex = text.indexOf(start)
        if (startIndex < 0) return null
        
        val contentStart = startIndex + start.length
        val endIndex = text.indexOf(end, contentStart)
        if (endIndex < 0) return null
        
        return text.substring(contentStart, endIndex)
    }
    
    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"")
        return pattern.find(json)?.groupValues?.get(1)?.let { unescapeJson(it) }
    }
    
    private fun unescapeJson(text: String): String {
        return text
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\/", "/")
            .replace("\\\\", "\\")
    }
}

data class RedditPost(
    val id: String,
    val title: String,
    val selftext: String,
    val permalink: String
)

data class RedditSearchResult(
    val title: String,
    val author: String?,
    val url: String,
    val score: Int
)
