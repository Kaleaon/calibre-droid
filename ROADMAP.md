# Calibre Kotlin Conversion Roadmap

## Phase 1: Core Architecture & Metadata (COMPLETED)
- [x] **Project Setup**: Kotlin Multiplatform structure (Desktop/Android/Shared).
- [x] **Data Model**: `Metadata` class, `Library` management.
- [x] **Storage**: JSON persistence (`library.json`), file management.
- [x] **Database Import**: Read Calibre `metadata.db` (SQLite).
- [x] **Metadata Parsing**:
    - [x] EPUB (using `java.util.zip` + `javax.xml`).
    - [x] PDF (using Apache PDFBox for Desktop).
    - [x] MOBI (Basic header parsing + PalmDoc decompression).
- [x] **Online Metadata**: Fetch from Google Books API.

## Phase 2: Format Expansion & Fidelity (IN PROGRESS)
- [x] **MOBI Input**: PalmDB/MOBI header parsing, text extraction.
- [ ] **MOBI Fidelity**: Handle images, full HTML reconstruction from records.
- [ ] **Conversion Engine**:
    - [x] `InputPlugin` / `OutputPlugin` interfaces.
    - [x] `EpubInput`, `MobiInput`.
    - [x] `TextOutput`, `HtmlOutput`.
    - [ ] **OEB Improvements**: Enhance `OebBook` to handle CSS, Images, TOC properly.
    - [ ] **CSS Flattening**: Implement CSS processing for simple readers.

## Phase 3: Server & Connectivity (IN PROGRESS)
- [x] **Content Server**:
    - [x] Basic HTTP Server (Java `HttpServer`).
    - [x] Browse Library (HTML).
    - [x] Download Books.
    - [x] **OPDS Feed**: Atom XML feed for external readers.
- [ ] **Device Integration**:
    - [x] Local Folder Sync.
    - [ ] MTP (Media Transfer Protocol) for Android devices (via USB).
    - [ ] Apple Device Support (placeholder).

## Phase 4: User Interfaces (IN PROGRESS)
- [x] **CLI**: Comprehensive command-line interface (add, list, search, convert, server, device, opds).
- [x] **Desktop GUI** (Swing):
    - [x] Book List.
    - [x] Add/Remove/Convert.
    - [x] Metadata Download.
    - [x] Simple Viewer (`JEditorPane`).
- [ ] **Android App**:
    - [x] Basic Project Setup.
    - [x] Shared Core Logic integration.
    - [ ] **Library UI**: RecyclerView with cover images.
    - [ ] **Reader**: WebView-based reader using `HtmlOutput` logic.
    - [ ] **Import**: SAF (Storage Access Framework) integration.

## Phase 5: The Editor (Future)
- [ ] **Tweak Book**: Unzip EPUB, edit HTML/CSS, repack.
- [ ] **Preview**: Live preview of edits.

## Phase 6: Final Polish (Future)
- [ ] **I18n**: Complete translations (ES, DE, FR, etc.).
- [ ] **Performance**: Lazy loading for large libraries.
- [ ] **Packaging**: Native installers (MSI, DMG, APK).
