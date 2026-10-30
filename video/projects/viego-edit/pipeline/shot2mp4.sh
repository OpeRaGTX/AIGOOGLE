#!/usr/bin/env bash
# shot2mp4.sh <shot> : 30fps renders -> 60fps (frame duplication, no warping on fast swings) 1080x1920, grade baked in
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/../../../tools/grade.sh"
V="${VIEGO_WORK:-$HERE/../work}"
OUT="$HERE/../public/shots"
mkdir -p "$OUT"
G="$(grade 1.08 1.08 1.06),eq=gamma=1.28"   # lifted shadows: the dark stage reads on a phone
ffmpeg -v error -y -framerate 30 -i "$V/renders/$1/%04d.png" -vf "fps=60,scale=1080:1920:flags=lanczos,unsharp=5:5:0.35,$G,format=yuv420p" \
  -c:v libx264 -crf 14 -preset medium "$OUT/$1.mp4"
echo "$1 -> $(ffprobe -v error -show_entries stream=nb_frames -of csv=p=0 "$OUT/$1.mp4") frames"
