# Calibre Kotlin Port (Proof of Concept)

This is a Proof of Concept (PoC) for converting Calibre to Kotlin.

## Implemented Features

- **Core Metadata Structure**: Ported the core `Metadata` class from `src/calibre/ebooks/metadata/book/base.py` to Kotlin.
- **Type Safety**: Converted dynamic Python fields to strong Kotlin types (`String`, `List<String>`, `LocalDateTime`, etc.).
- **Null Handling**: Implemented logic to handle Calibre's specific "null" values (e.g. "Unknown", "und").
- **Project Structure**: Set up a standard Gradle project structure.

## How to Run

Prerequisites: Java 21+ and Gradle.

Run the application using Gradle:

```bash
./gradlew run
```

(Note: You may need to install Gradle or use the provided gradle binary if available).

## Code Location

- `src/main/kotlin/org/calibre/metadata/Metadata.kt`: The converted Metadata class.
- `src/main/kotlin/org/calibre/metadata/Main.kt`: Entry point demonstrating usage.
