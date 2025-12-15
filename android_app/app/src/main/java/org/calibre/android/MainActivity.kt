package org.calibre.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.calibre.android.databinding.ActivityMainBinding
import org.calibre.metadata.Metadata
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var library: AndroidLibrary
    private lateinit var adapter: BookAdapter

    private val importBookLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { enqueueImport(it) }
    }

    private val exportLibraryLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { exportBackupToUri(it) }
    }

    private val importLibraryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importBackupFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        library = (application as CalibreApplication).library

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
                query?.let {
                    val q = it.trim()
                    if (q.startsWith("fts:", ignoreCase = true) || q.startsWith("content:", ignoreCase = true)) {
                        val term = q.substringAfter(":").trim()
                        val results = if (term.isBlank()) emptyList() else library.fullTextSearch(term)
                        adapter.updateData(results)
                        updateEmptyView()
                        return true
                    }
                }
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
            R.id.action_export_library -> {
                exportLibraryLauncher.launch("calibre-droid-backup.zip")
                true
            }
            R.id.action_import_library -> {
                importLibraryLauncher.launch(arrayOf("application/zip", "*/*"))
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
        importBookLauncher.launch(
            arrayOf(
                "application/epub+zip",
                "application/x-mobipocket-ebook",
                "application/pdf",
                "text/plain",
                "text/html",
                "*/*"
            )
        )
    }
    
    private fun enqueueImport(uri: Uri) {
        try {
            // Persist permission when possible (OpenDocument grants persistable permission)
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers don't allow persistable grants; worker will still try immediately.
            }

            val fileName = getFileName(uri) ?: "imported_book"
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"

            val input = Data.Builder()
                .putString(org.calibre.android.work.ImportBookWorker.KEY_URI, uri.toString())
                .putString(org.calibre.android.work.ImportBookWorker.KEY_DISPLAY_NAME, fileName)
                .putString(org.calibre.android.work.ImportBookWorker.KEY_MIME_TYPE, mime)
                .build()

            val request = OneTimeWorkRequestBuilder<org.calibre.android.work.ImportBookWorker>()
                .setInputData(input)
                .build()

            WorkManager.getInstance(this).enqueue(request)
            Toast.makeText(this, "Import queued", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportBackupToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    library.exportBackupZip(zip)
                }
            } ?: throw Exception("Could not open output stream")
            Toast.makeText(this, "Backup exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importBackupFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    library.importBackupZip(zip)
                }
            } ?: throw Exception("Could not open input stream")
            Toast.makeText(this, "Backup imported", Toast.LENGTH_SHORT).show()
            refreshList()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup import failed: ${e.message}", Toast.LENGTH_LONG).show()
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
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }
    
    override fun onResume() {
        super.onResume()
        refreshList()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::adapter.isInitialized) {
            adapter.shutdown()
        }
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
        private val library: AndroidLibrary,
        private var books: List<Metadata>,
        private val onItemClick: (Metadata) -> Unit
    ) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

        private val coverLock = Any()

        // Lazy loading: only load covers for visible items (bounded, avoids OOM)
        private val coverCache: LruCache<Int, android.graphics.Bitmap> = object :
            LruCache<Int, android.graphics.Bitmap>(calculateCacheSizeBytes()) {
            override fun sizeOf(key: Int, value: android.graphics.Bitmap): Int = value.byteCount
        }
        private val noCover = mutableSetOf<Int>()
        private val mainHandler = Handler(Looper.getMainLooper())
        private val executor = Executors.newFixedThreadPool(2)

        private fun calculateCacheSizeBytes(): Int {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            val cacheKb = (maxKb / 32).coerceAtLeast(1024) // ~3% heap, min 1MB
            return cacheKb * 1024
        }

        fun updateData(newBooks: List<Metadata>) {
            books = newBooks
            // Clear cache when data changes
            synchronized(coverLock) {
                coverCache.evictAll()
                noCover.clear()
            }
            notifyDataSetChanged()
        }

        fun shutdown() {
            executor.shutdown()
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

        override fun getItemCount() = books.size

        class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleView: TextView = itemView.findViewById(R.id.book_title)
            private val authorView: TextView = itemView.findViewById(R.id.book_author)
            private val coverView: ImageView? = itemView.findViewById(R.id.book_cover)

            fun bind(book: Metadata, library: AndroidLibrary, adapter: BookAdapter) {
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
                    imageView.tag = bookId
                    
                    // Check cache first
                    val cached = synchronized(adapter.coverLock) { adapter.coverCache.get(bookId) }
                    if (cached != null) {
                        imageView.setImageBitmap(cached)
                        return
                    }

                    // Known missing cover: avoid repeated work
                    if (synchronized(adapter.coverLock) { adapter.noCover.contains(bookId) }) {
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                        return
                    }

                    // Placeholder while loading
                    imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    loadCoverImageAsync(bookId, library, imageView, adapter)
                }
            }
            
            private fun loadCoverImageAsync(
                bookId: Int,
                library: AndroidLibrary,
                imageView: ImageView,
                adapter: BookAdapter
            ) {
                adapter.executor.execute {
                    try {
                        val bookFile = library.getBookFile(bookId)
                        if (bookFile != null && bookFile.extension.equals("epub", ignoreCase = true)) {
                            val coverBytes = org.calibre.metadata.CoverExtractor.extractCoverFromEpub(bookFile)
                            if (coverBytes != null) {
                                val bitmap = decodeScaledBitmap(coverBytes, maxDimPx = 256)
                                if (bitmap != null) {
                                    synchronized(adapter.coverLock) {
                                        adapter.coverCache.put(bookId, bitmap)
                                    }
                                    adapter.mainHandler.post {
                                        // Ensure this view holder still represents the same book
                                        if (imageView.tag == bookId) {
                                            imageView.setImageBitmap(bitmap)
                                        }
                                    }
                                    return@execute
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // fall through
                    }
                    synchronized(adapter.coverLock) {
                        adapter.noCover.add(bookId)
                    }
                }
            }

            private fun decodeScaledBitmap(bytes: ByteArray, maxDimPx: Int): android.graphics.Bitmap? {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

                var inSampleSize = 1
                val largestDim = maxOf(opts.outWidth, opts.outHeight)
                while (largestDim / inSampleSize > maxDimPx) {
                    inSampleSize *= 2
                }

                val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize.coerceAtLeast(1)
                    this.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            }
        }
    }
}
