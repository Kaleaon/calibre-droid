package org.calibre.web.comics

import org.calibre.web.WebContentFetcher
import org.calibre.utils.Logger
import java.io.File
import java.net.URL

/**
 * Multi-site webcomic downloader.
 * 
 * Supports:
 * - Webtoon (webtoons.com)
 * - Tapas
 * - XKCD
 * - Dilbert
 * - SMBC (Saturday Morning Breakfast Cereal)
 * - Questionable Content
 * - Penny Arcade
 * - Generic webcomic sites with navigation
 * 
 * Features:
 * - Full series download
 * - Individual chapter/episode download
 * - Cover image extraction
 * - CBZ/PDF output
 */
class WebComicDownloader : WebContentFetcher() {
    
    /**
     * Download a webcomic from any supported site.
     */
    fun download(url: String, outputDir: File, 
                 progressCallback: ((Int, Int) -> Unit)? = null): WebComicResult? {
        return when {
            url.contains("webtoons.com") -> downloadWebtoon(url, outputDir, progressCallback)
            url.contains("tapas.io") -> downloadTapas(url, outputDir, progressCallback)
            url.contains("xkcd.com") -> downloadXkcd(url, outputDir, progressCallback)
            url.contains("smbc-comics.com") -> downloadSmbc(url, outputDir, progressCallback)
            url.contains("questionablecontent.net") -> downloadQc(url, outputDir, progressCallback)
            url.contains("penny-arcade.com") -> downloadPennyArcade(url, outputDir, progressCallback)
            url.contains("mangadex.org") -> downloadMangadex(url, outputDir, progressCallback)
            url.contains("mangakakalot.com") || url.contains("manganato.com") -> 
                downloadMangakakalot(url, outputDir, progressCallback)
            else -> downloadGeneric(url, outputDir, progressCallback)
        }
    }
    
    /**
     * Download from Webtoon
     */
    private fun downloadWebtoon(url: String, outputDir: File,
                                 progressCallback: ((Int, Int) -> Unit)?): WebComicResult? {
        // Extract title ID
        val titleMatch = Regex("/([^/]+)/([^/]+)/list\\?title_no=(\\d+)").find(url)
            ?: Regex("/viewer\\?title_no=(\\d+)&episode_no=(\\d+)").find(url)
        
        val titleNo = titleMatch?.groupValues?.getOrNull(3) 
            ?: titleMatch?.groupValues?.getOrNull(1)
            ?: return null
        
        val listUrl = "https://www.webtoons.com/en/genre/title/list?title_no=$titleNo"
        val listPage = fetchHtml(listUrl) ?: return null
        
        // Extract series info
        val title = extractBetween(listPage, "<h1 class=\"subj\">", "</h1>")
            ?.let { htmlToText(it) } ?: "Unknown Webtoon"
        
        val author = extractBetween(listPage, "class=\"author\"", "</a>")
            ?.substringAfter(">")?.trim() ?: "Unknown Author"
        
        val description = extractBetween(listPage, "<p class=\"summary\">", "</p>")
            ?.let { htmlToText(it) }
        
        // Extract cover
        val coverUrl = Regex("class=\"thmb\"[^>]*>\\s*<img[^>]*src=\"([^\"]+)\"")
            .find(listPage)?.groupValues?.get(1)
        
        // Get episode list
        val episodes = mutableListOf<WebComicEpisode>()
        val episodePattern = Regex(
            "<a[^>]*href=\"([^\"]*viewer[^\"]*episode_no=(\\d+)[^\"]*)\">\\s*" +
            "<span class=\"subj\"><span>([^<]+)</span>"
        )
        
        for (match in episodePattern.findAll(listPage)) {
            var episodeUrl = match.groupValues[1]
            if (!episodeUrl.startsWith("http")) {
                episodeUrl = "https://www.webtoons.com$episodeUrl"
            }
            val episodeNo = match.groupValues[2].toIntOrNull() ?: continue
            val episodeTitle = match.groupValues[3].trim()
            
            episodes.add(WebComicEpisode(episodeNo, episodeTitle, episodeUrl))
        }
        
        episodes.sortBy { it.number }
        
        // Download episodes
        val comicDir = File(outputDir, sanitizeFilename(title))
        comicDir.mkdirs()
        
        val downloadedEpisodes = mutableListOf<DownloadedEpisode>()
        
        for ((index, episode) in episodes.withIndex()) {
            progressCallback?.invoke(index + 1, episodes.size)
            
            val episodeDir = File(comicDir, "Episode_${String.format("%04d", episode.number)}")
            val images = downloadWebtoonEpisode(episode.url, episodeDir)
            
            if (images.isNotEmpty()) {
                downloadedEpisodes.add(DownloadedEpisode(
                    number = episode.number,
                    title = episode.title,
                    images = images
                ))
            }
            
            // Rate limiting
            if (index < episodes.size - 1) Thread.sleep(500)
        }
        
        // Download cover
        var coverFile: File? = null
        if (coverUrl != null) {
            val coverData = fetchBinary(coverUrl)
            if (coverData != null) {
                coverFile = File(comicDir, "cover.jpg")
                coverFile.writeBytes(coverData)
            }
        }
        
        return WebComicResult(
            title = title,
            author = author,
            description = description,
            episodes = downloadedEpisodes,
            outputDir = comicDir,
            coverFile = coverFile,
            sourceUrl = url
        )
    }
    
    private fun downloadWebtoonEpisode(url: String, outputDir: File): List<File> {
        outputDir.mkdirs()
        val images = mutableListOf<File>()
        
        val html = fetchHtml(url) ?: return images
        
        // Webtoon uses data-url for lazy loading
        val imagePattern = Regex("class=\"_images\"[^>]*data-url=\"([^\"]+)\"")
        
        for ((index, match) in imagePattern.findAll(html).withIndex()) {
            val imageUrl = match.groupValues[1]
            
            // Webtoon requires specific headers
            headers["Referer"] = "https://www.webtoons.com/"
            val imageData = fetchBinary(imageUrl)
            headers.remove("Referer")
            
            if (imageData != null) {
                val ext = imageUrl.substringAfterLast(".").substringBefore("?")
                    .takeIf { it.length <= 4 } ?: "jpg"
                val imageFile = File(outputDir, "${String.format("%03d", index + 1)}.$ext")
                imageFile.writeBytes(imageData)
                images.add(imageFile)
            }
        }
        
        return images
    }
    
    /**
     * Download from Tapas
     */
    private fun downloadTapas(url: String, outputDir: File,
                               progressCallback: ((Int, Int) -> Unit)?): WebComicResult? {
        // Extract series ID
        val seriesId = Regex("/series/([^/]+)").find(url)?.groupValues?.get(1) ?: return null
        
        val seriesUrl = "https://tapas.io/series/$seriesId"
        val seriesPage = fetchHtml(seriesUrl) ?: return null
        
        // Extract metadata
        val title = extractBetween(seriesPage, "<a class=\"title\"", "</a>")
            ?.substringAfter(">")?.trim() ?: "Unknown Comic"
        
        val author = extractBetween(seriesPage, "class=\"creator\"", "</a>")
            ?.substringAfter(">")?.trim() ?: "Unknown Author"
        
        val description = extractBetween(seriesPage, "class=\"description\"", "</div>")
            ?.substringAfter(">")?.let { htmlToText(it) }
        
        val coverUrl = Regex("class=\"thumb\"[^>]*>\\s*<img[^>]*src=\"([^\"]+)\"")
            .find(seriesPage)?.groupValues?.get(1)
        
        // Get episode list
        val episodes = mutableListOf<WebComicEpisode>()
        val episodePattern = Regex("<a[^>]*href=\"(/episode/\\d+)\"[^>]*>\\s*<p[^>]*>([^<]+)</p>")
        
        for ((index, match) in episodePattern.findAll(seriesPage).withIndex()) {
            val episodeUrl = "https://tapas.io${match.groupValues[1]}"
            val episodeTitle = match.groupValues[2].trim()
            episodes.add(WebComicEpisode(index + 1, episodeTitle, episodeUrl))
        }
        
        // Download episodes
        val comicDir = File(outputDir, sanitizeFilename(title))
        comicDir.mkdirs()
        
        val downloadedEpisodes = mutableListOf<DownloadedEpisode>()
        
        for ((index, episode) in episodes.withIndex()) {
            progressCallback?.invoke(index + 1, episodes.size)
            
            val episodeDir = File(comicDir, "Episode_${String.format("%04d", episode.number)}")
            val images = downloadTapasEpisode(episode.url, episodeDir)
            
            if (images.isNotEmpty()) {
                downloadedEpisodes.add(DownloadedEpisode(
                    number = episode.number,
                    title = episode.title,
                    images = images
                ))
            }
            
            if (index < episodes.size - 1) Thread.sleep(500)
        }
        
        // Download cover
        var coverFile: File? = null
        if (coverUrl != null) {
            val coverData = fetchBinary(coverUrl)
            if (coverData != null) {
                coverFile = File(comicDir, "cover.jpg")
                coverFile.writeBytes(coverData)
            }
        }
        
        return WebComicResult(
            title = title,
            author = author,
            description = description,
            episodes = downloadedEpisodes,
            outputDir = comicDir,
            coverFile = coverFile,
            sourceUrl = url
        )
    }
    
    private fun downloadTapasEpisode(url: String, outputDir: File): List<File> {
        outputDir.mkdirs()
        val images = mutableListOf<File>()
        
        val html = fetchHtml(url) ?: return images
        
        val imagePattern = Regex("<img[^>]*class=\"content__img\"[^>]*src=\"([^\"]+)\"")
        
        for ((index, match) in imagePattern.findAll(html).withIndex()) {
            val imageUrl = match.groupValues[1]
            val imageData = fetchBinary(imageUrl)
            
            if (imageData != null) {
                val ext = imageUrl.substringAfterLast(".").substringBefore("?")
                    .takeIf { it.length <= 4 } ?: "jpg"
                val imageFile = File(outputDir, "${String.format("%03d", index + 1)}.$ext")
                imageFile.writeBytes(imageData)
                images.add(imageFile)
            }
        }
        
        return images
    }
    
    /**
     * Download XKCD comics
     */
    private fun downloadXkcd(url: String, outputDir: File,
                              progressCallback: ((Int, Int) -> Unit)?): WebComicResult? {
        // Determine range
        val comicNum = Regex("/(\\d+)/").find(url)?.groupValues?.get(1)?.toIntOrNull()
        
        // Get latest comic number
        val latestPage = fetchHtml("https://xkcd.com/") ?: return null
        val latestNum = Regex("Permanent link to this comic: https://xkcd\\.com/(\\d+)/")
            .find(latestPage)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        
        val startNum = comicNum ?: 1
        val endNum = comicNum ?: latestNum
        
        val comicDir = File(outputDir, "XKCD")
        comicDir.mkdirs()
        
        val downloadedEpisodes = mutableListOf<DownloadedEpisode>()
        val total = endNum - startNum + 1
        
        for ((index, num) in (startNum..endNum).withIndex()) {
            progressCallback?.invoke(index + 1, total)
            
            // Skip comic 404 (it's intentionally missing)
            if (num == 404) continue
            
            val comicUrl = "https://xkcd.com/$num/"
            val html = fetchHtml(comicUrl) ?: continue
            
            val title = extractBetween(html, "<div id=\"ctitle\">", "</div>") ?: "Comic $num"
            
            // Extract image
            val imageUrl = Regex("<img[^>]*src=\"(//imgs\\.xkcd\\.com/comics/[^\"]+)\"")
                .find(html)?.groupValues?.get(1)
            
            if (imageUrl != null) {
                val fullUrl = "https:$imageUrl"
                val imageData = fetchBinary(fullUrl)
                
                if (imageData != null) {
                    val episodeDir = File(comicDir, String.format("%04d", num))
                    episodeDir.mkdirs()
                    
                    val ext = imageUrl.substringAfterLast(".")
                    val imageFile = File(episodeDir, "001.$ext")
                    imageFile.writeBytes(imageData)
                    
                    // Also save alt text
                    val altText = Regex("title=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                    if (altText != null) {
                        File(episodeDir, "alt.txt").writeText(altText)
                    }
                    
                    downloadedEpisodes.add(DownloadedEpisode(
                        number = num,
                        title = title,
                        images = listOf(imageFile)
                    ))
                }
            }
            
            if (index < total - 1) Thread.sleep(200)
        }
        
        return WebComicResult(
            title = "XKCD",
            author = "Randall Munroe",
            description = "A webcomic of romance, sarcasm, math, and language.",
            episodes = downloadedEpisodes,
            outputDir = comicDir,
            sourceUrl = url
        )
    }
    
    /**
     * Download SMBC comics
     */
    private fun downloadSmbc(url: String, outputDir: File,
                              progressCallback: ((Int, Int) -> Unit)?): WebComicResult? {
        val comicId = Regex("/comic/([^/]+)").find(url)?.groupValues?.get(1)
        
        val comicDir = File(outputDir, "SMBC")
        comicDir.mkdirs()
        
        val downloadedEpisodes = mutableListOf<DownloadedEpisode>()
        
        if (comicId != null) {
            // Single comic download
            val result = downloadSmbcComic("https://www.smbc-comics.com/comic/$comicId", comicDir, 1)
            if (result != null) downloadedEpisodes.add(result)
        } else {
            // Get archive page
            val archiveUrl = "https://www.smbc-comics.com/comic/archive"
            val archivePage = fetchHtml(archiveUrl) ?: return null
            
            val comicLinks = mutableListOf<String>()
            val linkPattern = Regex("href=\"(/comic/[^\"]+)\"")
            
            for (match in linkPattern.findAll(archivePage)) {
                val link = "https://www.smbc-comics.com${match.groupValues[1]}"
                if (link !in comicLinks) comicLinks.add(link)
            }
            
            for ((index, link) in comicLinks.take(100).withIndex()) { // Limit for demo
                progressCallback?.invoke(index + 1, minOf(comicLinks.size, 100))
                
                val result = downloadSmbcComic(link, comicDir, index + 1)
                if (result != null) downloadedEpisodes.add(result)
                
                Thread.sleep(300)
            }
        }
        
        return WebComicResult(
            title = "Saturday Morning Breakfast Cereal",
            author = "Zach Weinersmith",
            description = "A daily webcomic featuring humor about science, philosophy, and more.",
            episodes = downloadedEpisodes,
            outputDir = comicDir,
            sourceUrl = url
        )
    }
    
    private fun downloadSmbcComic(url: String, outputDir: File, number: Int): DownloadedEpisode? {
        val html = fetchHtml(url) ?: return null
        
        val title = extractBetween(html, "<title>", "</title>")
            ?.replace("Saturday Morning Breakfast Cereal - ", "")
            ?.trim() ?: "Comic $number"
        
        // Main comic
        val mainImage = Regex("id=\"cc-comic\"[^>]*src=\"([^\"]+)\"")
            .find(html)?.groupValues?.get(1)
        
        // Bonus panel (hover text reveals it)
        val bonusImage = Regex("id=\"aftercomic\"[^>]*src=\"([^\"]+)\"")
            .find(html)?.groupValues?.get(1)
        
        val episodeDir = File(outputDir, String.format("%04d", number))
        episodeDir.mkdirs()
        
        val images = mutableListOf<File>()
        
        if (mainImage != null) {
            val imageData = fetchBinary(mainImage)
            if (imageData != null) {
                val imageFile = File(episodeDir, "001.png")
                imageFile.writeBytes(imageData)
                images.add(imageFile)
            }
        }
        
        if (bonusImage != null) {
            val imageData = fetchBinary(bonusImage)
            if (imageData != null) {
                val imageFile = File(episodeDir, "002_bonus.png")
                imageFile.writeBytes(imageData)
                images.add(imageFile)
            }
        }
        
        return if (images.isNotEmpty()) {
            DownloadedEpisode(number, title, images)
        } else null
    }
    
    /**
     * Download Questionable Content
     */
    private fun downloadQc(url: String, outputDir: File,
                            progressCallback: ((Int, Int) -> Unit)?): WebComicResult? {
        val comicNum = Regex("/view\\.php\\?comic=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
        
        // Get latest
        val latestPage = fetchHtml("https://www.questionablecontent.net/") ?: return null
        val latestNum = Regex("view\\.php\\?comic=(\\d+)").find(latestPage)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        
        val startNum = comicNum ?: 1
        val endNum = comicNum ?: latestNum
        
        val comicDir = File(outputDir, "Questionable_Content")
        comicDir.mkdirs()
        
        val downloadedEpisodes = mutableListOf<DownloadedEpisode>()
        val total = endNum - startNum + 1
        
        for ((index, num) in (startNum..endNum).withIndex()) {
            progressCallback?.invoke(index + 1, total)
            
            val comicUrl = "https://www.questionablecontent.net/view.php?comic=$num"
            val html = fetchHtml(comicUrl) ?: continue
            
            val imageUrl = Regex("<img[^>]*id=\"strip\"[^>]*src=\"([^\"]+)\"")
                .find(html)?.groupValues?.get(1)
            
            if (imageUrl != null) {
                val fullUrl = if (imageUrl.startsWith("http")) imageUrl
                    else "https://www.questionablecontent.net/$imageUrl"
                
                val imageData = fetchBinary(fullUrl)
                if (imageData != null) {
                    val episodeDir = File(comicDir, String.format("%04d", num))
                    episodeDir.mkdirs()
                    
                    val imageFile = File(episodeDir, "001.png")
                    imageFile.writeBytes(imageData)
                    
                    downloadedEpisodes.add(DownloadedEpisode(
                        number = num,
                        title = "Comic $num",
                        images = listOf(imageFile)
                    ))
                }
            }
            
            if (index < total - 1) Thread.sleep(200)
        }
        
        return WebComicResult(
            title = "Questionable Content",
            author = "Jeph Jacques",
            description = "An internet comic about romance and robots",
            episodes = downloadedEpisodes,
            outputDir = comicDir,
            sourceUrl = url
        )
    }
    
    /**
     * Download Penny Arcade
     */
    private fun downloadPennyArcade(url: String, outputDir: File,
                                     progressCallback: ((Int, Int) -> Unit)?): WebComicResult? {
        val comicDir = File(outputDir, "Penny_Arcade")
        comicDir.mkdirs()
        
        // Get archive
        val archiveUrl = "https://www.penny-arcade.com/comic/archive"
        val archivePage = fetchHtml(archiveUrl) ?: return null
        
        // Extract comic links
        val comicLinks = mutableListOf<String>()
        val linkPattern = Regex("href=\"(/comic/\\d{4}/\\d{2}/\\d{2}[^\"]*)\"")
        
        for (match in linkPattern.findAll(archivePage)) {
            val link = "https://www.penny-arcade.com${match.groupValues[1]}"
            if (link !in comicLinks) comicLinks.add(link)
        }
        
        val downloadedEpisodes = mutableListOf<DownloadedEpisode>()
        val limit = minOf(comicLinks.size, 100)
        
        for ((index, link) in comicLinks.take(limit).withIndex()) {
            progressCallback?.invoke(index + 1, limit)
            
            val html = fetchHtml(link) ?: continue
            
            val title = extractBetween(html, "<h2>", "</h2>")
                ?.let { htmlToText(it) } ?: "Comic ${index + 1}"
            
            val imageUrl = Regex("id=\"comicFrame\"[^>]*>\\s*<img[^>]*src=\"([^\"]+)\"")
                .find(html)?.groupValues?.get(1)
            
            if (imageUrl != null) {
                val imageData = fetchBinary(imageUrl)
                if (imageData != null) {
                    val episodeDir = File(comicDir, String.format("%04d", index + 1))
                    episodeDir.mkdirs()
                    
                    val imageFile = File(episodeDir, "001.jpg")
                    imageFile.writeBytes(imageData)
                    
                    downloadedEpisodes.add(DownloadedEpisode(
                        number = index + 1,
                        title = title,
                        images = listOf(imageFile)
                    ))
                }
            }
            
            Thread.sleep(300)
        }
        
        return WebComicResult(
            title = "Penny Arcade",
            author = "Mike Krahulik & Jerry Holkins",
            description = "A gaming-focused webcomic",
            episodes = downloadedEpisodes,
            outputDir = comicDir,
            sourceUrl = url
        )
    }
    
    /**
     * Download from Mangadex
     */
    private fun downloadMangadex(url: String, outputDir: File,
                                  progressCallback: ((Int, Int) -> Unit)?): WebComicResult? {
        // Extract manga ID
        val mangaId = Regex("/title/([a-f0-9-]+)").find(url)?.groupValues?.get(1) ?: return null
        
        // Use Mangadex API
        val apiUrl = "https://api.mangadex.org/manga/$mangaId?includes[]=author&includes[]=artist&includes[]=cover_art"
        val mangaJson = fetchJson(apiUrl) ?: return null
        
        val title = extractJsonString(mangaJson, "en") 
            ?: extractJsonString(mangaJson, "title") ?: "Unknown Manga"
        
        // Get chapters
        val chaptersUrl = "https://api.mangadex.org/manga/$mangaId/feed?translatedLanguage[]=en&order[chapter]=asc&limit=500"
        val chaptersJson = fetchJson(chaptersUrl) ?: return null
        
        // Parse chapters
        data class ChapterInfo(val id: String, val chapter: String?, val title: String?)
        val chapters = mutableListOf<ChapterInfo>()
        
        val chapterPattern = Regex("\"id\":\"([a-f0-9-]+)\"[^}]*\"chapter\":\"?([^,\"]+)\"?[^}]*\"title\":\"?([^\"]+)?\"?")
        for (match in chapterPattern.findAll(chaptersJson)) {
            chapters.add(ChapterInfo(
                match.groupValues[1],
                match.groupValues[2].takeIf { it != "null" },
                match.groupValues[3].takeIf { it != "null" && it.isNotBlank() }
            ))
        }
        
        val comicDir = File(outputDir, sanitizeFilename(title))
        comicDir.mkdirs()
        
        val downloadedEpisodes = mutableListOf<DownloadedEpisode>()
        
        for ((index, chapter) in chapters.withIndex()) {
            progressCallback?.invoke(index + 1, chapters.size)
            
            val chapterNum = chapter.chapter?.toDoubleOrNull()?.toInt() ?: (index + 1)
            val chapterTitle = chapter.title ?: "Chapter ${chapter.chapter ?: (index + 1)}"
            
            // Get chapter pages
            val pagesUrl = "https://api.mangadex.org/at-home/server/${chapter.id}"
            val pagesJson = fetchJson(pagesUrl)
            
            if (pagesJson != null) {
                val baseUrl = extractJsonString(pagesJson, "baseUrl") ?: continue
                val hash = extractJsonString(pagesJson, "hash") ?: continue
                
                val pageFiles = mutableListOf<String>()
                val dataPattern = Regex("\"data\":\\[([^\\]]+)\\]")
                val dataMatch = dataPattern.find(pagesJson)
                if (dataMatch != null) {
                    val pagePattern = Regex("\"([^\"]+)\"")
                    for (pageMatch in pagePattern.findAll(dataMatch.groupValues[1])) {
                        pageFiles.add(pageMatch.groupValues[1])
                    }
                }
                
                val episodeDir = File(comicDir, "Chapter_${String.format("%04d", chapterNum)}")
                episodeDir.mkdirs()
                
                val images = mutableListOf<File>()
                
                for ((pageIndex, pageFile) in pageFiles.withIndex()) {
                    val pageUrl = "$baseUrl/data/$hash/$pageFile"
                    val imageData = fetchBinary(pageUrl)
                    
                    if (imageData != null) {
                        val ext = pageFile.substringAfterLast(".")
                        val imageFile = File(episodeDir, "${String.format("%03d", pageIndex + 1)}.$ext")
                        imageFile.writeBytes(imageData)
                        images.add(imageFile)
                    }
                    
                    Thread.sleep(100)
                }
                
                if (images.isNotEmpty()) {
                    downloadedEpisodes.add(DownloadedEpisode(chapterNum, chapterTitle, images))
                }
            }
            
            Thread.sleep(500)
        }
        
        return WebComicResult(
            title = title,
            author = null,
            description = null,
            episodes = downloadedEpisodes,
            outputDir = comicDir,
            sourceUrl = url
        )
    }
    
    /**
     * Download from Mangakakalot/Manganato
     */
    private fun downloadMangakakalot(url: String, outputDir: File,
                                      progressCallback: ((Int, Int) -> Unit)?): WebComicResult? {
        val mainPage = fetchHtml(url) ?: return null
        
        val title = extractBetween(mainPage, "<h1>", "</h1>")
            ?.let { htmlToText(it) } ?: "Unknown Manga"
        
        val author = extractBetween(mainPage, "Author(s)", "</tr>")
            ?.let { extractBetween(it, "\">", "</a>") }
        
        // Get chapter list
        val chapterList = mutableListOf<Pair<String, String>>()
        val chapterPattern = Regex("<a[^>]*href=\"([^\"]+chapter[^\"]+)\"[^>]*>\\s*([^<]+)</a>")
        
        for (match in chapterPattern.findAll(mainPage)) {
            chapterList.add(Pair(match.groupValues[1], match.groupValues[2].trim()))
        }
        
        chapterList.reverse() // Usually listed newest first
        
        val comicDir = File(outputDir, sanitizeFilename(title))
        comicDir.mkdirs()
        
        val downloadedEpisodes = mutableListOf<DownloadedEpisode>()
        
        for ((index, chapter) in chapterList.withIndex()) {
            val (chapterUrl, chapterTitle) = chapter
            progressCallback?.invoke(index + 1, chapterList.size)
            
            val chapterPage = fetchHtml(chapterUrl) ?: continue
            
            val episodeDir = File(comicDir, "Chapter_${String.format("%04d", index + 1)}")
            episodeDir.mkdirs()
            
            val images = mutableListOf<File>()
            val imagePattern = Regex("<img[^>]*class=\"[^\"]*reader[^\"]*\"[^>]*src=\"([^\"]+)\"")
            
            for ((pageIndex, imageMatch) in imagePattern.findAll(chapterPage).withIndex()) {
                val imageUrl = imageMatch.groupValues[1]
                
                // Add referer for these sites
                headers["Referer"] = chapterUrl
                val imageData = fetchBinary(imageUrl)
                headers.remove("Referer")
                
                if (imageData != null) {
                    val ext = imageUrl.substringAfterLast(".").take(4)
                    val imageFile = File(episodeDir, "${String.format("%03d", pageIndex + 1)}.$ext")
                    imageFile.writeBytes(imageData)
                    images.add(imageFile)
                }
            }
            
            if (images.isNotEmpty()) {
                downloadedEpisodes.add(DownloadedEpisode(index + 1, chapterTitle, images))
            }
            
            Thread.sleep(500)
        }
        
        return WebComicResult(
            title = title,
            author = author,
            description = null,
            episodes = downloadedEpisodes,
            outputDir = comicDir,
            sourceUrl = url
        )
    }
    
    /**
     * Generic webcomic downloader - tries to detect images automatically
     */
    private fun downloadGeneric(url: String, outputDir: File,
                                 progressCallback: ((Int, Int) -> Unit)?): WebComicResult? {
        val html = fetchHtml(url) ?: return null
        
        val title = extractBetween(html, "<title>", "</title>")
            ?.let { htmlToText(it) } ?: "Unknown Comic"
        
        val comicDir = File(outputDir, sanitizeFilename(title))
        comicDir.mkdirs()
        
        // Try to find main comic image
        val imagePatterns = listOf(
            Regex("<img[^>]*id=\"[^\"]*comic[^\"]*\"[^>]*src=\"([^\"]+)\"", RegexOption.IGNORE_CASE),
            Regex("<img[^>]*class=\"[^\"]*comic[^\"]*\"[^>]*src=\"([^\"]+)\"", RegexOption.IGNORE_CASE),
            Regex("<div[^>]*class=\"[^\"]*comic[^\"]*\"[^>]*>\\s*<img[^>]*src=\"([^\"]+)\"", RegexOption.IGNORE_CASE),
            Regex("<img[^>]*src=\"([^\"]+(?:comic|strip|page)[^\"]+)\"", RegexOption.IGNORE_CASE)
        )
        
        val images = mutableListOf<File>()
        
        for (pattern in imagePatterns) {
            val match = pattern.find(html)
            if (match != null) {
                var imageUrl = match.groupValues[1]
                if (!imageUrl.startsWith("http")) {
                    val baseUrl = url.substringBefore("//") + "//" + 
                        URL(url).host
                    imageUrl = if (imageUrl.startsWith("/")) {
                        baseUrl + imageUrl
                    } else {
                        url.substringBeforeLast("/") + "/" + imageUrl
                    }
                }
                
                val imageData = fetchBinary(imageUrl)
                if (imageData != null) {
                    val ext = imageUrl.substringAfterLast(".").substringBefore("?").take(4)
                    val imageFile = File(comicDir, "001.$ext")
                    imageFile.writeBytes(imageData)
                    images.add(imageFile)
                    break
                }
            }
        }
        
        progressCallback?.invoke(1, 1)
        
        return WebComicResult(
            title = title,
            author = null,
            description = null,
            episodes = if (images.isNotEmpty()) {
                listOf(DownloadedEpisode(1, title, images))
            } else emptyList(),
            outputDir = comicDir,
            sourceUrl = url
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
    }
    
    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
    }
}

/**
 * Episode info before download
 */
data class WebComicEpisode(
    val number: Int,
    val title: String,
    val url: String
)

/**
 * Downloaded episode with images
 */
data class DownloadedEpisode(
    val number: Int,
    val title: String,
    val images: List<File>
)

/**
 * Result of webcomic download
 */
data class WebComicResult(
    val title: String,
    val author: String?,
    val description: String?,
    val episodes: List<DownloadedEpisode>,
    val outputDir: File,
    val coverFile: File? = null,
    val sourceUrl: String
) {
    val totalPages: Int get() = episodes.sumOf { it.images.size }
    val totalEpisodes: Int get() = episodes.size
}
