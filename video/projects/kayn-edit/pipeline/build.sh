#!/usr/bin/env bash
# build.sh [shot ...] [--force] [--no-edit] — whole Kayn edit (run via `npm run build -- kayn-edit` from video/):
#   inbox/ -> 3D shots (Blender, GPU) -> 60 fps mp4 -> gameplay/track -> Remotion -> out/kayn-edit.mp4
# No shot names = every shot that has no mp4 in public/shots yet (--force: all of them). --no-edit skips Remotion.
set -e
D="$(cd "$(dirname "$0")" && pwd)"; cd "$D"; . "$D/../../../tools/env.sh"
P="$(cd "$D/.." && pwd)"; W="${KAYN_WORK:-$P/work}"; PUB="$P/public"; IN="$P/inbox"
KILLS="K1_pantheon K2_ahri K3_volibear K4_seraphine K5_finale"; STATIC="S1_intro S7_hero"
FORCE=; EDIT=1; PICK=
for a in "$@"; do
  case "$a" in --force) FORCE=1 ;; --no-edit) EDIT= ;; *) PICK="$PICK $a" ;; esac
done
[ -n "$BLENDER" ] || { echo "Blender not found: put it on PATH or set BLENDER=/path/to/blender.exe"; exit 1; }
echo "blender: $BLENDER"

# --- inputs: accept the original (Cyrillic) file names, copy them to the names the scripts use
export KAYN_IN="$(winpath "$IN")"
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
  G="$IN/game.mp4"; [ -f "$W/gameplay_60fps.mp4" ] && G="$W/gameplay_60fps.mp4"
  ./game2mp4.sh "$G"
fi

# --- 3D shots
render() {  # render <script> <samples> <shot> <to-mp4 script>
  echo "== $3 $(date +%T)"; rm -rf "$W/renders/$3"
  "$BLENDER" -b --factory-startup -P "b3d/$1" -- "$3" "$2" full "$(winpath "$W/renders")" 2>&1 |
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
if [ -n "$EDIT" ]; then node "$TOOLS/render.mjs" kayn-edit --log=error; fi
