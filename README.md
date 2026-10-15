# MiiReader

An EPUB reader for Android, optimized for e-paper devices (BOOX, Bigme, and
similar). Text-first, monochrome, and deliberately free of the animations and
gradients that look bad and cause ghosting on e-ink displays.

Inspired by CrossPoint Reader's reading-focused design, but built as a native
Android app — not a firmware reproduction.

**Status:** v0.1.0 — all six phases of [`plan.md`](plan.md) implemented; this is
a **release candidate**, not a verified product. Everything is backed by 131
local unit tests plus debug/release builds — but no device or emulator exists
on the build machine, so nothing has been observed on e-paper hardware. Read
[`LIMITATIONS.md`](LIMITATIONS.md) before trusting anything on a real device.

## Features

**Working today**

- Import EPUBs via the system file picker (Storage Access Framework)
- Offline library — imported files are copied into app-private storage
- Duplicate-import detection (SHA-256 content checksum)
- Clear error handling for invalid, corrupt, or unsupported files
- Search by title and author
- Filter by All / Recent / Unread
- Sort by last-opened, title, or author
- E-paper theme: pure monochrome, high contrast, no animations or shadows —
  enforced by a source-scanning test so regressions fail the build
- Warm-white / pure-white background toggle
- Static, non-animated progress indicators and screen transitions
- Reading screen built on Readium's WebView navigator (paginated, no Compose
  pagination)
- Previous/next page controls, plus hardware page-turn keys (`PAGE_UP` /
  `PAGE_DOWN`)
- Table of contents and chapter navigation
- Font-size adjustment and Light / Dark / Sepia page themes
- Bookmarks — create, list, jump, remove
- Reading position persisted and restored on reopen
- Minimal toolbar that can be hidden while reading
- OPDS catalog browser — save catalog addresses, browse feeds (OPDS 1 and 2),
  open sub-feeds, and pick an acquisition format
- Download queue with progress, cancel, and retry; downloads are validated
  against the EPUB engine before they enter the library
- Downloads screen (active / completed / failed) with offline access to
  finished books
- Settings: e-paper display guidance, reading defaults, library defaults, and
  device diagnostics

**Deliberately not implemented**

- Device-specific refresh control — no vendor SDK has been confirmed on
  hardware (evaluation recorded in `plan.md` Phase 5). Refresh stays with the
  device's own system settings.
- Anything listed in [`LIMITATIONS.md`](LIMITATIONS.md)

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.3.20 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM, repository pattern, manual DI |
| EPUB engine | Readium Kotlin Toolkit 3.3.0 |
| OPDS | Readium's OPDS 1/2 parsers behind `OpdsHttpClient` |
| Persistence | Room 2.8.5 |
| Preferences | DataStore |
| Async | Coroutines + Flow |
| Build | Gradle Kotlin DSL, AGP 9.0.1 |

Minimum SDK 24 (Android 7.0), target/compile SDK 36.

> Readium is pinned to **3.3.0**. Version 3.4.0 requires compiling against
> Android SDK 37, which this project's toolchain does not yet support.

## Building

Requires a JDK 17+ and the Android SDK (platform 36).

```sh
./gradlew assembleDebug          # debug APK   -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests (JVM + Robolectric)
./gradlew assembleRelease        # release APK -> app/build/outputs/apk/release/
```

(There is no release-variant unit-test task in AGP 9; the same 131 tests run
against the debug variant, and `assembleRelease` verifies release compilation.)

The release build is **unsigned** and **not minified** (R8 off): Readium ships
no consumer keep rules and there is no device here to smoke-test a shrunk APK —
see `LIMITATIONS.md` §2.6 and the commented rules in `app/proguard-rules.pro`.

### Signing a release

Signing credentials never live in this repository. Create a keystore once,
outside the repo, then wire it up locally:

```sh
keytool -genkeypair -v -keystore ~/keystores/miireader.jks -keyalg RSA \
        -keysize 2048 -validity 10000 -alias miireader
```

```kotlin
// app/build.gradle.kts  (local only — do not commit credentials)
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("MIIREADER_STORE_FILE") ?: "missing.jks")
            storePassword = System.getenv("MIIREADER_STORE_PASSWORD")
            keyAlias = System.getenv("MIIREADER_KEY_ALIAS")
            keyPassword = System.getenv("MIIREADER_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            if (System.getenv("MIIREADER_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
```

`MIIREADER_STORE_FILE` pointing at the keystore makes `assembleRelease`
produce an installable signed APK; without it you get
`app-release-unsigned.apk`. Before publishing anything, run the first-hardware
bring-up checklist in `LIMITATIONS.md` §3 against the release APK.

## Testing

131 local unit tests cover the library, import (including malformed, empty,
and unsupported EPUBs), reader state, bookmarks and progress across database
restarts, database upgrades from v1/v2 schemas, the OPDS parser and HTTP
layer (against fixtures and a loopback server), the download queue (including
interrupted transfers), settings, the e-paper design rules, and activity
recreation. Tests needing a real `Uri`, `Context`, `Room`, or Readium `Url`
run under Robolectric (SDK 34).

```sh
./gradlew testDebugUnitTest
```

There is no instrumentation/UI test suite — no emulator exists on the build
machine. Rendering, page-turn keys, rotation on a panel, and e-paper refresh
behavior must be verified on physical hardware per `LIMITATIONS.md` §3.

## Project structure

```
app/src/main/java/com/miireader/
├── core/            dispatchers, error types, input bridges, shared models
├── data/
│   ├── epub/        Readium publication opening + metadata extraction
│   ├── local/       Room database, DAOs, entities
│   ├── download/    download queue (fetch -> validate -> import)
│   ├── opds/        OPDS HTTP client, feed parsing, catalog models
│   ├── preferences/ DataStore-backed user settings
│   └── repository/  library, import, progress, bookmark, catalog repositories
├── di/              manual dependency container + view-model factory
├── platform/epaper/ vendor-neutral display integration (no-op seam)
├── ui/
│   ├── bookshelf/   library screen
│   ├── catalog/     OPDS browser
│   ├── components/  reusable e-paper UI widgets
│   ├── downloads/   download queue screen
│   ├── navigation/  routes
│   ├── reader/      reading screen, Readium navigator host, reader panels
│   ├── settings/    reading + e-paper preferences
│   └── theme/       monochrome design system
└── MiiReaderApplication.kt, MainActivity.kt
```

## E-paper design principles

- No gradients, blur, translucent surfaces, or decorative shadows
- No shimmer, fade, or automatic transitions — screen changes are instant
- No continuously spinning loaders — explicit static text instead
- Stable layouts and stable list-item identity to minimize redraws
- ~48dp touch targets; respects system font scaling

These rules are not just conventions: `ui/EpaperUiConstraintTest` scans the
sources and fails the build if a forbidden construct (animation API, animated
navigation, ripple, translucent color, missing list key, non-`sp` text…)
reappears, and `ui/theme/EpaperThemeTest` asserts WCAG contrast of the palette.

The app detects known e-paper manufacturers (Onyx/BOOX, Bigme, Boyue, MeeBook)
but deliberately issues **no** vendor-specific or undocumented refresh calls.
Refresh behavior is left to the system until a documented device SDK is
confirmed — see `plan.md` Phase 5 for the evaluation.

## Roadmap

The full plan lives in [`plan.md`](plan.md); known gaps live in
[`LIMITATIONS.md`](LIMITATIONS.md).

- [x] Phase 1 — project foundation, theme, navigation
- [x] Phase 2 — local EPUB library and import
- [x] Phase 3 — EPUB reader (Readium navigator, bookmarks, position restore)
- [x] Phase 4 — OPDS catalogs and downloads
- [x] Phase 5 — e-paper optimization (rules enforced in tests; hardware items open)
- [x] Phase 6 — hardening and release (profiling + device validation remain
      open, see `plan.md` Phase 6)

## License

[MIT](LICENSE) © 2026 syfq91.
