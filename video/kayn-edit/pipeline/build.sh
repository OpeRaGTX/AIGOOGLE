#!/usr/bin/env bash
# build.sh [shot ...] [--force] [--no-edit] — whole Kayn edit on this PC in one go:
#   inputs -> 3D shots (Blender, GPU) -> 60 fps mp4 -> gameplay/track -> Remotion -> ../out/kayn_edit_tiktok.mp4
# No shot names = every shot that has no mp4 in public/shots yet (--force: all of them). --no-edit skips Remotion.
set -e
D="$(cd "$(dirname "$0")" && pwd)"; cd "$D"
W="${KAYN_WORK:-$D/work}"; PUB="$D/../public"
KILLS="K1_pantheon K2_ahri K3_volibear K4_seraphine K5_finale"; STATIC="S1_intro S7_hero"
FORCE=; EDIT=1; PICK=
for a in "$@"; do
  case "$a" in --force) FORCE=1 ;; --no-edit) EDIT= ;; *) PICK="$PICK $a" ;; esac
done

# --- tools
BLENDER="${BLENDER:-$(command -v blender || true)}"
if [ -z "$BLENDER" ]; then
  for b in /d/git/tools/Blender/blender.exe "/c/Program Files/Blender Foundation"/*/blender.exe; do
    [ -x "$b" ] && BLENDER="$b"
  done
fi
[ -n "$BLENDER" ] || { echo "Blender not found: put it on PATH or set BLENDER=/path/to/blender.exe"; exit 1; }
for t in ffmpeg ffprobe npx; do command -v $t >/dev/null || { echo "$t not found on PATH"; exit 1; }; done
echo "blender: $BLENDER"

# --- inputs: accept the original (Cyrillic) file names, copy them to the names the scripts use
IN="$W/in"; export KAYN_IN="$(cd "$IN" && (pwd -W 2>/dev/null || pwd))"
pick() {  # pick <target> <glob...>: copy the first match to $IN/<target> unless it exists
  [ -f "$IN/$1" ] && return
  local t=$1; shift
  for g in "$@"; do for f in "$IN"/$g; do [ -f "$f" ] && { cp "$f" "$IN/$t"; echo "input: $(basename "$f") -> $t"; return; }; done; done
  echo "missing input $t in $IN"; exit 1
}
pick kayn.glb '*каин*' '*kayn*'
pick pantheon.glb '*пантеон*'
pick ahri.glb 'ари*' '*ahri*'
pick volibear.glb '*волиб*'
pick seraphine.glb '*сераф*'
pick track.mp3 '*.mp3'
pick game.mp4 '*.MP4' '*.mp4' '*.mov' '*.MOV'

# --- gameplay + track (reuse an already interpolated 60 fps file if there is one)
mkdir -p "$PUB/shots" "$W/renders"
if [ -n "$FORCE" ] || [ ! -f "$PUB/game.mp4" ]; then
  G="$IN/game.mp4"; [ -f "$D/../out/gameplay_60fps.mp4" ] && G="$D/../out/gameplay_60fps.mp4"
  ./game2mp4.sh "$G"
fi

# --- 3D shots
render() {  # render <script> <samples> <shot> <to-mp4 script>
  echo "== $3 $(date +%T)"; rm -rf "$W/renders/$3"
  "$BLENDER" -b --factory-startup -P "b3d/$1" -- "$3" "$2" full "$(cd "$W/renders" && pwd -W 2>/dev/null || pwd)" 2>&1 |
    grep -E "stage\]|Error|Traceback" | grep -v HIPEW || true
  ls "$W/renders/$3"/*.png >/dev/null 2>&1 || { echo "render of $3 failed"; exit 1; }
  ./$4 "$3"
}
for s in $KILLS $STATIC; do
  if [ -n "$PICK" ]; then [[ " $PICK " == *" $s "* ]] || continue
  elif [ -z "$FORCE" ] && [ -f "$PUB/shots/$s.mp4" ]; then continue; fi
  case "$s" in K*) render kills.py 12 "$s" act2mp4.sh ;; *) render shots.py 16 "$s" shot2mp4.sh ;; esac
done

# --- edit
if [ -n "$EDIT" ]; then
  cd "$D/.."
  # Remotion's frame extractor occasionally fails under many threads ("No frame found at position"); a rerun passes
  for try in 1 2 3; do
    echo "== remotion $(date +%T) (try $try)"
    npx remotion render src/index.ts KaynEdit out/kayn_edit_tiktok.mp4 --log=error && break
    [ $try = 3 ] && exit 1
  done
  echo "done: $(cd "$D/.." && pwd)/out/kayn_edit_tiktok.mp4 $(date +%T)"
fi
