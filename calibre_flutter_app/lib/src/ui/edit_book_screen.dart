import 'package:flutter/material.dart';
import '../data/models/relations/book_with_details.dart';
import '../data/metadata_service.dart';
import '../data/database_repository.dart';

class EditBookScreen extends StatefulWidget {
  final BookWithDetails bookDetails;

  const EditBookScreen({super.key, required this.bookDetails});

  @override
  State<EditBookScreen> createState() => _EditBookScreenState();
}

class _EditBookScreenState extends State<EditBookScreen> {
  late TextEditingController _titleController;
  late TextEditingController _authorsController;
  late TextEditingController _publisherController;
  late TextEditingController _commentsController;

  @override
  void initState() {
    super.initState();
    final book = widget.bookDetails.book;
    final authors = widget.bookDetails.authors.map((a) => a.name).join(', ');

    _titleController = TextEditingController(text: book.title);
    _authorsController = TextEditingController(text: authors);
    _publisherController = TextEditingController(text: book.publisher ?? '');
    _commentsController = TextEditingController(text: book.comments ?? '');
  }

  @override
  void dispose() {
    _titleController.dispose();
    _authorsController.dispose();
    _publisherController.dispose();
    _commentsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Edit Metadata'),
        actions: [
          IconButton(
            icon: const Icon(Icons.save),
            onPressed: _handleSave,
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            TextFormField(
              controller: _titleController,
              decoration: const InputDecoration(labelText: 'Title'),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _authorsController,
              decoration: const InputDecoration(labelText: 'Authors (comma-separated)'),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _publisherController,
              decoration: const InputDecoration(labelText: 'Publisher'),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _commentsController,
              decoration: const InputDecoration(labelText: 'Comments'),
              maxLines: 5,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              icon: const Icon(Icons.cloud_download),
              label: const Text('Download Metadata'),
              onPressed: _handleDownloadMetadata,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _handleSave() async {
    final updatedBook = widget.bookDetails.book.copyWith(
      title: _titleController.text,
      publisher: _publisherController.text,
      comments: _commentsController.text,
    );

    final authorNames = _authorsController.text.split(',').map((e) => e.trim()).toList();

    await DatabaseRepository.instance.updateBook(updatedBook);
    await DatabaseRepository.instance.updateAuthorsForBook(updatedBook.id!, authorNames);

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Changes saved!')));
      Navigator.of(context).pop();
    }
  }

  Future<void> _handleDownloadMetadata() async {
    final service = MetadataService();
    final currentBook = widget.bookDetails.book;
    final currentAuthors = widget.bookDetails.authors.map((a) => a.name).join(', ');

    // Show a loading indicator
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Downloading...')));

    try {
      final fetchedBook = await service.fetchMetadata(
        title: currentBook.title,
        author: currentAuthors,
      );

      if (fetchedBook != null && mounted) {
        setState(() {
          _titleController.text = fetchedBook.title;
          _authorsController.text = fetchedBook.authorSort ?? ''; // Simplified for now
          _publisherController.text = fetchedBook.publisher ?? '';
          _commentsController.text = fetchedBook.comments ?? '';
        });
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Metadata updated!')));
      } else {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('No metadata found.')));
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
    }
  }
}
