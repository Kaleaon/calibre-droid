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
  final TextEditingController _searchController = TextEditingController();
  List<BookWithDetails> _allBooks = [];
  List<BookWithDetails> _displayedBooks = [];
  bool _isLoading = true;
  String? _libraryPath;

  @override
  void initState() {
    super.initState();
    _loadData();
    _searchController.addListener(_onSearchChanged);
  }

  @override
  void dispose() {
    _searchController.removeListener(_onSearchChanged);
    _searchController.dispose();
    super.dispose();
  }

  void _onSearchChanged() async {
    final query = _searchController.text;
    if (query.isEmpty) {
      setState(() {
        _displayedBooks = _allBooks;
      });
      return;
    }

    // Perform search
    final matchingIds = await DatabaseRepository.instance.searchBooks(query);
    setState(() {
      _displayedBooks = _allBooks.where((bookDetails) => matchingIds.contains(bookDetails.book.id)).toList();
    });
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    _libraryPath = await PreferenceService().getLibraryPath();
    final books = await DatabaseRepository.instance.getBooksWithDetails();
    setState(() {
      _allBooks = books;
      _displayedBooks = books;
      _isLoading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Calibre Library'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                labelText: 'Search by Title or Author',
                suffixIcon: IconButton(
                  icon: const Icon(Icons.clear),
                  onPressed: () => _searchController.clear(),
                ),
              ),
            ),
          ),
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _displayedBooks.isEmpty
                    ? Center(child: Text(_searchController.text.isEmpty ? 'No books found in library.' : 'No results found.'))
                    : ListView.builder(
                        itemCount: _displayedBooks.length,
                        itemBuilder: (context, index) {
                          final bookWithDetails = _displayedBooks[index];
                          final book = bookWithDetails.book;
                          final authors = bookWithDetails.authors.map((a) => a.name).join(', ');

                          Widget leadingWidget = const Icon(Icons.book, size: 50);
                          if (_libraryPath != null && book.path.isNotEmpty) {
                            final coverPath = p.join(_libraryPath!, book.path, 'cover.jpg');
                            final coverFile = File(coverPath);
                            // Note: sync file access in build is not ideal, but ok for this simple case.
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
                      ),
          ),
        ],
      ),
    );
  }
}
