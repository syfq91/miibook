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
- [x] Implement catalog URL management.
- [x] Parse supported OPDS feeds.
- [x] Display catalog entries and acquisition options.
- [x] Implement download queue and progress.
- [x] Validate downloaded files before opening.
- [x] Add offline library integration and download error recovery.

> **Deliverable:** Users can browse public OPDS catalogs, download books, and read them offline.

> **Verification for this phase is limited to unit tests and `assembleDebug`**, the same
> constraint as Phase 3: no device or emulator exists on the build machine. The OPDS feed
> parser is exercised against hand-written OPDS 1 and OPDS 2 fixtures through Readium's
> real parsers, and the download queue's import/validation path is covered only to the
> point where it would touch the network or the filesystem. Fetching from a live catalog,
> downloading a real EPUB, opening it from the downloads screen, and reading it offline
> have **not** been exercised on a device and must not be claimed to work.

### Phase 5: E-Paper Optimization
- [x] Audit the app for animations and unnecessary visual changes.
- [x] Test font scaling, high contrast, and touch target sizes.
- [ ] Validate physical button navigation on supported devices.
- [ ] Test on representative BOOX and Bigme devices.
- [x] Evaluate device refresh settings and supported vendor APIs.
- [x] Implement only documented hardware integrations.
- [ ] Tune list density and reader margins based on actual display dimensions.

> **Deliverable:** A stable, readable application optimized for physical e-paper hardware.

> **Audit (done, and now machine-enforced).** `EpaperUiConstraintTest` reads the real
> sources under `app/src/main/java/com/miireader` and fails the build if a forbidden
> construct returns: any animation API, any animated NavHost transition (the host keeps
> `EnterTransition.None` / `ExitTransition.None` for all four transitions), any clickable
> without `indication = null`, gradients/blur/shadow/explicit alpha/translucent color
> literals/non-zero elevation, spinning loaders or polling loops in `ui/`, lazy list
> `items(...)` without a `key =`, text sizes not in `sp`, a `fontScale` override, a touch
> target baseline other than 48dp, and Settings copy that advertises refresh behaviour
> MiiReader cannot deliver. The test was mutation-checked: injecting `AnimatedVisibility`
> into a screen makes it fail. `EpaperThemeTest` computes WCAG contrast from the real
> palette `Color` values — text pairings clear 4.5:1 (AA), borders are floored at 1.4:1,
> and every palette color must be fully opaque.
>
> **Three Settings controls were removed because they promised what no verified API can
> deliver:** "Allow UI Animations" (nothing consumed the preference, and animations are
> forbidden by design), "Full Screen Refresh every N pages" (nothing consumed it, and
> section 6 says to avoid automatic refresh timers), and "Flash Refresh Now", whose
> handler was a no-op while the UI claimed it "Clears residual ghosting immediately" — a
> direct violation of the rule against claiming unverified refresh control works. The
> `animationsEnabled` and `refreshIntervalPages` preferences were deleted with their
> DataStore keys, and the diagnostics row that reported an "E-Ink device profile active"
> now says plainly whether an e-paper device was detected. Settings instead carries
> static guidance pointing at the device's own refresh controls, which is what section 6
> prescribes when no supported refresh API exists.
>
> **Vendor API evaluation (2026-10-15), sources recorded so this need not be redone:**
> - Android defines **no** public e-ink refresh API — nothing in AOSP controls panel
>   refresh modes, GC flashes, or ghosting.
> - **Onyx/BOOX publishes a vendor SDK**: `com.onyx.android.sdk:onyxsdk-device` (1.1.11
>   probed live, HTTP 200) on `http://repo.boox.com/repository/maven-public/` — an
>   **HTTP-only** repository — documented in `github.com/onyx-intl/OnyxAndroidDemo`
>   (`EpdController` "EPD Screen Update API", `EpdDeviceManager`, `RefreshMode` demo).
>   Its POM pulls pre-AndroidX artifacts (`com.android.support:multidex`,
>   `support-annotations:28.0.0`), so consuming it needs an insecure-repo opt-in plus
>   jetifier, and it is **BOOX-only**. Hidden variants exist
>   (`android.onyx.optimization.EInkHelper.setAppScopeRefreshMode`) and are explicitly
>   forbidden here.
> - **Bigme has no documented public refresh SDK.** Community integrations reach it via
>   boot-classpath/reflective vendor calls, also forbidden.
> - BOOX's own manual states system-level refresh mode **overrides** app refresh mode,
>   so the system settings are the correct place for users to control refresh.
>
> **Decision: no vendor integration was implemented.** Nothing can be exercised on a
> machine with no e-paper hardware, and the only documented SDK is single-vendor and
> would put an insecure repository plus legacy support libraries into the build. A
> candidate that is untestable here must not ship while the project's rule is "never
> claim an unverified feature (especially hardware refresh control) works".
> `platform/epaper/EpaperController` therefore stays a vendor-neutral no-op seam (with
> `DefaultEpaperControllerTest` pinning that contract), and no Settings control exposes
> it. If a BOOX unit later confirms the SDK, the recorded path is: add
> `repo.boox.com` with `isAllowInsecureProtocol = true`, add `onyxsdk-device` behind
> jetifier, implement `OnyxEpaperController`, and keep the default implementation for
> every other vendor — never a shared assumption across BOOX and Bigme.
>
> **Still requires hardware (do not claim these work):** physical page-turn key
> dispatch through `MainActivity.dispatchKeyEvent` (the `PageTurnKeys` decision logic
> and the reader-side handler are unit-tested, but no real `KeyEvent` has traversed an
> Activity), e-paper detection against real device `Build` strings, refresh/ghosting
> behaviour in every mode, rotation and page-render quality on an e-ink panel, system
> font scaling at large accessibility sizes, list density and reader margins against
> actual display dimensions (they are fixed dp today — tuning is the remaining
> checkbox), and the warm-white background's appearance on a grayscale e-ink panel.
> One measured finding is already recorded for that pass: the `#CCCCCC` hairline border
> sits at 1.61:1 on white but only 1.47:1 on the warm background, so border contrast is
> worth revisiting when a device is available.
> The suite is at **100 unit tests**, all green, plus a clean `assembleDebug`.

### Phase 6: Hardening and Release
- [x] Add database migration tests.
- [x] Test malformed, large, and unsupported EPUBs.
- [x] Test network failures, interrupted downloads, and invalid OPDS feeds.
- [x] Test configuration changes and activity recreation.
- [x] Test offline reading and progress restoration.
- [ ] Profile memory use and EPUB rendering performance.
- [x] Document known device-specific limitations.
- [x] Produce a release build and setup documentation.

> **Deliverable:** A tested release candidate with documented limitations.

> **Scope of this phase:** every checkbox except profiling is complete *as far as a
> machine without hardware or an emulator can take it*. The suite grew 101 -> **131
> tests**, all green, and both `assembleDebug` and `assembleRelease` build.
>
> **What the new tests cover**
> - `AppDatabaseMigrationTest` — because no migrations exist, the destructive-fallback
>   contract is pinned: a real v1 and a real v2 SQLite file (schemas reconstructed from
>   git history) open at v3 **without crashing** with legacy rows dropped, a fresh install
>   starts empty at v3, and books/progress/bookmarks survive closing and reopening
>   (process-death path).
> - `EpubValidationTest` + `BookImporterRobolectricTest` (real Readium, real Room, real
>   `content://` stream) — valid EPUB metadata, random bytes, a non-EPUB zip, an empty
>   file, a plain-text file named `.epub`, a missing file, a **150-chapter** book, an EPUB
>   with an image and an embedded font, plus full import, duplicate detection, and
>   cleanup-after-rejection.
> - `ReadiumOpdsHttpClientTest` — the only production network code, exercised over a real
>   **loopback** HTTP server: 200 bodies, redirect following, 404/500 status wording,
>   an HTML page served with 200 rejected by the parser, connection refused, and an
>   address with no scheme. Two more download tests cover a transfer interrupted
>   **mid-stream** (fails cleanly, no partial import, no scratch file, typed
>   `NetworkError`, retried to completion afterwards).
> - `MainActivityRecreationTest` — full activity recreation through
>   `onCreate(savedInstanceState)` (the Readium dummy-fragment path), plus the manifest
>   `configChanges` contract that keeps the WebView alive across rotation.
> - `OfflineReadingRestorationTest` — a generated EPUB in app-private storage opened by
>   the production publication repository with no network in the path, progress and
>   bookmarks restored after the database restarts, and FK cascade on book deletion.
>
> **Two production bugs these tests caught, both fixed**
> 1. `ReadiumEpubMetadataExtractor` used `Contributor.toString()` for authors, so every
>    imported book stored the whole data class as its author (e.g.
>    `Contributor(localizedName=...)` instead of the real name). Now
>    `localizedName?.string`, verified against the 3.3.0 bytecode.
> 2. `DefaultBookImporter` returned failure for empty and unparseable files **without
>    deleting the `import_*.epub` scratch file**, contradicting its own class doc —
>    rejected imports silently accumulated orphaned files in app-private storage. Fixed
>    and asserted by both rejection tests.
>
> **Release build (decisions taken deliberately):** `assembleRelease` produces
> `app/build/outputs/apk/release/app-release-unsigned.apk` (20 MB; debug is 29 MB).
> It is **unsigned** (signing docs + env-var wiring in README; keystores must never enter
> the repo) and **not minified** — Readium 3.3.0 ships no consumer ProGuard rules and no
> device exists here to catch a class R8 stripped in error, so release behaviour stays
> identical to the tested debug build. `app/proguard-rules.pro` holds the commented
> starting rules for when R8 is enabled. There is no `testReleaseUnitTest` task in
> AGP 9, so release verification is compilation + packaging plus the debug-variant suite.
>
> **Docs delivered:** `LIMITATIONS.md` (verification matrix, nine device-specific
> limitation areas, first-hardware bring-up checklist), README rewrites (build/signing
> docs, current feature list, 131-test overview).
>
> **Still open — needs hardware, do not claim otherwise:** the profiling checkbox (no
> device to measure memory or rendering on), and the Definition-of-Done item "verified on
> at least one target e-paper device", which is **not met** — 1–8 of the `LIMITATIONS.md`
> §3 checklist are the gate. Robolectric recreation is not a real rotation; the loopback
> server is not the real internet; fixtures are not real-world DRM'd EPUBs.

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
