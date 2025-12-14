package org.calibre.android

import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebSettings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.calibre.android.databinding.ActivityReaderBinding
import org.calibre.conversion.ConversionPipeline
import java.io.File

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private var bookId: Int = -1
    private lateinit var library: AndroidLibrary
    private var startTime: Long = 0
    private var settings: org.calibre.metadata.ReadingSettings = org.calibre.metadata.ReadingSettings()
    private var lastProgressWriteAtMs: Long = 0
    private var lastProgressPercent: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookId = intent.getIntExtra("book_id", -1)
        library = (application as CalibreApplication).library
        val bookFile = library.getBookFile(bookId)

        if (bookFile != null && bookFile.exists()) {
            setupWebView()
            startTime = System.currentTimeMillis()
            
            try {
                // Check if we need conversion
                if (bookFile.extension.lowercase() in setOf("epub", "mobi", "azw3")) {
                    val readerCacheDir = File(cacheDir, "reader_cache")
                    if (!readerCacheDir.exists()) readerCacheDir.mkdirs()

                    val outputFile = File(readerCacheDir, "content_${bookId}.html")
                    val pipeline = ConversionPipeline()
                    try {
                        pipeline.convert(bookFile, "html", outputFile)
                        binding.webView.loadUrl("file://${outputFile.absolutePath}")
                    } catch (e: Exception) {
                        val html = "<html><body><h1>Conversion Error</h1><pre>${e.message}</pre></body></html>"
                        binding.webView.loadData(html, "text/html", "UTF-8")
                    }
                } else if (bookFile.extension.lowercase() == "html" || bookFile.extension.lowercase() == "txt") {
                    binding.webView.loadUrl("file://${bookFile.absolutePath}")
                } else {
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
    
    private fun setupWebView() {
        val webSettings: WebSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadsImagesAutomatically = true
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.allowContentAccess = false
        webSettings.allowFileAccess = true // required for file:// reader content
        webSettings.allowFileAccessFromFileURLs = false
        webSettings.allowUniversalAccessFromFileURLs = false
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        WebView.setWebContentsDebuggingEnabled(false)

        binding.webView.addJavascriptInterface(ReaderInterface(), "AndroidReader")
        
        // Load settings from SharedPreferences
        val prefs = getSharedPreferences("reading_settings", MODE_PRIVATE)
        settings = org.calibre.metadata.ReadingSettings(
            fontSize = prefs.getInt("fontSize", 16),
            fontFamily = prefs.getString("fontFamily", "serif") ?: "serif",
            theme = prefs.getString("theme", "light") ?: "light",
            marginHorizontal = prefs.getInt("marginHorizontal", 20),
            marginVertical = prefs.getInt("marginVertical", 30),
            lineHeight = prefs.getFloat("lineHeight", 1.5f).toDouble()
        )
        
        // Inject JavaScript to track scroll position and apply settings
        binding.webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // Apply reading settings
                val css = settings.toCss()
                view?.evaluateJavascript("""
                    (function() {
                        var style = document.createElement('style');
                        style.textContent = `$css`;
                        document.head.appendChild(style);
                    })();
                """.trimIndent(), null)
                
                // Inject tracking script
                view?.evaluateJavascript("""
                    (function() {
                        var lastScroll = 0;
                        window.addEventListener('scroll', function() {
                            var scrollTop = window.pageYOffset || document.documentElement.scrollTop;
                            var scrollHeight = document.documentElement.scrollHeight;
                            var clientHeight = document.documentElement.clientHeight;
                            var progress = Math.round((scrollTop / (scrollHeight - clientHeight)) * 100);
                            
                            if (Math.abs(progress - lastScroll) > 5) {
                                lastScroll = progress;
                                AndroidReader.updateProgress(progress, scrollHeight);
                            }
                        });
                    })();
                """.trimIndent(), null)
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reader_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_bookmark -> {
                addBookmark()
                true
            }
            R.id.action_bookmarks -> {
                showBookmarks()
                true
            }
            R.id.action_settings -> {
                showReadingSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showReadingSettings() {
        val prefs = getSharedPreferences("reading_settings", MODE_PRIVATE)
        val editor = prefs.edit()
        
        val themes = arrayOf("light", "dark", "sepia")
        val currentTheme = prefs.getString("theme", "light") ?: "light"
        val themeIndex = themes.indexOf(currentTheme).coerceAtLeast(0)
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Reading Settings")
            .setSingleChoiceItems(themes, themeIndex) { dialog, which ->
                editor.putString("theme", themes[which])
                editor.apply()
                settings.theme = themes[which]
                // Reload page to apply theme
                binding.webView.reload()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addBookmark() {
        binding.webView.evaluateJavascript("window.pageYOffset || document.documentElement.scrollTop", { scrollPos ->
            val position = scrollPos.replace("\"", "")
            val note = "" // Could show dialog for note
            try {
                library.addBookmark(bookId, position, note)
                Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun showBookmarks() {
        val book = library.getMetadata(bookId)
        if (book != null && book.bookmarks.isNotEmpty()) {
            val bookmarkList = book.bookmarks.joinToString("\n") { 
                "Position: ${it.position}\n${it.note ?: ""}\n"
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("Bookmarks")
                .setMessage(bookmarkList)
                .setPositiveButton("OK", null)
                .show()
        } else {
            Toast.makeText(this, "No bookmarks", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Save reading time
        val readingTime = ((System.currentTimeMillis() - startTime) / 60000).toInt()
        if (readingTime > 0) {
            library.addReadingTimeMinutes(bookId, readingTime)
        }
    }
    
    inner class ReaderInterface {
        @JavascriptInterface
        fun updateProgress(percent: Int, totalHeight: Int) {
            runOnUiThread {
                val now = SystemClock.elapsedRealtime()
                // Throttle writes to avoid saving on every scroll event
                if (percent == lastProgressPercent && now - lastProgressWriteAtMs < 2000) return@runOnUiThread
                if (now - lastProgressWriteAtMs < 1500) return@runOnUiThread

                val book = library.getMetadata(bookId)
                if (book != null) {
                    // Estimate pages (rough calculation)
                    val estimatedPages = (totalHeight / 800).coerceAtLeast(1) // ~800px per page
                    val currentPage = ((percent / 100.0) * estimatedPages).toInt()
                    library.updateReadingProgress(bookId, currentPage, estimatedPages, "$percent%")
                    lastProgressPercent = percent
                    lastProgressWriteAtMs = now
                }
            }
        }
    }
}
