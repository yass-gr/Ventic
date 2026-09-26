# Ventic

**A vintage hi-fi music player for Android that only plays the music already on your phone.**

Brushed-metal chassis, a glowing LCD, a live spectrum analyser, a swinging VU needle and a real rotary volume knob. It has no accounts, no streaming and no network access. The app does not even request the `INTERNET` permission.

## Features

- **Local library, instantly.** Songs are read from Android's MediaStore and cached on disk, so the list appears on the first frame, before the device is re-queried. Changes to your music are picked up automatically.
- **Real audio visualisation.** The 28-band spectrum and the VU meter are driven by an FFT that runs inside the audio pipeline (a Media3 `AudioProcessor`). They react to the actual music and need no microphone permission.
- **Background playback.** Plays through a `MediaSessionService` with lock-screen and notification controls, audio focus handling and pause on headphone unplug.
- **Four screens:** Library (search, play all, shuffle), Favorites, Now Playing and Settings.
- **Themes:** dark gunmetal or light cream-and-walnut chassis, with blue, amber, red or green accent lighting.
- **Remembers everything:** favorites, theme, accent, volume, shuffle/repeat, the music-folder filter, and the last track with its position.

## Built for speed

| | |
|---|---|
| Release APK | ~3.9 MB (R8 + resource shrinking) |
| UI | Jetpack Compose (foundation only, no Material) |
| Per-frame animation | Spectrum, VU needle and seek bar read state in the draw phase only, so there is no recomposition per frame |
| Audio analysis | Hand-written radix-2 FFT with no allocation on the audio thread |
| Startup | Binary library cache, manual DI, baseline-profile installer |

## Tech stack

Kotlin · Jetpack Compose · Media3 ExoPlayer + MediaSession · DataStore · Coroutines/Flow.
minSdk 29 (Android 10) · targetSdk 36.

```
app/src/main/java/com/yass/vintageplayer/
├── data/       MediaStore library, binary cache, DataStore settings & favorites, album art
├── playback/   PlaybackService (ExoPlayer), MediaController connection, FFT level meter
└── ui/         theme tokens, skeuomorphic components, screens, ViewModel
```

## Building

Requirements: JDK 17+ and the Android SDK (platform 36).

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew installDebug        # install on a connected device
```

The release build is signed with the debug key so you can install it for local testing. Use your own keystore before distributing it.

## Design

The UI is a direct port of a Claude Design mock-up (`design/Main.component.html`). Colours, gradients, bevels, LED glows and spacing are taken from its token set.

## Credits

Fonts: [Barlow Semi Condensed](https://fonts.google.com/specimen/Barlow+Semi+Condensed), [Orbitron](https://fonts.google.com/specimen/Orbitron) and [Share Tech Mono](https://fonts.google.com/specimen/Share+Tech+Mono), all under the SIL Open Font License 1.1 (see [`licenses/`](licenses)).
