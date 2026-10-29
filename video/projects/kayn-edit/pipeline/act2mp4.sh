#!/usr/bin/env bash
# act2mp4.sh <shot> — action renders @30fps -> 60fps by frame duplication (no warping on fast spins), 1080x1920, graded
set -e
D="$(cd "$(dirname "$0")" && pwd)"; . "$D/../../../tools/env.sh"
W="${KAYN_WORK:-$D/../work}"
OUT="$D/../public/shots"
ffmpeg -v error -y -framerate 30 -i "$W/renders/$1/%04d.png" -vf "fps=60,scale=1080:1920:flags=lanczos,unsharp=5:5:0.35,$(grade 1.12 1.06 1.18)" \
  -c:v libx264 -crf 14 -preset medium -pix_fmt yuv420p "$OUT/$1.mp4"
echo "$1 -> $(ffprobe -v error -show_entries stream=nb_frames -of csv=p=0 $OUT/$1.mp4) frames"
