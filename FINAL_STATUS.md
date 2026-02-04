# Calibre Kotlin Conversion - Status Snapshot

> **Reference:** See the source-of-truth note in `CONVERSION_STATUS.md` for verified status details.

## Overall Status

The Kotlin conversion is **functional for core ebook management and conversion**, but several major systems remain partial or experimental (FTS persistence, news scheduling, media server integration, format fidelity for complex formats).

## Implemented (Verified)

- JSON-backed library management with metadata, reading progress, bookmarks
- Conversion pipeline with OEB model and a wide set of input/output plugins (some simplified)
- Basic HTTP content server with OPDS 1.2 feed generation
- RSS/Atom news fetching (basic)
- Simple logging utility
- Desktop Swing UI, CLI, and Android reader app (basic feature set)

## Partial / Experimental

- Full-text search (in-memory only, limited extractors)
- News fetching (no scheduler or recipe library management)
- MediaLibrary + MediaServer prototypes (not integrated)
- Complex format fidelity (PDF input placeholder, MOBI output simplified)
- WebSocket server not wired into HTTP server

## Next Focus Areas

1. Persist FTS indexing and add UI/CLI integration
2. Expand news fetching into a managed, scheduled workflow
3. Decide on media server direction (integrate vs. remove prototype)
4. Improve PDF input and MOBI output fidelity
5. Expand test coverage and packaging
