package org.calibre.android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.calibre.android.databinding.ActivityBookDetailBinding
import org.calibre.metadata.Library

class BookDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookDetailBinding
    private lateinit var library: Library
    private var bookId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        KThemeEngine.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityBookDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        library = (application as CalibreApplication).library

        bookId = intent.getIntExtra("book_id", -1)
        val book = library.getMetadata(bookId)

        if (book != null) {
            displayBookDetails(book)
            setupActions(book)
        } else {
            Toast.makeText(this, "Book not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun displayBookDetails(book: org.calibre.metadata.Metadata) {
        binding.detailTitle.text = book.title
        binding.detailAuthor.text = book.authors.joinToString(", ")
        if (book.series != null) {
            binding.detailSeries.text = "${book.series} #${book.seriesIndex}"
        } else {
            binding.detailSeries.text = ""
        }
        binding.detailDescription.text = book.comments ?: "No description available."
        
        // Show rating if available
        if (book.rating != null) {
            binding.detailRating?.text = "Rating: ${String.format("%.1f", book.rating)}/5.0"
        }
        
        // Show reading progress
        if (book.readingProgress.totalPages > 0) {
            val progress = book.readingProgress.progressPercent.toInt()
            binding.detailProgress?.text = "Progress: $progress% (${book.readingProgress.currentPage}/${book.readingProgress.totalPages} pages)"
        }
        
        // Show tags
        if (book.tags.isNotEmpty()) {
            binding.detailTags?.text = "Tags: ${book.tags.joinToString(", ")}"
        }
    }
    
    private fun setupActions(book: org.calibre.metadata.Metadata) {
        binding.btnRead.setOnClickListener {
            val intent = Intent(this, ReaderActivity::class.java)
            intent.putExtra("book_id", bookId)
            startActivity(intent)
        }
        
        // Add tag button
        binding.root.findViewById<android.widget.Button>(R.id.btn_add_tag)?.setOnClickListener {
            showAddTagDialog()
        }
        
        // Rating button
        binding.root.findViewById<android.widget.Button>(R.id.btn_rating)?.setOnClickListener {
            showRatingDialog(book.rating ?: 0.0)
        }
    }
    
    private fun showAddTagDialog() {
        val input = android.widget.EditText(this)
        android.app.AlertDialog.Builder(this)
            .setTitle("Add Tag")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val tag = input.text.toString().trim()
                if (tag.isNotEmpty()) {
                    library.addTag(bookId, tag)
                    refreshBookDetails()
                    Toast.makeText(this, "Tag added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showRatingDialog(currentRating: Double) {
        val ratings = arrayOf("0", "1", "2", "3", "4", "5")
        val currentIndex = currentRating.toInt().coerceIn(0, 5)
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Set Rating")
            .setSingleChoiceItems(ratings, currentIndex) { dialog, which ->
                val rating = ratings[which].toDouble()
                library.setRating(bookId, rating)
                refreshBookDetails()
                Toast.makeText(this, "Rating set to $rating", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun refreshBookDetails() {
        val book = library.getMetadata(bookId)
        if (book != null) {
            displayBookDetails(book)
        }
    }
    
    override fun onResume() {
        super.onResume()
        refreshBookDetails()
    }
}
