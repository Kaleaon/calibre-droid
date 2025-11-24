package org.calibre.android

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.calibre.android.databinding.ActivityBookDetailBinding

class BookDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookDetailBinding
    private lateinit var library: AndroidLibrary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        library = AndroidLibrary(this)

        val bookId = intent.getIntExtra("book_id", -1)
        val book = library.getMetadata(bookId)

        if (book != null) {
            binding.detailTitle.text = book.title
            binding.detailAuthor.text = book.authors.joinToString(", ")
            if (book.series != null) {
                binding.detailSeries.text = "${book.series} #${book.seriesIndex}"
            }
            binding.detailDescription.text = book.comments ?: "No description available."
            
            binding.btnRead.setOnClickListener {
                Toast.makeText(this, "Read/Convert functionality coming soon", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Book not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
