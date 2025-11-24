package org.calibre.metadata

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress

class ContentServer(private val library: Library, private val port: Int = 8080) {

    fun start() {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.createContext("/", LibraryHandler(library))
        server.createContext("/download/", DownloadHandler(library))
        server.executor = null // creates a default executor
        server.start()
        println("Content Server started on port $port")
        println("Access at: http://localhost:$port/")
    }
}

class LibraryHandler(private val library: Library) : HttpHandler {
    override fun handle(t: HttpExchange) {
        val sb = StringBuilder()
        sb.append("<html><head><title>Calibre Kotlin Library</title></head><body>")
        sb.append("<h1>Library</h1>")
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
                t.responseHeaders.add("Content-Type", "application/octet-stream")
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
