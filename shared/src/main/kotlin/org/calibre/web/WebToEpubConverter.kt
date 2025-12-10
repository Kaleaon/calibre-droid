package org.calibre.web

import org.calibre.web.comics.DownloadedEpisode
import org.calibre.web.comics.WebComicResult
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * Converts web content (fanfiction, webcomics, Reddit posts) to EPUB format.
 * 
 * Features:
 * - Text story to EPUB with proper formatting
 * - Webcomic to EPUB with images
 * - CBZ archive creation for comics
 * - Cover image embedding
 * - Table of contents generation
 * - Metadata embedding
 */
class WebToEpubConverter {
    
    /**
     * Convert text-based web content to EPUB.
     */
    fun convertToEpub(content: WebContent, outputFile: File): Boolean {
        try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
                // mimetype (must be first, uncompressed)
                zip.setMethod(ZipOutputStream.STORED)
                val mimeEntry = ZipEntry("mimetype")
                val mimeBytes = "application/epub+zip".toByteArray()
                mimeEntry.size = mimeBytes.size.toLong()
                mimeEntry.crc = calculateCrc(mimeBytes)
                zip.putNextEntry(mimeEntry)
                zip.write(mimeBytes)
                zip.closeEntry()
                
                // Switch to deflated for other entries
                zip.setMethod(ZipOutputStream.DEFLATED)
                
                // META-INF/container.xml
                zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                zip.write(generateContainerXml().toByteArray())
                zip.closeEntry()
                
                // OEBPS/content.opf
                zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
                zip.write(generateOpf(content).toByteArray())
                zip.closeEntry()
                
                // OEBPS/toc.ncx
                zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
                zip.write(generateNcx(content).toByteArray())
                zip.closeEntry()
                
                // OEBPS/nav.xhtml (EPUB3 nav)
                zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
                zip.write(generateNav(content).toByteArray())
                zip.closeEntry()
                
                // OEBPS/styles.css
                zip.putNextEntry(ZipEntry("OEBPS/styles.css"))
                zip.write(generateStyles().toByteArray())
                zip.closeEntry()
                
                // Title page
                zip.putNextEntry(ZipEntry("OEBPS/title.xhtml"))
                zip.write(generateTitlePage(content).toByteArray())
                zip.closeEntry()
                
                // Chapters
                for (chapter in content.chapters) {
                    zip.putNextEntry(ZipEntry("OEBPS/chapter_${chapter.index}.xhtml"))
                    zip.write(generateChapter(chapter, content.title).toByteArray())
                    zip.closeEntry()
                }
            }
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Convert webcomic to EPUB (image-based).
     */
    fun convertComicToEpub(comic: WebComicResult, outputFile: File): Boolean {
        try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
                // mimetype
                zip.setMethod(ZipOutputStream.STORED)
                val mimeEntry = ZipEntry("mimetype")
                val mimeBytes = "application/epub+zip".toByteArray()
                mimeEntry.size = mimeBytes.size.toLong()
                mimeEntry.crc = calculateCrc(mimeBytes)
                zip.putNextEntry(mimeEntry)
                zip.write(mimeBytes)
                zip.closeEntry()
                
                zip.setMethod(ZipOutputStream.DEFLATED)
                
                // META-INF/container.xml
                zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                zip.write(generateContainerXml().toByteArray())
                zip.closeEntry()
                
                // Collect all images and create manifest
                val imageEntries = mutableListOf<ComicImageEntry>()
                
                for (episode in comic.episodes) {
                    for (image in episode.images) {
                        val ext = image.extension.lowercase()
                        val mediaType = when (ext) {
                            "jpg", "jpeg" -> "image/jpeg"
                            "png" -> "image/png"
                            "gif" -> "image/gif"
                            "webp" -> "image/webp"
                            else -> "image/jpeg"
                        }
                        
                        val id = "img_${episode.number}_${image.nameWithoutExtension}"
                        val href = "images/${episode.number}/${image.name}"
                        
                        imageEntries.add(ComicImageEntry(id, href, mediaType, image, episode.number))
                    }
                }
                
                // Add cover if available
                if (comic.coverFile != null && comic.coverFile.exists()) {
                    val coverExt = comic.coverFile.extension.lowercase()
                    val coverType = if (coverExt == "png") "image/png" else "image/jpeg"
                    imageEntries.add(0, ComicImageEntry(
                        "cover", "images/cover.${coverExt}", coverType, comic.coverFile, 0
                    ))
                }
                
                // OEBPS/content.opf
                zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
                zip.write(generateComicOpf(comic, imageEntries).toByteArray())
                zip.closeEntry()
                
                // OEBPS/toc.ncx
                zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
                zip.write(generateComicNcx(comic).toByteArray())
                zip.closeEntry()
                
                // OEBPS/nav.xhtml
                zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
                zip.write(generateComicNav(comic).toByteArray())
                zip.closeEntry()
                
                // OEBPS/styles.css
                zip.putNextEntry(ZipEntry("OEBPS/styles.css"))
                zip.write(generateComicStyles().toByteArray())
                zip.closeEntry()
                
                // Add images
                for (entry in imageEntries) {
                    if (entry.file.exists()) {
                        zip.putNextEntry(ZipEntry("OEBPS/${entry.href}"))
                        zip.write(entry.file.readBytes())
                        zip.closeEntry()
                    }
                }
                
                // Generate pages for each episode
                for (episode in comic.episodes) {
                    zip.putNextEntry(ZipEntry("OEBPS/episode_${episode.number}.xhtml"))
                    zip.write(generateComicEpisodePage(episode, comic.title).toByteArray())
                    zip.closeEntry()
                }
            }
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Convert webcomic to CBZ archive.
     */
    fun convertComicToCbz(comic: WebComicResult, outputFile: File): Boolean {
        try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
                // Add cover
                if (comic.coverFile != null && comic.coverFile.exists()) {
                    zip.putNextEntry(ZipEntry("cover.${comic.coverFile.extension}"))
                    zip.write(comic.coverFile.readBytes())
                    zip.closeEntry()
                }
                
                // Add images organized by episode
                for (episode in comic.episodes) {
                    val epNum = String.format("%04d", episode.number)
                    
                    for ((index, image) in episode.images.withIndex()) {
                        val pageNum = String.format("%03d", index + 1)
                        zip.putNextEntry(ZipEntry("${epNum}_${episode.title}/${pageNum}.${image.extension}"))
                        zip.write(image.readBytes())
                        zip.closeEntry()
                    }
                }
                
                // Add ComicInfo.xml for comic readers
                zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                zip.write(generateComicInfo(comic).toByteArray())
                zip.closeEntry()
            }
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    // EPUB generation helpers
    
    private fun generateContainerXml(): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
    }
    
    private fun generateOpf(content: WebContent): String {
        val uuid = java.util.UUID.randomUUID().toString()
        val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uuid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uuid">urn:uuid:$uuid</dc:identifier>
    <dc:title>${escapeXml(content.title)}</dc:title>
""")
            content.author?.let { append("    <dc:creator>${escapeXml(it)}</dc:creator>\n") }
            content.description?.let { append("    <dc:description>${escapeXml(it)}</dc:description>\n") }
            append("""    <dc:language>en</dc:language>
    <dc:date>$date</dc:date>
    <dc:source>${escapeXml(content.sourceUrl)}</dc:source>
    <meta property="dcterms:modified">${date}T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="css" href="styles.css" media-type="text/css"/>
    <item id="title" href="title.xhtml" media-type="application/xhtml+xml"/>
""")
            for (chapter in content.chapters) {
                append("    <item id=\"ch${chapter.index}\" href=\"chapter_${chapter.index}.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
            }
            append("""  </manifest>
  <spine toc="ncx">
    <itemref idref="title"/>
""")
            for (chapter in content.chapters) {
                append("    <itemref idref=\"ch${chapter.index}\"/>\n")
            }
            append("""  </spine>
</package>""")
        }
    }
    
    private fun generateNcx(content: WebContent): String {
        val uuid = java.util.UUID.randomUUID().toString()
        
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="urn:uuid:$uuid"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>${escapeXml(content.title)}</text></docTitle>
  <navMap>
    <navPoint id="title" playOrder="1">
      <navLabel><text>Title</text></navLabel>
      <content src="title.xhtml"/>
    </navPoint>
""")
            for ((index, chapter) in content.chapters.withIndex()) {
                append("""    <navPoint id="ch${chapter.index}" playOrder="${index + 2}">
      <navLabel><text>${escapeXml(chapter.title)}</text></navLabel>
      <content src="chapter_${chapter.index}.xhtml"/>
    </navPoint>
""")
            }
            append("""  </navMap>
</ncx>""")
        }
    }
    
    private fun generateNav(content: WebContent): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
  <title>Table of Contents</title>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Table of Contents</h1>
    <ol>
      <li><a href="title.xhtml">Title</a></li>
""")
            for (chapter in content.chapters) {
                append("      <li><a href=\"chapter_${chapter.index}.xhtml\">${escapeXml(chapter.title)}</a></li>\n")
            }
            append("""    </ol>
  </nav>
</body>
</html>""")
        }
    }
    
    private fun generateStyles(): String {
        return """body {
  font-family: Georgia, serif;
  line-height: 1.6;
  margin: 1em;
  padding: 0;
}
h1, h2, h3, h4, h5, h6 {
  font-family: Helvetica, Arial, sans-serif;
  margin-top: 1.5em;
  margin-bottom: 0.5em;
}
h1 { font-size: 2em; text-align: center; }
h2 { font-size: 1.5em; }
p { margin: 0.5em 0; text-indent: 1.5em; }
p:first-of-type { text-indent: 0; }
blockquote {
  margin: 1em 2em;
  padding: 0.5em 1em;
  border-left: 3px solid #ccc;
  font-style: italic;
}
hr {
  margin: 2em auto;
  width: 50%;
  border: none;
  border-top: 1px solid #999;
}
a { color: #0066cc; }
code { font-family: monospace; background: #f4f4f4; padding: 0.2em 0.4em; }
pre { background: #f4f4f4; padding: 1em; overflow-x: auto; }
.chapter-title { text-align: center; margin-bottom: 2em; }
.author { text-align: center; font-style: italic; color: #666; }
.source { text-align: center; font-size: 0.9em; color: #999; margin-top: 2em; }"""
    }
    
    private fun generateTitlePage(content: WebContent): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${escapeXml(content.title)}</title>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
  <div style="text-align: center; margin-top: 30%;">
    <h1>${escapeXml(content.title)}</h1>
""")
            content.author?.let {
                append("    <p class=\"author\">by ${escapeXml(it)}</p>\n")
            }
            content.description?.let {
                append("    <p style=\"margin-top: 2em; font-style: italic;\">${escapeXml(it)}</p>\n")
            }
            if (content.tags.isNotEmpty()) {
                append("    <p style=\"margin-top: 2em; font-size: 0.9em; color: #666;\">${content.tags.joinToString(" • ")}</p>\n")
            }
            append("""    <p class="source">Source: ${escapeXml(content.sourceUrl)}</p>
  </div>
</body>
</html>""")
        }
    }
    
    private fun generateChapter(chapter: WebChapter, bookTitle: String): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${escapeXml(chapter.title)}</title>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
  <h2 class="chapter-title">${escapeXml(chapter.title)}</h2>
  ${chapter.content}
</body>
</html>"""
    }
    
    // Comic EPUB generation
    
    private fun generateComicOpf(comic: WebComicResult, images: List<ComicImageEntry>): String {
        val uuid = java.util.UUID.randomUUID().toString()
        val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uuid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uuid">urn:uuid:$uuid</dc:identifier>
    <dc:title>${escapeXml(comic.title)}</dc:title>
""")
            comic.author?.let { append("    <dc:creator>${escapeXml(it)}</dc:creator>\n") }
            append("""    <dc:language>en</dc:language>
    <dc:date>$date</dc:date>
    <meta property="dcterms:modified">${date}T00:00:00Z</meta>
    <meta name="fixed-layout" content="true"/>
    <meta name="original-resolution" content="1280x1920"/>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="css" href="styles.css" media-type="text/css"/>
""")
            for (image in images) {
                append("    <item id=\"${image.id}\" href=\"${image.href}\" media-type=\"${image.mediaType}\"/>\n")
            }
            for (episode in comic.episodes) {
                append("    <item id=\"ep${episode.number}\" href=\"episode_${episode.number}.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
            }
            append("""  </manifest>
  <spine toc="ncx">
""")
            for (episode in comic.episodes) {
                append("    <itemref idref=\"ep${episode.number}\"/>\n")
            }
            append("""  </spine>
</package>""")
        }
    }
    
    private fun generateComicNcx(comic: WebComicResult): String {
        val uuid = java.util.UUID.randomUUID().toString()
        
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="urn:uuid:$uuid"/>
    <meta name="dtb:depth" content="1"/>
  </head>
  <docTitle><text>${escapeXml(comic.title)}</text></docTitle>
  <navMap>
""")
            for ((index, episode) in comic.episodes.withIndex()) {
                append("""    <navPoint id="ep${episode.number}" playOrder="${index + 1}">
      <navLabel><text>${escapeXml(episode.title)}</text></navLabel>
      <content src="episode_${episode.number}.xhtml"/>
    </navPoint>
""")
            }
            append("""  </navMap>
</ncx>""")
        }
    }
    
    private fun generateComicNav(comic: WebComicResult): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
  <title>Table of Contents</title>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>${escapeXml(comic.title)}</h1>
    <ol>
""")
            for (episode in comic.episodes) {
                append("      <li><a href=\"episode_${episode.number}.xhtml\">${escapeXml(episode.title)}</a></li>\n")
            }
            append("""    </ol>
  </nav>
</body>
</html>""")
        }
    }
    
    private fun generateComicStyles(): String {
        return """body { margin: 0; padding: 0; }
.page { 
  width: 100%; 
  height: 100vh; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  background: #000;
}
img { 
  max-width: 100%; 
  max-height: 100%; 
  object-fit: contain; 
}"""
    }
    
    private fun generateComicEpisodePage(episode: DownloadedEpisode, comicTitle: String): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${escapeXml(episode.title)}</title>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
""")
            for (image in episode.images) {
                val href = "images/${episode.number}/${image.name}"
                append("  <div class=\"page\"><img src=\"$href\" alt=\"Page\"/></div>\n")
            }
            append("""</body>
</html>""")
        }
    }
    
    // CBZ helpers
    
    private fun generateComicInfo(comic: WebComicResult): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <Title>${escapeXml(comic.title)}</Title>
""")
            comic.author?.let { append("  <Writer>${escapeXml(it)}</Writer>\n") }
            comic.description?.let { append("  <Summary>${escapeXml(it)}</Summary>\n") }
            append("  <PageCount>${comic.totalPages}</PageCount>\n")
            append("  <Web>${escapeXml(comic.sourceUrl)}</Web>\n")
            append("</ComicInfo>")
        }
    }
    
    // Utility methods
    
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
    
    private fun calculateCrc(data: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(data)
        return crc.value
    }
}

private data class ComicImageEntry(
    val id: String,
    val href: String,
    val mediaType: String,
    val file: File,
    val episodeNumber: Int
)
