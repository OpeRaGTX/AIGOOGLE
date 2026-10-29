#!/usr/bin/env bash
# shot2mp4.sh <shot> : 30fps renders -> 60fps (frame duplication, no warping) 1080x1920, grade baked in
set -e
V="${VIEGO_WORK:-$(cd "$(dirname "$0")/../work" && pwd)}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/public/shots"
mkdir -p $OUT
ffmpeg -v error -y -framerate 30 -i "$V/renders/$1/%04d.png" -vf "fps=60,scale=1080:1920:flags=lanczos,unsharp=5:5:0.35,format=gbrp,lutrgb=r=(val-127.5)*1.1+127.5:g=(val-127.5)*1.1+127.5:b=(val-127.5)*1.1+127.5,colorchannelmixer=rr=1.03935:rg=-0.03575:rb=-0.0036:gr=-0.01065:gg=1.01425:gb=-0.0036:br=-0.01065:bg=-0.03575:bb=1.0464,lutrgb=r=val*1.12:g=val*1.12:b=val*1.12,format=yuv420p" -c:v libx264 -crf 14 -preset medium "$OUT/$1.mp4"
echo "$1 -> $(ffprobe -v error -show_entries stream=nb_frames -of csv=p=0 $OUT/$1.mp4) frames"
