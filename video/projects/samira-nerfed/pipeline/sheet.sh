#!/usr/bin/env bash
# sheet.sh [file] [step_s] — contact sheets of the gameplay with source timecodes -> work/sheets/sheet_NN.jpg
# (6x5 tiles, one frame every step_s seconds, default 0.5). Used to find kills/multikills and Samira's position.
set -e
D="$(cd "$(dirname "$0")" && pwd)"; P="$(cd "$D/.." && pwd)"
SRC="${1:-$P/inbox/gameplay.mp4}"; STEP="${2:-0.5}"
mkdir -p "$P/work/sheets"
ffmpeg -v error -y -i "$SRC" -an -vf "fps=1/$STEP,scale=-2:300,drawtext=text='%{pts\:hms}':fontsize=30:x=8:y=8:fontcolor=white:box=1:boxcolor=black@0.7,tile=6x5" \
  -q:v 4 "$P/work/sheets/sheet_%02d.jpg"
ls "$P/work/sheets"
