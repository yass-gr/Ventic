# Vintage Player — Implementation Plan

Native Android music player that plays **local audio only** (the manifest has no INTERNET permission).
Pixel-faithful port of the Claude Design file `design/Main.component.html`.
The design has 4 screens × dark/light × 4 accent colours.

## 0. Goals & non-goals

| Goal | How |
|---|---|
| Lightning-fast cold start (< 400 ms to first frame with library visible) | Library served from an on-disk binary cache before MediaStore is touched; manual DI; no reflection-heavy libs; R8 + baseline profile (profileinstaller); window background = chassis colour (no white flash) |
| Smooth 60/120 fps | Spectrum/VU/progress read state **in the draw phase** (`Canvas`/`drawBehind` + lambdas), never trigger recomposition per frame; `LazyColumn` with `key` + `contentType`; stable/immutable models |
| Tiny APK | Only Compose foundation (no Material3), Media3, DataStore. No image library: album art via `ContentResolver.loadThumbnail` + `LruCache` |
| Real playback | Media3 ExoPlayer in a `MediaSessionService` → background play, lock-screen/notification controls, audio focus, headphone-unplug pause |
| Local only | MediaStore query of `MediaStore.Audio.Media` (`IS_MUSIC != 0`), optional folder filter |

Non-goals: streaming, lyrics, tag editing, playlists beyond Library/Favorites (v1).

## 1. Stack (pinned in `gradle/libs.versions.toml`)

Kotlin 2.2.10 · AGP 8.11.1 · Gradle 8.14.3 · JDK 21 (bytecode target 17) · compileSdk/targetSdk 36 · **minSdk 29**
Compose BOM 2025.08.00 (ui + foundation only) · Media3 1.8.0 (exoplayer, session) · DataStore Preferences 1.1.7 · Coroutines 1.10.2.

**No other dependencies may be added without approval from the lead.**

## 2. Architecture

```
com.yass.vintageplayer
├── VintageApp / AppGraph            manual DI singleton                     [Phase 0 + lead wiring]
├── MainActivity                     edge-to-edge, permission gate, setContent  [Agent D]
├── data/
│   ├── Models.kt, Stores.kt         CONTRACTS (Track, AppSettings, interfaces) [Phase 0 — frozen]
│   ├── MediaStoreLibrary.kt         LibraryRepository impl                     [Agent A]
│   ├── LibraryCache.kt              binary cache in filesDir                   [Agent A]
│   ├── PrefsStores.kt               FavoritesStore + SettingsStore (DataStore) [Agent A]
│   └── AlbumArt.kt                  thumbnail loader + LruCache                [Agent A]
├── playback/
│   ├── PlayerContract.kt            CONTRACT (PlaybackState, AudioLevels, PlayerConnection) [Phase 0 — frozen]
│   ├── PlaybackService.kt           MediaSessionService + ExoPlayer            [Agent B]
│   ├── LevelMeterProcessor.kt       AudioProcessor → 28 bands + level          [Agent B]
│   └── MediaControllerConnection.kt PlayerConnection impl                      [Agent B]
└── ui/
    ├── theme/                       tokens, fonts, VintageTheme                [Agent C]
    ├── components/                  skeuomorphic widgets                       [Agent C]
    ├── screens/                     Library, Favorites, Player, Settings       [Agent D]
    └── MainViewModel.kt, VintageAppUi.kt                                       [Agent D]
```

Data flow: `MediaStore → LibraryRepository.tracks (StateFlow) → MainViewModel → screens`.
Playback: `screens → MainViewModel → PlayerConnection → MediaController ⇄ PlaybackService(ExoPlayer)`.
Levels: `ExoPlayer audio pipeline → LevelMeterProcessor (singleton, same process) → PlayerConnection.levels → Canvas draw`.

## 3. Execution — waves

### Wave 0 — Foundation (lead) ✅
Toolchain (JDK 21 via mise, Android SDK 36 in `~/Android/Sdk`), Gradle skeleton + wrapper, manifest, fonts (`res/font`), launcher icon, contracts, AppGraph, `PLAN.md`. The project compiles.

### Wave 1 — three agents in parallel, each in its own git worktree/branch
Each agent: **only creates/edits the files it owns**, never edits contracts, and must reach a green
`./gradlew :app:compileDebugKotlin` before finishing.

#### Agent A — Data layer (`agent/data`)
1. `data/LibraryCache.kt`
   - `object LibraryCache { fun read(ctx): List<Track>?; fun write(ctx, tracks) }`
   - File `filesDir/library.bin`, `DataOutputStream(BufferedOutputStream)`; header magic `0x56504C31` + version int + count; each field written with writeLong/writeUTF. Write to a temp file, then rename (atomic). Return null on any exception or bad magic.
2. `data/MediaStoreLibrary.kt` — `class MediaStoreLibrary(ctx, settings: SettingsStore, scope: CoroutineScope) : LibraryRepository`
   - `start()`: once only (AtomicBoolean). On `Dispatchers.IO`: read cache → emit immediately; then `rescan()`.
   - `rescan()`: `isScanning=true`; query `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` with projection `_ID, TITLE, ARTIST, ALBUM, ALBUM_ID, DURATION, RELATIVE_PATH, DATE_ADDED`, selection `IS_MUSIC != 0 AND DURATION >= 10000`. Map with column indexes resolved once (no getColumnIndex inside the loop). `<unknown>` artist → "Unknown artist". Sort with `String.CASE_INSENSITIVE_ORDER` on title. Write cache only if the list differs. `isScanning=false` in `finally`.
   - Keep the unfiltered list internally; `tracks` = list filtered by `settings.folder` (prefix match on relativePath). Re-filter when settings.folder changes (collect `settings.settings.map{it.folder}.distinctUntilChanged()`).
   - `folders` = distinct first path segment + "/" (e.g. "Music/"), sorted.
   - Register a `ContentObserver` on the audio URI; debounce 1500 ms → `rescan()`.
   - If permission is missing, catch `SecurityException` and emit empty list (UI shows the permission gate).
3. `data/PrefsStores.kt`
   - A single `preferencesDataStore(name = "vintage")` delegate.
   - `class DataStoreSettings(ctx, scope) : SettingsStore` — `settings` is a `StateFlow` started with **`runBlocking` first read** (DataStore read is ~5 ms; this avoids a theme flash on start) then `stateIn(scope, Eagerly, initial)`. `update{}` applies the transform to the current value, sets the StateFlow immediately (optimistic), and persists asynchronously.
   - `class DataStoreFavorites(ctx, scope) : FavoritesStore` — stored as a string set of ids; same optimistic pattern.
4. `data/AlbumArt.kt`
   - `object AlbumArt { suspend fun load(ctx, track: Track, sizePx: Int): ImageBitmap? }` — `ContentResolver.loadThumbnail(uri, Size(sizePx,sizePx), null)` on IO; `LruCache<String, ImageBitmap>` sized to 1/16 of the max heap (in bytes via `sizeOf`); cache misses (FileNotFoundException/IOException) as a sentinel so we do not retry; return null.

#### Agent B — Playback (`agent/playback`)
1. `playback/LevelMeterProcessor.kt` — `class LevelMeterProcessor : androidx.media3.common.audio.BaseAudioProcessor`
   - Accepts PCM_16BIT and PCM_FLOAT (return the input format unchanged in `onConfigure`; throw `UnhandledAudioFormatException` for anything else). `queueInput` copies the buffer through unchanged (`replaceOutputBuffer(remaining).put(input).flip()`) and, **before copying**, reads samples (mixing channels to mono) into a 1024-sample ring.
   - Every 1024 samples (throttled to ≤ 30 Hz using sample count), run a tiny radix-2 FFT (hand-written, preallocated arrays, no allocation per frame), group bins into 28 log-spaced bands (60 Hz → 16 kHz), convert to 0..1 via `(20*log10(mag)+60)/60` clamped, then smoothing: attack 0.6, release 0.15. `level` = RMS converted the same way and smoothed.
   - Publish via `companion object { val levels = MutableStateFlow(AudioLevels.SILENT) }` using a double-buffered FloatArray (swap between 2 arrays, new AudioLevels wrapper per emission is fine).
   - `onFlush`/`onReset`: clear state, emit SILENT.
2. `playback/PlaybackService.kt` — `class PlaybackService : MediaSessionService()`
   - `ExoPlayer.Builder(this)` with a custom `DefaultRenderersFactory` overriding `buildAudioSink` → `DefaultAudioSink.Builder(ctx).setAudioProcessors(arrayOf(LevelMeterProcessor())).build()`.
   - `setAudioAttributes(AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC), handleAudioFocus = true)`, `setHandleAudioBecomingNoisy(true)`, `setWakeMode(C.WAKE_MODE_LOCAL)`.
   - `MediaSession.Builder(this, player).setSessionActivity(PendingIntent to MainActivity)`. Default Media3 notification (DefaultMediaNotificationProvider) is fine.
   - `onGetSession` returns the session; `onTaskRemoved`: if not playing → `stopSelf()`; `onDestroy` releases session + player.
3. `playback/MediaControllerConnection.kt` — `class MediaControllerConnection(ctx, scope, settings: SettingsStore) : PlayerConnection`
   - `connect()`: build `MediaController` with `SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))`, `buildAsync()`, await with `kotlinx.coroutines.guava.await()`. Queue commands issued before connection completes (store a pending lambda list and replay).
   - Map `Track` → `MediaItem` (`setMediaId(id.toString())`, `setUri(uri)`, `MediaMetadata` title/artist/albumTitle/`setArtworkUri(ContentUris.withAppendedId("content://media/external/audio/albumart", albumId))`). Keep a `Map<String, Track>` for mediaId → Track to build `PlaybackState.current`.
   - `Player.Listener.onEvents` → recompute `state` (current, isPlaying, duration, shuffle = `shuffleModeEnabled`, repeatOne = `repeatMode == REPEAT_MODE_ONE`, index, size, volume).
   - `playQueue`: `setMediaItems(items, startIndex, 0)`, `shuffleModeEnabled = shuffle`, `prepare()`, `play()`.
   - `previous()`: if `currentPosition > 3000` seek to 0 else `seekToPreviousMediaItem()` (wrapping to last when at index 0).
   - `next()`: `seekToNextMediaItem()`; at the end of the queue wrap to index 0 (design wraps).
   - `setVolume` → `controller.volume`; persist volume/shuffle/repeat to `SettingsStore`. On connect, apply persisted volume/shuffle/repeat.
   - `levels`: expose `LevelMeterProcessor.levels`, but emit `AudioLevels.SILENT` when not playing (`combine`).
   - Persist `lastTrackId`/`lastPositionMs` to settings on pause and every 10 s while playing (not every tick).
   - `restore(tracks)` extra public fun (not in contract): if nothing is loaded and settings.lastTrackId exists in tracks, set the queue to `tracks` at that index + position **without playing**.

#### Agent C — Design system (`agent/ui-kit`)
Source of truth: `design/Main.component.html` (the `t` token object, `bevel()`, `led()`, the `st` styles, `coverFor()`).
1. `ui/theme/VintageTokens.kt` — `@Immutable data class VintageTokens(...)` with **every** key from the design's dark and light `t` objects translated to Compose (`Brush`/`Color`/shadow specs), plus derived `accent`, `accentText`, `accentGlow`, `lcdText`, `lcdDim`, `lcdGlow`, exactly as the formulas in `renderVals()` (port `mix()` and `rgba()`). `fun vintageTokens(dark: Boolean, accent: Color): VintageTokens`.
   - Brushed-metal `repeating-linear-gradient(90deg, …)` chassis → draw with a cached `ImageShader` tile (1×3 px bitmap, TileMode.Repeated) on top of the vertical gradient. Build it once per theme.
   - `conic-gradient` knob → `Brush.sweepGradient` (rotate 10°); `radial-gradient` → `Brush.radialGradient`.
2. `ui/theme/Type.kt` — `FontFamily`s: `Barlow` (500/600/700 from `R.font.barlow_*`), `Orbitron` (variable, `R.font.orbitron`, weights 600/800 via `FontVariation.Settings`), `ShareTechMono`.
3. `ui/theme/VintageTheme.kt` — `LocalVintage = staticCompositionLocalOf<VintageTokens>`; `@Composable fun VintageTheme(dark, accent, content)`; `remember(dark, accent)` for tokens.
4. `ui/components/` — each a small, allocation-free composable (use `Modifier.drawBehind`/`Canvas`, remember brushes):
   - `Bevel.kt`: `Modifier.bevel(on: Boolean, shape)` = gradient bg + 1 dp border + outer shadow / inset shadow (implement inset shadow by drawing a blurred dark stroke clipped to the shape using `Paint.setMaskFilter(BlurMaskFilter)` via `drawIntoCanvas`); `BevelButton(onClick, on, shape, modifier, content)` with a pressed state that switches to the "on" style while pressed (tactile).
   - `Led.kt`: `Led(on, size = 6.dp)` with glow.
   - `LcdPanel.kt`: bezel + LCD bg + scanlines (`repeating 1px/3px`) + top gloss gradient overlay.
   - `SpectrumBars.kt`: `SpectrumBars(levels: () -> AudioLevels, playing: () -> Boolean, modifier)` — 28 bars, 7 dp wide, 3 dp gap, 48 dp tall, segmented (3 px lit / 2 px gap) with dim ghost segments; pure `Canvas`, reads the lambda inside `DrawScope`. When paused draw the 0.06 floor.
   - `VuMeter.kt`: exact port of the SVG (viewBox 200×126): arc, red zone, 8 ticks, labels 20/10/5/0/+3/VU, needle rotation `-50°` when idle, else `-40 + 88*vol*level` clamped to 48; needle eases toward target with `animateFloatAsState`-free manual smoothing inside a `withFrameNanos` loop only while playing. Top gloss overlay.
   - `VolumeKnob.kt`: 104 dp ring (`sweepGradient` arc 270° from 225°), inner knob with sweep brush, accent dot indicator rotated `-135 + vol*270`. Drag gesture: vertical drag and circular drag both adjust; haptic tick every 5 %.
   - `SeekGroove.kt`: 10 dp groove, accent fill with glow, 22 dp knob thumb; tap/drag to seek (`progress: () -> Float`, `onSeek(Float)`); while dragging show the drag value.
   - `CoverArt.kt`: `CoverArt(track: Track, size: Dp, corner: Dp)` — generated cover from `coverFor(id)` palette (use `abs(id) % 12`) drawn in Canvas (linear 135° gradient, stripe overlay, circle at 68%/34%); if `AlbumArt.load` returns a bitmap draw it over. (Agent C may call `AlbumArt` — A's file; code against this signature: `suspend fun AlbumArt.load(ctx: Context, track: Track, sizePx: Int): ImageBitmap?`. In C's worktree create a temporary stub file `data/AlbumArt.kt` returning null; the lead will drop it at merge.)
   - `LcdPill.kt`, `SectionLabel.kt` (label colour + text shadow), `Module.kt` (module card style), `BottomNav.kt` (4 items, LED + icon + label, dark metal / light walnut background), `MiniPlayer.kt` (layout only; takes state + lambdas), `Icons.kt` (all SVG icons from the design as `ImageVector`s: chevron-down, prev, next, play, pause, shuffle, repeat, heart, search, folder, music-note, disc, sliders, moon, sun).
   - Include `@Preview`s for dark & light.

### Wave 2 — Agent D — Screens & wiring (`agent/screens`, after Wave 1 is merged)
1. `ui/MainViewModel.kt` — holds `screen` (enum LIBRARY, FAVORITES, PLAYER, SETTINGS), `query`; exposes combined `UiState` via `StateFlow` (`tracks`, `favorites`, `settings`, `playback`, `filtered` list computed with `combine` on `Dispatchers.Default`). Actions mirror the design's `renderVals()` handlers: playAll, shuffleAll, playTrack(index in current list), toggleFav, setDark/Light, setAccent, rescan, pickFolder.
2. `ui/screens/LibraryScreen.kt` / `FavoritesScreen.kt` (share `TrackListScreen`): header + count pill, search field (library only, LCD style, uppercase mono), PLAY ALL / SHUFFLE, inset list (`LazyColumn`, `key = id`), row = cover 44 dp (with animated mini bars overlay on the current track), title/artist·time, heart toggle; empty states "NO MATCH" and "NO FAVORITES YET".
3. `ui/screens/PlayerScreen.kt`: exact layout from the design (header, LCD with TRK/SHUF/REP/FAV/PLAY indicators, cover 60 dp, Orbitron title, spectrum, time row, seek groove, transport row with 104 dp play ring, VU + volume modules, LOVE THIS button). Position text updates via a `produceState` ticking every 250 ms **only on this screen and the mini player** (text only; bars/needle use draw-phase lambdas).
4. `ui/screens/SettingsScreen.kt`: theme DARK/LIGHT bevel buttons with LEDs, accent swatches (4), music folder pill (tap → simple folder picker dialog listing `library.folders` + "ALL AUDIO"), RESCAN (shows SCANNING… while `isScanning`), tracks-found pill.
5. `ui/VintageAppUi.kt`: chassis background, `AnimatedContent`-free screen switch (just `when`, instant), mini player on non-player screens, bottom nav. System back: from PLAYER/FAVORITES/SETTINGS go to LIBRARY, then exit.
6. `MainActivity.kt`: `enableEdgeToEdge()`, status/nav bar icon colours follow theme, window insets padding, permission gate (READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE + POST_NOTIFICATIONS) styled as a vintage LCD card with a GRANT ACCESS bevel button; on grant → `library.start()`. `onStart` → `player.connect()`. Restore the last track after the library emits.

### Wave 3 — Lead review & hardening ✅ (device smoke test pending)
1. Merge branches, remove stubs, wire `AppGraph` (A + B implementations, `SupervisorJob + Dispatchers.Default` app scope).
2. Code review against this plan + the design (tokens, sizes, spacing).
3. `./gradlew assembleDebug assembleRelease lint`.
4. Performance pass: check for per-frame recomposition (Layout Inspector counts), confirm R8 shrinking, add `baseline-prof.txt` if needed.
5. Install on a device over adb when one is connected; smoke test: permission → scan → play → background → notification controls → favorites persist → theme/accent persist.

## 4. Definition of done
- `assembleRelease` green, APK < 6 MB.
- Plays mp3/flac/ogg/m4a/wav from device storage; background playback with notification.
- All 4 screens match the design in dark and light with all 4 accents.
- Favorites, theme, accent, volume, shuffle/repeat, and the last track + position persist across restarts.
- No INTERNET permission in the merged manifest.
