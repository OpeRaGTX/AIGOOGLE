#!/usr/bin/env bash
# fetch_addons.sh — gather the Blender add-ons the studio uses into video/tools/addons/ (not committed):
#   mcp_for_blender.py   ahujasid/mcp-for-blender addon (MIT)       — Claude's "eyes": viewport screenshots, live control
#   blender_toolkit.zip  Dev-GOM blender-toolkit addon (Apache-2.0) — Mixamo retarget with bone mapping (needs aiohttp)
#   wiggle_2.py          shteeve3d/blender-wiggle-2 v2.2.3 (GPL-3.0) — optional live spring bones (we bake our own)
# Install in Blender: Edit > Preferences > Add-ons > (top-right menu) Install from Disk… > pick the file > enable it.
set -e
D="$(cd "$(dirname "$0")" && pwd)"; R="$(cd "$D/../../.." && pwd)"
curl -sfL https://raw.githubusercontent.com/ahujasid/mcp-for-blender/HEAD/addon.py -o "$D/mcp_for_blender.py"
curl -sfL https://github.com/shteeve3d/blender-wiggle-2/releases/download/v2.2.3/wiggle_2.py -o "$D/wiggle_2.py"
tmp="$D/.pkg"; rm -rf "$tmp"; mkdir -p "$tmp/blender_toolkit"
cp -r "$R/.claude/skills/blender-toolkit/addon/." "$tmp/blender_toolkit/"
find "$tmp" -name "__pycache__" -prune -exec rm -rf {} +
(cd "$tmp" && python -c "import shutil; shutil.make_archive('../blender_toolkit', 'zip', '.', 'blender_toolkit')")
rm -rf "$tmp"
ls -la "$D" | grep -E "\.py$|\.zip$"
