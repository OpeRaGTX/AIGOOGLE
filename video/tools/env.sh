# env.sh — source from a project's pipeline/*.sh: finds Blender, checks ffmpeg, loads grade(). Exports:
#   STUDIO (video/), TOOLS (video/tools), BLENDER (path to blender.exe or blender)
TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; STUDIO="$(cd "$TOOLS/.." && pwd)"
BLENDER="${BLENDER:-$(command -v blender || true)}"
if [ -z "$BLENDER" ]; then
  for b in /d/git/tools/Blender/blender.exe "/c/Program Files/Blender Foundation"/*/blender.exe; do
    [ -x "$b" ] && BLENDER="$b"
  done
fi
for t in ffmpeg ffprobe; do command -v $t >/dev/null || { echo "$t not found on PATH"; exit 1; }; done
export STUDIO TOOLS BLENDER
. "$TOOLS/grade.sh"
# winpath <path> — absolute path in the form native Windows programs (Blender) understand
winpath() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }
