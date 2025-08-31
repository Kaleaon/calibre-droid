import 'package:flutter/material.dart';
import '../data/database_repository.dart';
import '../data/models/relations/book_with_details.dart';

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
  final _formKey = GlobalKey<FormState>();

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

  void _handleSave() async {
    if (_formKey.currentState!.validate()) {
      final bookId = widget.bookDetails.book.id!;

      final updatedBook = widget.bookDetails.book.copyWith(
        title: _titleController.text,
        publisher: _publisherController.text,
        comments: _commentsController.text,
      );

      final authorNames = _authorsController.text.split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();

      // In a real app, you'd have a similar field for tags.
      // final tagNames = _tagsController.text.split(',')...

      await DatabaseRepository.instance.updateBook(updatedBook);
      await DatabaseRepository.instance.updateAuthorsForBook(bookId, authorNames);
      // await DatabaseRepository.instance.updateTagsForBook(bookId, tagNames);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Metadata saved successfully!')),
        );
        Navigator.of(context).pop();
      }
    }
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
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TextFormField(
                controller: _titleController,
                decoration: const InputDecoration(
                  labelText: 'Title',
                  border: OutlineInputBorder(),
                ),
                validator: (value) => (value == null || value.isEmpty) ? 'Title cannot be empty' : null,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _authorsController,
                decoration: const InputDecoration(
                  labelText: 'Authors',
                  hintText: 'Separate authors with a comma',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _publisherController,
                decoration: const InputDecoration(
                  labelText: 'Publisher',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _commentsController,
                decoration: const InputDecoration(
                  labelText: 'Comments',
                  border: OutlineInputBorder(),
                ),
                maxLines: 8,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
