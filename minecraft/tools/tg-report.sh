#!/usr/bin/env bash
# Sends a wave report to the owner's Telegram bot chat: text (split if long), a photo album and the zip.
# Token and chat id live OUTSIDE the repo: ~/.config/hearthholm/telegram_bot_token and telegram_chat_id (chmod 600).
# Usage: bash minecraft/tools/tg-report.sh <text-file> [--zip file.zip] [--zip-caption "text"] [photo1.png photo2.png ...]
set -uo pipefail
TOK=$(cat ~/.config/hearthholm/telegram_bot_token); CHAT=$(cat ~/.config/hearthholm/telegram_chat_id)
API="https://api.telegram.org/bot$TOK"
TEXT=${1:?text file}; shift
ZIP=""; ZCAP=""; PHOTOS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --zip) ZIP=$2; shift 2;;
    --zip-caption) ZCAP=$2; shift 2;;
    *) PHOTOS+=("$1"); shift;;
  esac
done
status() { python3 -c "import sys,json; d=json.load(sys.stdin); print('ok' if d.get('ok') else 'ERROR: '+str(d.get('description')))"; }
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
# the text goes out in chunks of at most 3900 characters, split on line borders (Telegram allows 4096)
python3 - "$TEXT" "$TMP" <<'PY'
import sys
text = open(sys.argv[1], encoding='utf-8').read()
chunks, cur = [], ''
for line in text.split('\n'):
    if len(cur) + len(line) + 1 > 3900:
        chunks.append(cur); cur = ''
    cur += line + '\n'
if cur.strip(): chunks.append(cur)
for i, c in enumerate(chunks): open(f'{sys.argv[2]}/chunk{i:02d}.txt', 'w', encoding='utf-8').write(c)
PY
for f in "$TMP"/chunk*.txt; do
  echo -n "text $(basename "$f"): "
  curl -s -X POST "$API/sendMessage" --data-urlencode chat_id="$CHAT" --data-urlencode text@"$f" | status
done
if [ ${#PHOTOS[@]} -eq 1 ]; then
  echo -n "photo: "; curl -s -X POST "$API/sendPhoto" -F chat_id="$CHAT" -F photo=@"${PHOTOS[0]}" | status
elif [ ${#PHOTOS[@]} -gt 1 ]; then
  ARGS=(); MEDIA="["; i=0
  for p in "${PHOTOS[@]}"; do
    ARGS+=(-F "p$i=@$p"); [ $i -gt 0 ] && MEDIA+=","; MEDIA+="{\"type\":\"photo\",\"media\":\"attach://p$i\"}"; i=$((i+1))
  done
  MEDIA+="]"
  echo -n "photos: "; curl -s -X POST "$API/sendMediaGroup" -F chat_id="$CHAT" -F media="$MEDIA" "${ARGS[@]}" | status
fi
if [ -n "$ZIP" ]; then
  echo -n "zip: "; curl -s -X POST "$API/sendDocument" -F chat_id="$CHAT" -F document=@"$ZIP" -F caption="$ZCAP" | status
fi
