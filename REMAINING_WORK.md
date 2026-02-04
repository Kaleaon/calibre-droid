# Remaining Work Assessment

> **Reference:** See the source-of-truth note in `CONVERSION_STATUS.md` for verified feature status. This document summarizes what is still missing or only partially implemented.

## High Priority Gaps

### 1. News Fetching (Partial)
- **Current**: RSS/Atom parser + fetcher + recipe interface exist in `shared`.
- **Missing**:
  - Recipe library/management
  - Scheduling and background jobs
  - UI/CLI integration and error reporting

### 2. Full-Text Search (Partial)
- **Current**: In-memory index with limited text extraction (HTML/EPUB/TXT).
- **Missing**:
  - Persistent index storage
  - Index updates on library changes beyond import
  - UI/CLI integration for FTS queries
  - Richer extractors (PDF/MOBI)

### 3. Logging (Basic)
- **Current**: Simple logger with levels and optional file output.
- **Missing**:
  - Log rotation/retention
  - Structured output (JSON)
  - Centralized error reporting

### 4. Server & Sync (Basic)
- **Current**: Simple HTTP content server + OPDS 1.2; standalone WebSocket server; auth manager.
- **Missing**:
  - OPDS 2.0 support
  - FTS integration in OPDS
  - Unified server wiring (HTTP + WebSocket)
  - User management persistence (currently in-memory)

### 5. Media Server (Experimental)
- **Current**: `MediaLibrary` + `MediaServer` prototypes in `shared`.
- **Missing**:
  - Integration into CLI/desktop/Android apps
  - Stable API/UX surface
  - Production-grade streaming and auth

## Medium Priority Gaps

### 6. Format Fidelity Improvements
- PDF input is a placeholder (needs PDF parsing integration).
- MOBI output is simplified (no KF8/Huff/CDIC, limited metadata handling).
- Several complex formats (LIT/LRF/CHM/DJVU/PDB) rely on simplified parsers.

### 7. Tests
- Expand coverage for conversion pipeline, server flows, and Android UI.

### 8. Packaging / Distribution
- Desktop installers, Android release pipeline, and reproducible build docs.

### 9. Internationalization
- Current resource bundles exist, but coverage beyond core strings is limited.

## Recommended Next Steps

1. Persist FTS index + wire into CLI/GUI search.
2. Stabilize news fetching (scheduler + recipe management).
3. Decide on media server direction (integrate or remove prototypes).
4. Improve PDF input + MOBI output fidelity.
5. Expand tests and CI coverage.
