#!/usr/bin/env bash
# Headless Blender (bpy) for cloud sessions. Usage: source video/setup-blender.sh; then $BLENDER_PY script.py
set -e
VENV="${BLENDER_VENV:-$HOME/.cache/bpy-venv}"
[ -x "$VENV/bin/python" ] || uv venv -q -p python3.11 "$VENV"
"$VENV/bin/python" -c "import bpy" 2>/dev/null || uv pip install -q -p "$VENV/bin/python" bpy
export BLENDER_PY="$VENV/bin/python"
"$BLENDER_PY" -c "import bpy; print('bpy', bpy.app.version_string)"
