package org.calibre.search

import org.calibre.metadata.Metadata
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Full-Text Search (FTS) engine for searching book content.
 * Indexes text content from books and provides fast search capabilities.
 */
class FullTextSearch(private val libraryDir: File) {
    private val index = ConcurrentHashMap<Int, SearchIndexEntry>()
    private val invertedIndex = ConcurrentHashMap<String, MutableSet<Int>>() // term -> book IDs
    
    data class SearchIndexEntry(
        val bookId: Int,
        val title: String,
        val author: String,
        val content: String, // Full text content
        val terms: Set<String> // Normalized terms
    )
    
    data class SearchResult(
        val bookId: Int,
        val title: String,
        val author: String,
        val relevance: Double,
        val snippets: List<String> // Text snippets showing matches
    )
    
    /**
     * Index a book's content for full-text search.
     */
    fun indexBook(book: Metadata, contentFile: File?) {
        if (contentFile == null || !contentFile.exists()) {
            // Index metadata only
            val terms = tokenize("${book.title} ${book.authors.joinToString(" ")} ${book.tags.joinToString(" ")}")
            val entry = SearchIndexEntry(
                bookId = book.id ?: return,
                title = book.title,
                author = book.authors.joinToString(", "),
                content = "",
                terms = terms
            )
            index[entry.bookId] = entry
            updateInvertedIndex(entry)
            return
        }
        
        // Extract text from content file
        val content = try {
            extractText(contentFile)
        } catch (e: Exception) {
            // Fallback to metadata only
            ""
        }
        
        val fullText = "${book.title} ${book.authors.joinToString(" ")} $content"
        val terms = tokenize(fullText)
        
        val entry = SearchIndexEntry(
            bookId = book.id ?: return,
            title = book.title,
            author = book.authors.joinToString(", "),
            content = content,
            terms = terms
        )
        
        // Remove old index entry if exists
        index[entry.bookId]?.let { oldEntry ->
            oldEntry.terms.forEach { term ->
                invertedIndex[term]?.remove(oldEntry.bookId)
            }
        }
        
        index[entry.bookId] = entry
        updateInvertedIndex(entry)
    }
    
    /**
     * Remove a book from the index.
     */
    fun removeBook(bookId: Int) {
        index[bookId]?.let { entry ->
            entry.terms.forEach { term ->
                invertedIndex[term]?.remove(bookId)
            }
            index.remove(bookId)
        }
    }
    
    /**
     * Search for books containing the query terms.
     */
    fun search(query: String, maxResults: Int = 50): List<SearchResult> {
        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()
        
        // Find books containing any of the query terms
        val bookScores = mutableMapOf<Int, Double>()
        val bookMatches = mutableMapOf<Int, MutableList<String>>() // bookId -> matched terms
        
        queryTerms.forEach { term ->
            invertedIndex[term]?.forEach { bookId ->
                val entry = index[bookId] ?: return@forEach
                
                // Calculate relevance score
                val termFrequency = entry.content.lowercase().split(term.lowercase()).size - 1
                val titleMatch = if (entry.title.lowercase().contains(term.lowercase())) 10.0 else 0.0
                val authorMatch = if (entry.author.lowercase().contains(term.lowercase())) 5.0 else 0.0
                
                val score = termFrequency * 1.0 + titleMatch + authorMatch
                bookScores[bookId] = (bookScores[bookId] ?: 0.0) + score
                bookMatches.getOrPut(bookId) { mutableListOf() }.add(term)
            }
        }
        
        // Sort by relevance
        val results = bookScores.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .mapNotNull { (bookId, score) ->
                val entry = index[bookId] ?: return@mapNotNull null
                val snippets = generateSnippets(entry, bookMatches[bookId] ?: emptyList())
                
                SearchResult(
                    bookId = bookId,
                    title = entry.title,
                    author = entry.author,
                    relevance = score,
                    snippets = snippets
                )
            }
        
        return results
    }
    
    /**
     * Generate text snippets showing where matches occur.
     */
    private fun generateSnippets(entry: SearchIndexEntry, matchedTerms: List<String>, snippetLength: Int = 150): List<String> {
        if (entry.content.isEmpty()) return emptyList()
        
        val snippets = mutableListOf<String>()
        val content = entry.content
        val lowerContent = content.lowercase()
        
        matchedTerms.forEach { term ->
            val lowerTerm = term.lowercase()
            var startIndex = 0
            
            while (true) {
                val index = lowerContent.indexOf(lowerTerm, startIndex)
                if (index == -1) break
                
                val snippetStart = maxOf(0, index - snippetLength / 2)
                val snippetEnd = minOf(content.length, index + term.length + snippetLength / 2)
                
                var snippet = content.substring(snippetStart, snippetEnd)
                if (snippetStart > 0) snippet = "..." + snippet
                if (snippetEnd < content.length) snippet = snippet + "..."
                
                snippets.add(snippet)
                
                startIndex = index + 1
                if (snippets.size >= 3) break // Limit snippets per term
            }
        }
        
        return snippets.distinct().take(5) // Max 5 snippets
    }
    
    /**
     * Tokenize text into searchable terms.
     */
    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 } // Minimum 2 characters
            .toSet()
    }
    
    /**
     * Extract text from various file formats.
     */
    private fun extractText(file: File): String {
        return when (file.extension.lowercase()) {
            "txt" -> file.readText()
            "html", "xhtml", "htm" -> extractTextFromHtml(file)
            "epub" -> extractTextFromEpub(file)
            "mobi", "azw", "azw3" -> extractTextFromMobi(file)
            "pdf" -> extractTextFromPdf(file)
            else -> file.readText(Charsets.UTF_8)
        }
    }
    
    private fun extractTextFromHtml(file: File): String {
        val content = file.readText()
        // Simple HTML tag removal
        return content
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    private fun extractTextFromEpub(file: File): String {
        // Simplified EPUB text extraction
        try {
            val zip = java.util.zip.ZipFile(file)
            val entries = zip.entries()
            val textBuilder = StringBuilder()
            
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".html") || entry.name.endsWith(".xhtml") || entry.name.endsWith(".htm")) {
                    zip.getInputStream(entry).use { input ->
                        val content = input.readBytes().toString(Charsets.UTF_8)
                        textBuilder.append(extractTextFromHtml(java.io.File(entry.name).apply { 
                            // Create temp file for processing
                        }))
                        // Actually, process content directly
                        val htmlText = content
                            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
                            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
                            .replace(Regex("<[^>]+>"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        textBuilder.append(htmlText).append(" ")
                    }
                }
            }
            zip.close()
            return textBuilder.toString()
        } catch (e: Exception) {
            return ""
        }
    }
    
    private fun extractTextFromMobi(file: File): String {
        // Simplified - would need full MOBI parser
        return ""
    }
    
    private fun extractTextFromPdf(file: File): String {
        // Would need PDFBox text extraction
        return ""
    }
    
    /**
     * Update the inverted index with terms from an entry.
     */
    private fun updateInvertedIndex(entry: SearchIndexEntry) {
        entry.terms.forEach { term ->
            invertedIndex.getOrPut(term) { mutableSetOf() }.add(entry.bookId)
        }
    }
    
    /**
     * Clear the entire index.
     */
    fun clear() {
        index.clear()
        invertedIndex.clear()
    }
    
    /**
     * Get index statistics.
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "indexedBooks" to index.size,
            "totalTerms" to invertedIndex.size,
            "averageTermsPerBook" to if (index.isEmpty()) 0.0 else invertedIndex.values.sumOf { it.size }.toDouble() / index.size
        )
    }
}
