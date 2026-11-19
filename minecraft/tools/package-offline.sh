#!/usr/bin/env bash
# Offline variant of package-mod.sh for containers that cannot reach Modrinth: repacks minecraft/dist/<name>-<ver>-mc<mc>.zip with the FRESH mod jar from
# build/libs and re-uses the Fabric API jar and УСТАНОВКА.txt of the previous zip (same versions, so nothing changes for the player).
# Usage: bash minecraft/tools/package-offline.sh minecraft/mods/hearthholm   (run ./gradlew build first)
set -euo pipefail
PROJ=$(cd "${1:?project dir}" && pwd)
ROOT=$(cd "$(dirname "$0")/.." && pwd)
prop() { grep -E "^$1=" "$PROJ/gradle.properties" | head -1 | cut -d= -f2- | tr -d '[:space:]'; }
MC=$(prop minecraft_version); VER=$(prop mod_version); [ -n "$VER" ] || VER=$(prop version)
NAME=$(prop archives_base_name); [ -n "$NAME" ] || NAME=$(basename "$PROJ")
JAR=$(ls "$PROJ"/build/libs/*.jar 2>/dev/null | grep -vE -- '-(sources|dev|javadoc)\.jar$' | head -1 || true)
[ -n "$JAR" ] || { echo "no jar in $PROJ/build/libs: run ./gradlew build first"; exit 1; }
ZIP="$ROOT/dist/$NAME-$VER-mc$MC.zip"
[ -f "$ZIP" ] || { echo "no previous zip $ZIP to take the Fabric API jar from"; exit 1; }
STAGE=$(mktemp -d); trap 'rm -rf "$STAGE"' EXIT
(cd "$STAGE" && unzip -q "$ZIP")
rm -f "$STAGE"/mods/"$NAME"-*.jar
cp "$JAR" "$STAGE/mods/"
rm -f "$ZIP"
(cd "$STAGE" && zip -qr "$ZIP" mods УСТАНОВКА.txt)
echo "$ZIP ($(du -h "$ZIP" | cut -f1)): $(unzip -Z1 "$ZIP" | tr '\n' ' ')"
