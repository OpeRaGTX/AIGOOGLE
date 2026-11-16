#!/usr/bin/env bash
# Packs a built Fabric mod into a player-ready zip for the official Minecraft Launcher:
#   <mod>.jar + the matching Fabric API jar (from Modrinth, SHA-512 checked) + УСТАНОВКА.txt
# Usage: bash minecraft/tools/package-mod.sh minecraft/mods/<modid>   (run ./gradlew build first)
# Output: minecraft/dist/<archives_name>-<version>-mc<mc_version>.zip
set -euo pipefail
PROJ=$(cd "${1:?project dir}" && pwd)
ROOT=$(cd "$(dirname "$0")/.." && pwd)
prop() { grep -E "^$1=" "$PROJ/gradle.properties" | head -1 | cut -d= -f2- | tr -d '[:space:]'; }
MC=$(prop minecraft_version); FAPI=$(prop fabric_api_version); LOADER=$(prop loader_version)
VER=$(prop mod_version); [ -n "$VER" ] || VER=$(prop version)
NAME=$(prop archives_base_name); [ -n "$NAME" ] || NAME=$(basename "$PROJ")
JAR=$(ls "$PROJ"/build/libs/*.jar 2>/dev/null | grep -vE -- '-(sources|dev|javadoc)\.jar$' | head -1 || true)
[ -n "$JAR" ] || { echo "no jar in $PROJ/build/libs — run ./gradlew build first"; exit 1; }

OUT="$ROOT/dist"; STAGE=$(mktemp -d); trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$OUT" "$STAGE/mods"
cp "$JAR" "$STAGE/mods/"

if [ -n "$FAPI" ]; then
  INFO=$(curl -fsSL -A "mod-packager" -G "https://api.modrinth.com/v2/project/fabric-api/version" \
    --data-urlencode "game_versions=[\"$MC\"]" --data-urlencode 'loaders=["fabric"]' |
    python3 -c "import json,sys
v=[x for x in json.load(sys.stdin) if x['version_number']=='$FAPI']
f=[x for x in v[0]['files'] if x['primary']][0] if v else None
print(f['url'], f['hashes']['sha512'], f['filename']) if f else print('')")
  [ -n "$INFO" ] || { echo "Fabric API $FAPI for $MC not found on Modrinth"; exit 1; }
  read -r URL SHA FILE <<<"$INFO"
  curl -fsSL -o "$STAGE/mods/$FILE" "$URL"
  echo "$SHA  $STAGE/mods/$FILE" | sha512sum -c --quiet
fi

cat >"$STAGE/УСТАНОВКА.txt" <<EOF
Мод: $NAME $VER — Minecraft Java $MC, Fabric Loader $LOADER+

1. Установи Fabric: https://fabricmc.net/use/installer/ → скачай установщик для Windows (.exe)
   или Universal (.jar) → вкладка Client → версия Minecraft $MC → Install.
   (Официальный лаунчер должен быть закрыт.) В лаунчере появится установка "fabric-loader-$MC".
2. Открой папку модов:
   Windows: Win+R → %appdata%\\.minecraft → папка mods (создай, если её нет)
   macOS:   ~/Library/Application Support/minecraft/mods
   Linux:   ~/.minecraft/mods
3. Скопируй туда ВСЕ .jar из папки mods этого архива (наш мод + Fabric API).
4. Открой официальный лаунчер → «Установки»/«Играть» → выбери fabric-loader-$MC → «Играть».

Совет: чтобы моды не мешали обычной игре, в лаунчере открой «Установки» → fabric-loader-$MC →
«Изменить» → «Папка игры» и укажи отдельную папку; тогда mods кладутся в неё.
EOF

ZIP="$OUT/$NAME-$VER-mc$MC.zip"; rm -f "$ZIP"
(cd "$STAGE" && python3 -m zipfile -c "$ZIP" mods УСТАНОВКА.txt)
echo "ready: $ZIP"; python3 -m zipfile -l "$ZIP"
