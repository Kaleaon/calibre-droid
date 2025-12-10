package org.calibre.web.fanfiction

import org.calibre.web.WebChapter
import org.calibre.web.WebContent
import org.calibre.web.WebContentFetcher
import org.calibre.utils.Logger
import java.time.LocalDate

/**
 * Multi-site fanfiction downloader.
 * 
 * Supports:
 * - FanFiction.Net (FFN)
 * - Archive of Our Own (AO3)
 * - Wattpad
 * - Royal Road
 * - Scribble Hub
 * - SpaceBattles/Sufficient Velocity
 * - Questionable Questing
 * 
 * Features:
 * - Full story download with all chapters
 * - Metadata extraction (author, summary, tags, etc.)
 * - Cover image download
 * - Progress tracking
 */
class FanfictionDownloader : WebContentFetcher() {
    
    /**
     * Download a story from any supported site.
     */
    fun download(url: String, progressCallback: ((Int, Int) -> Unit)? = null): WebContent? {
        return when {
            url.contains("fanfiction.net") -> downloadFFN(url, progressCallback)
            url.contains("archiveofourown.org") || url.contains("ao3.org") -> downloadAO3(url, progressCallback)
            url.contains("wattpad.com") -> downloadWattpad(url, progressCallback)
            url.contains("royalroad.com") -> downloadRoyalRoad(url, progressCallback)
            url.contains("scribblehub.com") -> downloadScribbleHub(url, progressCallback)
            url.contains("spacebattles.com") -> downloadXenForo(url, "SpaceBattles", progressCallback)
            url.contains("sufficientvelocity.com") -> downloadXenForo(url, "SufficientVelocity", progressCallback)
            url.contains("questionablequesting.com") -> downloadXenForo(url, "QuestionableQuesting", progressCallback)
            else -> {
                Logger.warn("Unsupported fanfiction site: $url")
                null
            }
        }
    }
    
    /**
     * Download from FanFiction.Net
     */
    private fun downloadFFN(url: String, progressCallback: ((Int, Int) -> Unit)?): WebContent? {
        // Extract story ID
        val storyId = Regex("/s/(\\d+)").find(url)?.groupValues?.get(1) ?: return null
        val baseUrl = "https://www.fanfiction.net/s/$storyId"
        
        // Fetch first page
        val firstPage = fetchHtml(baseUrl) ?: return null
        
        // Extract metadata
        val title = extractBetween(firstPage, "<b class='xcontrast_txt'>", "</b>") ?: "Unknown Title"
        val author = extractBetween(firstPage, "By:</span> <a", "</a>")
            ?.substringAfter(">") ?: "Unknown Author"
        
        val description = extractBetween(firstPage, "<div class='xcontrast_txt'>", "</div>")
            ?.let { htmlToText(it) }
        
        // Extract chapter count
        val chapterSelect = extractBetween(firstPage, "id=chap_select", "</select>")
        val chapterCount = if (chapterSelect != null) {
            Regex("<option").findAll(chapterSelect).count()
        } else 1
        
        // Extract additional metadata
        val metaLine = extractBetween(firstPage, "class='xgray xcontrast_txt'>", "</span>") ?: ""
        val rating = Regex("Rated:\\s*([^-]+)").find(metaLine)?.groupValues?.get(1)?.trim()
        val language = Regex("-\\s*([A-Za-z]+)\\s*-").find(metaLine)?.groupValues?.get(1)
        val genres = Regex("-\\s*[A-Za-z]+\\s*-\\s*([^-]+)\\s*-").find(metaLine)?.groupValues?.get(1)
        val words = Regex("Words:\\s*([\\d,]+)").find(metaLine)?.groupValues?.get(1)
        
        val tags = mutableListOf<String>()
        rating?.let { tags.add("Rating: $it") }
        genres?.split("/")?.forEach { tags.add(it.trim()) }
        
        // Download chapters
        val chapters = mutableListOf<WebChapter>()
        
        for (i in 1..chapterCount) {
            progressCallback?.invoke(i, chapterCount)
            
            val chapterUrl = "$baseUrl/$i"
            val html = if (i == 1) firstPage else fetchHtml(chapterUrl)
            
            if (html != null) {
                val chapterTitle = extractBetween(html, "id=chap_select", "</select>")
                    ?.let { select ->
                        Regex("<option[^>]*value=\"$i\"[^>]*>([^<]+)").find(select)
                            ?.groupValues?.get(1)?.substringAfter(". ")
                    } ?: "Chapter $i"
                
                val content = extractBetween(html, "id='storytext'>", "</div>") ?: ""
                
                chapters.add(WebChapter(
                    title = chapterTitle,
                    content = content,
                    url = chapterUrl,
                    index = i
                ))
            }
            
            // Rate limiting
            if (i < chapterCount) Thread.sleep(1000)
        }
        
        val metadata = mutableMapOf<String, String>()
        rating?.let { metadata["rating"] = it }
        language?.let { metadata["language"] = it }
        words?.let { metadata["words"] = it }
        
        return WebContent(
            title = title,
            author = author,
            description = description,
            chapters = chapters,
            coverUrl = null,
            sourceUrl = url,
            tags = tags,
            metadata = metadata
        )
    }
    
    /**
     * Download from Archive of Our Own (AO3)
     */
    private fun downloadAO3(url: String, progressCallback: ((Int, Int) -> Unit)?): WebContent? {
        // Extract work ID
        val workId = Regex("/works/(\\d+)").find(url)?.groupValues?.get(1) ?: return null
        val baseUrl = "https://archiveofourown.org/works/$workId"
        
        // Try to get full work in one page
        val fullUrl = "$baseUrl?view_full_work=true&view_adult=true"
        val html = fetchHtml(fullUrl) ?: fetchHtml(baseUrl) ?: return null
        
        // Extract metadata
        val title = extractBetween(html, "<h2 class=\"title heading\">", "</h2>")?.trim() 
            ?: "Unknown Title"
        
        val author = extractBetween(html, "rel=\"author\">", "</a>")?.trim()
            ?: "Unknown Author"
        
        val description = extractBetween(html, "<blockquote class=\"userstuff\">", "</blockquote>")
            ?.let { htmlToText(it) }
        
        // Extract tags
        val tags = mutableListOf<String>()
        
        val fandomSection = extractBetween(html, "class=\"fandom tags\">", "</dd>")
        if (fandomSection != null) {
            extractAll(fandomSection, Regex("<a[^>]*>([^<]+)</a>")).forEach { 
                tags.add("Fandom: $it") 
            }
        }
        
        val warningSection = extractBetween(html, "class=\"warning tags\">", "</dd>")
        if (warningSection != null) {
            extractAll(warningSection, Regex("<a[^>]*>([^<]+)</a>")).forEach { 
                tags.add("Warning: $it") 
            }
        }
        
        val relationshipSection = extractBetween(html, "class=\"relationship tags\">", "</dd>")
        if (relationshipSection != null) {
            extractAll(relationshipSection, Regex("<a[^>]*>([^<]+)</a>")).forEach { 
                tags.add("Ship: $it") 
            }
        }
        
        val characterSection = extractBetween(html, "class=\"character tags\">", "</dd>")
        if (characterSection != null) {
            extractAll(characterSection, Regex("<a[^>]*>([^<]+)</a>")).forEach { 
                tags.add("Character: $it") 
            }
        }
        
        val freeformSection = extractBetween(html, "class=\"freeform tags\">", "</dd>")
        if (freeformSection != null) {
            extractAll(freeformSection, Regex("<a[^>]*>([^<]+)</a>")).forEach { 
                tags.add(it) 
            }
        }
        
        // Extract chapters
        val chapters = mutableListOf<WebChapter>()
        
        // Find all chapter sections
        val chapterPattern = Regex(
            "<h3 class=\"title\"[^>]*>\\s*<a[^>]*>([^<]+)</a>",
            RegexOption.DOT_MATCHES_ALL
        )
        
        val chapterContents = html.split(Regex("<div class=\"chapter\"[^>]*>"))
        
        if (chapterContents.size > 1) {
            for ((index, chapterHtml) in chapterContents.drop(1).withIndex()) {
                progressCallback?.invoke(index + 1, chapterContents.size - 1)
                
                val chapterTitle = Regex("<h3 class=\"title\"[^>]*>([^<]+)")
                    .find(chapterHtml)?.groupValues?.get(1)?.trim()
                    ?: "Chapter ${index + 1}"
                
                val content = extractBetween(chapterHtml, 
                    "<div class=\"userstuff module\"", "</div>")
                    ?.substringAfter(">") ?: ""
                
                chapters.add(WebChapter(
                    title = chapterTitle,
                    content = content,
                    url = "$baseUrl/chapters/${index + 1}",
                    index = index + 1
                ))
            }
        } else {
            // Single chapter work
            val content = extractBetween(html, 
                "<div class=\"userstuff\"", "</div>")
                ?.substringAfter(">") ?: ""
            
            chapters.add(WebChapter(
                title = title,
                content = content,
                url = baseUrl,
                index = 1
            ))
        }
        
        // Extract metadata
        val rating = extractBetween(html, "class=\"rating tags\">", "</dd>")
            ?.let { Regex("<a[^>]*>([^<]+)</a>").find(it)?.groupValues?.get(1) }
        
        val wordCount = extractBetween(html, "<dd class=\"words\">", "</dd>")
        
        val metadata = mutableMapOf<String, String>()
        rating?.let { metadata["rating"] = it }
        wordCount?.let { metadata["words"] = it }
        
        return WebContent(
            title = title,
            author = author,
            description = description,
            chapters = chapters,
            coverUrl = null,
            sourceUrl = url,
            tags = tags,
            metadata = metadata
        )
    }
    
    /**
     * Download from Royal Road
     */
    private fun downloadRoyalRoad(url: String, progressCallback: ((Int, Int) -> Unit)?): WebContent? {
        // Extract fiction ID
        val fictionId = Regex("/fiction/(\\d+)").find(url)?.groupValues?.get(1) ?: return null
        val baseUrl = "https://www.royalroad.com/fiction/$fictionId"
        
        val mainPage = fetchHtml(baseUrl) ?: return null
        
        // Extract metadata
        val title = extractBetween(mainPage, "<h1 property=\"name\"", "</h1>")
            ?.substringAfter(">")?.trim() ?: "Unknown Title"
        
        val author = extractBetween(mainPage, "property=\"author\"", "</a>")
            ?.substringAfter(">")?.trim() ?: "Unknown Author"
        
        val description = extractBetween(mainPage, "property=\"description\"", "</div>")
            ?.substringAfter(">")?.let { htmlToText(it) }
        
        // Extract cover
        val coverUrl = Regex("class=\"cover\"[^>]*src=\"([^\"]+)\"").find(mainPage)
            ?.groupValues?.get(1)
        
        // Extract tags
        val tags = mutableListOf<String>()
        val tagSection = extractBetween(mainPage, "class=\"tags\"", "</span>") ?: ""
        extractAll(tagSection, Regex("<a[^>]*>([^<]+)</a>")).forEach { tags.add(it.trim()) }
        
        // Get chapter list
        val chapterList = mutableListOf<Pair<String, String>>()
        val chapterPattern = Regex("<a[^>]*href=\"(/fiction/$fictionId/chapter/[^\"]+)\"[^>]*>\\s*([^<]+)</a>")
        
        for (match in chapterPattern.findAll(mainPage)) {
            val chapterUrl = "https://www.royalroad.com${match.groupValues[1]}"
            val chapterTitle = match.groupValues[2].trim()
            if (!chapterList.any { it.first == chapterUrl }) {
                chapterList.add(Pair(chapterUrl, chapterTitle))
            }
        }
        
        // Download chapters
        val chapters = mutableListOf<WebChapter>()
        
        for ((index, pair) in chapterList.withIndex()) {
            val (chapterUrl, chapterTitle) = pair
            progressCallback?.invoke(index + 1, chapterList.size)
            
            val chapterHtml = fetchHtml(chapterUrl)
            if (chapterHtml != null) {
                val content = extractBetween(chapterHtml, 
                    "class=\"chapter-content\"", "</div>")
                    ?.substringAfter(">") ?: ""
                
                chapters.add(WebChapter(
                    title = chapterTitle,
                    content = content,
                    url = chapterUrl,
                    index = index + 1
                ))
            }
            
            // Rate limiting
            if (index < chapterList.size - 1) Thread.sleep(500)
        }
        
        // Extract stats
        val statsSection = extractBetween(mainPage, "class=\"fiction-stats\"", "</div>") ?: ""
        val views = Regex("([\\d,]+)\\s*Views").find(statsSection)?.groupValues?.get(1)
        val followers = Regex("([\\d,]+)\\s*Followers").find(statsSection)?.groupValues?.get(1)
        
        val metadata = mutableMapOf<String, String>()
        views?.let { metadata["views"] = it }
        followers?.let { metadata["followers"] = it }
        
        return WebContent(
            title = title,
            author = author,
            description = description,
            chapters = chapters,
            coverUrl = coverUrl,
            sourceUrl = url,
            tags = tags,
            metadata = metadata
        )
    }
    
    /**
     * Download from Wattpad
     */
    private fun downloadWattpad(url: String, progressCallback: ((Int, Int) -> Unit)?): WebContent? {
        // Extract story ID
        val storyId = Regex("/story/(\\d+)").find(url)?.groupValues?.get(1) ?: return null
        
        // Use Wattpad API
        val apiUrl = "https://www.wattpad.com/api/v3/stories/$storyId?fields=id,title,description,cover,user(name),tags,parts(id,title,url)"
        
        val jsonResponse = fetchJson(apiUrl) ?: return null
        
        // Parse JSON manually
        val title = extractJsonString(jsonResponse, "title") ?: "Unknown Title"
        val description = extractJsonString(jsonResponse, "description")
        val coverUrl = extractJsonString(jsonResponse, "cover")
        
        // Extract author from user object
        val userSection = extractBetween(jsonResponse, "\"user\":{", "}")
        val author = userSection?.let { extractJsonString(it, "name") } ?: "Unknown Author"
        
        // Extract tags
        val tags = mutableListOf<String>()
        val tagsSection = extractBetween(jsonResponse, "\"tags\":[", "]")
        if (tagsSection != null) {
            Regex("\"([^\"]+)\"").findAll(tagsSection).forEach { 
                tags.add(it.groupValues[1]) 
            }
        }
        
        // Extract parts (chapters)
        val parts = mutableListOf<Pair<String, String>>() // id, title
        val partsSection = extractBetween(jsonResponse, "\"parts\":[", "]")
        if (partsSection != null) {
            val partPattern = Regex("\"id\":(\\d+)[^}]*\"title\":\"([^\"]+)\"")
            for (match in partPattern.findAll(partsSection)) {
                parts.add(Pair(match.groupValues[1], match.groupValues[2]))
            }
        }
        
        // Download chapters
        val chapters = mutableListOf<WebChapter>()
        
        for ((index, part) in parts.withIndex()) {
            val (partId, partTitle) = part
            progressCallback?.invoke(index + 1, parts.size)
            
            val partUrl = "https://www.wattpad.com/apiv2/storytext?id=$partId"
            val partContent = fetchHtml(partUrl)
            
            if (partContent != null) {
                chapters.add(WebChapter(
                    title = partTitle,
                    content = partContent,
                    url = "https://www.wattpad.com/$partId",
                    index = index + 1
                ))
            }
            
            // Rate limiting
            if (index < parts.size - 1) Thread.sleep(500)
        }
        
        return WebContent(
            title = title,
            author = author,
            description = description,
            chapters = chapters,
            coverUrl = coverUrl,
            sourceUrl = url,
            tags = tags
        )
    }
    
    /**
     * Download from Scribble Hub
     */
    private fun downloadScribbleHub(url: String, progressCallback: ((Int, Int) -> Unit)?): WebContent? {
        // Extract series ID
        val seriesId = Regex("/series/(\\d+)").find(url)?.groupValues?.get(1) ?: return null
        val baseUrl = "https://www.scribblehub.com/series/$seriesId"
        
        val mainPage = fetchHtml("$baseUrl/") ?: return null
        
        // Extract metadata
        val title = extractBetween(mainPage, "<div class=\"fic_title\"", "</div>")
            ?.substringAfter(">")?.trim() ?: "Unknown Title"
        
        val author = extractBetween(mainPage, "class=\"auth_name_fic\"", "</span>")
            ?.substringAfter(">")?.trim() ?: "Unknown Author"
        
        val description = extractBetween(mainPage, "class=\"wi_fic_desc\"", "</div>")
            ?.substringAfter(">")?.let { htmlToText(it) }
        
        // Extract cover
        val coverUrl = Regex("class=\"fic_image\"[^>]*>\\s*<img[^>]*src=\"([^\"]+)\"")
            .find(mainPage)?.groupValues?.get(1)
        
        // Extract tags/genres
        val tags = mutableListOf<String>()
        val genreSection = extractBetween(mainPage, "class=\"wi_fic_genre\"", "</span>") ?: ""
        extractAll(genreSection, Regex(">([^<]+)<")).forEach { 
            val tag = it.trim()
            if (tag.isNotEmpty()) tags.add(tag) 
        }
        
        // Get chapter list from table of contents
        val tocUrl = "$baseUrl/?toc=1"
        val tocPage = fetchHtml(tocUrl) ?: mainPage
        
        val chapterList = mutableListOf<Pair<String, String>>()
        val chapterPattern = Regex("<a[^>]*href=\"(https://www\\.scribblehub\\.com/read/[^\"]+)\"[^>]*>\\s*([^<]+)</a>")
        
        for (match in chapterPattern.findAll(tocPage)) {
            val chapterUrl = match.groupValues[1]
            val chapterTitle = match.groupValues[2].trim()
            chapterList.add(Pair(chapterUrl, chapterTitle))
        }
        
        // Download chapters
        val chapters = mutableListOf<WebChapter>()
        
        for ((index, pair) in chapterList.withIndex()) {
            val (chapterUrl, chapterTitle) = pair
            progressCallback?.invoke(index + 1, chapterList.size)
            
            val chapterHtml = fetchHtml(chapterUrl)
            if (chapterHtml != null) {
                val content = extractBetween(chapterHtml, "class=\"chp_raw\"", "</div>")
                    ?.substringAfter(">") ?: ""
                
                chapters.add(WebChapter(
                    title = chapterTitle,
                    content = content,
                    url = chapterUrl,
                    index = index + 1
                ))
            }
            
            if (index < chapterList.size - 1) Thread.sleep(500)
        }
        
        return WebContent(
            title = title,
            author = author,
            description = description,
            chapters = chapters,
            coverUrl = coverUrl,
            sourceUrl = url,
            tags = tags
        )
    }
    
    /**
     * Download from XenForo forums (SpaceBattles, Sufficient Velocity, etc.)
     */
    private fun downloadXenForo(url: String, siteName: String, 
                                 progressCallback: ((Int, Int) -> Unit)?): WebContent? {
        // Get threadmarks page
        val baseUrl = url.substringBefore("#").substringBefore("?")
        val threadmarksUrl = "$baseUrl/threadmarks"
        
        val threadmarksPage = fetchHtml(threadmarksUrl) ?: return null
        
        // Extract title
        val title = extractBetween(threadmarksPage, "<h1 class=\"p-title-value\">", "</h1>")
            ?.let { htmlToText(it) } ?: "Unknown Title"
        
        // Extract author
        val author = extractBetween(threadmarksPage, "data-author=\"", "\"")
            ?: extractBetween(threadmarksPage, "class=\"username\"", "</a>")
                ?.substringAfter(">")?.trim()
            ?: "Unknown Author"
        
        // Get chapter links from threadmarks
        val chapterList = mutableListOf<Pair<String, String>>()
        val chapterPattern = Regex("<a[^>]*href=\"([^\"]*threads/[^\"]+/post-[^\"]+)\"[^>]*>\\s*([^<]+)</a>")
        
        for (match in chapterPattern.findAll(threadmarksPage)) {
            var chapterUrl = match.groupValues[1]
            if (!chapterUrl.startsWith("http")) {
                chapterUrl = url.substringBefore("/threads") + chapterUrl
            }
            val chapterTitle = match.groupValues[2].trim()
            if (!chapterList.any { it.second == chapterTitle }) {
                chapterList.add(Pair(chapterUrl, chapterTitle))
            }
        }
        
        // Download chapters
        val chapters = mutableListOf<WebChapter>()
        
        for ((index, pair) in chapterList.withIndex()) {
            val (chapterUrl, chapterTitle) = pair
            progressCallback?.invoke(index + 1, chapterList.size)
            
            val chapterHtml = fetchHtml(chapterUrl)
            if (chapterHtml != null) {
                // Extract post content
                val content = extractBetween(chapterHtml, 
                    "class=\"message-body js-selectToQuote\"", "</article>")
                    ?.let { extractBetween(it, "class=\"bbWrapper\">", "</div>") }
                    ?: ""
                
                chapters.add(WebChapter(
                    title = chapterTitle,
                    content = content,
                    url = chapterUrl,
                    index = index + 1
                ))
            }
            
            if (index < chapterList.size - 1) Thread.sleep(1000)
        }
        
        return WebContent(
            title = title,
            author = author,
            description = null,
            chapters = chapters,
            coverUrl = null,
            sourceUrl = url,
            tags = listOf("Source: $siteName")
        )
    }
    
    // Helper methods
    
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
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?.replace("\\\\", "\\")
    }
}
