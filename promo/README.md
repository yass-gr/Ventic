# Ventic release video

A 37-second, 1080p60 motion-graphics trailer. Everything is generated from code: the soundtrack is composed and synthesized in Python, the visuals are React components rendered by [Remotion](https://www.remotion.dev/), and the phone footage is real screen recordings of the app.

## Soundtrack (`audio/`)

A chill lo-fi funk track: 100 BPM, A minor, swung 16ths, built on a Fmaj9 → E7♯9 → Am9 → Gm9/C9 progression. Every sound is synthesized with no samples:

- Karplus-Strong fingered bass and funk guitar
- FM electric piano with tremolo and tape wow
- Synth brass stabs and a gliding lead
- Drum synthesis: kick, snare, hats, shaker, tambourine and crash
- A vinyl scratch, needle drop, crackle and a record-stop ending

It is mixed with send reverbs, sidechain pump and a look-ahead limiter, then mastered to −14 LUFS.

`compose.py` also exports `src/audio.json`, which holds per-frame loudness, a 32-band spectrum and musical events (kicks, snares, brass stabs, impacts, the scratch, record speed). The video uses it to cut, flash and animate in sync with the music.

```bash
uv venv .venv --python 3.12 && uv pip install --python .venv/bin/python numpy numba scipy pedalboard pyloudnorm soundfile
cd audio && ../.venv/bin/python compose.py      # -> out/music.wav + src/audio.json
```

## Video (`src/`)

Seven scenes run on the soundtrack's bar grid:
1. Needle drop
2. Logo drop
3. Kinetic words
4. Zoom-to-feature shots
5. Accent colours and the dark/light flip
6. Stats
7. End card with a record stop and CRT power-off

Footage frames go in `public/footage/<clip>/0001.jpg…` (60 fps, 1080×2290, status bar cropped). They are recorded with `adb shell screenrecord` and are not committed.

```bash
npm install
node scripts/stills.mjs 380 760 1400        # quick review stills -> out/stills/
npx remotion render src/index.ts Ventic out/video_silent.mp4 --crf=15
ffmpeg -i out/video_silent.mp4 -i out/music.wav -map 0:v -map 1:a -c:v libx264 -crf 19 -preset slow \
       -pix_fmt yuv420p -c:a aac -b:a 256k -movflags +faststart ventic-release.mp4
```
