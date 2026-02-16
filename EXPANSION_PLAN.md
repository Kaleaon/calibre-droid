# Calibre-Droid Expansion & Stabilization Plan

This plan breaks "fix and expand all of Calibre-Droid" into realistic, testable phases.

## 1) Stabilization Baseline (Week 1-2)
- **Automate checks**
  - Add CI for Android lint, unit tests, and Gradle build.
  - Add static analysis (ktlint/detekt) and fail fast on regressions.
- **Crash & ANR hardening**
  - Replace blocking file/cover work on UI thread with coroutines + dispatchers.
  - Add structured logging around import, metadata extraction, and reader rendering.
- **Data integrity**
  - Add migration/versioning for `library.json` schema.
  - Add backup/restore smoke tests for library metadata.

## 2) Core Reader Improvements (Week 3-5)
- **Navigation and rendering**
  - Proper chapter navigation, TOC support, and persistent reading location.
  - Improve HTML conversion output (headings, images, links, footnotes).
- **Performance**
  - Cache converted chapters and extracted covers with TTL.
  - Paginated reader mode in addition to scroll mode.
- **Accessibility**
  - Dynamic font scaling, dyslexia-friendly fonts, contrast presets, screen reader labels.

## 3) Library & Discovery (Week 6-8)
- **Metadata quality**
  - Background metadata enrichment and duplicate detection.
  - Better search ranking and filters by tags/series/read status.
- **Collections**
  - User collections/shelves, custom sort profiles, pinned queries.
- **Bulk tools**
  - Multi-select actions (tag, delete, export, mark read/unread).

## 4) Theming Platform (Started)
- Integrated a lightweight **KThemeEngine adapter** inspired by
  `github.com/Kaleaon/ktheme` so the app now supports persisted theme presets.
- Current presets: Classic, Ocean, Sepia, Night Reader.
- Next step (when remote dependencies are available): swap adapter internals to the
  upstream KTheme engine while retaining the same app-facing API.

## 5) Ecosystem & Expansion (Week 9+)
- **Sync**: optional Calibre content server sync and conflict strategy.
- **Plugins**: importer/exporter hooks for additional formats.
- **Observability**: opt-in telemetry dashboards for startup time, crashes, and conversion latency.
- **Release readiness**: staged rollout, changelog automation, and in-app feedback.

## Milestone Acceptance Criteria
- No critical crashes in common flows: import, open, read, bookmark, search.
- Startup under target budget on mid-range devices.
- Reader settings and app theme persist reliably across relaunches.
- Every new feature lands with tests + measurable performance impact.

## Execution Log
1. ✅ **Step 1 (Crash & ANR hardening) started**
   - Moved cover extraction/bitmap decoding off the UI thread in `MainActivity.BookAdapter` by introducing a background executor and main-thread callback.
   - Added null-aware cache handling to avoid repeated failed cover extraction work.
2. ✅ **Step 1 (Crash & ANR hardening) continued**
   - Added structured Android logging (`AppLogger`) and instrumented import/theme/app startup and reader conversion/error flows.
3. ✅ **Step 1 (Automate checks) started**
   - Added GitHub Actions workflow for Android app CI that installs Android SDK components, runs Kotlin compile checks, and runs unit tests.
4. ✅ **Step 1 (Data integrity) started**
   - Introduced versioned library storage snapshots (`schemaVersion`, `books`, `exportedAt`) with backward-compatible loading of legacy array format.
   - Added safer save behavior with temp-file write + backup (`.bak`) before overwrite.
   - Added/expanded unit tests for legacy-format loading and versioned snapshot persistence.

