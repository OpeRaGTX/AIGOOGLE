#!/usr/bin/env bash
# Installs Eclipse Temurin JDK 25 (needed by Minecraft 26.x and Fabric Loom) into /opt/jdk-25.
# Idempotent; verifies the SHA-256 published by Adoptium. Usage: bash minecraft/tools/setup-jdk.sh
set -euo pipefail
DEST=${JDK_DEST:-/opt/jdk-25}
if [ -x "$DEST/bin/java" ]; then "$DEST/bin/java" -version 2>&1 | grep -m1 version; echo "JAVA_HOME=$DEST"; exit 0; fi
case "$(uname -m)" in x86_64) ARCH=x64 ;; aarch64|arm64) ARCH=aarch64 ;; *) echo "unsupported arch"; exit 1 ;; esac
META=$(curl -fsSL "https://api.adoptium.net/v3/assets/latest/25/hotspot?os=linux&architecture=$ARCH&image_type=jdk")
URL=$(printf '%s' "$META" | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['binary']['package']['link'])")
SUM=$(printf '%s' "$META" | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['binary']['package']['checksum'])")
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
curl -fsSL -o "$TMP/jdk.tar.gz" "$URL"
echo "$SUM  $TMP/jdk.tar.gz" | sha256sum -c --quiet
mkdir -p "$DEST" && tar -xzf "$TMP/jdk.tar.gz" -C "$DEST" --strip-components=1
"$DEST/bin/java" -version 2>&1 | grep -m1 version
echo "JAVA_HOME=$DEST"
