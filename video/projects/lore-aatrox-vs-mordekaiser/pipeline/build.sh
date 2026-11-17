#!/usr/bin/env bash
# Full build: npm run build -- aatrox   (extra args go to remotion render)   -> out/aatrox-vs-mordekaiser.mp4
# Copied from lore-shaco-vs-jhin: inbox voice + inbox Whisper word timings (NOTES fixes) -> EDL background -> Remotion
# (captions, flashes, plates) -> mux voice. One part, no music.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p public/fonts out work
[ -f public/fonts/montserrat-cyrillic-900-normal.woff2 ] || {
  (cd work && npm pack @fontsource/montserrat --silent >/dev/null && tar xzf fontsource-montserrat-*.tgz)
  cp work/package/files/montserrat-{cyrillic,latin}-{800,900}-normal.woff2 public/fonts/; }
python3 pipeline/words.py                               # -> src/words.json, out/voice.wav
python3 pipeline/prep_clips.py                          # -> public/bg.mp4, src/edl.json
(cd ../.. && node tools/render.mjs lore-aatrox-vs-mordekaiser --comp aatrox-vs-mordekaiser --out work/silent.mp4 --muted "$@")
# Remotion renders full-range BT.601 (yuvj420p); TikTok wants limited-range BT.709. zscale converts exactly, plain swscale
# shifts the colours by a few levels, so it is only the fallback for an ffmpeg built without zimg.
CONV="scale=in_range=pc:out_range=tv"
ffmpeg -hide_banner -filters 2>/dev/null | grep -q ' zscale ' && CONV="zscale=rangein=full:range=limited:matrixin=470bg:matrix=709"
ffmpeg -v error -y -i work/silent.mp4 -i out/voice.wav -map 0:v -map 1:a \
  -vf "$CONV,format=yuv420p" -c:v libx264 -preset slow -crf 22 -profile:v high -level 4.2 \
  -color_range tv -colorspace bt709 -color_primaries bt709 -color_trc bt709 -c:a aac -b:a 192k -movflags +faststart \
  out/aatrox-vs-mordekaiser.mp4
echo "done: out/aatrox-vs-mordekaiser.mp4"
