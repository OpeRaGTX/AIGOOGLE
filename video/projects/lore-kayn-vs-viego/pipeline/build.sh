#!/usr/bin/env bash
# Full build of lore-kayn-vs-viego: stills -> voice + word timings -> voice FX -> Remotion render -> mux.
# Run from video/: npm run build -- lore  (extra args go to remotion render, e.g. --concurrency=3)
set -euo pipefail
cd "$(dirname "$0")/.."
source ../../tools/grade.sh
mkdir -p public/fonts work/voices out

# 1. stills: cover 1080x1920 x1.15 = 1242x2208 (lanczos) + grade contrast +6 %, saturation +5 %
G=$(grade 1.06 1.05 1.0)
for f in inbox/*.png; do
  [ -f "public/$(basename "$f")" ] || ffmpeg -v error -y -i "$f" -vf "scale=1242:2208:flags=lanczos,$G,format=rgb24" -frames:v 1 "public/$(basename "$f")"
done

# 2. caption font (Montserrat 800/900, cyrillic + latin) from npm
if [ ! -f public/fonts/montserrat-cyrillic-900-normal.woff2 ]; then
  (cd work && npm pack @fontsource/montserrat --silent >/dev/null && tar xzf fontsource-montserrat-*.tgz)
  cp work/package/files/montserrat-{cyrillic,latin}-{800,900}-normal.woff2 public/fonts/
fi

# 3. Piper voice (needs huggingface.co + its CDN in the network allowlist) -> work/vo/voice_raw.wav + src/timeline.json
V=$(python3 -c "import json; print(json.load(open('pipeline/scenes.json'))['voice'])")
HF=https://huggingface.co/rhasspy/piper-voices/resolve/main/ru/ru_RU/$(echo "$V" | cut -d- -f2)/medium
for e in onnx onnx.json; do [ -f "work/voices/$V.$e" ] || curl -sSfL -o "work/voices/$V.$e" "$HF/$V.$e"; done
python3 -c "import piper, onnx" 2>/dev/null || pip install --break-system-packages piper-tts onnx
python3 pipeline/vo.py
./pipeline/voice_fx.sh   # -> out/voice.wav

# 4. picture (silent) + mux voice
(cd ../.. && node tools/render.mjs lore-kayn-vs-viego --out work/silent.mp4 --muted "$@")
ffmpeg -v error -y -i work/silent.mp4 -i out/voice.wav -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -shortest \
  -movflags +faststart out/lore-kayn-vs-viego.mp4
echo "done: out/lore-kayn-vs-viego.mp4"
