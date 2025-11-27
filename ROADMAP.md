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

## Phase 2: Format Expansion & Fidelity (COMPLETED)
- [x] **MOBI Input**: PalmDB/MOBI header parsing, text extraction.
- [x] **MOBI Images**: Image extraction from MOBI records (JPEG, PNG, GIF).
- [x] **Conversion Engine**:
    - [x] `InputPlugin` / `OutputPlugin` interfaces.
    - [x] `EpubInput`, `MobiInput`, `Azw3Input`, `TextInput`, **`DocxInput`**, **`RtfInput`**.
    - [x] `TextOutput`, `HtmlOutput`, **`EpubOutput`**.
    - [x] **OEB Improvements**: Enhanced `OebBook` to handle CSS, Images properly.
    - [x] **CSS Flattening**: Implemented CSS processing via `CssProcessor`.

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
- [x] **Android App**:
    - [x] Basic Project Setup.
    - [x] Shared Core Logic integration.
    - [x] **Library UI**: RecyclerView with cover images.
    - [x] **Reader**: WebView-based reader using `HtmlOutput` logic.
    - [x] **Import**: SAF (Storage Access Framework) integration.

## Phase 5: The Editor (Future)
- [ ] **Tweak Book**: Unzip EPUB, edit HTML/CSS, repack.
- [ ] **Preview**: Live preview of edits.

## Phase 6: Final Polish (IN PROGRESS)
- [x] **Testing**: Basic unit tests for Library operations.
- [x] **Error Handling**: Comprehensive user-friendly error messages.
- [x] **Advanced Features**:
    - [x] Reading Progress Tracking
    - [x] Bookmarks & Annotations
    - [x] Advanced Search with Filters
    - [x] Reading Statistics
    - [x] Batch Operations
    - [x] Reading Settings (Theme, Font, Margins)
    - [x] Library Export/Import
    - [x] Collections/Tags Management UI
    - [x] Cover Image Caching
    - [x] Lazy Loading for Performance
- [x] **Format Expansion**: DOCX, RTF input support.
- [x] **Desktop GUI Enhancements**: Progress indicators, statistics view.
- [ ] **I18n**: Complete translations (ES, DE, FR, etc.).
- [ ] **Packaging**: Native installers (MSI, DMG, APK).
