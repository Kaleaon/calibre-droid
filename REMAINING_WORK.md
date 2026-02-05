# Remaining Work Assessment

## Critical Missing Features (High Priority)

### 1. **News Fetching System** (Major Calibre Feature)
- **Status**: Partial
- **Complexity**: High
- **Impact**: High - Core Calibre feature for RSS/website content
- **What's Needed**:
  - Recipe catalog/management (built-in + custom)
  - News download scheduling + UI/CLI entry points
  - Recipe collection management + persistence
  - Rich article extraction (readability, images)
  - Integration with conversion pipeline and library
- **Current State**: Basic RSS parser + `NewsFetcher` exist, but no scheduling, UI/CLI, or recipe repository integration.
  - **Files to Port**: `src/calibre/web/feeds/`, `src/calibre/web/fetch/`

### 2. **Full-Text Search (FTS)**
- **Status**: Partial
- **Complexity**: Medium
- **Impact**: High - Essential for large libraries
- **What's Needed**:
  - Persistent index storage (disk/DB-backed)
  - Robust text extraction for MOBI/PDF/EPUB
  - Query interface exposed in CLI/GUI
  - Search UI integration (snippets, ranking)
- **Current State**: In-memory index with limited extractors; no persistence or UI/CLI hooks.
  - **Files to Port**: `src/calibre/srv/fts.py`

### 3. **PDF Output Plugin**
- **Status**: Partial
- **Complexity**: High
- **Impact**: Medium - Common output format
- **What's Needed**:
  - PDF generation from OEB with consistent layout and pagination
  - Proper CSS support, fonts, and image placement
  - Metadata embedding
  - Reliable rendering dependency wiring
- **Current State**: HTML-to-PDF rendering via OpenHTMLToPDF (reflection) with PDFBox fallback; fidelity varies and dependencies are optional.
  - **Dependencies**: OpenHTMLToPDF + PDFBox

### 4. **MOBI Output Plugin**
- **Status**: Partial
- **Complexity**: Very High
- **Impact**: Medium - Kindle compatibility
- **What's Needed**:
  - Full MOBI/KF8 generation (KF8/AZW3)
  - Robust record/index generation and navigation
  - Image embedding and NCX/guide support
  - Optional Huff/CDIC compression
- **Current State**: Simplified MOBI writer with PalmDoc compression and minimal metadata; no KF8, images, or indexes.
  - **Files to Port**: `src/calibre/ebooks/mobi/writer8/`

### 5. **Enhanced MOBI Support**
- **Status**: Partial
- **Complexity**: High
- **Impact**: Medium
- **What's Needed**:
  - Wire Huff/CDIC decompression into standard MOBI text extraction
  - KF8 (AZW3) full support
  - Better HTML reconstruction from MOBI records
- **Current State**: Huff/CDIC decompressor exists, but main extraction path still returns a warning for Huff/CDIC content.
  - **Files to Port**: `src/calibre/ebooks/mobi/huffcdic.py`

## Additional Format Support (Medium Priority)

### 6. **Placeholder Conversion Formats (Not Implemented)**
- **Status**: Not Started
- **Complexity**: Medium (varies by format)
- **Impact**: Low-Medium (legacy/proprietary formats)
- **What's Needed**:
  - **AZW4 input**: PDF-based print replica parsing
    - **Priority**: Medium
    - **Dependencies**: PDF parsing library (PDFBox/iText) + AZW4 container parsing
  - **RocketBook (RB) input/output**: Proprietary parser and writer
    - **Priority**: Low
    - **Dependencies**: RocketBook format specification, binary parser/serializer
  - **SNB input/output**: Shanda Bambook parser and writer
    - **Priority**: Low
    - **Dependencies**: SNB format specification, compression/decompression routines
  - **CBR input**: RAR archive extraction for comic pages
    - **Priority**: Medium
    - **Dependencies**: RAR extraction library (e.g., junrar or unrar bindings)

### 7. **FB2 (FictionBook) Support**
- **Status**: Not Started
- **Complexity**: Medium
- **Impact**: Low-Medium (popular in Russia/Eastern Europe)
- **Files to Port**: `src/calibre/ebooks/fb2/`

### 8. **HTMLZ Input/Output**
- **Status**: Not Started
- **Complexity**: Low
- **Impact**: Low
- **What's Needed**: Zipped HTML handling

### 9. **More Output Formats**
- **Status**: Not Started
- **Complexity**: Varies
- **Impact**: Low-Medium
- **Options**: LIT, LRF, PDB, etc.

## The Editor (Phase 5)

### 10. **Tweak Book Feature**
- **Status**: Not Started
- **Complexity**: High
- **Impact**: Medium - Popular Calibre feature
- **What's Needed**:
  - EPUB unpacking
  - HTML/CSS editor
  - Image management
  - Repacking
  - Validation

### 11. **Live Preview**
- **Status**: Not Started
- **Complexity**: Medium
- **Impact**: Low-Medium
- **What's Needed**: Real-time preview of edits

## Server Enhancements (Medium Priority)

### 12. **Authentication & User Management**
- **Status**: Not Started
- **Complexity**: Medium
- **Impact**: Medium - Required for remote access
- **What's Needed**:
  - User accounts
  - Password authentication
  - Permission system
- **Files to Port**: `src/calibre/srv/users.py`, `src/calibre/srv/auth.py`

### 13. **Enhanced OPDS**
- **Status**: Basic implementation
- **Complexity**: Low-Medium
- **Impact**: Low-Medium
- **What's Needed**:
  - OPDS 2.0 support
  - Search in OPDS
  - Pagination
  - Categories/collections

### 14. **WebSocket Support**
- **Status**: Not Started
- **Complexity**: Medium
- **Impact**: Low - For real-time updates
- **Files to Port**: `src/calibre/srv/web_socket.py`

## Device Integration (Low-Medium Priority)

### 15. **MTP Device Support**
- **Status**: Not Started
- **Complexity**: High
- **Impact**: Medium - Android device sync
- **What's Needed**: MTP library integration

### 16. **Better Device Detection**
- **Status**: Basic
- **Complexity**: Medium
- **Impact**: Low-Medium
- **What's Needed**: Auto-detect connected devices

## Production Readiness (High Priority)

### 17. **Comprehensive Testing**
- **Status**: Basic tests only
- **Complexity**: Medium
- **Impact**: High
- **What's Needed**:
  - Unit tests for all parsers
  - Integration tests for conversion pipeline
  - UI tests for Android
  - Performance tests

### 18. **Logging System**
- **Status**: Not Started
- **Complexity**: Low
- **Impact**: High - Essential for debugging
- **What's Needed**:
  - Centralized initialization + configuration
  - Log levels/formatting in all entry points
  - File rotation/retention
  - Error tracking/telemetry hooks
  - Consistent log destinations
  - **Current State**: Basic Logger exists with console/file output, but no rotation or wiring in app entry points.

### 19. **Performance for Large Libraries**
- **Status**: Basic lazy loading
- **Complexity**: Medium
- **Impact**: High
- **What's Needed**:
  - Database backend option (SQLite)
  - Pagination
  - Virtual scrolling
  - Index optimization

### 20. **I18n Expansion**
- **Status**: English/Spanish only
- **Complexity**: Low
- **Impact**: Medium
- **What's Needed**: More language files (DE, FR, IT, etc.)

### 21. **Packaging & Distribution**
- **Status**: Not Started
- **Complexity**: Medium
- **Impact**: High - Required for distribution
- **What's Needed**:
  - Windows installer (MSI/NSIS)
  - macOS app bundle (DMG)
  - Linux packages (DEB, RPM)
  - Android APK signing
  - App store submissions
  - **Current State**: Scripted build + jar packaging; no native installers or signing flow.

## Most urgent next steps
- Wire news fetching into CLI/GUI with recipe selection, scheduling, and storage.
- Persist the FTS index and expose a CLI/GUI search mode that uses it.
- Initialize logging at app startup with file rotation and configurable log levels.
- Add installer packaging (MSI/DMG/DEB/RPM) plus Android signing configuration.
- Improve MOBI/PDF output fidelity (KF8 support, images, robust HTML/CSS rendering).

## Code Quality (Ongoing)

### 22. **Code Cleanup**
- Unused variables (many warnings)
- Better error messages
- Code documentation
- API documentation

### 23. **Better Error Recovery**
- Graceful degradation
- Partial conversion support
- Better validation

### 6. Format Fidelity Improvements
- PDF input is a placeholder (needs PDF parsing integration).
- MOBI output is simplified (no KF8/Huff/CDIC, limited metadata handling).
- Several complex formats (LIT/LRF/CHM/DJVU/PDB) rely on simplified parsers.

### Core Functionality: **~85% Complete**
- ✅ Library management
- ✅ Basic conversion
- ✅ Reading features
- ✅ Android/Desktop UIs
- ⚠️ News fetching (partial)
- ⚠️ Full-text search (partial)
- ⚠️ PDF/MOBI output (partial)

### 8. Packaging / Distribution
- Desktop installers, Android release pipeline, and reproducible build docs.

### Production Readiness: **~60% Complete**
- ✅ Basic features work
- ✅ Error handling
- ❌ Comprehensive testing
- ⚠️ Logging (partial)
- ⚠️ Packaging (partial)

## Recommended Next Steps

1. Persist FTS index + wire into CLI/GUI search.
2. Stabilize news fetching (scheduler + recipe management).
3. Decide on media server direction (integrate or remove prototypes).
4. Improve PDF input + MOBI output fidelity.
5. Expand tests and CI coverage.
