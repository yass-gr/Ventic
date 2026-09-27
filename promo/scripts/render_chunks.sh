#!/usr/bin/env bash
# Render the composition in fresh-browser chunks (keeps Chrome memory bounded), then join losslessly.
set -euo pipefail
cd "$(dirname "$0")/.."
export TMPDIR="$PWD/out/tmp"
mkdir -p out/tmp out/chunks
TOTAL=2232
STEP=372
: > out/chunks/list.txt
for ((s = 0; s < TOTAL; s += STEP)); do
  e=$(( s + STEP - 1 < TOTAL - 1 ? s + STEP - 1 : TOTAL - 1 ))
  out="out/chunks/c_$(printf '%04d' $s).mp4"
  npx remotion render src/index.ts Ventic "$out" --frames="$s-$e" --codec=h264 --crf=15 --concurrency=4 \
    --image-format=jpeg --jpeg-quality=93 --enable-multiprocess-on-linux --log=error
  echo "file '$(basename "$out")'" >> out/chunks/list.txt
  echo "chunk $s-$e done"
done
ffmpeg -v error -y -f concat -safe 0 -i out/chunks/list.txt -c copy out/video_silent.mp4
echo "ALL DONE"
