import 'package:flutter/material.dart';
import '../data/models/relations/book_with_details.dart';

class BookDetailsScreen extends StatelessWidget {
  final BookWithDetails bookDetails;

  const BookDetailsScreen({super.key, required this.bookDetails});

  @override
  Widget build(BuildContext context) {
    final book = bookDetails.book;
    final authors = bookDetails.authors.map((a) => a.name).join(', ');
    final tags = bookDetails.tags.map((t) => t.name).join(', ');

    return Scaffold(
      appBar: AppBar(
        title: Text(book.title),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(
                book.title,
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 8),
              Text(
                'by ${authors.isNotEmpty ? authors : 'Unknown Author'}',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 16),
              const Divider(),
              const SizedBox(height: 16),
              _buildDetailRow('Path', book.path),
              _buildDetailRow('ISBN', book.isbn ?? 'N/A'),
              _buildDetailRow('Tags', tags.isNotEmpty ? tags : 'N/A'),
              // Add more details as needed
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '$label: ',
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
          Expanded(
            child: Text(value),
          ),
        ],
      ),
    );
  }
}
