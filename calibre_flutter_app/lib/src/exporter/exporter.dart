import 'package:sqflite/sqflite.dart';
import '../data/database_repository.dart';

class Exporter {
  final String outputPath;
  final DatabaseRepository appDbRepository;

  Exporter({required this.outputPath, required this.appDbRepository});

  Future<void> export() async {
    Database? exportDb;
    try {
      exportDb = await openDatabase(outputPath);

      await exportDb.transaction((txn) async {
        // Step 1: Create the complete legacy Calibre schema.
        await _createCalibreSchema(txn);

        // Step 2: Fetch data from the app's repository.
        final books = await appDbRepository.getAllBooks();
        // In a full implementation, we'd also fetch authors, tags, etc.
        // This is a simplified example.

        // Step 3: Insert data into the new legacy database.
        for (final book in books) {
          await txn.rawInsert(
            'INSERT INTO books (id, title, sort, path, has_cover) VALUES (?, ?, ?, ?, ?)',
            [book.id, book.title, book.sortTitle, book.path, book.hasCover ? 1 : 0],
          );
          // ... and so on for all other tables and link tables.
        }
      });
    } finally {
      await exportDb?.close();
    }
  }

  Future<void> _createCalibreSchema(DatabaseExecutor db) async {
    // This is a small subset of the full schema for demonstration.
    // A full implementation would execute every statement from metadata_sqlite.sql.
    final schemaStatements = [
      "CREATE TABLE authors ( id   INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, sort TEXT COLLATE NOCASE, link TEXT NOT NULL DEFAULT '', UNIQUE(name) )",
      "CREATE TABLE books ( id      INTEGER PRIMARY KEY AUTOINCREMENT, title     TEXT NOT NULL DEFAULT 'Unknown' COLLATE NOCASE, sort      TEXT COLLATE NOCASE, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, pubdate   TIMESTAMP DEFAULT CURRENT_TIMESTAMP, series_index REAL NOT NULL DEFAULT 1.0, author_sort TEXT COLLATE NOCASE, isbn TEXT DEFAULT '' COLLATE NOCASE, lccn TEXT DEFAULT '' COLLATE NOCASE, path TEXT NOT NULL DEFAULT '', flags INTEGER NOT NULL DEFAULT 1, uuid TEXT, has_cover BOOL DEFAULT 0, last_modified TIMESTAMP NOT NULL DEFAULT '2000-01-01 00:00:00+00:00')",
      "CREATE TABLE books_authors_link ( id INTEGER PRIMARY KEY, book INTEGER NOT NULL, author INTEGER NOT NULL, UNIQUE(book, author) )",
      "CREATE TABLE publishers ( id   INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, sort TEXT COLLATE NOCASE, link TEXT NOT NULL DEFAULT '', UNIQUE(name) )",
      "CREATE TABLE books_publishers_link ( id INTEGER PRIMARY KEY, book INTEGER NOT NULL, publisher INTEGER NOT NULL, UNIQUE(book) )",
      "CREATE TABLE series ( id   INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, sort TEXT COLLATE NOCASE, link TEXT NOT NULL DEFAULT '', UNIQUE (name) )",
      "CREATE TABLE books_series_link ( id INTEGER PRIMARY KEY, book INTEGER NOT NULL, series INTEGER NOT NULL, UNIQUE(book) )",
      "CREATE TABLE tags ( id   INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, link TEXT NOT NULL DEFAULT '', UNIQUE (name) )",
      "CREATE TABLE books_tags_link ( id INTEGER PRIMARY KEY, book INTEGER NOT NULL, tag INTEGER NOT NULL, UNIQUE(book, tag) )",
      "CREATE TABLE comments ( id INTEGER PRIMARY KEY, book INTEGER NOT NULL, text TEXT NOT NULL COLLATE NOCASE, UNIQUE(book) )",
      "CREATE TABLE data ( id     INTEGER PRIMARY KEY, book   INTEGER NOT NULL, format TEXT NOT NULL COLLATE NOCASE, uncompressed_size INTEGER NOT NULL, name TEXT NOT NULL, UNIQUE(book, format) )",
      "CREATE TABLE identifiers  ( id     INTEGER PRIMARY KEY, book   INTEGER NOT NULL, type   TEXT NOT NULL DEFAULT 'isbn' COLLATE NOCASE, val    TEXT NOT NULL COLLATE NOCASE, UNIQUE(book, type) )",
      "CREATE TRIGGER books_delete_trg AFTER DELETE ON books BEGIN DELETE FROM books_authors_link WHERE book=OLD.id; DELETE FROM books_publishers_link WHERE book=OLD.id; DELETE FROM books_series_link WHERE book=OLD.id; DELETE FROM books_tags_link WHERE book=OLD.id; DELETE FROM data WHERE book=OLD.id; DELETE FROM comments WHERE book=OLD.id; DELETE FROM identifiers WHERE book=OLD.id; END",
    ];

    for (final statement in schemaStatements) {
      await db.execute(statement);
    }
  }
}
