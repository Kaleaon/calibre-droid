import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';
import '../data/database_repository.dart';
import '../data/models/book.dart';

class Importer {
  final String libraryDirectoryPath;
  final DatabaseRepository appDbRepository;

  Importer({required this.libraryDirectoryPath, required this.appDbRepository});

  Future<void> import() async {
    Database? legacyDb;
    try {
      // Construct the path to the metadata.db file.
      final legacyDbPath = p.join(libraryDirectoryPath, 'metadata.db');

      // Open the legacy database read-only.
      legacyDb = await openDatabase(legacyDbPath, readOnly: true);

      // Get the app's database.
      final appDb = await appDbRepository.database;

      // Use a transaction for the app's database for performance.
      await appDb.transaction((txn) async {
        // Import authors and links
        var legacyAuthors = await legacyDb!.rawQuery('SELECT id, name, sort FROM authors');
        for (var legacyAuthor in legacyAuthors) {
          await txn.insert('authors', {'id': legacyAuthor['id'], 'name': legacyAuthor['name'], 'sort_name': legacyAuthor['sort']}, conflictAlgorithm: ConflictAlgorithm.ignore);
        }
        var authorLinks = await legacyDb.rawQuery('SELECT book, author FROM books_authors_link');
        for (var link in authorLinks) {
          await txn.insert('book_author_links', {'book_id': link['book'], 'author_id': link['author']});
        }

        // Import tags and links
        var legacyTags = await legacyDb.rawQuery('SELECT id, name FROM tags');
        for (var legacyTag in legacyTags) {
          await txn.insert('tags', {'id': legacyTag['id'], 'name': legacyTag['name']}, conflictAlgorithm: ConflictAlgorithm.ignore);
        }
        var tagLinks = await legacyDb.rawQuery('SELECT book, tag FROM books_tags_link');
        for (var link in tagLinks) {
          await txn.insert('book_tag_links', {'book_id': link['book'], 'tag_id': link['tag']});
        }

        // Import publishers and links
        var legacyPublishers = await legacyDb.rawQuery('SELECT id, name FROM publishers');
        for (var legacyPublisher in legacyPublishers) {
            await txn.insert('publishers', {'id': legacyPublisher['id'], 'name': legacyPublisher['name']}, conflictAlgorithm: ConflictAlgorithm.ignore);
        }
        var publisherLinks = await legacyDb.rawQuery('SELECT book, publisher FROM books_publishers_link');
        for (var link in publisherLinks) {
            await txn.insert('book_publisher_links', {'book_id': link['book'], 'publisher_id': link['publisher']});
        }

        // Import series and link from books table
        var legacySeries = await legacyDb.rawQuery('SELECT id, name FROM series');
        for (var legacy in legacySeries) {
            await txn.insert('series', {'id': legacy['id'], 'name': legacy['name']}, conflictAlgorithm: ConflictAlgorithm.ignore);
        }

        // Import books (must be last to ensure foreign keys are present)
        var legacyBooks = await legacyDb.rawQuery('SELECT b.id, b.title, b.sort, b.path, b.has_cover, b.series_index, b.timestamp, b.pubdate, bsl.series as series_id FROM books b LEFT JOIN books_series_link bsl ON b.id = bsl.book');
        for (var legacyBook in legacyBooks) {
          final book = Book(
            id: legacyBook['id'] as int,
            title: legacyBook['title'] as String,
            sortTitle: legacyBook['sort'] as String?,
            path: legacyBook['path'] as String,
            hasCover: (legacyBook['has_cover'] as int) == 1,
            seriesIndex: legacyBook['series_index'] as double,
            seriesId: legacyBook['series_id'] as int?,
            lastModified: _parseDate(legacyBook['timestamp']),
            publicationDate: _parseDate(legacyBook['pubdate']),
          );
          await txn.insert('books', book.toMap());
        }
      });
    } finally {
      await legacyDb?.close();
    }
  }

  int _parseDate(dynamic dateValue) {
    if (dateValue is String) {
      try {
        return DateTime.parse(dateValue).millisecondsSinceEpoch;
      } catch (e) {
        // Fallback for invalid format
        return 0;
      }
    }
    // Return 0 or some other default if it's not a string
    return 0;
  }
}
