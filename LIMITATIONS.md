# Known Limitations

MiiReader is built and verified on a machine with **no Android device and no
emulator**. Everything below is either a known gap in that verification or a
deliberate limitation of the current build. Nothing in this file may be
"resolved" without evidence from a real device.

Last reviewed: 2026-10-15 (Phase 6).

## 1. How this project is verified

| Verified here | Not verified here |
|---|---|
| ~130 local unit tests (JVM + Robolectric) | Anything on a physical screen |
| `assembleDebug` and `assembleRelease` succeed | Actual e-paper refresh, ghosting, contrast |
| EPUB parsing/opening against generated fixtures | Hardware page-turn key dispatch |
| OPDS parsing against fixtures; HTTP layer against a **loopback** server | A live OPDS catalog over the real internet |
| Room persistence across reopen, destructive upgrade from v1/v2 | Behaviour on a device upgrade path with real user data |
| E-paper UI rules, enforced by a source-scanning test | Rendering quality, memory use, performance under load |

Verification = unit tests + APK builds. Per `plan.md`, no feature may be
claimed to "work" on hardware until it has been observed there.

## 2. Device-specific limitations

### 2.1 No refresh control (by design, until a vendor SDK is confirmed)

- Android itself exposes **no** e-ink refresh API.
- Onyx/BOOX publishes a vendor SDK (`com.onyx.android.sdk:onyxsdk-device`,
  `EpdController`) but only on an HTTP-only Maven repository, with
  pre-AndroidX dependencies, and only for BOOX hardware.
- Bigme has no documented public refresh SDK; known community routes use
  hidden/reflective vendor calls, which this project forbids.

So `platform/epaper/EpaperController` is a **deliberate no-op seam**. The app
never forces a full refresh, never sets a refresh mode, and never shells out to
vendor commands. Ghosting and refresh cadence are entirely whatever the
device's own system settings decide — Settings points users there. The full
evaluation and the steps to integrate the BOOX SDK (once confirmable on
hardware) are recorded in `plan.md` Phase 5.

### 2.2 Page-turn keys

- Only `KEYCODE_PAGE_UP` / `KEYCODE_PAGE_DOWN` are handled. Volume keys are
  deliberately **not** swallowed (that would break ringer/media controls), so
  a device whose buttons emit `VOLUME_*` or `DPAD_*` will not turn pages with
  this app unless the system remaps those buttons.
- The key-decision logic and the reader's handler are unit-tested, but no real
  `KeyEvent` has traversed `MainActivity.dispatchKeyEvent` on a device.

### 2.3 E-paper detection is a string match

`DefaultEpaperController.isEpaperDevice` compares `Build.BRAND` /
`Build.MANUFACTURER` against known vendors (Onyx, BOOX, Bigme, Boyue, MeeBook,
Moan). It does not probe hardware. A device that reports something else shows
"Not detected" in Settings — that is a cosmetic difference today, since no
code branches on the flag besides the diagnostics row.

### 2.4 Layout is not tuned to real panels

- List density, reader margins, and divider/border contrast are fixed dp
  values chosen from the design spec, not measured on a panel. One measured
  data point from the palette test: the `#CCCCCC` hairline is 1.61:1 on white
  but 1.47:1 on the warm background — worth revisiting on hardware.
- System font scaling is respected (all text is `sp`, no `fontScale`
  overrides), but layout at large accessibility scales (1.5×/2.0×) has never
  been rendered.

### 2.5 Rendering and performance are unmeasured

- Chapter layout, pagination, images, embedded fonts, and WebView behaviour
  come from Readium's `EpubNavigatorFragment`; how that looks (and how much
  memory a large book uses) on an e-ink panel is **unprofiled**. Phase 6's
  "profile memory use and EPUB rendering performance" cannot be done without a
  device and remains open.
- The app avoids the usual e-paper sins (no animations, no translucent
  layers, no continuously spinning loaders) — enforced by
  `ui/EpaperUiConstraintTest` — but that is a code-level guarantee, not a
  measured refresh rate.

### 2.6 Release build is unsigned and unshrunk

- `assembleRelease` produces an **unsigned** APK; signing needs a keystore
  that must never live in this repository (see README "Release builds").
- R8/minification is **off** in release: Readium 3.3.0 ships no consumer
  keep rules, and no device exists here to catch a stripped class at runtime.
  The release APK therefore behaves like the tested debug build. Enabling R8
  is a documented follow-up that must be smoke-tested on hardware first.

### 2.7 Destructive database migrations

`AppDatabase` still uses `fallbackToDestructiveMigration(dropAllTables =
true)`: upgrading from any pre-release schema version **wipes** books,
progress, bookmarks, catalogs, and downloads rather than migrating them.
Acceptable while there are no users; it must change before a public release.
`AppDatabaseMigrationTest` pins the current contract (v1/v2 databases open at
v3 without crashing, with legacy rows dropped).

### 2.8 OPDS catalogs and downloads over the real internet

The HTTP layer is tested against a loopback server and feeds against
fixtures. Not covered: TLS interception/cert quirks, captive portals, proxies,
very large feeds, servers that stream slowly until Readium's timeout, and
real-world EPUBs with DRM or exotic packaging. `Internet` permission exists;
live-catalog behaviour is unverified.

### 2.9 No instrumentation or UI tests

The `androidTest` sourceset holds only AGP scaffolding. There is no emulator
on the build machine, so no UI test has ever run: navigation flows, Compose
layout, WebView rendering, and activity recreation on a real rotation are
exercised only to the limit Robolectric allows.

## 3. First-hardware bring-up checklist

When a BOOX/Bigme unit is available, in order:

1. Install the debug APK; import a known-good EPUB via the file picker.
2. Read a chapter; confirm pagination and text quality; turn pages with the
   hardware buttons and note which key codes the device sends.
3. Kill the app; reopen; confirm position, bookmarks, and library restore.
4. Rotate (or fold, if applicable); confirm the reader keeps its position.
5. Toggle refresh modes in the *device* settings; watch ghosting after page
   turns and after opening the bookshelf; note whether MiiReader triggers
   unwanted full refreshes (it issues no refresh commands of its own).
6. Set system font scaling to 1.5× and 2.0×; check bookshelf, settings, and
   reader chrome for clipped text or undersized touch targets.
7. Browse a live OPDS catalog over Wi-Fi, start a download, toggle Wi-Fi off
   mid-download, confirm the failure is reported and retry works after
   reconnecting.
8. Profile memory while opening a large book (this closes the Phase 6
   profiling item).
9. Only after 1–8 pass: consider enabling R8 and re-running the whole list
   against the release APK.
