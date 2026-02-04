# Completed Features Summary

> **Reference:** See the source-of-truth note in `CONVERSION_STATUS.md` for verified status details.

## Core Library & Metadata
- JSON-backed library storage and metadata management
- Import/export of library metadata
- Reading progress, bookmarks, and basic reading statistics
- Basic Calibre `metadata.db` import (metadata only)

## Conversion Engine
- Plugin-based conversion pipeline with OEB intermediate model
- CSS processing/flattening helpers
- PalmDoc compression helper

### Input Formats (Implemented, some simplified)
- EPUB, MOBI/AZW, AZW3
- TXT, DOCX, RTF, FB2
- HTML/OPF, HTMLZ
- ODT, TCR, PML
- Comic archives (CBZ/CBR)
- LIT, LRF, CHM, DJVU, PDB (simplified parsers)

### Output Formats (Implemented, some simplified)
- EPUB, HTML, TXT, FB2, DOCX, RTF
- OEB, HTMLZ, PML, TCR
- MOBI output (simplified)
- PDF output (OpenHTMLToPDF when available; basic fallback)
- LIT/LRF/PDB/RB/SNB outputs (present but simplified)

## Search
- Metadata search (title/author/tags/series) + advanced filter search
- Full-text search engine (in-memory; limited text extraction)

## News Fetching (Basic)
- RSS/Atom parsing and fetcher
- Recipe interface + OEB book generation

## Server & Connectivity (Basic)
- HTTP content server (library browse + downloads)
- OPDS 1.2 feed generation with pagination and metadata search
- In-memory authentication manager
- Standalone WebSocket server for library events (not wired into HTTP server)

## Media Library / Plex-like Server (Experimental)
- `MediaLibrary` and `MediaServer` prototypes in `shared`
- Not yet integrated into desktop/Android apps

## User Interfaces
- CLI for library management, conversion, and server startup
- Desktop Swing UI for library browsing and conversion
- Android app with library list, reader, import, and reading progress

## Utilities
- Basic logging utility with log levels and optional file output
- Web content downloaders in `shared/web` (web-to-epub, webcomics, Reddit fiction)

## Device Integration (Prototype)
- Device driver scaffolding (Kindle/Kobo/MTP)
