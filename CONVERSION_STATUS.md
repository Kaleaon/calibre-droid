# Calibre Python to Kotlin Conversion Status

> **Source of truth:** This file is the authoritative status record for feature implementation and conversion progress. Other status documents (REMAINING_WORK.md, ROADMAP.md, FINAL_STATUS.md, COMPLETED_FEATURES.md) should reference this note to avoid drift.

## Scope

This status reflects verified code under `shared/`, `kotlin_app/`, and `android_app/`.

## Conversion Infrastructure (Implemented)

- Plugin interfaces (`InputPlugin`, `OutputPlugin`) and conversion pipeline
- OEB intermediate data model
- CSS processing / flattening helpers
- PalmDoc compression helpers

## Input Plugins (Current State)

**Implemented (usable, though some parsers are simplified):**
- EPUB, MOBI/AZW, AZW3, TXT, DOCX, RTF, FB2
- HTML/OPF, HTMLZ
- ODT, TCR, PML
- Comic inputs (CBZ/CBR)
- LIT, LRF, CHM, DJVU, PDB

**Placeholders / incomplete:**
- PDF input (explicit placeholder; requires PDF parsing library integration)
- AZW4, RB, SNB inputs (not fully implemented)
- Recipe input (delegated to news subsystem, not a real converter)

> Note: Several complex formats (LIT/LRF/CHM/DJVU/PDB/CBR) are implemented with simplified parsers and documented limitations in code comments.

## Output Plugins (Current State)

**Implemented (usable, though some are simplified):**
- EPUB, HTML, TXT, FB2, DOCX, RTF, OEB, HTMLZ, PML, TCR
- MOBI output (simplified; not full spec)
- PDF output (uses OpenHTMLToPDF when available; fallback is basic PDFBox text output)
- LIT/LRF/PDB/RB/SNB outputs (present but simplified)

## Feature Inventory (Verified)

### Library & Metadata
- JSON-backed library in `shared` with import/export
- Optional SQLite-backed library (`SqliteLibrary`) and a basic Calibre `metadata.db` import service

### Search
- Metadata search and advanced filter search
- Full-text search exists as an in-memory index with limited text extraction (HTML/EPUB/TXT). No persistence, no UI integration.

### News Fetching
- RSS/Atom parser, recipe interface, and a fetcher that builds an OEB book
- No scheduler, recipe library management, or UI integration

### Logging
- Simple logging utility with levels and optional file output
- No log rotation or structured log export

### Server
- Basic HTTP content server (library browse + OPDS) in `kotlin_app`
- OPDS 1.2 feed generation with pagination and metadata search
- Authentication manager (in-memory users + sessions)
- Standalone WebSocket server for library events (not wired into HTTP server)

### Media Library / Plex-like Server (Experimental)
- `MediaLibrary` + `MediaServer` exist in `shared` as prototypes
- Not wired into CLI/desktop/Android apps and not production-ready

### UI
- Desktop: basic Swing UI and CLI for library/conversion tasks
- Android: basic library list, reader, import, and reading progress

## Python Source Status

Original Python sources remain in `src/`; this repo is not “Python deleted after conversion.”

## Next Steps (High Level)

- Persist FTS index and expose it in UI/CLI
- Harden news fetching (recipe library, scheduling, error reporting)
- Decide on media server integration or remove experimental prototypes
- Improve test coverage and performance profiling
