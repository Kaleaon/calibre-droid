# Calibre Flutter Porting Project - Progress Summary

This project has pivoted to a cross-platform implementation using Flutter and Dart. The `calibre_flutter_app` directory contains the new application foundation.

### Key Accomplishments:

*   **Successful Pivot to Flutter:** The project was successfully moved from a native Android approach to Flutter to support cross-platform development (Android & iOS). Environmental issues with the Flutter toolchain were diagnosed and resolved.
*   **Data Layer Foundation:** A complete data layer was built in Dart, including:
    *   Data models for all core entities (Books, Authors, Tags, etc.).
    *   A `DatabaseRepository` using the `sqflite` package to manage the app's internal database.
*   **Data Migration:** Core logic for data migration was implemented:
    *   **Importer:** A class to read a legacy Calibre `metadata.db` file and populate the app's database.
    *   **Exporter:** A class to write the app's data back out to a Calibre-compatible `metadata.db` file.
*   **UI Scaffolding:** A functional, read-only UI was built to:
    *   Display a list of all books from the database, including cover images.
    *   Show a detailed view for each book.
    *   Provide navigation between screens.
*   **Dependency Management:** All project dependencies were updated and resolved to a stable, compatible configuration.
*   **Code Verification:** The entire Dart codebase passes `flutter analyze` with no issues, ensuring high code quality and syntactic correctness.

**Note:** Further development, such as integrating a third-party e-reader package, is currently blocked by environmental limitations that prevent the automatic generation of native platform files (`android`/`ios` sub-projects) needed for packages with native code components.

---

# calibre

<img align="left" src="https://raw.githubusercontent.com/kovidgoyal/calibre/master/resources/images/lt.png" height="200" width="200"/>

calibre is an e-book manager. It can view, convert, edit and catalog e-books 
in all of the major e-book formats. It can also talk to e-book reader 
devices. It can go out to the internet and fetch metadata for your books. 
It can download newspapers and convert them into e-books for convenient 
reading. It is cross platform, running on Linux, Windows and macOS.

For more information, see the [calibre About page](https://calibre-ebook.com/about).

[![Build Status](https://github.com/kovidgoyal/calibre/workflows/CI/badge.svg)](https://github.com/kovidgoyal/calibre/actions?query=workflow%3ACI)

## Screenshots  

[Screenshots page](https://calibre-ebook.com/demo)

## Usage

See the [User Manual](https://manual.calibre-ebook.com).

## Development

[Setting up a development environment for calibre](https://manual.calibre-ebook.com/develop.html).

A [tarball of the source code](https://calibre-ebook.com/dist/src) for the 
current calibre release.

## Bugs

Bug reports and feature requests should be made in the calibre bug tracker at [Launchpad](https://bugs.launchpad.net/calibre).
GitHub is only used for code hosting and pull requests.

## Support calibre

calibre is a result of the efforts of many volunteers from all over the world.
If you find it useful, please consider contributing to support its development.
[Donate to support calibre development](https://calibre-ebook.com/donate).

## Building calibre binaries

See [Build instructions](bypy/README.rst) for instructions on how to build the
calibre binaries and installers for all the platforms calibre supports.

## calibre package versions in various repositories

[![Packaging Status](https://repology.org/badge/vertical-allrepos/calibre.svg?columns=3&header=calibre)](https://repology.org/project/calibre/versions)
