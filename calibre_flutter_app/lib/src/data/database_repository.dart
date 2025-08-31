import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import 'models/book.dart';
import 'models/author.dart';
import 'models/tag.dart';
import 'models/publisher.dart';
import 'models/series.dart';

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
    // Note: Using TEXT for date/timestamp fields and storing as ISO 8601 strings
    // or INTEGER for unix timestamps. Using INTEGER for simplicity here.
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

    // Link tables for many-to-many relationships
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
    final maps = await db.query(
      'books',
      where: 'id = ?',
      whereArgs: [id],
    );

    if (maps.isNotEmpty) {
      return Book.fromMap(maps.first);
    } else {
      return null;
    }
  }

  // Note: A full implementation would have methods to get authors/tags for a book
  // and a comprehensive query builder. This is a simplified version.
  Future<List<Book>> getAllBooks() async {
    final db = await instance.database;
    final result = await db.query('books', orderBy: 'sort_title ASC');
    return result.map((json) => Book.fromMap(json)).toList();
  }

  Future<void> close() async {
    final db = await instance.database;
    _database = null;
    db.close();
  }
}
