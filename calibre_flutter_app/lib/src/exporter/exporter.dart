import 'dart:io';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
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

      // Get the app's documents directory to find the source files.
      final appDocsDir = await getApplicationDocumentsDirectory();

      await exportDb.transaction((txn) async {
        // Step 1: Create the complete legacy Calibre schema.
        await _createCalibreSchema(txn);

        // Step 2: Fetch data from the app's repository.
        final booksWithDetails = await appDbRepository.getBooksWithDetails();

        // Step 3: Insert data and copy files.
        for (final bookDetails in booksWithDetails) {
          final book = bookDetails.book;

          // Insert book metadata into the new DB
          await txn.insert('books', book.toMap());

          // ... Here you would insert authors, tags, and all link table entries ...

          // Copy the book's files
          if (book.path.isNotEmpty) {
            final sourceDir = Directory(p.join(appDocsDir.path, book.path));
            // Note: The outputPath from the file picker is the DB file itself.
            // We need to create the book folder relative to its parent directory.
            final exportRoot = Directory(p.dirname(outputPath));
            final destDir = Directory(p.join(exportRoot.path, book.path));

            if (!await destDir.exists()) {
              await destDir.create(recursive: true);
            }

            if (await sourceDir.exists()) {
              await for (final file in sourceDir.list()) {
                if (file is File) {
                  final newPath = p.join(destDir.path, p.basename(file.path));
                  await file.copy(newPath);
                }
              }
            }
          }
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
