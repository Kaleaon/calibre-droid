# Calibre Kotlin Port

A comprehensive port of Calibre functionalities to Kotlin, including Desktop, Android, CLI, and Web Server.

## Project Structure

- **kotlin_app/**: The core project containing shared logic, desktop GUI, and CLI.
- **android_app/**: Android application project (requires Android SDK).

## Features Implemented

### 1. Conversion Pipeline
- **Architecture**: Pluggable `InputPlugin` -> `OebBook` -> `OutputPlugin` pipeline.
- **Input Formats**: EPUB (`EpubInput`), PDF (Metadata only).
- **Output Formats**: Text (`TextOutput`), HTML (`HtmlOutput`).
- **OEB Model**: Intermediate representation of books (Spine, Manifest, Metadata).

### 2. Connectivity & Metadata
- **Online Search**: Fetch metadata from **Google Books API**.
- **Architecture**: Extensible `MetadataSource` interface.

### 3. Device Management
- **Driver Interface**: `DeviceDriver` abstraction for syncing with e-readers.
- **Local Folder Driver**: Syncs books to a local directory.

### 4. Library Management
- **Persistent Database**: JSON-based metadata storage.
- **SQLite Import**: Import existing Calibre `metadata.db`.
- **File Management**: Organized storage of book files.
- **Search**: Advanced search (field:value).

### 5. User Interfaces
- **CLI**: Robust command-line interface.
- **Web Server**: Embedded HTTP server for browsing/downloading.
- **Desktop GUI**: Java Swing-based interface with:
  - **Internationalization**: English/Spanish support.
  - **Viewer**: Built-in HTML-based ebook viewer.
  - **Metadata Download**: Integrated online search dialog.
- **Android App**: Modern Android UI with RecyclerView and Reader Mode.

## Usage (Desktop/CLI)

### Build
```bash
cd kotlin_app
../tools/gradle-8.5/bin/gradle build
```

### CLI Commands
```bash
# Add a book
../tools/gradle-8.5/bin/gradle run --args="add ../resources/quick_start/eng.epub"

# Fetch Online Metadata
../tools/gradle-8.5/bin/gradle run --args="fetch-meta 'The Hobbit'"

# Convert a book
../tools/gradle-8.5/bin/gradle run --args="convert 1 html"

# Sync to Device
../tools/gradle-8.5/bin/gradle run --args="device my_device sync 1"

# Start Web Server
../tools/gradle-8.5/bin/gradle run --args="server 8080"
```

### GUI
```bash
# Launch Desktop GUI (requires X11)
../tools/gradle-8.5/bin/gradle run --args="gui"
```

## Future Roadmap
See `ROADMAP.md` for the detailed plan to reach feature parity with Calibre.
