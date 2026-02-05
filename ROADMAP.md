# Calibre Kotlin Conversion Roadmap

> **Reference:** See the source-of-truth note in `CONVERSION_STATUS.md` for verified status details.

## Phase 1: Core Architecture & Metadata (Mostly Complete)
- [x] Kotlin multi-module layout (shared, desktop, Android)
- [x] JSON library storage (`Library`)
- [x] Basic metadata parsing (EPUB/MOBI/TXT/etc.)
- [x] Calibre `metadata.db` import (basic metadata only)
- [ ] Finish SQLite-backed library integration across apps

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

## Phase 5: News Fetching (Partial)
- [x] RSS/Atom parser + fetcher
- [ ] Recipe library management
- [ ] Scheduling / background jobs
- [ ] UI/CLI integration

## Phase 6: Editor & UX (Basic)
- [x] Tweak Book (EPUB unpack/edit/repack)
- [ ] Live preview for editor workflows

## Phase 7: Release & Polish (Planned)
- [ ] Packaging / installers
- [ ] Expanded i18n coverage
- [ ] Performance and profiling for large libraries
