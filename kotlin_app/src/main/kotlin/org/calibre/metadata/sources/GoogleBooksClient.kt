package org.calibre.metadata.sources

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.calibre.metadata.Metadata
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class GoogleBooksClient : MetadataSource {
    override val name = "Google Books"
    private val client = HttpClient.newHttpClient()
    private val mapper = ObjectMapper()

    override fun search(query: String): List<Metadata> {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val uri = URI.create("https://www.googleapis.com/books/v1/volumes?q=$encodedQuery&maxResults=5")
        
        val request = HttpRequest.newBuilder(uri)
            .header("Accept", "application/json")
            .GET()
            .build()

        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                return parseResponse(response.body())
            }
        } catch (e: Exception) {
            println("Error fetching metadata: ${e.message}")
        }
        return emptyList()
    }

    private fun parseResponse(json: String): List<Metadata> {
        val results = mutableListOf<Metadata>()
        val root: JsonNode = mapper.readTree(json)
        
        if (root.has("items")) {
            val items = root.get("items")
            for (item in items) {
                val info = item.get("volumeInfo")
                
                val title = info.path("title").asText("Unknown")
                val authorsNode = info.path("authors")
                val authors = mutableListOf<String>()
                if (authorsNode.isArray) {
                    for (author in authorsNode) {
                        authors.add(author.asText())
                    }
                }
                if (authors.isEmpty()) authors.add("Unknown")
                
                val desc = info.path("description").asText(null)
                val publisher = info.path("publisher").asText(null)
                
                val tags = mutableListOf<String>()
                val categories = info.path("categories")
                if (categories.isArray) {
                    for (cat in categories) tags.add(cat.asText())
                }
                
                val metadata = Metadata(
                    title = title,
                    authors = authors,
                    comments = desc,
                    publisher = publisher,
                    tags = tags
                )
                results.add(metadata)
            }
        }
        return results
    }
}
