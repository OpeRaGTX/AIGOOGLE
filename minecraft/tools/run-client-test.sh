#!/usr/bin/env bash
# Runs a mod's Fabric client game test headless (no display needed):
# Minecraft 26.x opens its window through SDL3, which here uses the offscreen driver + Mesa llvmpipe.
# Usage: bash minecraft/tools/run-client-test.sh minecraft/mods/<modid> [logfile]
# Screenshots: <mod>/build/run/clientGameTest/screenshots/ (or run/clientGameTest/screenshots/)
set -uo pipefail
cd "${1:?mod dir}"
LOG=${2:-build/client-gametest.log}
mkdir -p "$(dirname "$LOG")"
export JAVA_HOME=/opt/jdk-25 PATH=/opt/jdk-25/bin:$PATH
export SDL_VIDEODRIVER=offscreen LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
./gradlew runClientGameTest --no-daemon >"$LOG" 2>&1
code=$?
grep -E 'HHTEST|TEST |Caused by|FAILED' "$LOG" | tail -80
echo "exit=$code"
exit $code
