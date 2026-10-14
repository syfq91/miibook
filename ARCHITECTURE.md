# MiiReader — Design

Technical design notes: how the pieces fit together and *why* they were built
this way. For the roadmap and feature plan see [`plan.md`](plan.md); for build
instructions see [`README.md`](README.md).

## Goals that drive the design

1. **E-paper first.** Every UI decision optimizes for a monochrome, slow-refresh
   display: no animation, no gradients, stable layouts, minimal redraws.
2. **Offline reliability.** A book the user imported must open with no network
   and no permissions over the original file location.
3. **Maintainable seams.** The EPUB engine, persistence, and platform quirks sit
   behind interfaces so they can be swapped, faked in tests, or deferred to later
   phases without touching UI code.

## Layered architecture

Data flows one way. UI never talks to Room, DataStore, or Readium directly.

```
ui/  (Compose + ViewModel, StateFlow of immutable UI state)
 │   depends only on repository *interfaces* and injected dispatchers
 ▼
data/repository/  (orchestration + business rules; interface + Default* impl)
 │
 ├── data/epub/        Readium publication opening
 ├── data/local/       Room (books, reading_progress, bookmarks)
 ├── data/preferences/ DataStore (settings)
 └── platform/epaper/  vendor-neutral display integration
```

`core/` holds cross-cutting pieces used everywhere: `CoroutineDispatchers`,
`AppError`/`ErrorHandler`, and shared models (`SortOrder`, `FilterMode`).

### Dependency injection

Manual DI — no Hilt. A single `AppContainer` (interface) with a
`DefaultAppContainer` exposes lazily-constructed singletons; `ViewModelFactory`
reads from it to build ViewModels.

The interface/impl split matters: tests construct ViewModels with fakes and never
touch the container. **Adding a dependency means updating three files** — the
container interface, its impl, and the factory.

## Data model

Three Room entities, version 2. `exportSchema` is off; migration is currently
`fallbackToDestructiveMigration(dropAllTables = true)`, which is acceptable
because there are no users yet. Real migrations become mandatory at release.

- **`books`** — id (UUID), title, author, `file_path` (absolute path in
  app-private storage), optional `cover_path`, `import_date`,
  `last_opened_date`, `download_status`, and `content_checksum`.
- **`reading_progress`** — keyed by `book_id` (FK, cascade delete). Stores a
  **serialized Readium `Locator`** (`locator_json`), not a page number, plus a
  cached `progress_percentage`. Locator is deliberate: pagination shifts with
  font size and screen, so page numbers can't restore a position reliably.
- **`bookmarks`** — keyed by `book_id` (FK, cascade delete), with
  `locator_json`, optional title/chapter label, and `created_at`.

`content_checksum` (SHA-256 of file bytes) exists solely for duplicate-import
detection; it was added in Phase 2 along with the v1→v2 bump.

## Import pipeline (Phase 2)

The one complete vertical slice. `DefaultBookImporter.importBook(uri)`:

```
content:// URI (SAF OpenDocument)
        │
        ▼
1. copy bytes  ──►  filesDir/books/import_<rand>.epub   (temp file)
        │
        ▼
2. extract metadata via Readium   (title, author)
        │   └─ on failure: delete temp, return Failure(reason)
        ▼
3. SHA-256 checksum
        │   └─ if checksum already in library: delete temp, return Duplicate
        ▼
4. rename temp ──►  filesDir/books/<timestamp>.epub
        │
        ▼
5. insert BookEntity ──► Room  ──► bookshelf Flow auto-refreshes
```

**Why copy instead of persisting URI permissions:** SAF grants are revocable and
provider-dependent. Copying into `filesDir/books/` makes the library genuinely
offline and immune to the source provider disappearing. The cost is disk space,
which is the right trade for a reader.

**Why metadata is extracted before the DB write:** a corrupt or non-EPUB file is
rejected *before* any row exists, so the library never contains a book that
can't be opened. Any partially-written temp file is removed on every failure
path, so `books/` never accumulates orphans.

**Error taxonomy:** `ImportResult` is a sealed type — `Success` / `Duplicate` /
`Failure(reason)`. The `reason` strings are plain-language and safe to show
directly in the UI error banner.

## UI state management

ViewModels expose a single `StateFlow<UiState>` assembled with `combine(...)`
over repository flows + preference flows + local interaction state, materialized
via `stateIn(scope, SharingStarted.WhileSubscribed(5000), initial)`.

Consequences:
- UI is a pure function of state; it never mutates data itself.
- Persistence-backed changes (e.g. a new import) propagate automatically through
  Room's `Flow` — no manual refresh calls.
- Filtering/sorting happens in the ViewModel over the emitted list, keyed by the
  user's persisted `SortOrder`/`FilterMode` preferences.

The bookshelf `LazyColumn` keys items by stable book **id**, so rows keep
identity across updates and e-paper doesn't repaint the whole list.

## E-paper design system

A dedicated monochrome theme (`ui/theme/`) plus reusable widgets
(`ui/components/`) so the constraints are enforced in one place rather than
re-derived per screen.

Palette (`Color.kt`): `PureWhite`/`WarmWhite` backgrounds, `NearBlack` text,
greys for secondary text and borders. Selection uses **inverted high-contrast**
(black bg / white text) rather than tinting, which reads cleanly on e-ink. The
one non-monochrome color is a dark red for error text — chosen because it
survives greyscale rendering while still signaling.

`MiiReaderTheme` takes `warmBackground` and maps it to a Material 3
`lightColorScheme` (there is no dark scheme — e-ink is reflective). The
background choice is exposed via `LocalWarmBackground` for direct reads.

`EpaperDimensions` centralizes touch targets (48dp min), border weights, padding,
and corner radii. **`CornerZero`/`CornerSmall` (0–4dp) only** — large rounded
corners waste space and look soft on a high-contrast display.

Hard rules, enforced by reusing the existing components:
- No shimmer/fade/ripple/animated transitions. Navigation uses
  `EnterTransition.None`/`ExitTransition.None`; clickables pass
  `indication = null` with a remembered `MutableInteractionSource`.
- No continuously spinning loaders → `EpaperLoadingIndicator` shows static text.
- No gradients, blur, translucency, or decorative shadows.

## Platform integration

`platform/epaper/EpaperController` is an interface with a safe default
(`DefaultEpaperController`) that performs **no** vendor calls. It detects known
e-paper manufacturers (Onyx/BOOX, Bigme, Boyue, MeeBook) but deliberately does
nothing vendor-specific — no hidden APIs, no shell commands, no polling.

This is a deliberate stall: BOOX and Bigme do not expose identical refresh
controls, and undocumented calls risk breakage. Vendor-specific refresh control
is deferred to Phase 5 and will only be added behind a confirmed, documented
SDK. Until then refresh behavior is the system's responsibility.

## Error handling

`core/error/AppError` is a sealed hierarchy (`StorageError`, `DatabaseError`,
`BookParseError`, `NetworkError`, `GeneralError`). `ErrorHandler` is a
`SharedFlow<AppError>` with `emit`/`tryEmit`, letting any layer surface a
user-visible error without crashing or leaving corrupt state.

Domain operations that can fail in expected ways return sealed result types
(`ImportResult`) instead of throwing, so the caller is forced to handle each
case. Exceptions are reserved for genuinely unexpected failures and are caught
at the ViewModel boundary.

## Reader (Phase 3)

`ui/reader/` drives the reading screen. The governing rule from `plan.md`:
**EPUB pagination is never reimplemented with Compose `Text`** — content is
rendered by Readium's `EpubNavigatorFragment` (a WebView), hosted from Compose.

- `ReaderViewModel` opens the publication, restores the saved locator from
  `reading_progress`, builds the table of contents, and persists progress and
  bookmarks. It exposes `uiState` (the usual `combine` + `stateIn`) plus a
  separate `session: StateFlow<ReaderSession?>` carrying the live `Publication`,
  the resolved TOC and the locator to restore into.
- `EpubNavigatorHost` owns the fragment transaction. It defers the transaction
  with `View.post { }` until its `FragmentContainerView` is attached
  (FragmentManager throws "No view found for id" otherwise) and removes the
  fragment in a `DisposableEffect`.
- `ReaderNavigator` is a four-method interface (`currentLocator`, `go`,
  `goForward`, `goBackward`), adapted from Readium's `OverflowableNavigator` by
  `ReadiumReaderNavigator`. `ReaderViewModel` depends only on that interface,
  which is what makes it testable without a WebView.
- `MainActivity` extends `FragmentActivity`, installs
  `EpubNavigatorFragment.createDummyFactory()` *before* `super.onCreate()` so
  system restoration does not crash on the internal constructor, removes the
  restored dummy immediately afterwards, and intercepts `KEYCODE_PAGE_UP` /
  `KEYCODE_PAGE_DOWN` in `dispatchKeyEvent` (volume keys are deliberately left
  alone).
- `android:configChanges` keeps the Activity — and therefore the WebView and its
  scroll position — alive across rotation instead of recreating everything.

Publication lifecycle: `ReaderViewModel.onCleared` closes the `Publication`
explicitly, because `Publication` is **not** `AutoCloseable`. The ViewModel is
scoped to the navigation entry, so popping the reader route closes it.

Progress and bookmarks are stored as the serialized Readium `Locator` plus a
derived percentage. Page numbers are deliberately not persisted: they change
whenever the font size or screen changes.

## Known gaps

- **The reader has never been run on hardware.** Phase 3 was verified with unit
  tests and `assembleDebug` only; there is no device or emulator on the build
  machine. Page rendering, hardware page-turn keys, rotation behaviour and
  e-paper refresh are unproven on a real screen.
- **No e-paper refresh control from the reader.** `EpaperController` is still a
  no-op baseline and nothing in the reading path calls it; the Settings "full
  refresh" button is a placeholder. Phase 5.
- **Catalog/OPDS is a placeholder screen.** Phase 4.
- **Covers are not extracted or displayed.** `BookEntity.cover_path` is reserved;
  Coil is in the version catalog but not yet wired into dependencies.
