import 'package:flutter/material.dart';
import '../data/database_repository.dart';
import '../data/models/relations/book_with_details.dart';
import 'book_details_screen.dart';

class LibraryScreen extends StatefulWidget {
  const LibraryScreen({super.key});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  late Future<List<BookWithDetails>> _booksFuture;

  @override
  void initState() {
    super.initState();
    _booksFuture = DatabaseRepository.instance.getBooksWithDetails();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Calibre Library'),
      ),
      body: FutureBuilder<List<BookWithDetails>>(
        future: _booksFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          } else if (snapshot.hasError) {
            return Center(child: Text('Error: ${snapshot.error}'));
          } else if (!snapshot.hasData || snapshot.data!.isEmpty) {
            return const Center(child: Text('No books found in library.'));
          } else {
            final books = snapshot.data!;
            return ListView.builder(
              itemCount: books.length,
              itemBuilder: (context, index) {
                final bookWithDetails = books[index];
                final book = bookWithDetails.book;
                final authors = bookWithDetails.authors.map((a) => a.name).join(', ');

                return ListTile(
                  title: Text(book.title),
                  subtitle: Text(authors.isNotEmpty ? authors : 'Unknown Author'),
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => BookDetailsScreen(bookDetails: bookWithDetails),
                      ),
                    );
                  },
                );
              },
            );
          }
        },
      ),
    );
  }
}
