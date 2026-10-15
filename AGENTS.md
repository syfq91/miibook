# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

## What this is

MiiReader is a native Android EPUB reader optimized for e-paper devices (BOOX,
Bigme, etc.). Kotlin + Jetpack Compose + Material 3, MVVM, repository pattern,
**manual dependency injection** (no Hilt/Dagger).

`plan.md` is the master spec and roadmap. **Section 10 of `plan.md` ("Instructions
to the Coding Agent") is binding** — read it before making changes. Core rules
there: work incrementally, keep the project compiling, consult official Readium
docs for exact APIs, and never claim an unverified feature (especially hardware
refresh control) works.

## Build & test

```sh
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # unsigned release APK -> app/build/outputs/apk/release/
./gradlew testDebugUnitTest      # local unit tests
./gradlew :app:compileDebugKotlin  # fast compile check
```

- JDK 17 toolchain (`jvmToolchain(17)`), Android SDK platform **36** installed.
  There is **no** platform 37 on this machine.
- Local unit tests that need a real `Uri`/`Context` run under **Robolectric**.
  Annotate the class `@RunWith(RobolectricTestRunner::class)` and
  `@Config(sdk = [34])`.
- AGP 9 creates **no `testReleaseUnitTest`** task — release verification is
  `assembleRelease` (compilation + packaging) plus the debug-variant suite.
  Release is unsigned and intentionally **not** minified (R8 off; see
  `app/proguard-rules.pro` header and `LIMITATIONS.md` §2.6).

## Version pins — do not "upgrade" without reading this

- **Readium is pinned to `3.3.0`** (`gradle/libs.versions.toml`). This is
  deliberate. Readium **3.4.0**'s AAR declares `minCompileSdk=37`, which fails
  this project's build hard (`compileSdk 36`, AGP 9.0.1 which caps at 36). Do
  not bump Readium unless compileSdk and AGP are raised together.
- **Robolectric `@Config(sdk = [34])`, not 36.** Robolectric's SDK 36 sandbox
  requires Java 21; the Gradle test toolchain is Java 17, so SDK 36 throws
  `UnsupportedOperationException`. SDK 34 works on Java 17.
- Coil is declared in the version catalog but **not yet added to
  `app/build.gradle.kts` dependencies and not used anywhere.** It is staged for
  Phase 2+ (book covers).

## Readium API gotchas (verified against 3.3.0 bytecode)

The docs online are often for a different version. These bite hard:

- `Try` has **`fold(onSuccess, onFailure)`**, `getOrNull()`, `onSuccess`,
  `onFailure` — but **no `getOrElse`**. Use `fold`.
- `File.toUrl()` **requires** an argument: `file.toUrl(isDirectory = false)`.
- `Publication` is **not** `AutoCloseable` — do **not** use `.use { }`. Close in
  a `try { } finally { publication.close() }`.
- `Metadata.title` is a plain `String?` (no `.toString()` needed).
  `Metadata.authors` is a `List<Contributor>` — see the OPDS notes below for
  how to get a readable name out of one.
- Exact class locations:
  - `org.readium.r2.shared.util.asset.AssetRetriever`
  - `org.readium.r2.shared.util.http.DefaultHttpClient`
  - `org.readium.r2.shared.util.toUrl` (extension)
  - `org.readium.r2.streamer.PublicationOpener`
  - `org.readium.r2.streamer.parser.DefaultPublicationParser` (note the `.parser`
    package)
- `DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory)` —
  `pdfFactory` is nullable; pass `null` for EPUB-only support.
- `assetRetriever.retrieve(url)` and `opener.open(asset, allowUserInteraction =
  false)` both return `Try`.
- `VisualNavigator` does **not** declare `goForward`/`goBackward`; those live on
  `OverflowableNavigator` (which `EpubNavigatorFragment` implements). Anything
  that turns pages should depend on `ui/reader/ReaderNavigator` instead of a
  Readium navigator type.
- `Publication.locatorFromLink(link)` returns **null** unless the href exists in
  the reading order *and* that reading-order `Link` has a `mediaType`. A
  `Manifest` carrying only a `tableOfContents` resolves nothing — always build
  the reading order too (see `testPublication` in the test doubles).
- **`Metadata.authors` is `List<Contributor>`, not `List<LocalizedString>`** —
  the older note above is stale. The readable name is
  `authors.firstOrNull()?.localizedName?.string`; `toString()` on a `Contributor`
  prints the whole data class.
- **Anything that builds a Readium `Url`/`AbsoluteUrl`, or parses an OPDS feed,
  needs Robolectric** even with no Activity involved: those types are backed by
  `android.net.Uri`, which is a stub that throws in a plain JVM test. Every
  parser test in `OpdsFeedParserTest` failed with a bogus "invalid address"
  until the class was annotated `@RunWith(RobolectricTestRunner::class)` +
  `@Config(sdk = [34])`.
- **OPDS 1 navigation is entries, not feed-level links.** `OPDS1Parser` fills
  `Feed.navigation` only from `<entry>` elements that carry *no* acquisition link
  (a sub-section); the feed's own `<link>` elements are self/search descriptors
  and never become navigation. A fixture with only feed-level links correctly
  reports an empty `navigation` — that is not a mapping bug.
- Readium's Compose navigators (`readium-navigators-web-reflowable` plus its
  `-common` / `-internals` siblings) are **not published to Maven Central** for
  3.3.0 — every version returns HTTP 404. Only the Android/Fragment navigator is
  consumable while Readium stays pinned to 3.3.0.
- `EpubNavigatorFragment`'s constructor is `internal`. Build it via
  `EpubNavigatorFactory(publication).createFragmentFactory(...)` +
  `factory.instantiate(...)`. Install
  `EpubNavigatorFragment.createDummyFactory()` on the FragmentManager **before**
  `super.onCreate()` so system restoration does not crash, and remove the
  restored dummy immediately afterwards — its `onResume()` throws
  `RestorationNotSupportedException`.

To verify an API, inspect the actual AAR in
`~/.gradle/caches/modules-2/files-2.1/org.readium.kotlin-toolkit/` rather than
trusting online docs.

## E-paper constraints (from plan.md)

These override normal Android UI instincts. The theme lives in `ui/theme/`, and
reusable widgets in `ui/components/` already follow these rules — reuse them.

- **No animations**: no shimmer, fade, ripple, or animated transitions. NavHost
  uses `EnterTransition.None` / `ExitTransition.None`. Clickables use
  `indication = null` and a remembered `MutableInteractionSource`.
- **No** gradients, blur, translucent surfaces, or decorative shadows.
- **No continuously spinning loaders** — use static text (see
  `EpaperLoadingIndicator`).
- Solid, static colors; monochrome high-contrast by default.
- Stable layouts; stable list-item keys (`LazyColumn` items keyed by book id).
- ~48dp touch targets; respect system font scaling.

`platform/epaper/EpaperController` must stay vendor-neutral. **Never** call
undocumented/vendor shell commands or hidden APIs. Device-specific refresh
control is Phase 5 and requires a confirmed manufacturer SDK.

## Architecture & conventions

- **DI**: `di/AppContainer` (interface) + `DefaultAppContainer` (lazy
  singletons) + `di/ViewModelFactory`. Add new dependencies to all three. No Hilt.
- Repositories are an **interface + `Default*` implementation** pair in
  `data/repository/`. ViewModels depend only on the interface.
- ViewModels expose immutable UI state via `StateFlow`, built with `combine(...)`
  and `stateIn(..., SharingStarted.WhileSubscribed(5000), ...)`.
- Dispatchers are injected via `core/dispatchers/CoroutineDispatchers` (never
  hardcode `Dispatchers.IO` in classes under test); tests pass `TestDispatchers`.
- Errors flow through `core/error/ErrorHandler` (a `SharedFlow<AppError>`) and
  `AppError` subtypes. User-facing messages should be plain and non-technical.
- Persistence is Room (`data/local/`). When changing `BookEntity` or other
  entities, **bump the DB version** in `AppDatabase`; migration is currently
  `fallbackToDestructiveMigration(dropAllTables = true)` (acceptable pre-release,
  no users yet). Add DAO queries as `@Query` methods on the DAO interface.

## Testing

- Test doubles live in `app/src/test/java/com/miireader/test/` (e.g.
  `FakeBookRepository`, `FakeBookImporter`). When you add a repository method,
  update **both** the interface/impl and the corresponding fake, or compilation
  of the tests breaks.
- Local tests needing `Uri`/`Context` use Robolectric (see above).
- `stateIn(WhileSubscribed(...))` conflates: a collector sees updates late while
  `viewModel.uiState.value` is already current. Subscribe to keep the sharing
  alive, then assert on `.value` rather than on values captured from the
  collected stream — see `ReaderViewModelTest.activate`.
- There is no instrumentation test suite yet beyond the default scaffolding.
- **`RoomDatabase` does not implement `Closeable`** — `AppDatabase.create(...).use { }`
  does not compile. Close databases explicitly in `try { } finally { }` (or an
  `@After`).
- **Never let tests reach the real internet.** Network behaviour is covered with
  fixtures (`test/EpubFixtures.kt` for EPUBs, `data/opds/OpdsFixtures.kt` for
  feeds) and a loopback `com.sun.net.httpserver` server
  (`data/opds/ReadiumOpdsHttpClientTest`).
- Failed imports must not leave scratch files: `DefaultBookImporter` deletes its
  `import_*.epub` temp file on **every** exit path, and
  `BookImporterRobolectricTest` asserts `booksDir` is empty after each rejection.
- **E-paper UI rules are machine-enforced** by `ui/EpaperUiConstraintTest`, which
  reads the real sources under `app/src/main/java/com/miireader` and fails the
  build on animation APIs, animated NavHost transitions, clickables without
  `indication = null`, gradients/blur/shadow/translucent colors/non-zero
  elevation, spinning loaders or polling in `ui/`, lazy lists without `key =`,
  non-`sp` text sizes, `fontScale` overrides, a `MinTouchTarget` other than 48dp,
  and Settings copy that promises refresh behaviour MiiReader cannot deliver. If
  you add UI that trips it, fix the UI — do not relax the test.
  `ui/theme/EpaperThemeTest` separately asserts WCAG contrast and full opacity of
  the palette, computed from the real `Color` values.

## Git

- Repo-local identity is set to `MiiReader <miireader@local>`; match existing
  commit style: conventional commits (`feat:`, `docs:`, `chore:`), imperative
  mood, body explaining *why* for non-obvious changes.
- Remote: `git@github.com:syfq91/miibook.git`, branch `main`.
- `local.properties` is gitignored (contains `sdk.dir`) — never commit it.

## Current status

Phase 1 (foundation), Phase 2 (local EPUB library + SAF import), Phase 3 (EPUB
reader), Phase 4 (OPDS catalog browser + downloads), Phase 5 (e-paper
optimization) and Phase 6 (hardening and release) are implemented. Phase 5 = an
audit whose rules now live in `EpaperUiConstraintTest`/`EpaperThemeTest`,
removal of three Settings controls that advertised refresh/animation behaviour
no verified API provides (`animationsEnabled` and `refreshIntervalPages`
preferences deleted), and a documented evaluation of vendor refresh APIs:
BOOX/Onyx has a vendor SDK on an HTTP-only Maven repo with pre-AndroidX deps,
Bigme has none documented, Android itself has none — so **no vendor integration
was implemented** and `platform/epaper/EpaperController` remains a
vendor-neutral no-op seam. `plan.md`'s Phase 5 section carries the sources and
the integration path to follow if hardware ever confirms the SDK.

Phase 6 added the hardening suite (131 tests total): destructive-migration
tests for v1/v2 databases, malformed/large/unsupported EPUB fixtures against
real Readium, real imports over `content://` streams, loopback HTTP tests for
every network failure mode, mid-stream interruption of downloads, offline
reading + progress restoration across a database restart, and activity
recreation. It also **produced the release build** (`assembleRelease` →
unsigned, unminified APK; signing docs in README) and `LIMITATIONS.md`, which
is the canonical list of device-specific gaps and the first-hardware bring-up
checklist. Two bugs those tests caught are fixed: author metadata came from
`Contributor.toString()`, and rejected imports leaked `import_*.epub` scratch
files.

**Verification is unit tests plus `assembleDebug`/`assembleRelease` only** —
there is no device or emulator on this machine. Page rendering, hardware
page-turn keys, rotation, e-paper refresh, *every Phase 4 network and download
path against the real internet*, *all Phase 5 device behaviour*, memory and
rendering performance, and R8-minified release behaviour have **not** been
observed on hardware; never claim any of them work without testing them. The
Definition-of-Done item "verified on at least one target e-paper device" is
**not met** — the gate is `LIMITATIONS.md` §3.
