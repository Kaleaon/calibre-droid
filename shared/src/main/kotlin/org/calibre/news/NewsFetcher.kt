package org.calibre.news

import org.calibre.conversion.OebBook
import org.calibre.conversion.OebItem
import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.File
import java.net.URL
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Fetches news articles from RSS feeds using a recipe and creates an OEB book.
 */
class NewsFetcher(private val recipe: NewsRecipe) {
    private val rssParser = RssParser()
    private val cutoffDate = LocalDateTime.now().minusDays(recipe.oldestArticleDays.toLong())
    
    /**
     * Fetch all articles from recipe feeds and create an OEB book.
     */
    fun fetch(workDir: File): OebBook {
        val allArticles = mutableListOf<Article>()
        val feeds = recipe.getFeeds()
        
        Logger.info("Fetching news from ${feeds.size} feeds for: ${recipe.title}")
        
        feeds.forEach { (feedName, feedUrl) ->
            try {
                Logger.info("Fetching feed: $feedName")
                val items = rssParser.parseFeed(feedUrl)
                
                val articles = items
                    .filter { item ->
                        // Filter by date
                        item.pubDate == null || item.pubDate.isAfter(cutoffDate)
                    }
                    .take(recipe.maxArticlesPerFeed)
                    .map { item ->
                        Article(
                            title = item.title,
                            url = item.link,
                            description = item.description,
                            pubDate = item.pubDate,
                            author = item.author,
                            feedName = feedName
                        )
                    }
                
                allArticles.addAll(articles)
                Logger.info("Fetched ${articles.size} articles from $feedName")
            } catch (e: Exception) {
                Logger.error("Error fetching feed $feedName: ${e.message}", e)
            }
        }
        
        // Sort by date (newest first)
        allArticles.sortByDescending { it.pubDate ?: LocalDateTime.MIN }
        
        Logger.info("Total articles fetched: ${allArticles.size}")
        
        // Download article content
        val articlesDir = File(workDir, "articles")
        articlesDir.mkdirs()
        
        val downloadedArticles = allArticles.mapIndexed { index, article ->
            try {
                downloadArticle(article, articlesDir, index)
            } catch (e: Exception) {
                Logger.error("Error downloading article ${article.title}: ${e.message}", e)
                null
            }
        }.filterNotNull()
        
        Logger.info("Successfully downloaded ${downloadedArticles.size} articles")
        
        // Create OEB book
        return createOebBook(downloadedArticles, workDir)
    }
    
    private data class Article(
        val title: String,
        val url: String,
        val description: String,
        val pubDate: LocalDateTime?,
        val author: String?,
        val feedName: String
    )
    
    private data class DownloadedArticle(
        val article: Article,
        val htmlFile: File,
        val content: String
    )
    
    private fun downloadArticle(article: Article, articlesDir: File, index: Int): DownloadedArticle? {
        try {
            val url = URL(article.url)
            val connection = url.openConnection()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Calibre Kotlin)")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val html = connection.getInputStream().bufferedReader().readText()
            
            // Preprocess HTML using recipe
            val processedHtml = recipe.preprocessHtml(html)
            
            // Extract article content
            val content = recipe.extractArticleContent(processedHtml, article.url)
            
            // Save to file
            val safeTitle = article.title.replace("[^a-zA-Z0-9]".toRegex(), "_").take(100)
            val htmlFile = File(articlesDir, "${index}_$safeTitle.html")
            htmlFile.writeText(content)
            
            return DownloadedArticle(article, htmlFile, content)
        } catch (e: Exception) {
            Logger.warn("Failed to download article: ${article.url} - ${e.message}")
            return null
        }
    }
    
    private fun createOebBook(articles: List<DownloadedArticle>, workDir: File): OebBook {
        val metadata = Metadata(
            title = recipe.title,
            authors = mutableListOf(recipe.author),
            language = recipe.language,
            description = recipe.description,
            dateAdded = LocalDateTime.now()
        )
        
        val book = OebBook(metadata = metadata)
        val manifest = book.manifest
        val spine = book.spine
        
        // Add CSS
        val cssFile = File(workDir, "style.css")
        cssFile.writeText("""
            body {
                font-family: serif;
                line-height: 1.6;
                margin: 2em;
            }
            h1 {
                font-size: 2em;
                margin-bottom: 0.5em;
            }
            h2 {
                font-size: 1.5em;
                margin-top: 1.5em;
                margin-bottom: 0.5em;
            }
            .article-meta {
                color: #666;
                font-size: 0.9em;
                margin-bottom: 1em;
            }
            img {
                max-width: 100%;
                height: auto;
            }
        """.trimIndent())
        
        val cssItem = OebItem(
            id = "style",
            href = "style.css",
            mediaType = "text/css",
            file = cssFile
        )
        manifest["style"] = cssItem
        
        // Add articles as chapters
        articles.forEachIndexed { index, downloaded ->
            val item = OebItem(
                id = "article_$index",
                href = downloaded.htmlFile.name,
                mediaType = "application/xhtml+xml",
                file = downloaded.htmlFile
            )
            manifest["article_$index"] = item
            spine.add(item)
        }
        
        return book
    }
}
