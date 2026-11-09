"""Voice-over + word timings for lore-kayn-vs-viego.

Piper TTS per scene (one chunk per sentence), word times from Piper's own phoneme alignments (exact, no Whisper).
Writes work/vo/voice_raw.wav (scene timeline, 22050 Hz mono) and src/timeline.json (scene frames + word times).
Run from the project dir: python3 pipeline/vo.py
"""
import json, math, re, wave
from pathlib import Path

import numpy as np
from piper import PiperVoice, SynthesisConfig

ROOT = Path(__file__).resolve().parent.parent
cfg = json.loads((ROOT / 'pipeline/scenes.json').read_text(encoding='utf-8'))
FPS = cfg['fps']
voice = PiperVoice.load(ROOT / f"work/voices/{cfg['voice']}.onnx", include_alignments=True)
syn = SynthesisConfig(length_scale=cfg['lengthScale'])
SR = voice.config.sample_rate
STRESS = set('ˈˌ')
PUNCT = set('.,;:!?—-…')


def text_words(text):
    """Display words (letters/digits, hyphenated kept), each with trailing punctuation for chunking."""
    out = []
    for tok in text.split():
        if not re.search(r'\w', tok):  # a lone dash: attach to previous word
            if out:
                out[-1]['punct'] += tok
            continue
        m = re.match(r'^(.*?\w)([^\w]*)$', tok)
        out.append({'w': m.group(1), 'punct': m.group(2)})
    return out


def phoneme_words(chunk):
    """[(start_s, end_s)] per spoken word in one Piper chunk, from phoneme alignments."""
    words, t, cur = [], 0.0, None
    for a in chunk.phoneme_alignments:
        dur = float(a.num_samples) / SR
        p = a.phoneme
        if p in ('^', '$', '_'):
            pass
        elif p == ' ':
            if cur:
                words.append(cur)
            cur = None
        elif p in PUNCT:
            pass
        else:
            if cur is None:
                cur = [t, t + dur]
            else:
                cur[1] = t + dur
        t += dur
    if cur:
        words.append(cur)
    return words, t


scenes, audio, t0 = [], [], 0.0
for si, sc in enumerate(cfg['scenes']):
    gap = sc.get('gap', cfg['sentenceGap'])
    pieces, spans, t = [], [], 0.0
    for ci, ch in enumerate(voice.synthesize(sc['text'], syn, include_alignments=True)):
        if ci:
            pieces.append(np.zeros(int(gap * SR), np.float32))
            t += gap
        a = ch.audio_float_array.astype(np.float32)
        ws, total = phoneme_words(ch)
        scale = (len(a) / SR) / total if total else 1.0  # alignment sum vs real length (should be ~1)
        spans += [(t + s * scale, t + e * scale) for s, e in ws]
        pieces.append(a)
        t += len(a) / SR
    vo = np.concatenate(pieces)
    tw = text_words(sc['text'])
    if len(tw) != len(spans):
        raise SystemExit(f"scene {si + 1}: {len(tw)} text words vs {len(spans)} spoken words — fix the text")
    vo_len = len(vo) / SR
    dur = max(cfg['minScene'], vo_len + cfg['sceneTail'])
    if si == len(cfg['scenes']) - 1:
        dur += cfg['endHold']
    frames = math.ceil(dur * FPS)
    start_f = round(t0 * FPS)
    pad = np.zeros(int(round(frames / FPS * SR)) - len(vo), np.float32)
    audio += [vo, pad]
    scenes.append({
        'img': sc['img'], 'from': start_f, 'dur': frames, 'voice': round(vo_len, 3),
        'words': [{'w': w['w'], 'p': w['punct'], 's': round(t0 + s, 3), 'e': round(t0 + e, 3)}
                  for w, (s, e) in zip(tw, spans)],
    })
    t0 += frames / FPS
    print(f"{si + 1:2d} {sc['img']:>3} voice {vo_len:5.2f}s scene {frames / FPS:5.2f}s words {len(tw)}")

full = np.concatenate(audio)
(ROOT / 'work/vo').mkdir(parents=True, exist_ok=True)
with wave.open(str(ROOT / 'work/vo/voice_raw.wav'), 'wb') as w:
    w.setnchannels(1), w.setsampwidth(2), w.setframerate(SR)
    w.writeframes((np.clip(full, -1, 1) * 32767).astype(np.int16).tobytes())
total_f = sum(s['dur'] for s in scenes)
(ROOT / 'src/timeline.json').write_text(json.dumps({'fps': FPS, 'frames': total_f, 'scenes': scenes},
                                                   ensure_ascii=False, indent=1), encoding='utf-8')
print(f"total {total_f / FPS:.2f}s ({total_f} frames)")
