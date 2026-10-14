# MiiReader

An EPUB reader for Android, optimized for e-paper devices (BOOX, Bigme, and
similar). Text-first, monochrome, and deliberately free of the animations and
gradients that look bad and cause ghosting on e-ink displays.

Inspired by CrossPoint Reader's reading-focused design, but built as a native
Android app — not a firmware reproduction.

**Status:** v0.1.0 — Phase 2 of 6 complete. You can import local EPUB files into
an offline library. The reader itself is not built yet (Phase 3).

## Features

**Working today**

- Import EPUBs via the system file picker (Storage Access Framework)
- Offline library — imported files are copied into app-private storage
- Duplicate-import detection (SHA-256 content checksum)
- Clear error handling for invalid, corrupt, or unsupported files
- Search by title and author
- Filter by All / Recent / Unread
- Sort by last-opened, title, or author
- E-paper theme: pure monochrome, high contrast, no animations or shadows
- Warm-white / pure-white background toggle
- Static, non-animated progress indicators and screen transitions

**Not yet implemented**

- The actual EPUB reader (Phase 3)
- Bookmarks and reading-position restore (Phase 3)
- OPDS catalog browsing and downloads (Phase 4)
- Device-specific e-paper refresh control (Phase 5)

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.3.20 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM, repository pattern, manual DI |
| EPUB engine | Readium Kotlin Toolkit 3.3.0 |
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
./gradlew assembleDebug      # build the debug APK
./gradlew testDebugUnitTest  # run unit tests
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Testing

17 local unit tests cover the bookshelf view model (search, filter, sort,
delete, import outcomes) and the book repository. Local tests that need a real
`Uri` or `Context` run under Robolectric.

```sh
./gradlew testDebugUnitTest
```

Rendering, page-turn, and e-paper refresh behavior must still be verified on
physical hardware — Compose-level tests cannot confirm panel refresh modes.

## Project structure

```
app/src/main/java/com/miireader/
├── core/           dispatchers, error types, shared models
├── data/
│   ├── epub/       Readium publication opening + metadata extraction
│   ├── local/      Room database, DAOs, entities
│   ├── preferences/ DataStore-backed user settings
│   └── repository/ library, import, progress, bookmark repositories
├── di/             manual dependency container + view-model factory
├── platform/epaper/ vendor-neutral display integration
└── ui/
    ├── bookshelf/  library screen
    ├── catalog/    OPDS browser (placeholder)
    ├── components/ reusable e-paper UI widgets
    ├── navigation/ routes
    ├── settings/   reading + e-paper preferences
    └── theme/      monochrome design system
```

## E-paper design principles

- No gradients, blur, translucent surfaces, or decorative shadows
- No shimmer, fade, or automatic transitions — screen changes are instant
- No continuously spinning loaders — explicit static text instead
- Stable layouts and stable list-item identity to minimize redraws
- ~48dp touch targets; respects system font scaling

The app detects known e-paper manufacturers (Onyx/BOOX, Bigme, Boyue, MeeBook)
but deliberately issues **no** vendor-specific or undocumented refresh calls.
Refresh behavior is left to the system until a documented device SDK is
confirmed.

## Roadmap

The full plan lives in [`plan.md`](plan.md).

- [x] Phase 1 — project foundation, theme, navigation
- [x] Phase 2 — local EPUB library and import
- [ ] Phase 3 — EPUB reader (Readium navigator, bookmarks, position restore)
- [ ] Phase 4 — OPDS catalogs and downloads
- [ ] Phase 5 — e-paper optimization and hardware validation
- [ ] Phase 6 — hardening and release

## License

[MIT](LICENSE) © 2026 syfq91.
