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
        super.onCreate(savedInstanceState)
        binding = ActivityBookDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        library = (application as CalibreApplication).library

        bookId = intent.getIntExtra("book_id", -1)
        val book = library.getMetadata(bookId)

        if (book != null) {
            binding.detailTitle.text = book.title
            binding.detailAuthor.text = book.authors.joinToString(", ")
            if (book.series != null) {
                binding.detailSeries.text = "${book.series} #${book.seriesIndex}"
            }
            binding.detailDescription.text = book.comments ?: "No description available."
            
            binding.btnRead.setOnClickListener {
                val intent = Intent(this, ReaderActivity::class.java)
                intent.putExtra("book_id", bookId)
                startActivity(intent)
            }
        } else {
            Toast.makeText(this, "Book not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
