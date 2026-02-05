# Calibre Kotlin Conversion Roadmap

> **Reference:** See the source-of-truth note in `CONVERSION_STATUS.md` for verified status details.

## Phase 2: Format Expansion & Fidelity (IN PROGRESS)
- [x] **MOBI Input**: PalmDB/MOBI header parsing, text extraction.
- [x] **MOBI Images**: Image extraction from MOBI records (JPEG, PNG, GIF).
- [x] **Conversion Engine**:
    - [x] `InputPlugin` / `OutputPlugin` interfaces.
    - [x] `EpubInput`, `MobiInput`, `Azw3Input`, `TextInput`, **`DocxInput`**, **`RtfInput`**, **`Fb2Input`**.
    - [x] `TextOutput`, `HtmlOutput`, **`EpubOutput`**
    - [ ] **`PdfOutput`** (basic fidelity; optional renderer dependency)
    - [ ] **`MobiOutput`** (basic fidelity; no KF8/images/indexes)
    - [x] **OEB Improvements**: Enhanced `OebBook` to handle CSS, Images properly.
    - [x] **CSS Flattening**: Implemented CSS processing via `CssProcessor`.

## Phase 2: Format Support & Conversion (Partial)
- [x] Conversion pipeline and OEB model
- [x] Core input/output plugins for common formats
- [ ] Improve fidelity for complex formats (PDF input, MOBI output, LIT/LRF/CHM/DJVU)
- [ ] Expand test coverage for format conversions

## Phase 3: Search & Discovery (Partial)
- [x] Metadata search and advanced filters
- [x] In-memory full-text indexing
- [ ] Persist FTS index and expose in UI/CLI
- [ ] Improve text extraction for MOBI/PDF

## Phase 4: Server & Connectivity (Partial)
- [x] Basic HTTP server and OPDS 1.2 feed
- [x] In-memory authentication manager
- [x] Standalone WebSocket server (not wired)
- [ ] OPDS 2.0 support + FTS integration
- [ ] Persistent user management
- [ ] Decide on MediaServer integration or deprecate prototype

## Phase 6: Final Polish (IN PROGRESS)
- [x] **Testing**: Comprehensive unit tests for parsers and Library operations.
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
    - [ ] **Full-Text Search (FTS)**: Content indexing and search (prototype only)
    - [x] **SQLite Backend**: Optional SQLite storage for large libraries
    - [ ] **Logging System**: Structured logging with file output (no app initialization/rotation)
    - [ ] **News Fetching**: RSS feed fetching and ebook generation (API-only; no scheduler/UI)
- [x] **Format Expansion**: DOCX, RTF, FB2 input support; PDF, MOBI output.
- [x] **Desktop GUI Enhancements**: Progress indicators, statistics view.
- [ ] **Packaging**: Build scripts for distribution (no native installers/signing).
- [ ] **I18n**: Complete translations (ES, DE, FR, etc.).
- [ ] **Native Installers**: MSI, DMG, APK packaging (requires platform-specific tools).
