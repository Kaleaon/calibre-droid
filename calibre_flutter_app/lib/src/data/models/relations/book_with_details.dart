import '../author.dart';
import '../book.dart';
import '../tag.dart';

class BookWithDetails {
  final Book book;
  final List<Author> authors;
  final List<Tag> tags;

  BookWithDetails({
    required this.book,
    required this.authors,
    required this.tags,
  });
}
