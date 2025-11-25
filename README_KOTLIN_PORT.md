# Calibre Kotlin Port

This is a comprehensive port of the Calibre ebook management software to Kotlin, designed for cross-platform compatibility (Desktop & Android).

## Features Implemented

### Core Library
- **Metadata Management**: Title, Author, Tags, Series, Publisher, Description.
- **Storage**: JSON-based metadata persistence + file-based book storage.
- **Database Import**: Import existing Calibre `metadata.db` (SQLite).
- **Format Support**:
  - **EPUB**: Full metadata extraction and content processing.
  - **PDF**: Metadata extraction (using PDFBox).
  - **MOBI**: Metadata extraction and Text content extraction (PalmDoc decompression).

### Conversion Engine
- **Pipeline**: Modular Input/Output plugin system.
- **Inputs**: EPUB, MOBI, AZW3.
- **Outputs**: Text, HTML (Single File).
- **Architecture**: Uses `OebBook` intermediate representation (similar to Calibre's OEB).

### User Interfaces
1.  **CLI (Command Line)**:
    - `add`: Import books (auto-detects format).
    - `list`, `search`: Manage library.
    - `convert`: Convert between formats.
    - `server`: Start content server.
    - `opds`: Test OPDS feed.
    - `device`: Sync to local folders.
    - `fetch-meta`: Download metadata from Google Books.

2.  **Desktop GUI (Java Swing)**:
    - Visual library manager.
    - Integrated Viewer (HTML-based).
    - Online Metadata Download dialog.
    - Internationalization (English/Spanish).

3.  **Content Server**:
    - HTTP Server for browsing library.
    - **OPDS Feed**: Standard feed for ebook reader apps (Moon+ Reader, etc.).
    - Direct downloads.

4.  **Android App (In Progress)**:
    - Shared Core Logic.
    - Basic Reader Activity.

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
./gradlew :kotlin_app:run --args="server 8080"
```

### Run GUI
```bash
./gradlew :kotlin_app:run --args="gui"
```
