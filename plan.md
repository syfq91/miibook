# Project Plan: E-Paper Optimized EPUB Reader for Android

## 1. Project Objective

Build a production-quality Android EPUB reader inspired by CrossPoint Reader, optimized for e-paper devices such as BOOX and Bigme.

The application must use Kotlin and Jetpack Compose, provide a monochrome text-first interface, support OPDS catalogs, and use Readium Kotlin Toolkit for EPUB parsing and rendering.

Prioritize readability, low visual complexity, reliable offline reading, minimal unnecessary screen updates, and maintainable architecture.

Do not attempt to reproduce CrossPoint firmware. Build a native Android application inspired by its reading-focused design.

---

## 2. Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose and Material 3
- **Architecture:** MVVM with repository pattern
- **EPUB Engine:** Readium Kotlin Toolkit
- **OPDS:** Readium OPDS module
- **Database:** Room
- **Preferences:** DataStore
- **Asynchronous Operations:** Kotlin Coroutines and Flow
- **Dependency Injection:** Hilt or manual dependency injection
- **Image Loading:** Coil, configured to avoid unnecessary animations
- **File Import:** Android Storage Access Framework
- **Build System:** Gradle Kotlin DSL
- **Testing:** JUnit, coroutine testing, Compose UI tests, and Android instrumentation tests

Use compatible stable dependency versions. Verify Readium's current version, Android requirements, and API signatures against its official documentation before implementing integrations. Do not assume example code from another version will compile unchanged.

---

## 3. Application Architecture

Organize the code into feature-oriented packages:

- `core/`: shared models, dispatchers, error handling, and utilities.
- `data/local/`: Room database, DAOs, entities, and migrations.
- `data/epub/`: Readium publication opening and navigation integration.
- `data/opds/`: catalog retrieval, feed parsing, and acquisition metadata.
- `data/download/`: downloads, storage management, and validation.
- `data/repository/`: library, bookmark, progress, and catalog repositories.
- `domain/`: use cases and domain models.
- `ui/bookshelf/`: library screen and book list.
- `ui/catalog/`: OPDS catalog browser.
- `ui/reader/`: EPUB navigator integration and reading controls.
- `ui/bookmarks/`: bookmark list and navigation.
- `ui/settings/`: reading and e-paper preferences.
- `ui/theme/`: monochrome design system.
- `platform/epaper/`: optional vendor-specific display integration.

Keep UI state separate from persistence and EPUB rendering. Use ViewModels and immutable UI state, exposed through `StateFlow`.

---

## 4. UI and Design Requirements

### 4.1 E-Paper Design System

Create a dedicated theme with the following defaults:

- **Background:** off-white or white.
- **Primary text:** near-black.
- **Secondary text:** dark gray with sufficient contrast.
- **Borders:** solid gray or black.
- **Visuals:** No gradients, blur, translucent surfaces, or decorative shadows.
- **Transitions:** No shimmer effects or automatically animated transitions.
- **Progress:** No continuously animated loading indicators. Prefer explicit loading text or static progress indicators.
- **Layout:** Use stable screen layouts and predictable navigation.
- **Accessibility:** Support system font scaling and accessibility services.
- **Touch targets:** Provide touch targets of approximately 48 dp or larger.

Allow users to switch between pure white and warm white backgrounds. Keep the default theme monochrome and high contrast.

### 4.2 Bookshelf

Implement a compact, text-first list instead of a large cover grid.

Each row should display:
- Book title in prominent text.
- Author.
- Reading progress percentage.
- A thin, static progress indicator.
- A clear open-book action.
- An optional overflow menu.

Provide:
- Search by title and author.
- Recent and unread filters.
- Sorting by title, author, or last opened.
- Import EPUB action.
- Empty-library and error states.
- Catalog, Downloads, and Settings navigation.

Keep rows stable during updates. Use `LazyColumn` with stable book IDs as item keys.

Book covers are optional. If included, use small, fixed-size thumbnails and avoid animated image loading.

### 4.3 Reader Screen

Use Readium for EPUB parsing, HTML rendering, and reading navigation.

Provide:
- Paginated reading mode by default.
- Previous-page and next-page actions.
- Support for physical page-turn buttons where possible.
- Table of contents and chapter navigation.
- Font-size adjustment.
- Light and dark or sepia reading themes, subject to navigator support.
- Bookmark creation and removal.
- Current chapter and reading progress.
- Restore-last-position functionality.
- A minimal toolbar that can be hidden while reading.

Avoid implementing EPUB pagination using Compose Text. EPUB content may contain complex HTML, CSS, embedded fonts, images, and other publishing features.

Evaluate the stability of Readium's Compose navigator against the target EPUB collection. If it is unsuitable for production, embed the established Android navigator in a Compose screen using an appropriate Android view or fragment host.

### 4.4 OPDS Catalog Browser

Implement an OPDS catalog management and browsing interface.

Features:
- Add and remove catalog URLs.
- Validate catalog URLs and show clear errors.
- Parse supported OPDS feeds using Readium's OPDS module.
- Display book title, author, cover, summary, and available acquisition formats.
- Navigate between catalog feeds and entries.
- Download EPUBs into app-private storage.
- Track download progress and errors.
- Prevent duplicate downloads where practical.
- Allow downloaded books to open offline.

Support public OPDS catalogs first. Treat authentication, DRM, and vendor-specific catalog extensions as separate features requiring explicit compatibility work.

Do not bypass authentication or access restrictions.

---

## 5. Persistence Model

Use Room for persistent application data.

### 5.1 Books

Store:
- Stable book ID.
- Title and author.
- Local EPUB URI or app-private file reference.
- Optional cover location.
- Import date.
- Last-opened timestamp.
- Download status.

### 5.2 Reading Progress

Store:
- Book ID.
- Serialized Readium locator.
- Progress percentage.
- Last-updated timestamp.

Save progress after navigation settles, when leaving the reader, and at suitable lifecycle boundaries. Avoid writing to the database on every rapid intermediate page event.

Restore the saved locator when reopening a book. Do not rely solely on page numbers because pagination can change with font size, screen size, or reading preferences.

### 5.3 Bookmarks

Store:
- Bookmark ID.
- Book ID.
- Serialized Readium locator.
- Optional title or chapter label.
- Creation timestamp.

Allow users to list, open, and delete bookmarks.

### 5.4 Preferences

Use DataStore for:
- Font size.
- Reading theme.
- Font family where supported.
- Margins and spacing where supported.
- Preferred navigation mode.
- E-paper-specific application settings.
- Bookshelf sort and filter preferences.

---

## 6. E-Paper Performance and Hardware Integration

Treat e-paper refresh control as an optional platform integration.

First establish a reliable baseline using ordinary Android APIs.

Requirements:
- Avoid unnecessary recompositions and full-screen UI reconstruction.
- Keep list item identity stable.
- Avoid animated navigation and animated content transitions.
- Use static colors and backgrounds.
- Do not poll hardware state continuously.
- Avoid automatic refresh timers unless required by a documented device API.
- Do not issue undocumented shell commands or hidden vendor API calls.
- Never assume BOOX and Bigme expose identical refresh controls.

Create a platform interface such as `EpaperController`, with a safe default implementation that performs no vendor-specific operations.

Only add device-specific implementations after confirming the manufacturer's documented SDK or supported integration method.

If no supported refresh API is available, leave refresh behavior to the system and expose guidance about the device's built-in refresh settings.

Test visual ghosting and refresh behavior on physical hardware. Compose-level optimizations alone cannot guarantee a specific panel refresh mode.

---

## 7. Implementation Phases

### Phase 1: Project Foundation
- [x] Create the Android project and package structure.
- [x] Configure Compose, Room, DataStore, and dependency injection.
- [x] Establish compatible dependency versions.
- [x] Implement the monochrome theme and reusable e-paper UI components.
- [x] Add navigation and application-level error handling.

> **Deliverable:** A compiling app with a bookshelf shell and settings screen.

### Phase 2: Local EPUB Library
- [x] Implement EPUB import through the Storage Access Framework.
- [x] Handle URI permissions or copy imported files into app-private storage.
- [x] Integrate Readium's publication opener.
- [x] Store imported-book metadata in Room.
- [x] Implement search, filters, sorting, and book opening.
- [x] Add invalid-file and unsupported-publication handling.

> **Deliverable:** Users can import EPUBs and see them in an offline library.

> **Phase 2 notes:** Import uses the SAF `OpenDocument` picker, copies the file
> into `filesDir/books/`, extracts title/author via Readium's `PublicationOpener`,
> computes a SHA-256 checksum for duplicate detection, and persists to Room.
> Invalid/corrupt/non-EPUB files are rejected with a user-facing message and the
> temporary copy is cleaned up. The bookshelf "open" action is wired but reading
> itself is delivered in Phase 3 (the reader screen). Readium is pinned to 3.3.0
> because 3.4.0's AAR requires compileSdk 37 (this project is on 36 / AGP 9.0.1).

### Phase 3: EPUB Reader
- [x] Integrate the selected Readium navigator.
- [x] Implement paginated navigation.
- [x] Add font-size and theme settings.
- [x] Implement table of contents navigation.
- [x] Add bookmark creation and removal.
- [x] Persist and restore reading positions.
- [x] Handle lifecycle changes and publication closure correctly.

> **Deliverable:** A usable EPUB reader with persistent progress and bookmarks.

> **Phase 3 notes:** The reader embeds Readium's Fragment/WebView-based
> `EpubNavigatorFragment` inside a Compose host — the fallback path described in
> section 4.3. Readium's Compose navigators (`readium-navigators-web-reflowable` and
> its `-common`/`-internals` siblings) are **not published as Maven artifacts in
> 3.3.0**; they return HTTP 404 on Maven Central for every version, so the Android
> navigator is the only consumable choice while Readium is pinned to 3.3.0.
> `MainActivity` extends `FragmentActivity`, installs
> `EpubNavigatorFragment.createDummyFactory()` before `super.onCreate()` so process
> restoration does not crash, and declares `android:configChanges` so the WebView
> survives rotation. Hardware page-turn keys are intercepted only for
> `KEYCODE_PAGE_UP`/`KEYCODE_PAGE_DOWN`; volume keys are deliberately not consumed.
>
> **Verification for this phase is limited to unit tests and `assembleDebug`.** No
> device or emulator exists on the build machine, so page rendering, hardware key
> handling, rotation, and e-paper refresh behaviour have **not** been exercised on
> real hardware and must not be claimed to work.

### Phase 4: OPDS and Downloads
- [ ] Implement catalog URL management.
- [ ] Parse supported OPDS feeds.
- [ ] Display catalog entries and acquisition options.
- [ ] Implement download queue and progress.
- [ ] Validate downloaded files before opening.
- [ ] Add offline library integration and download error recovery.

> **Deliverable:** Users can browse public OPDS catalogs, download books, and read them offline.

### Phase 5: E-Paper Optimization
- [ ] Audit the app for animations and unnecessary visual changes.
- [ ] Test font scaling, high contrast, and touch target sizes.
- [ ] Validate physical button navigation on supported devices.
- [ ] Test on representative BOOX and Bigme devices.
- [ ] Evaluate device refresh settings and supported vendor APIs.
- [ ] Implement only documented hardware integrations.
- [ ] Tune list density and reader margins based on actual display dimensions.

> **Deliverable:** A stable, readable application optimized for physical e-paper hardware.

### Phase 6: Hardening and Release
- [ ] Add database migration tests.
- [ ] Test malformed, large, and unsupported EPUBs.
- [ ] Test network failures, interrupted downloads, and invalid OPDS feeds.
- [ ] Test configuration changes and activity recreation.
- [ ] Test offline reading and progress restoration.
- [ ] Profile memory use and EPUB rendering performance.
- [ ] Document known device-specific limitations.
- [ ] Produce a release build and setup documentation.

> **Deliverable:** A tested release candidate with documented limitations.

---

## 8. Testing Requirements

At minimum, test:
- Empty and populated libraries.
- Duplicate imports.
- EPUBs containing images, embedded fonts, and complex HTML.
- Invalid EPUB archives.
- Large books and long chapter lists.
- Font changes and reading-theme changes.
- Bookmark persistence after process death.
- Reading-position restoration.
- Interrupted OPDS downloads.
- Unreachable catalogs and malformed feeds.
- Offline opening of downloaded books.
- Screen rotation or configuration changes where supported.
- Accessibility font scaling.
- Device-specific ghosting and page-turn behavior.

Use automated unit and UI tests where practical, but validate rendering and refresh behavior on real e-paper hardware.

---

## 9. Definition of Done

The application is ready for an initial release when:
- The project builds successfully using documented, compatible dependencies.
- Users can import and read local EPUB files.
- The bookshelf is monochrome, text-first, and comfortable to use on e-paper.
- Pagination, font settings, and reading themes work correctly.
- Bookmarks and reading positions survive application restarts.
- Public OPDS catalogs can be browsed and supported EPUBs downloaded.
- Downloaded books are available offline.
- Unsupported files and network errors produce useful messages.
- No unsupported vendor-specific refresh APIs are required for normal operation.
- Core workflows have automated tests and have been verified on at least one target e-paper device.

---

## 10. Instructions to the Coding Agent

- Work incrementally and keep the project compiling after every phase.
- Before writing integration code, inspect the existing repository, build files, SDK configuration, and installed dependency versions.
- Consult official Readium documentation and examples for the exact APIs used. Do not invent classes or silently substitute incompatible APIs.
- Implement one vertical slice at a time: import one EPUB, render it, save its reading position, and restore it. Expand only after that workflow works.
- Prefer small, testable changes over generating the entire application at once. Run available unit tests and build tasks after each major change.
- Report completed features, modified files, build/test results, remaining issues, and assumptions at the end of each phase.
- Do not claim that hardware refresh control or a feature works until it has been verified on the target device.
