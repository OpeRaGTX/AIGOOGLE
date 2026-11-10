"""Part voice -> word timings for captions (script spelling, Whisper timing) + processed voice track.

  python3 pipeline/align_voice.py p1
Reads inbox/<part>/voice.mp3, inbox/<part>/edl.json (video length) and pipeline/script.json (part 1) /
pipeline/script_<part>.json; Whisper words cached in work/asr/words.json (faster-whisper large-v3-turbo, word timestamps;
set HF_HUB_DISABLE_XET=1 in the cloud). Writes src/words.json and out/voice-<part>.wav (highpass 70 Hz + two-pass
loudnorm -16 LUFS / -1.5 dBTP, NOT sped up, padded to the EDL end).
"""
import difflib, json, re, subprocess, sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
PART = sys.argv[1] if len(sys.argv) > 1 else 'p1'
SRC = ROOT / f'inbox/{PART}/voice.mp3'
EDL = json.loads((ROOT / f'inbox/{PART}/edl.json').read_text(encoding='utf-8'))
SCRIPT = ROOT / ('pipeline/script.json' if PART == 'p1' else f'pipeline/script_{PART}.json')
cfg = json.loads(SCRIPT.read_text(encoding='utf-8'))
ASR = ROOT / f'work/asr/words{"" if PART == "p1" else "_" + PART}.json'
norm = lambda w: re.sub(r'[^\w-]', '', w.lower().replace('ё', 'е')).strip('-')


def text_words(text):
    out = []
    for tok in text.split():
        if not re.search(r'\w', tok):
            if out:
                out[-1]['p'] += tok
            continue
        m = re.match(r"^([^\w]*)(.*?\w)([^\w]*)$", tok)
        out.append({'w': m.group(2), 'p': m.group(3)})
    return out


if not ASR.exists():
    from faster_whisper import WhisperModel
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(SRC), '-ac', '1', '-ar', '16000', '-f', 'f32le', '-'],
                         capture_output=True, check=True).stdout
    model = WhisperModel('large-v3-turbo', device='cpu', compute_type='int8', download_root=str(ROOT / 'work/models/whisper'))
    segs, _ = model.transcribe(np.frombuffer(raw, np.float32), language='ru', word_timestamps=True, beam_size=5)
    ASR.parent.mkdir(parents=True, exist_ok=True)
    ASR.write_text(json.dumps([{'w': w.word.strip(), 's': w.start, 'e': w.end} for s in segs for w in s.words],
                              ensure_ascii=False), encoding='utf-8')
asr = []
for w in json.loads(ASR.read_text(encoding='utf-8')):
    if asr and w['w'].startswith('-'):
        asr[-1]['w'] += w['w']
        asr[-1]['e'] = w['e']
    elif norm(w['w']):
        asr.append(dict(w))

# word starts/ends snapped out of pauses
sd = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(SRC), '-af', 'silencedetect=n=-40dB:d=0.12', '-f', 'null', '-'],
                    capture_output=True, text=True).stderr
pauses = list(zip([float(x) for x in re.findall(r'silence_start: ([\d.]+)', sd)],
                  [float(x) for x in re.findall(r'silence_end: ([\d.]+)', sd)]))
for w in asr:
    for a, b in pauses:
        if a - 0.08 <= w['s'] < b:  # whisper often starts a word right at the previous word's end
            w['s'] = b - 0.03
        if a < w['e'] <= b + 0.01:
            w['e'] = a + 0.03
    w['e'] = max(w['e'], w['s'] + 0.08)

script = [(pi, tw) for pi, ph in enumerate(cfg['phrases']) for tw in text_words(ph['text'])]
a, b = [norm(tw['w']) for _, tw in script], [norm(w['w']) for w in asr]
times = [None] * len(script)
for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
    if tag == 'equal':
        for k in range(i2 - i1):
            times[i1 + k] = (asr[j1 + k]['s'], asr[j1 + k]['e'])
    elif tag == 'replace':
        s0, e0 = asr[j1]['s'], asr[j2 - 1]['e']
        L = [len(a[i]) + 1 for i in range(i1, i2)]
        acc = 0
        for k, l in enumerate(L):
            times[i1 + k] = (s0 + (e0 - s0) * acc / sum(L), s0 + (e0 - s0) * (acc + l) / sum(L))
            acc += l
        print(f'  script {a[i1:i2]} <- heard {b[j1:j2]}')
    elif tag == 'delete':
        print(f'  not heard: {a[i1:i2]}')
    elif tag == 'insert':
        print(f'  extra heard: {b[j1:j2]}')
for i, t in enumerate(times):
    if t is None:
        prev = next((times[k][1] for k in range(i - 1, -1, -1) if times[k]), 0.0)
        nxt = next((times[k][0] for k in range(i + 1, len(times)) if times[k]), prev + 0.3)
        times[i] = (prev, max(prev + 0.05, (prev + nxt) / 2))

total = round(EDL['segments'][-1]['start'] * EDL['fps'] + EDL['segments'][-1]['dur'] * EDL['fps'])
words = [{'w': tw['w'], 'p': tw['p'], 's': round(t[0], 3), 'e': round(t[1], 3), 'ph': pi,
          'q': bool(cfg['phrases'][pi].get('quote'))} for (pi, tw), t in zip(script, times)]
(ROOT / 'src').mkdir(exist_ok=True)
(ROOT / 'src/words.json').write_text(json.dumps({'fps': EDL['fps'], 'frames': total, 'words': words}, ensure_ascii=False, indent=0),
                                    encoding='utf-8')
print(f'{len(words)} words, video {total} frames ({total / EDL["fps"]:.2f}s)')

FX = 'aresample=48000:resampler=soxr,highpass=f=70'
meas = subprocess.run(['ffmpeg', '-hide_banner', '-nostats', '-i', str(SRC), '-af', f'{FX},loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json',
                       '-f', 'null', '-'], capture_output=True, text=True).stderr
m = json.loads(meas[meas.rindex('{'):meas.rindex('}') + 1])
ln = (f"loudnorm=I=-16:TP=-1.5:LRA=11:measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}"
      f":measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true")
(ROOT / 'out').mkdir(exist_ok=True)
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(SRC), '-af', f'{FX},{ln},aresample=48000,apad', '-t', f'{total / EDL["fps"]:.3f}',
                '-ac', '2', '-c:a', 'pcm_s16le', str(ROOT / f'out/voice-{PART}.wav')], check=True)
print(f'voice -> out/voice-{PART}.wav')
