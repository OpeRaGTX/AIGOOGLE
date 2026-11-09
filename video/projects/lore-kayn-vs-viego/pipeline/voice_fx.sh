#!/usr/bin/env bash
# work/vo/voice_raw.wav -> out/voice.wav: EQ, compression, light room echo, two-pass loudnorm -16 LUFS / -1.5 dBTP.
set -euo pipefail
cd "$(dirname "$0")/.."
FX="aresample=48000:resampler=soxr,highpass=f=70,lowshelf=g=3:f=150,acompressor=threshold=-18dB:ratio=3:attack=5:release=80:makeup=2,aecho=0.8:0.6:40:0.15"
M=$(ffmpeg -hide_banner -nostats -i work/vo/voice_raw.wav -af "$FX,loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json" -f null - 2>&1 | sed -n '/^{/,/^}/p')
g() { echo "$M" | python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"; }
mkdir -p out
ffmpeg -v error -y -i work/vo/voice_raw.wav -af "$FX,loudnorm=I=-16:TP=-1.5:LRA=11:measured_I=$(g input_i):measured_TP=$(g input_tp):measured_LRA=$(g input_lra):measured_thresh=$(g input_thresh):offset=$(g target_offset):linear=true,aresample=48000" -ac 2 -c:a pcm_s16le out/voice.wav
ffmpeg -hide_banner -nostats -i out/voice.wav -af ebur128=peak=true -f null - 2>&1 | grep -E "^\s+(I|Peak):" | tr -s ' '
