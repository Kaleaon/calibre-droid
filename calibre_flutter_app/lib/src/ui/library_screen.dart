import 'dart:io';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;
import '../data/database_repository.dart';
import '../data/models/relations/book_with_details.dart';
import '../data/preference_service.dart';
import 'book_details_screen.dart';

class LibraryScreen extends StatefulWidget {
  const LibraryScreen({super.key});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  late Future<List<BookWithDetails>> _booksFuture;
  String? _libraryPath;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    // Load the library path first
    _libraryPath = await PreferenceService().getLibraryPath();
    // Then fetch the books
    setState(() {
      _booksFuture = DatabaseRepository.instance.getBooksWithDetails();
    });
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

                Widget leadingWidget = const Icon(Icons.book); // Placeholder
                if (_libraryPath != null && book.path.isNotEmpty) {
                  final coverPath = p.join(_libraryPath!, book.path, 'cover.jpg');
                  final coverFile = File(coverPath);
                  if (coverFile.existsSync()) {
                    leadingWidget = Image.file(
                      coverFile,
                      width: 50,
                      height: 70,
                      fit: BoxFit.cover,
                    );
                  }
                }

                return ListTile(
                  leading: leadingWidget,
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
