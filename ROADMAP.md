# Calibre to Kotlin: Total Conversion Roadmap

This document outlines the strategic plan to convert the entire Calibre codebase (approx. 1.5M LOC of Python/C++) to Kotlin.

## Phase 1: The Foundation (Completed)
**Goal**: Establish architecture and core library management.
- [x] **Project Structure**: Gradle monorepo (Core, Desktop, Android).
- [x] **Data Model**: `Metadata` class, `Library` management, Persistence (`library.json`, SQLite import).
- [x] **Conversion Architecture**: Pluggable Pipeline (`InputPlugin` -> `OebBook` -> `OutputPlugin`).
- [x] **Basic Formats**: EPUB (Input), PDF (Meta), Text/HTML (Output).
- [x] **Device Sync**: Abstraction for device drivers; Local Folder implementation.
- [x] **UI Prototypes**: CLI, Swing Desktop, Android App, Web Server.

## Phase 2: Format Expansion & fidelity (Current Focus)
**Goal**: Support the "Big 3" formats (EPUB, MOBI, AZW3) with high fidelity.
- [ ] **MOBI/AZW3 Input**: Port `calibre.ebooks.mobi` to Kotlin (Binary parsing of PalmDB/MOBI headers).
- [ ] **Conversion Fidelity**:
    - Implement CSS flattening and CSS filtering (port `calibre.ebooks.oeb.stylizer`).
    - Implement Font subsetting/embedding.
    - Image processing (resizing, grayscale).
- [ ] **DocX Input**: Port `calibre.ebooks.docx`.

## Phase 3: Connectivity & Metadata
**Goal**: Make the app "smart" and connected.
- [ ] **Metadata Sources**:
    - Port `calibre.ebooks.metadata.sources`.
    - Implement Google Books, Amazon, ISBNDB scrapers/APIs.
    - **Action Item**: Implement `GoogleBooksSource` immediately.
- [ ] **Content Server**:
    - Upgrade to support OPDS feeds (for connecting to other readers).
    - Implement proper user accounts/auth.

## Phase 4: The Viewer
**Goal**: Replace the Python/Qt/WebEngine viewer.
- [ ] **Engine Choice**: Move away from `JEditorPane`.
    - **Option A**: JavaFX `WebView` (bundled WebKit).
    - **Option B**: JCEF (Chromium Embedded Framework) - Closer to Calibre's current QtWebEngine.
    - **Option C**: Compose Multiplatform HTML rendering (experimental).
- [ ] **Features**: Annotations, Highlights, Text-to-Speech, Color profiles.

## Phase 5: Device Drivers
**Goal**: Hardware support.
- [ ] **MTP/USB**: Implement `libusb` / `libmtp` wrappers in Java/Kotlin (using `Project Panama` or JNA).
- [ ] **Driver Logic**: Port specific drivers (Kindle, Kobo, Nook).
    - This involves parsing device-specific databases (e.g., Kobo `KoboReader.sqlite`).

## Phase 6: The Editor ("Tweak Book")
**Goal**: A full IDE for books.
- [ ] **UI**: High-complexity UI with file browser, code editor (syntax highlighting), preview pane.
- [ ] **Tools**: Check Book, Spellcheck, Regex search/replace.
- [ ] **Container**: Manipulation of ZIP/OEBPS structures in-place.

## Phase 7: Advanced Features
- [ ] **News/Recipes**: Port the Python scraping engine (`calibre.web.feeds`).
    - *Challenge*: Calibre recipes are Python scripts. We would either embed a Python engine (Jython/GraalVM) to run legacy recipes or transpile them.
- [ ] **Plugins**: Define a Kotlin-based Plugin API.

## Technical Strategy
1.  **Iterative Vertical Slices**: Don't port all formats at once. Port one format, full pipeline, UI, then next.
2.  **Testing**: High test coverage is essential as we replace logic.
3.  **Dependency Management**: Replace Python specific libs (lxml, Pillow) with JVM equivalents (Jackson/Xml, Java 2D/TwelveMonkeys).

## Estimated Timeline
- **Months 1-3**: Core formats & Metadata (Phases 1-3).
- **Months 4-6**: Viewer & Device Drivers (Phases 4-5).
- **Months 7-12**: Editor & Advanced Features.
