#!/usr/bin/env bash
# game2mp4.sh [src] — gameplay -> 60fps (minterpolate unless already 60), center crop 9:16, graded -> public/game.mp4; track -> public/
set -e
D="$(cd "$(dirname "$0")" && pwd)"; . "$D/grade.sh"
W="${KAYN_WORK:-$D/work}"
SRC="${1:-$W/in/game.mp4}"
OUT="$D/../public"; mkdir -p "$OUT/shots"
MI=""; [ "$(ffprobe -v error -select_streams v:0 -show_entries stream=r_frame_rate -of csv=p=0 "$SRC")" = "60/1" ] ||
  MI="minterpolate=fps=60:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1,"
ffmpeg -v error -y -i "$SRC" -an -vf "${MI}crop=ih*9/16:ih,scale=1080:1920:flags=lanczos,$GRADE_GAME" \
  -c:v libx264 -crf 16 -preset medium -pix_fmt yuv420p "$OUT/game.mp4"
cp "$W/in/track.mp3" "$OUT/track.mp3"
echo "game -> $(ffprobe -v error -show_entries stream=nb_frames -of csv=p=0 $OUT/game.mp4) frames"
