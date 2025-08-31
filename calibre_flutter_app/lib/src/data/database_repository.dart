import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import 'models/book.dart';
import 'models/author.dart';
import 'models/tag.dart';
import 'models/publisher.dart';
import 'models/series.dart';
import 'models/relations/book_with_details.dart';

class DatabaseRepository {
  static final DatabaseRepository instance = DatabaseRepository._init();
  static Database? _database;

  DatabaseRepository._init();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDB('calibre_app.db');
    return _database!;
  }

  Future<Database> _initDB(String filePath) async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, filePath);

    return await openDatabase(path, version: 1, onCreate: _createDB);
  }

  Future _createDB(Database db, int version) async {
    await db.execute('''
      CREATE TABLE books (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        sort_title TEXT,
        last_modified INTEGER NOT NULL,
        publication_date INTEGER NOT NULL,
        series_index REAL NOT NULL DEFAULT 1.0,
        author_sort TEXT,
        isbn TEXT,
        uuid TEXT,
        has_cover INTEGER NOT NULL DEFAULT 0,
        path TEXT NOT NULL,
        publisher TEXT,
        comments TEXT,
        series_id INTEGER,
        FOREIGN KEY (series_id) REFERENCES series (id) ON DELETE SET NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE authors (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE,
        sort_name TEXT
      )
    ''');
    await db.execute('''
      CREATE TABLE tags (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE
      )
    ''');
    await db.execute('''
      CREATE TABLE publishers (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE
      )
    ''');
    await db.execute('''
      CREATE TABLE series (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE
      )
    ''');
    await db.execute('''
      CREATE TABLE book_author_links (
        book_id INTEGER NOT NULL,
        author_id INTEGER NOT NULL,
        PRIMARY KEY (book_id, author_id),
        FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
        FOREIGN KEY (author_id) REFERENCES authors (id) ON DELETE CASCADE
      )
    ''');
    await db.execute('''
      CREATE TABLE book_tag_links (
        book_id INTEGER NOT NULL,
        tag_id INTEGER NOT NULL,
        PRIMARY KEY (book_id, tag_id),
        FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
        FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
      )
    ''');
    await db.execute('''
      CREATE TABLE book_publisher_links (
        book_id INTEGER NOT NULL,
        publisher_id INTEGER NOT NULL,
        PRIMARY KEY (book_id, publisher_id),
        FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
        FOREIGN KEY (publisher_id) REFERENCES publishers (id) ON DELETE CASCADE
      )
    ''');
  }

  // --- Insert Methods ---
  Future<int> insertBook(Book book) async {
    final db = await instance.database;
    return await db.insert('books', book.toMap());
  }

  Future<int> insertAuthor(Author author) async {
    final db = await instance.database;
    return await db.insert('authors', author.toMap(), conflictAlgorithm: ConflictAlgorithm.ignore);
  }

  Future<int> insertTag(Tag tag) async {
    final db = await instance.database;
    return await db.insert('tags', tag.toMap(), conflictAlgorithm: ConflictAlgorithm.ignore);
  }

  Future<int> insertPublisher(Publisher publisher) async {
    final db = await instance.database;
    return await db.insert('publishers', publisher.toMap(), conflictAlgorithm: ConflictAlgorithm.ignore);
  }

  Future<int> insertSeries(Series series) async {
    final db = await instance.database;
    return await db.insert('series', series.toMap(), conflictAlgorithm: ConflictAlgorithm.ignore);
  }

  Future<void> linkBookToAuthor(int bookId, int authorId) async {
    final db = await instance.database;
    await db.insert('book_author_links', {'book_id': bookId, 'author_id': authorId});
  }

  Future<void> linkBookToTag(int bookId, int tagId) async {
    final db = await instance.database;
    await db.insert('book_tag_links', {'book_id': bookId, 'tag_id': tagId});
  }

  Future<void> linkBookToPublisher(int bookId, int publisherId) async {
    final db = await instance.database;
    await db.insert('book_publisher_links', {'book_id': bookId, 'publisher_id': publisherId});
  }

  // --- Query Methods ---
  Future<Book?> getBookById(int id) async {
    final db = await instance.database;
    final maps = await db.query('books', where: 'id = ?', whereArgs: [id]);
    if (maps.isNotEmpty) {
      return Book.fromMap(maps.first);
    }
    return null;
  }

  Future<List<int>> searchBooks(String query) async {
    final db = await instance.database;
    final sanitizedQuery = '%${query.replaceAll("'", "''")}%';
    final List<Map<String, dynamic>> maps = await db.rawQuery('''
      SELECT DISTINCT b.id FROM books b
      LEFT JOIN book_author_links bal ON b.id = bal.book_id
      LEFT JOIN authors a ON a.id = bal.author_id
      WHERE b.title LIKE ? OR a.name LIKE ?
    ''', [sanitizedQuery, sanitizedQuery]);
    if (maps.isNotEmpty) {
      return maps.map((map) => map['id'] as int).toList();
    }
    return [];
  }

  Future<List<Book>> getAllBooks() async {
    final db = await instance.database;
    final result = await db.query('books', orderBy: 'sort_title ASC');
    return result.map((json) => Book.fromMap(json)).toList();
  }

  Future<List<BookWithDetails>> getBooksWithDetails() async {
    final db = await instance.database;
    final bookMaps = await db.query('books', orderBy: 'sort_title ASC');
    List<BookWithDetails> results = [];
    for (var bookMap in bookMaps) {
      final book = Book.fromMap(bookMap);
      final authorMaps = await db.rawQuery('SELECT a.* FROM authors a INNER JOIN book_author_links l ON a.id = l.author_id WHERE l.book_id = ?', [book.id]);
      final authors = authorMaps.map((map) => Author.fromMap(map)).toList();
      final tagMaps = await db.rawQuery('SELECT t.* FROM tags t INNER JOIN book_tag_links l ON t.id = l.tag_id WHERE l.book_id = ?', [book.id]);
      final tags = tagMaps.map((map) => Tag.fromMap(map)).toList();
      results.add(BookWithDetails(book: book, authors: authors, tags: tags));
    }
    return results;
  }

  // --- Update Methods ---
  Future<int> updateBook(Book book) async {
    final db = await instance.database;
    return await db.update('books', book.toMap(), where: 'id = ?', whereArgs: [book.id]);
  }

  Future<void> updateAuthorsForBook(int bookId, List<String> authorNames) async {
    final db = await instance.database;
    await db.delete('book_author_links', where: 'book_id = ?', whereArgs: [bookId]);
    for (final name in authorNames) {
      if (name.trim().isEmpty) continue;
      int authorId;
      final existingAuthors = await db.query('authors', where: 'name = ?', whereArgs: [name.trim()]);
      if (existingAuthors.isNotEmpty) {
        authorId = existingAuthors.first['id'] as int;
      } else {
        authorId = await db.insert('authors', {'name': name.trim(), 'sort_name': name.trim()});
      }
      await linkBookToAuthor(bookId, authorId);
    }
  }

  Future<void> updateTagsForBook(int bookId, List<String> tagNames) async {
    final db = await instance.database;
    await db.delete('book_tag_links', where: 'book_id = ?', whereArgs: [bookId]);
    for (final name in tagNames) {
      if (name.trim().isEmpty) continue;
      int tagId;
      final existingTags = await db.query('tags', where: 'name = ?', whereArgs: [name.trim()]);
      if (existingTags.isNotEmpty) {
        tagId = existingTags.first['id'] as int;
      } else {
        tagId = await db.insert('tags', {'name': name.trim()});
      }
      await linkBookToTag(bookId, tagId);
    }
  }

  Future<void> close() async {
    final db = await instance.database;
    _database = null;
    db.close();
  }
}
