#!/usr/bin/env bash
# Build one part: npm run build -- yunara [p1]   (extra args after the part go to remotion render)
# voice + Whisper word timings -> EDL background -> Remotion (captions, flashes, end card) -> mux voice.
set -euo pipefail
cd "$(dirname "$0")/.."
PART=${1:-p1}; shift || true
mkdir -p public/fonts out "work/$PART"
[ -f public/fonts/montserrat-cyrillic-900-normal.woff2 ] || {
  (cd work && npm pack @fontsource/montserrat --silent >/dev/null && tar xzf fontsource-montserrat-*.tgz)
  cp work/package/files/montserrat-{cyrillic,latin}-{800,900}-normal.woff2 public/fonts/; }
python3 -c "import faster_whisper" 2>/dev/null || pip install --break-system-packages faster-whisper
HF_HUB_DISABLE_XET=1 python3 pipeline/align_voice.py "$PART"   # -> src/words.json, out/voice-$PART.wav
python3 pipeline/prep_clips.py "$PART"                          # -> public/$PART/bg.mp4
if ls "inbox/$PART"/*.png >/dev/null 2>&1; then                  # stills -> living-picture maps
  M=work/models/depth-anything-v2-small.onnx
  [ -e "$M" ] || { mkdir -p work/models && curl -sSfL -o "$M" https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/main/onnx/model.onnx; }
  python3 pipeline/living_maps.py "$PART"                       # -> public/$PART/img|depth|maps|glow
fi
(cd ../.. && node tools/render.mjs lore-yunara-vs-xayah --comp "yunara-vs-xayah-$PART" --out "work/$PART/silent.mp4" --muted "$@")
ffmpeg -v error -y -i "work/$PART/silent.mp4" -i "out/voice-$PART.wav" -map 0:v -map 1:a \
  -vf "scale=in_range=pc:out_range=tv,format=yuv420p" -c:v libx264 -preset slow -crf 22 -profile:v high -level 4.2 \
  -color_range tv -colorspace bt709 -color_primaries bt709 -color_trc bt709 -c:a aac -b:a 192k -movflags +faststart \
  "out/yunara-vs-xayah-$PART.mp4"
echo "done: out/yunara-vs-xayah-$PART.mp4"
