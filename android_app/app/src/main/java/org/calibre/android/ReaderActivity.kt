package org.calibre.android

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.calibre.android.databinding.ActivityReaderBinding
import org.calibre.conversion.ConversionPipeline
import java.io.File

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookId = intent.getIntExtra("book_id", -1)
        val library = (application as CalibreApplication).library
        val bookFile = library.getBookFile(bookId)

        if (bookFile != null && bookFile.exists()) {
            try {
                // Check if we need conversion
                if (bookFile.extension.lowercase() in setOf("epub", "mobi", "azw3")) {
                    // We need a place to put the converted HTML
                    // Use Android cache dir
                    val cacheDir = File(cacheDir, "reader_cache")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    
                    val outputFile = File(cacheDir, "content.html")
                    
                    // Run conversion in background ideally, but for PoC main thread is okay if small
                    // Or just use CoroutineScope if I had set up Kotlin Coroutines
                    // Let's just do it here for now (blocking UI, sorry!)
                    
                    val pipeline = ConversionPipeline()
                    try {
                        pipeline.convert(bookFile, "html", outputFile)
                        binding.webView.loadUrl("file://${outputFile.absolutePath}")
                    } catch (e: Exception) {
                         val html = "<html><body><h1>Conversion Error</h1><p>${e.message}</p></body></html>"
                         binding.webView.loadData(html, "text/html", "UTF-8")
                    }
                } else if (bookFile.extension.lowercase() == "html" || bookFile.extension.lowercase() == "txt") {
                    binding.webView.loadUrl("file://${bookFile.absolutePath}")
                } else {
                    // PDF or other?
                     val html = "<html><body><h1>Format Not Supported</h1><p>Cannot read ${bookFile.extension}</p></body></html>"
                     binding.webView.loadData(html, "text/html", "UTF-8")
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening book: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Book file not found", Toast.LENGTH_SHORT).show()
        }
    }
}
