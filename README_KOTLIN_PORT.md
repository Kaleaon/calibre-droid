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

### 2. Device Management
- **Driver Interface**: `DeviceDriver` abstraction for syncing with e-readers.
- **Local Folder Driver**: Syncs books to a local directory (simulating a mounted device).

### 3. Library Management
- **Persistent Database**: JSON-based metadata storage.
- **File Management**: Organized storage of book files.
- **Search**: Advanced search (field:value).

### 4. User Interfaces
- **CLI**: Robust command-line interface for headless operations and scripting.
- **Web Server**: Embedded HTTP server for browsing and downloading books.
- **Desktop GUI**: Java Swing-based interface with Book List, Add, Remove, and Convert actions (auto-detects headless environment).
- **Android App**: Modern Android UI with RecyclerView, details screen, and tablet optimization.

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

# Convert a book
../tools/gradle-8.5/bin/gradle run --args="convert 1 html"

# Sync to Device
mkdir my_device
../tools/gradle-8.5/bin/gradle run --args="device my_device sync 1"

# Start Web Server
../tools/gradle-8.5/bin/gradle run --args="server 8080"
```

### GUI
```bash
# Launch Desktop GUI (requires X11)
../tools/gradle-8.5/bin/gradle run --args="gui"
```

## Android Build
See `android_app/README.md` for Android-specific instructions.
