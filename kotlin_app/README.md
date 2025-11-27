# Calibre Kotlin Port

A complete Proof of Concept (PoC) porting core Calibre functionality to Kotlin.

## Features

### 1. Library Management
- **Add Books**: Import existing EPUB and **PDF** files or add metadata manually.
- **Storage**: Books are stored in a local file structure (`library_files/`).
- **Persistence**: Library metadata is persisted in `library.json`.
- **Remove**: Delete books from the library and disk.

### 2. Metadata Engine
- **Epub Parser**: Automatically extracts metadata (Title, Author, Series, Description, Tags) from EPUB files.
- **Pdf Parser**: Extracts metadata (Title, Author, Keywords) from PDF files using Apache PDFBox.
- **Search**: 
  - Simple search (keyword matching).
  - Advanced search (`title:Start`, `author:John`, `tag:Fiction`).

### 3. Conversion Engine
- **EPUB to Text**: Converts EPUB files to plain text, stripping HTML tags while preserving basic structure (headers, paragraphs).

### 4. Content Server
- **HTTP Server**: A lightweight web server to browse the library and download books via a web browser.
- **Download**: Direct download links for all books in the library.

### 5. CLI Interface
Interactive and Argument-based modes supported.

## Usage

### Build
```bash
./gradlew build
```

### Commands

**Add a Book (Auto-Import):**
```bash
./gradlew run --args="add ../resources/quick_start/eng.epub"
./gradlew run --args="add my_document.pdf"
```

**Start Content Server:**
```bash
./gradlew run --args="server 8080"
```
Access at `http://localhost:8080`.

**List Books:**
```bash
./gradlew run --args="list"
```

**Search:**
```bash
./gradlew run --args="search author:Schember"
```

**Convert to Text:**
```bash
./gradlew run --args="convert 1 txt"
```

**Export:**
```bash
./gradlew run --args="export 1 /tmp/exported_books"
```
