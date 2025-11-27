package org.calibre.news

import org.calibre.metadata.Metadata
import java.net.URL
import java.time.LocalDateTime

/**
 * Base interface for news recipes.
 * Recipes define how to fetch and process news articles from RSS feeds.
 */
interface NewsRecipe {
    val title: String
    val description: String
    val author: String
    val language: String
    val oldestArticleDays: Int
    val maxArticlesPerFeed: Int
    
    /**
     * List of RSS feed URLs to fetch articles from.
     * Each entry is a pair of (feed name, feed URL).
     */
    fun getFeeds(): List<Pair<String, String>>
    
    /**
     * Preprocess HTML content from an article.
     * Can be used to clean up HTML, remove unwanted elements, etc.
     */
    fun preprocessHtml(html: String): String {
        return html // Default: no preprocessing
    }
    
    /**
     * Extract article content from HTML.
     * Override to customize content extraction.
     */
    fun extractArticleContent(html: String, articleUrl: String): String {
        return html // Default: use full HTML
    }
}

/**
 * Basic news recipe implementation.
 * Can be extended or used directly with feed URLs.
 */
open class BasicNewsRecipe(
    override val title: String,
    override val description: String = "",
    override val author: String = "Calibre Kotlin",
    override val language: String = "en",
    override val oldestArticleDays: Int = 7,
    override val maxArticlesPerFeed: Int = 100
) : NewsRecipe {
    
    private val feeds: MutableList<Pair<String, String>> = mutableListOf()
    
    fun addFeed(name: String, url: String) {
        feeds.add(Pair(name, url))
    }
    
    override fun getFeeds(): List<Pair<String, String>> {
        return feeds
    }
}
