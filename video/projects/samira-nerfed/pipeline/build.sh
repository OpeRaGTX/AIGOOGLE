#!/usr/bin/env bash
# build.sh [--src file] [--force] [--no-render] — whole edit (run via `npm run build -- samira-nerfed` from video/):
#   inbox/gameplay.mp4 + inbox/track.m4a + src/edl.json -> public/ (prep.py) -> Remotion -> out/samira-nerfed.mp4
set -e
D="$(cd "$(dirname "$0")" && pwd)"; . "$D/../../../tools/env.sh"
P="$(cd "$D/.." && pwd)"; RENDER=1; ARGS=()
for a in "$@"; do case "$a" in --no-render) RENDER= ;; *) ARGS+=("$a") ;; esac; done
PY="$(command -v python3 || command -v python)"
"$PY" "$D/prep.py" --grade "$(grade 1.08 1.2 1.02)" "${ARGS[@]}"
[ -n "$RENDER" ] || exit 0
cd "$STUDIO" && node tools/render.mjs samira-nerfed --crf 17 --color-space=bt709
ffmpeg -hide_banner -nostats -i "$P/out/samira-nerfed.mp4" -af ebur128=peak=true -f null - 2>&1 | grep -A12 'Summary:' | grep -E 'I:|Peak:'
ffprobe -v error -select_streams v:0 -show_entries stream=width,height,r_frame_rate,nb_frames:format=duration,size -of default=nw=1 "$P/out/samira-nerfed.mp4"
