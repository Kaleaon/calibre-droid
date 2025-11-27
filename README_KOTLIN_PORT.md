# Calibre Kotlin Port

This is a comprehensive port of the Calibre ebook management software to Kotlin, designed for cross-platform compatibility (Desktop & Android).

## Features Implemented

### Core Library
- **Metadata Management**: Title, Author, Tags, Series, Publisher, Description, Rating, Dates.
- **Storage**: JSON-based metadata persistence + file-based book storage.
- **Database Import**: Import existing Calibre `metadata.db` (SQLite).
- **Reading Features**:
  - **Progress Tracking**: Automatic page/position tracking with reading time.
  - **Bookmarks & Annotations**: Save positions with optional notes.
  - **Reading Statistics**: Total reading time, completion rates, average ratings.
- **Format Support**:
  - **EPUB**: Full metadata extraction, content processing, cover extraction.
  - **PDF**: Metadata extraction (using PDFBox).
  - **MOBI/AZW3**: Metadata extraction, text extraction (PalmDoc decompression), **image extraction**.
  - **DOCX**: Text extraction from Word documents.
  - **RTF**: Text extraction from Rich Text Format files.
  - **TXT**: Plain text file support.

### Conversion Engine
- **Pipeline**: Modular Input/Output plugin system.
- **Inputs**: EPUB, MOBI, AZW3, TXT, **DOCX**, **RTF**.
- **Outputs**: Text, HTML (Single File), **EPUB** (bidirectional conversion).
- **Features**:
  - **Image extraction**: From EPUB and MOBI files with proper embedding.
  - **CSS processing**: Flattening and aggregation for consistent rendering.
  - **Full OEB representation**: Complete manifest, spine, and asset management.
  - **Format coverage**: 6 input formats, 3 output formats.
- **Architecture**: Uses `OebBook` intermediate representation (similar to Calibre's OEB).

### User Interfaces
1.  **CLI (Command Line)**:
    - `add`: Import books (auto-detects format).
    - `list`, `search`: Manage library with advanced filters.
    - `convert`: Convert between formats (txt, html, epub).
    - `server`: Start content server.
    - `opds`: Test OPDS feed.
    - `device`: Sync to local folders.
    - `fetch-meta`: Download metadata from Google Books.
    - **Advanced Commands**:
      - `stats`: Show reading statistics.
      - `bookmark`: Add/list/remove bookmarks.
      - `rating`: Set book ratings (0-5).
      - `tag`: Manage tags (add/remove/list).
      - `batch`: Batch operations (remove/export multiple books).
      - `export-library`: Export entire library metadata.
      - `import-library`: Import library metadata.
      - `collections`: List all tags/collections.

2.  **Desktop GUI (Java Swing + JavaFX)**:
    - Visual library manager with book list.
    - **JavaFX WebView**: Modern HTML5/CSS3 capable viewer.
    - Online Metadata Download dialog.
    - Internationalization (English/Spanish).

3.  **Content Server**:
    - HTTP Server for browsing library.
    - **OPDS Feed**: Standard feed for ebook reader apps (Moon+ Reader, etc.).
    - Direct downloads with proper MIME types.

4.  **Android App (Full Featured)**:
    - **File Import**: SAF (Storage Access Framework) integration.
    - **Library UI**: RecyclerView with cover images and reading progress.
    - **Search**: Real-time search with SearchView.
    - **Reader**: WebView-based reader with:
      - Automatic reading progress tracking.
      - Bookmarks and annotations.
      - Reading settings (theme: light/dark/sepia, font, margins).
      - Reading time tracking.
    - **Statistics**: View reading statistics and recently read books.

## Architecture
- **Shared Module**: Core logic (Metadata, Conversion, Formats) shared between Desktop and Android.
- **Desktop App**: Swing GUI + JDBC Database + CLI.
- **Android App**: Native UI + WebView Reader (using shared conversion logic).

## Usage
### Build
```bash
# Desktop
./gradlew :kotlin_app:build

# Android (requires SDK)
./gradlew :android_app:assembleDebug
```

### Run CLI
```bash
./gradlew :kotlin_app:run --args="help"
./gradlew :kotlin_app:run --args="add mybook.epub"
./gradlew :kotlin_app:run --args="stats"
./gradlew :kotlin_app:run --args="search rating:4"
./gradlew :kotlin_app:run --args="convert 1 epub"
./gradlew :kotlin_app:run --args="server 8080"
```

### Run GUI
```bash
./gradlew :kotlin_app:run --args="gui"
```

## Advanced Features

### Reading Progress & Statistics
- Automatic tracking of reading position and time
- Reading statistics dashboard (total time, completion rates)
- Recently read books list
- Progress indicators in library view

### Bookmarks & Annotations
- Add bookmarks at any position
- Optional notes for bookmarks
- View all bookmarks for a book
- Bookmarks persist across sessions

### Advanced Search
- Field-specific search: `title:1984`, `author:Orwell`, `rating:4`, `read:true`
- Advanced search with multiple filters (title, author, series, tags, rating, read status)
- Multiple sort options (title, author, date, rating, progress)

### Batch Operations
- Batch remove multiple books
- Batch export to directory
- Efficient processing of multiple items

### Reading Settings
- **Themes**: Light, Dark, Sepia
- **Font**: Size and family customization
- **Margins**: Horizontal and vertical spacing
- **Line Height**: Adjustable for comfortable reading
- Settings persist across sessions

### Library Management
- **Export/Import**: Full library metadata export and import
- **Collections**: Tag-based organization system
- **Cover Caching**: Efficient cover image loading with caching
- **Lazy Loading**: Optimized for large libraries

### Format Support Expansion
- **DOCX**: Microsoft Word document conversion
- **RTF**: Rich Text Format support
- **MOBI Images**: Enhanced image extraction from MOBI records
- **EPUB Output**: Complete bidirectional EPUB conversion
