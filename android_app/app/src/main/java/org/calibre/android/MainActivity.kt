package org.calibre.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.calibre.android.databinding.ActivityMainBinding
import org.calibre.metadata.Library
import org.calibre.metadata.Metadata
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var library: Library
    private lateinit var adapter: BookAdapter
    
    companion object {
        private const val REQUEST_CODE_IMPORT = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        library = (application as CalibreApplication).library

        setupRecyclerView()
        
        binding.fabAdd.setOnClickListener {
            openFilePicker()
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
                
                // Create temp file in app's cache
                val tempFile = File(cacheDir, "import_${System.currentTimeMillis()}.$extension")
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                
                // Import using Library
                val id = library.importBook(tempFile)
                
                // Clean up temp file
                tempFile.delete()
                
                Toast.makeText(this, "Book imported: ID $id", Toast.LENGTH_SHORT).show()
                refreshList()
            } ?: run {
                Toast.makeText(this, "Error: Could not open file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
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

        fun updateData(newBooks: List<Metadata>) {
            books = newBooks
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
            return BookViewHolder(view)
        }

        override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
            val book = books[position]
            holder.bind(book, library)
            holder.itemView.setOnClickListener { onItemClick(book) }
        }

        override fun getItemCount() = books.size

        class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleView: TextView = itemView.findViewById(R.id.book_title)
            private val authorView: TextView = itemView.findViewById(R.id.book_author)
            private val coverView: ImageView? = itemView.findViewById(R.id.book_cover)

            fun bind(book: Metadata, library: Library) {
                titleView.text = book.title
                authorView.text = book.authors.joinToString(", ")
                
                // Load cover image if available
                coverView?.let { imageView ->
                    val bookFile = library.getBookFile(book.id ?: 0)
                    if (bookFile != null) {
                        loadCoverImage(bookFile, imageView)
                    } else {
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            }
            
            private fun loadCoverImage(bookFile: File, imageView: ImageView) {
                try {
                    if (bookFile.extension.equals("epub", ignoreCase = true)) {
                        val coverBytes = org.calibre.metadata.CoverExtractor.extractCoverFromEpub(bookFile)
                        if (coverBytes != null) {
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                            imageView.setImageBitmap(bitmap)
                            return
                        }
                    }
                } catch (e: Exception) {
                    // Fall through to placeholder
                }
                imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }
    }
}
