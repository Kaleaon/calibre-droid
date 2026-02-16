package org.calibre.android

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.calibre.android.databinding.ActivityMainBinding
import org.calibre.metadata.Library
import org.calibre.metadata.Metadata
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var library: Library
    private lateinit var adapter: BookAdapter
    
    companion object {
        private const val REQUEST_CODE_IMPORT = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        KThemeEngine.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        library = (application as CalibreApplication).library
        AppLogger.i("MainActivity", "Main screen created")

        setupRecyclerView()
        setupSearch()
        
        binding.fabAdd.setOnClickListener {
            openFilePicker()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    refreshList()
                } else {
                    val results = library.search(newText)
                    adapter.updateData(results)
                    updateEmptyView()
                }
                return true
            }
        })
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_statistics -> {
                showStatistics()
                true
            }
            R.id.action_recently_read -> {
                showRecentlyRead()
                true
            }
            R.id.action_theme -> {
                showThemePicker()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupSearch() {
        // Search is handled by SearchView in menu
    }
    
    private fun showStatistics() {
        val stats = library.getReadingStatistics()
        val message = """
            Total Books: ${stats.totalBooks}
            Read: ${stats.readBooks}
            Unread: ${stats.unreadBooks}
            Reading Time: ${String.format("%.1f", stats.totalReadingTimeHours)} hours
            Bookmarks: ${stats.totalBookmarks}
            Average Rating: ${String.format("%.1f", stats.averageRating)}/5.0
        """.trimIndent()
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Reading Statistics")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showThemePicker() {
        val options = KThemeEngine.themes
        val labels = options.map { it.displayName }.toTypedArray()
        val current = KThemeEngine.getSelectedTheme(this)
        val currentIndex = options.indexOfFirst { it.id == current.id }.coerceAtLeast(0)

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.menu_theme))
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val chosen = options[which]
                if (KThemeEngine.selectTheme(this, chosen.id)) {
                    AppLogger.i("MainActivity", "Theme changed to ${chosen.id}")
                    dialog.dismiss()
                    recreate()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRecentlyRead() {
        val recent = library.getRecentlyRead(10)
        if (recent.isNotEmpty()) {
            val message = recent.joinToString("\n") { 
                "${it.title} - ${it.readingProgress.lastReadDate?.toLocalDate()}"
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("Recently Read")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        } else {
            Toast.makeText(this, "No recently read books", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/epub+zip",
                "application/x-mobipocket-ebook",
                "application/pdf",
                "text/plain"
            ))
        }
        startActivityForResult(Intent.createChooser(intent, "Select Book File"), REQUEST_CODE_IMPORT)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                importBook(uri)
            }
        }
    }
    
    private fun importBook(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                // Get filename from URI
                val fileName = getFileName(uri) ?: "imported_book"
                val extension = fileName.substringAfterLast('.', "")
                AppLogger.i("MainActivity", "Import requested for file=$fileName extension=$extension")

                // Create temp file in app's cache
                val tempFile = File(cacheDir, "import_${System.currentTimeMillis()}.$extension")
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                // Import using Library
                val id = library.importBook(tempFile)

                // Clean up temp file
                tempFile.delete()

                AppLogger.i("MainActivity", "Import completed with id=$id")
                Toast.makeText(this, "Book imported: ID $id", Toast.LENGTH_SHORT).show()
                refreshList()
            } ?: run {
                AppLogger.w("MainActivity", "Import failed: content resolver returned null input stream")
                Toast.makeText(this, "Error: Could not open file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Import failed for uri=$uri", e)
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
    
    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun setupRecyclerView() {
        adapter = BookAdapter(library, library.getAllBooks()) { book ->
            // On Item Click
            val intent = Intent(this, BookDetailActivity::class.java)
            intent.putExtra("book_id", book.id)
            startActivity(intent)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        
        updateEmptyView()
    }
    
    private fun refreshList() {
        adapter.updateData(library.getAllBooks())
        updateEmptyView()
    }
    
    private fun updateEmptyView() {
        if (adapter.itemCount == 0) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    class BookAdapter(
        private val library: Library,
        private var books: List<Metadata>,
        private val onItemClick: (Metadata) -> Unit
    ) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {
        
        // Lazy loading: only load covers for visible items
        private val coverCache = mutableMapOf<Int, Bitmap?>()
        private val coverExecutor: ExecutorService = Executors.newFixedThreadPool(2)
        private val mainHandler = Handler(Looper.getMainLooper())

        fun updateData(newBooks: List<Metadata>) {
            books = newBooks
            // Clear cache when data changes
            coverCache.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
            return BookViewHolder(view)
        }

        override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
            val book = books[position]
            holder.bind(book, library, this)
            holder.itemView.setOnClickListener { onItemClick(book) }
        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            coverExecutor.shutdownNow()
        }

        override fun getItemCount() = books.size

        class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleView: TextView = itemView.findViewById(R.id.book_title)
            private val authorView: TextView = itemView.findViewById(R.id.book_author)
            private val coverView: ImageView? = itemView.findViewById(R.id.book_cover)

            fun bind(book: Metadata, library: Library, adapter: BookAdapter) {
                titleView.text = book.title
                authorView.text = book.authors.joinToString(", ")
                
                // Show reading progress if available
                if (book.readingProgress.totalPages > 0) {
                    val progress = book.readingProgress.progressPercent.toInt()
                    titleView.text = "${book.title} ($progress%)"
                }
                
                // Load cover image with caching
                coverView?.let { imageView ->
                    val bookId = book.id ?: 0
                    
                    // Check cache first (including null cache entries)
                    if (adapter.coverCache.containsKey(bookId)) {
                        val cached = adapter.coverCache[bookId]
                        if (cached != null) {
                            imageView.setImageBitmap(cached)
                        } else {
                            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    } else {
                        // Load placeholder first
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                        
                        // Load cover asynchronously (simplified - in production use coroutines/thread pool)
                        loadCoverImageAsync(bookId, library, imageView, adapter)
                    }
                }
            }
            
            private fun loadCoverImageAsync(
                bookId: Int,
                library: Library,
                imageView: ImageView,
                adapter: BookAdapter
            ) {
                imageView.tag = bookId
                adapter.coverExecutor.execute {
                    val bitmap = try {
                        val bookFile = library.getBookFile(bookId)
                        if (bookFile != null && bookFile.extension.equals("epub", ignoreCase = true)) {
                            val coverBytes = org.calibre.metadata.CoverExtractor.extractCoverFromEpub(bookFile)
                            if (coverBytes != null) {
                                BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }

                    adapter.coverCache[bookId] = bitmap
                    adapter.mainHandler.post {
                        if (imageView.tag == bookId) {
                            if (bitmap != null) {
                                imageView.setImageBitmap(bitmap)
                            } else {
                                imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                            }
                        }
                    }
                }
            }
        }
    }
}
