# Calibre Kotlin Port (Proof of Concept)

This is a Proof of Concept (PoC) for converting Calibre to Kotlin.

## Implemented Features

- **Core Metadata Structure**: Ported the core `Metadata` class from `src/calibre/ebooks/metadata/book/base.py` to Kotlin.
- **Library Management**: Implemented a `Library` class (`src/calibre/db/cache.py` equivalent) that manages a collection of books.
- **Persistence**: The library persists its state to a JSON file (`library.json`) using Jackson.
- **CLI Interface**: A command-line interface to interact with the library.
- **Project Structure**: Standard Gradle project structure.

## How to Run

Prerequisites: Java 21+ and Gradle.

Run the application using Gradle:

```bash
# Interactive Mode
./gradlew run

# Command Line Arguments
./gradlew run --args="list"
./gradlew run --args="add --title The_Hobbit --author Tolkien"
./gradlew run --args="search hobbit"
./gradlew run --args="remove 1"
```

(Note: You may need to install Gradle or use the provided gradle binary if available).

## Code Location

- `src/main/kotlin/org/calibre/metadata/Metadata.kt`: The converted Metadata class.
- `src/main/kotlin/org/calibre/metadata/Library.kt`: Library management and persistence.
- `src/main/kotlin/org/calibre/metadata/Main.kt`: CLI entry point.
- `src/main/kotlin/org/calibre/metadata/Constants.kt`: Shared constants.
