# Calibre for Android: A Development Roadmap

This document outlines a high-level roadmap for creating a native Android version of Calibre. The project is broken down into several phases, each with a set of major tasks. For each task, the relevant files or components from the original Calibre codebase are referenced to provide context.

## Phase 1: The Foundation

This phase focuses on setting up the core infrastructure of the Android application and implementing the database layer, which is the foundation upon which all other features will be built.

*   **1.1. Project Setup:**
    *   **Task:** Create a new Android Studio project.
    *   **Details:** Configure the project with Kotlin as the primary language and enable support for modern Android Jetpack libraries. Set up version control with Git.
    *   **Key Files:** `build.gradle`, `app/build.gradle`, `AndroidManifest.xml`.

*   **1.2. Architectural Pattern:**
    *   **Task:** Establish the core application architecture.
    *   **Details:** Implement the MVVM (Model-View-ViewModel) pattern. This will involve setting up base classes for Activities, Fragments, ViewModels, and Repositories.
    *   **Key Files:** This is a conceptual task, but it will result in base classes like `BaseActivity.kt`, `BaseViewModel.kt`, etc.

*   **1.3. Database Layer Implementation:**
    *   **Task:** Re-implement the Calibre database logic using native Android technologies.
    *   **Details:**
        *   Define the database schema using Room `@Entity` classes. This will be a translation of the schema found in Calibre's `library/database.py`.
        *   Create Room DAOs (`@Dao` interfaces) to define the API for querying the database. This will replace the raw SQL queries in Calibre's `db/backend.py`.
        *   Implement a `DatabaseRepository` that uses the DAOs to provide data to the rest of the application. All database operations must be performed on a background thread using Kotlin Coroutines.
        *   Implement a mechanism to handle the user's library on external storage (SD card) using the Storage Access Framework (SAF), as discussed previously.
    *   **Calibre References:** `src/calibre/library/database.py` (for schema), `src/calibre/db/backend.py` (for API), `src/calibre/db/tables.py` (for table abstractions).

*   **1.4. Initial UI Shell:**
    *   **Task:** Create the main application `Activity` and basic navigation structure.
    *   **Details:** Implement a `MainActivity` that will host the different screens of the app. Set up the Jetpack Navigation component with a navigation graph for future screens.
    *   **Key Files:** `MainActivity.kt`, `res/layout/activity_main.xml`, `res/navigation/nav_graph.xml`.

## Phase 2: Core Features

With the database in place, this phase focuses on building the core user-facing features for library management and content acquisition.

*   **2.1. Library View:**
    *   **Task:** Implement the main library screen that displays the list of books.
    *   **Details:**
        *   Create a `LibraryViewModel` that retrieves the list of books from the `DatabaseRepository`.
        *   Use a `RecyclerView` or a lazy-loading list in Jetpack Compose to efficiently display the book list.
        *   Each item in the list should display the book's cover, title, and author(s).
        *   Implement sorting and filtering options for the library view.
    *   **Calibre References:** `src/calibre/gui2/library/views.py` (for the view concept), `src/calibre/gui2/library/models.py` (for the data model).

*   **2.2. Book Details View:**
    *   **Task:** Create a screen to display the detailed metadata for a single book.
    *   **Details:**
        *   Create a `BookDetailsViewModel` that retrieves all metadata for a specific book ID.
        *   Display all major metadata fields: cover, title, authors, series, rating, tags, publisher, publication date, and comments.
        *   This screen should also show the available formats for the book.
    *   **Calibre References:** `src/calibre/gui2/book_details.py`.

*   **2.3. News Fetching (Recipes):**
    *   **Task:** Implement the news fetching system.
    *   **Details:**
        *   As recommended in the deep-dive analysis, this would likely involve a hybrid Python/Kotlin approach.
        *   Use the `python-for-android` toolchain to bundle the Calibre recipe runner (`src/calibre/web/feeds/news.py`) and the standard recipes from the `recipes/` directory.
        *   Create a native Android UI for managing and scheduling news downloads.
        *   Use a `WorkManager` background task to execute the Python recipe runner via a JNI bridge (e.g., Chaquopy or pyjnius).
        *   The downloaded e-books would then be added to the Calibre library.
    *   **Calibre References:** `recipes/`, `src/calibre/web/feeds/news.py`.

## Phase 3: Advanced Features

This phase introduces more advanced functionality, including integration with the Calibre ecosystem and the core e-reading experience.

*   **3.1. Content Server Client:**
    *   **Task:** Implement a client to connect to a running Calibre Content Server.
    *   **Details:**
        *   Create a UI for users to enter their server's address, username, and password.
        *   Use Retrofit and an XML parser to connect to the server's OPDS feed.
        *   Allow users to browse and download books from their remote library to their device.
        *   This provides an alternative to manually copying the library for users who run the server.
    *   **Calibre References:** `src/calibre/srv/` (for understanding the server's API and OPDS implementation).

*   **3.2. E-book Reader Integration:**
    *   **Task:** Integrate a component for reading e-books.
    *   **Details:**
        *   This is a major task in itself. A good approach would be to integrate an existing open-source Android e-book reader SDK rather than building one from scratch.
        *   **Option 1 (Recommended):** Use a library like `FolioReader` (for EPUB) or `android-pdf-viewer`. These libraries handle the complexities of rendering, pagination, and user settings (font size, margins, etc.).
        *   **Option 2 (Advanced):** Create a custom reader using a `WebView` for rendering HTML-based formats like EPUB. This offers more control but is much more complex, requiring manual implementation of pagination, TOC handling, and state management.
    *   **Functionality:** The reader should support opening the downloaded book formats, provide a good reading experience, and remember the user's reading position.

## Phase 4: Polish and Release

This final phase focuses on preparing the application for a public release.

*   **4.1. Theming and UI Polish:**
    *   **Task:** Refine the application's visual design.
    *   **Details:**
        *   Implement a full Material Design 3 theme, including support for dynamic color theming on Android 12+.
        *   Implement a dark theme.
        *   Review all UI elements for consistent styling, padding, and typography.
        *   Add animations and transitions to create a more polished user experience.

*   **4.2. Accessibility:**
    *   **Task:** Ensure the application is usable by everyone.
    *   **Details:**
        *   Add `contentDescription` attributes to all icons and image-based buttons for screen readers like TalkBack.
        *   Ensure all touch targets are at least 48dp x 48dp.
        *   Test the app with various accessibility settings enabled (e.g., large fonts, high contrast mode).

*   **4.3. Performance Tuning and Testing:**
    *   **Task:** Profile and optimize the application for performance and stability.
    *   **Details:**
        *   Use Android Studio's built-in profilers to identify and fix memory leaks, CPU hotspots, and janky UI rendering.
        *   Perform extensive testing on a variety of devices (phones, tablets, different Android versions) and with large Calibre libraries.
        *   Write unit and integration tests for the ViewModels and Repositories.

*   **4.4. Release Preparation:**
    *   **Task:** Prepare the application for distribution on the Google Play Store.
    *   **Details:**
        *   Create a production signing key and configure the build process to generate a signed Android App Bundle (AAB).
        *   Create a Google Play Store listing, including screenshots, an app icon, and a detailed description.
        *   Implement an in-app feedback or bug reporting mechanism.
