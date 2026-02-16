# Calibre Android Port

This directory contains the Android version of the Calibre Kotlin port.

## Project Structure

- **app/src/main/java**: Contains the Kotlin source code.
  - `org.calibre.android.MainActivity`: The main entry point, displaying the book list.
  - `org.calibre.android.BookDetailActivity`: Displays book details.
  - `org.calibre.android.AndroidLibrary`: Manages the book library (persistence, search) using Android's `Context`.
  - `org.calibre.metadata.Metadata`: The core data model (shared logic).
- **app/src/main/res**: Android resources.
  - `layout`: XML layouts for phones.
  - `layout-sw600dp`: XML layouts optimized for tablets (split pane).

## Prerequisites

To build this project, you need:
1.  **JDK 17+**
2.  **Android SDK** (Command line tools or Studio).
3.  Set the `ANDROID_HOME` environment variable or create a `local.properties` file with `sdk.dir=/path/to/sdk`.

## Build Instructions

Run the following command to build the Debug APK:

```bash
./gradlew assembleDebug
```

The APK will be located in `app/build/outputs/apk/debug/app-debug.apk`.

## Features

- **Responsive UI**: Adapts to phones (single pane) and tablets (split pane master-detail flow).
- **Library Management**: Add, list, and view books (persisted to internal storage).
- **Metadata**: Uses the same robust metadata model as the desktop Kotlin port.
- **Theming (KTheme-inspired)**: App-wide theme presets with persistence (Classic, Ocean, Sepia, Night Reader).
