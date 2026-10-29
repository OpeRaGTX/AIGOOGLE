#!/usr/bin/env bash
# shot2mp4.sh <shot>  — renders/<shot>/####.png @30fps -> public/shots/<shot>.mp4 @60fps 1080p, graded
set -e
D="$(cd "$(dirname "$0")" && pwd)"; . "$D/grade.sh"
W="${KAYN_WORK:-$D/work}"
OUT="$D/../public/shots"
ffmpeg -v error -y -framerate 30 -i "$W/renders/$1/%04d.png" \
  -vf "minterpolate=fps=60:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1,scale=1080:1920:flags=lanczos,unsharp=5:5:0.35,$GRADE_SHOT" \
  -c:v libx264 -crf 14 -preset medium -pix_fmt yuv420p "$OUT/$1.mp4"
echo "$1 -> $(ffprobe -v error -show_entries stream=nb_frames -of csv=p=0 $OUT/$1.mp4) frames"
