package org.calibre.android

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.calibre.android.databinding.ActivityMainBinding
import org.calibre.metadata.Library
import org.calibre.metadata.Metadata

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var library: Library
    private lateinit var adapter: BookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        library = (application as CalibreApplication).library
        
        // Add dummy data if empty for testing
        if (library.getAllBooks().isEmpty()) {
            library.addBook(Metadata(title = "The Hitchhiker's Guide to the Galaxy", authors = mutableListOf("Douglas Adams")))
            library.addBook(Metadata(title = "1984", authors = mutableListOf("George Orwell")))
        }

        setupRecyclerView()
        
        binding.fabAdd.setOnClickListener {
            // In real app: Open file picker or add dialog
            library.addBook(Metadata(title = "New Book", authors = mutableListOf("Unknown Author")))
            refreshList()
        }
    }
    
    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun setupRecyclerView() {
        adapter = BookAdapter(library.getAllBooks()) { book ->
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
            holder.bind(book)
            holder.itemView.setOnClickListener { onItemClick(book) }
        }

        override fun getItemCount() = books.size

        class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleView: TextView = itemView.findViewById(R.id.book_title)
            private val authorView: TextView = itemView.findViewById(R.id.book_author)

            fun bind(book: Metadata) {
                titleView.text = book.title
                authorView.text = book.authors.joinToString(", ")
            }
        }
    }
}
