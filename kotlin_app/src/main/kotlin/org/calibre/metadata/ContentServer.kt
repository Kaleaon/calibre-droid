package org.calibre.metadata

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ContentServer(private val library: Library, private val port: Int = 8080) {

    fun start() {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.createContext("/", LibraryHandler(library))
        server.createContext("/opds", OpdsHandler(library))
        server.createContext("/download/", DownloadHandler(library))
        server.executor = null // creates a default executor
        server.start()
        println("Content Server started on port $port")
        println("Access at: http://localhost:$port/")
        println("OPDS Feed: http://localhost:$port/opds")
    }
}

class LibraryHandler(private val library: Library) : HttpHandler {
    override fun handle(t: HttpExchange) {
        if (t.requestURI.path != "/") {
             t.sendResponseHeaders(404, 0)
             t.close()
             return
        }
        val sb = StringBuilder()
        sb.append("<html><head><title>Calibre Kotlin Library</title></head><body>")
        sb.append("<h1>Library</h1>")
        sb.append("<p><a href='/opds'>OPDS Feed</a></p>")
        sb.append("<ul>")
        
        val books = library.getAllBooks()
        if (books.isEmpty()) {
            sb.append("<li>Library is empty</li>")
        } else {
            for (book in books) {
                sb.append("<li>")
                sb.append("<b>${book.title}</b> by ${book.authors.joinToString(", ")}")
                if (book.series != null) {
                     sb.append(" (${book.series} #${book.seriesIndex})")
                }
                sb.append(" <a href='/download/${book.id}'>[Download]</a>")
                sb.append("</li>")
            }
        }
        sb.append("</ul></body></html>")
        
        val response = sb.toString()
        t.sendResponseHeaders(200, response.length.toLong())
        val os = t.responseBody
        os.write(response.toByteArray())
        os.close()
    }
}

class OpdsHandler(private val library: Library) : HttpHandler {
    override fun handle(t: HttpExchange) {
        val sb = StringBuilder()
        val updated = java.time.ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<feed xmlns=\"http://www.w3.org/2005/Atom\">\n")
        sb.append("  <title>Calibre Kotlin Library</title>\n")
        sb.append("  <id>urn:calibre:kotlin:library</id>\n")
        sb.append("  <updated>$updated</updated>\n")
        sb.append("  <link rel=\"self\" href=\"/opds\" type=\"application/atom+xml;profile=opds-catalog;kind=navigation\"/>\n")
        sb.append("  <link rel=\"start\" href=\"/opds\" type=\"application/atom+xml;profile=opds-catalog;kind=navigation\"/>\n")
        
        val books = library.getAllBooks()
        for (book in books) {
            val bookFile = library.getBookFile(book.id ?: 0)
            val mimeType = when(bookFile?.extension?.lowercase()) {
                "epub" -> "application/epub+zip"
                "pdf" -> "application/pdf"
                "mobi" -> "application/x-mobipocket-ebook"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
            
            sb.append("  <entry>\n")
            sb.append("    <title>${escapeXml(book.title)}</title>\n")
            sb.append("    <author><name>${escapeXml(book.authors.joinToString(", "))}</name></author>\n")
            sb.append("    <id>urn:calibre:kotlin:book:${book.id}</id>\n")
            val bookUpdated = if (book.pubDate != null) 
                book.pubDate!!.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                else updated
            sb.append("    <updated>$bookUpdated</updated>\n")
            if (book.comments != null) {
                sb.append("    <content type=\"text\">${escapeXml(book.comments!!)}</content>\n")
            }
            sb.append("    <link rel=\"http://opds-spec.org/acquisition\" href=\"/download/${book.id}\" type=\"$mimeType\"/>\n")
            sb.append("  </entry>\n")
        }
        
        sb.append("</feed>")
        
        val response = sb.toString()
        t.responseHeaders.add("Content-Type", "application/xml; charset=UTF-8")
        t.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
        val os = t.responseBody
        os.write(response.toByteArray(Charsets.UTF_8))
        os.close()
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
    }
}

class DownloadHandler(private val library: Library) : HttpHandler {
    override fun handle(t: HttpExchange) {
        val uri = t.requestURI.toString()
        // Expected /download/{id}
        val parts = uri.split("/")
        if (parts.size < 3) {
            t.sendResponseHeaders(400, 0)
            t.close()
            return
        }
        
        try {
            val id = parts[2].toInt()
            val bookFile = library.getBookFile(id)
            
            if (bookFile != null && bookFile.exists()) {
                val mimeType = when(bookFile.extension.lowercase()) {
                    "epub" -> "application/epub+zip"
                    "pdf" -> "application/pdf"
                    "mobi" -> "application/x-mobipocket-ebook"
                    "txt" -> "text/plain"
                    else -> "application/octet-stream"
                }
                t.responseHeaders.add("Content-Type", mimeType)
                t.responseHeaders.add("Content-Disposition", "attachment; filename=\"${bookFile.name}\"")
                t.sendResponseHeaders(200, bookFile.length())
                
                val os = t.responseBody
                bookFile.inputStream().use { input ->
                    input.copyTo(os)
                }
                os.close()
            } else {
                val response = "File not found"
                t.sendResponseHeaders(404, response.length.toLong())
                val os = t.responseBody
                os.write(response.toByteArray())
                os.close()
            }
        } catch (e: NumberFormatException) {
             t.sendResponseHeaders(400, 0)
             t.close()
        }
    }
}
