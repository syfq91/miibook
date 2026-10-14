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
./gradlew testDebugUnitTest      # local unit tests
./gradlew :app:compileDebugKotlin  # fast compile check
```

- JDK 17 toolchain (`jvmToolchain(17)`), Android SDK platform **36** installed.
  There is **no** platform 37 on this machine.
- Local unit tests that need a real `Uri`/`Context` run under **Robolectric**.
  Annotate the class `@RunWith(RobolectricTestRunner::class)` and
  `@Config(sdk = [34])`.

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
  `Metadata.authors` is a `List<LocalizedString>` — use `.firstOrNull()
  ?.toString()`.
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
- There is no instrumentation test suite yet beyond the default scaffolding.

## Git

- Repo-local identity is set to `MiiReader <miireader@local>`; match existing
  commit style: conventional commits (`feat:`, `docs:`, `chore:`), imperative
  mood, body explaining *why* for non-obvious changes.
- Remote: `git@github.com:syfq91/miibook.git`, branch `main`.
- `local.properties` is gitignored (contains `sdk.dir`) — never commit it.

## Current status

Phase 1 (foundation) and Phase 2 (local EPUB library + SAF import) are done and
pushed. **The reader screen is not built** (Phase 3). Tapping a book currently
shows a placeholder. OPDS/catalog is a placeholder. Do not claim reading works
until Phase 3 is implemented and verified.
