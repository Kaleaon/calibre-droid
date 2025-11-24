# Calibre Kotlin Port

A complete Proof of Concept (PoC) porting core Calibre functionality to Kotlin.

## Features

### 1. Library Management
- **Add Books**: Import existing EPUB files or add metadata manually.
- **Storage**: Books are stored in a local file structure (`library_files/`).
- **Persistence**: Library metadata is persisted in `library.json`.
- **Remove**: Delete books from the library and disk.

### 2. Metadata Engine
- **Extraction**: Automatically extracts metadata (Title, Author, Series, Description, Tags) from EPUB files using a custom OPF parser.
- **Search**: 
  - Simple search (keyword matching).
  - Advanced search (`title:Start`, `author:John`, `tag:Fiction`).

### 3. Conversion Engine
- **EPUB to Text**: Converts EPUB files to plain text, stripping HTML tags while preserving basic structure (headers, paragraphs).

### 4. CLI Interface
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
```

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
*Output: Quick_Start_Guide.txt*

**Export:**
```bash
./gradlew run --args="export 1 /tmp/exported_books"
```

**Interactive Mode:**
```bash
./gradlew run
> help
> list
> exit
```
