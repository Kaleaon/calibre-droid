package org.calibre.android

import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.calibre.android.databinding.ActivityReaderBinding
import java.io.File

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookId = intent.getIntExtra("book_id", -1)
        val library = AndroidLibrary(this)
        val bookFile = library.getBookFile(bookId)

        if (bookFile != null && bookFile.exists()) {
            // For this PoC, we will assume it is an EPUB and we want to convert it to HTML to display in WebView.
            // Since we can't easily run the full conversion pipeline on Android Main Thread or without more setup,
            // we will simulate reading text content if it's a text file, or show a placeholder for EPUB
            // unless we port the text converter to Android fully (it uses javax.xml which is available).
            
            // Let's try to read it as text/html content
            try {
                // In a real app, we would use the ConversionPipeline.
                // Here we assume the file might be convertible or just display info.
                // If we had the TextConverter available in common code, we could use it.
                // The TextConverter is in org.calibre.metadata but relies on javax.xml
                
                // For PoC, load as file URL if it is HTML/TXT/PDF (WebView handles PDF on some versions or via Google Docs)
                // Actually, WebView cannot open PDF directly without JS lib.
                
                if (bookFile.extension == "html" || bookFile.extension == "txt") {
                    binding.webView.loadUrl("file://${bookFile.absolutePath}")
                } else {
                    // Placeholder for EPUB reading
                    val html = "<html><body><h1>Reader Mode</h1><p>Reading ${bookFile.name}</p><p>(Full EPUB rendering requires extraction)</p></body></html>"
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
