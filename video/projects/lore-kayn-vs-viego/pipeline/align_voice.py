"""External voice-over (e.g. ElevenLabs) -> word timings + scene cuts.

Whisper (faster-whisper large-v3-turbo, word timestamps) on scenes.json["voiceFile"], words aligned to the script in
scenes.json (script spelling/punctuation kept for captions), starts snapped out of pauses (silencedetect).
Writes src/timeline.json (same format as vo.py) and out/voice.wav (highpass + two-pass loudnorm, padded to the end).
Run from the project dir: HF_HUB_DISABLE_XET=1 python3 pipeline/align_voice.py
"""
import difflib, json, math, re, subprocess
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
cfg = json.loads((ROOT / 'pipeline/scenes.json').read_text(encoding='utf-8'))
FPS = cfg['fps']
SRC = ROOT / cfg['voiceFile']
ASR = ROOT / 'work/asr/words.json'


def text_words(text):
    out = []
    for tok in text.split():
        if not re.search(r'\w', tok):
            if out:
                out[-1]['punct'] += tok
            continue
        m = re.match(r'^(.*?\w)([^\w]*)$', tok)
        out.append({'w': m.group(1), 'punct': m.group(2)})
    return out


norm = lambda w: re.sub(r'[^\wа-яё-]', '', w.lower().replace('ё', 'е')).strip('-')

# 1. ASR word timestamps (cached)
if not ASR.exists():
    from faster_whisper import WhisperModel
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(SRC), '-ac', '1', '-ar', '16000', '-f', 'f32le', '-'],
                         capture_output=True, check=True).stdout
    model = WhisperModel('large-v3-turbo', device='cpu', compute_type='int8', download_root=str(ROOT / 'work/models/whisper'))
    segs, _ = model.transcribe(np.frombuffer(raw, np.float32), language='ru', word_timestamps=True, beam_size=5,
                               initial_prompt='Каин против Виего. Рааст, даркин, Иония, Зед, Чёрный туман.')
    ASR.parent.mkdir(parents=True, exist_ok=True)
    ASR.write_text(json.dumps([{'w': w.word.strip(), 's': w.start, 'e': w.end} for s in segs for w in s.words],
                              ensure_ascii=False), encoding='utf-8')
asr = []
for w in json.loads(ASR.read_text(encoding='utf-8')):
    if asr and w['w'].startswith('-'):  # whisper splits hyphenated words: «по» + «-настоящему»
        asr[-1]['w'] += w['w']
        asr[-1]['e'] = w['e']
    elif norm(w['w']):
        asr.append(dict(w))

# 2. pauses: a word start inside a pause moves to the pause end, an end inside a pause moves to the pause start
sd = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(SRC), '-af', 'silencedetect=n=-40dB:d=0.12', '-f', 'null', '-'],
                    capture_output=True, text=True).stderr
st = [float(x) for x in re.findall(r'silence_start: ([\d.]+)', sd)]
en = [float(x) for x in re.findall(r'silence_end: ([\d.]+)', sd)]
pauses = list(zip(st, en))
for w in asr:
    for a, b in pauses:
        if a - 0.01 <= w['s'] < b:
            w['s'] = b - 0.03
        if a < w['e'] <= b + 0.01:
            w['e'] = a + 0.03
    w['e'] = max(w['e'], w['s'] + 0.08)

# 3. align script words to ASR words
script = [(si, tw) for si, sc in enumerate(cfg['scenes']) for tw in text_words(sc['text'])]
a, b = [norm(tw['w']) for _, tw in script], [norm(w['w']) for w in asr]
times = [None] * len(script)
for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
    if tag == 'equal':
        for k in range(i2 - i1):
            times[i1 + k] = (asr[j1 + k]['s'], asr[j1 + k]['e'])
    elif tag == 'replace' and j2 > j1:  # spread the ASR span over the script words by length
        s0, e0 = asr[j1]['s'], asr[j2 - 1]['e']
        L = [len(a[i]) + 1 for i in range(i1, i2)]
        acc = 0
        for k, l in enumerate(L):
            times[i1 + k] = (s0 + (e0 - s0) * acc / sum(L), s0 + (e0 - s0) * (acc + l) / sum(L))
            acc += l
        print(f'  replaced {a[i1:i2]} <- {b[j1:j2]}')
    elif tag in ('delete', 'replace'):
        print(f'  not heard: {a[i1:i2]}')
for i, t in enumerate(times):  # words the ASR missed: squeeze between neighbours
    if t is None:
        prev = next((times[k][1] for k in range(i - 1, -1, -1) if times[k]), 0.0)
        nxt = next((times[k][0] for k in range(i + 1, len(times)) if times[k]), prev + 0.3)
        times[i] = (prev, max(prev + 0.05, (prev + nxt) / 2))

# 4. scenes: cut on the first word of each scene (2 frames ahead of the voice), last scene holds endHold after the voice
dur_audio = float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', str(SRC)],
                                 capture_output=True, text=True).stdout)
n_sc = len(cfg['scenes'])
first = [next(i for i, (si, _) in enumerate(script) if si == s) for s in range(n_sc)]
last = [max(i for i, (si, _) in enumerate(script) if si == s) for s in range(n_sc)]
cuts = [0] + [max(math.ceil(times[last[s - 1]][1] * FPS), math.floor(times[first[s]][0] * FPS) - 2) for s in range(1, n_sc)]
total = math.ceil((dur_audio + cfg['endHold']) * FPS)
scenes = []
for s, sc in enumerate(cfg['scenes']):
    end = cuts[s + 1] if s + 1 < n_sc else total
    ws = [(tw, times[i]) for i, (si, tw) in enumerate(script) if si == s]
    scenes.append({'img': sc['img'], 'from': cuts[s], 'dur': end - cuts[s], 'voice': round(ws[-1][1][1] - ws[0][1][0], 3),
                   'words': [{'w': tw['w'], 'p': tw['punct'], 's': round(t[0], 3), 'e': round(t[1], 3)} for tw, t in ws]})
    print(f"{s + 1:2d} {sc['img']:>3} from {cuts[s] / FPS:6.2f}s dur {(end - cuts[s]) / FPS:5.2f}s words {len(ws)}")
(ROOT / 'src/timeline.json').write_text(json.dumps({'fps': FPS, 'frames': total, 'scenes': scenes}, ensure_ascii=False, indent=1),
                                       encoding='utf-8')
print(f'total {total / FPS:.2f}s ({total} frames), audio {dur_audio:.2f}s')

# 5. voice track: gentle highpass + two-pass loudnorm -16 LUFS / -1.5 dBTP, padded to the video length
FX = 'aresample=48000:resampler=soxr,highpass=f=70'
meas = subprocess.run(['ffmpeg', '-hide_banner', '-nostats', '-i', str(SRC), '-af', f'{FX},loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json',
                       '-f', 'null', '-'], capture_output=True, text=True).stderr
m = json.loads(meas[meas.rindex('{'):meas.rindex('}') + 1])
ln = (f"loudnorm=I=-16:TP=-1.5:LRA=11:measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}"
      f":measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true")
(ROOT / 'out').mkdir(exist_ok=True)
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(SRC), '-af', f'{FX},{ln},aresample=48000,apad', '-t', f'{total / FPS:.3f}',
                '-ac', '2', '-c:a', 'pcm_s16le', str(ROOT / 'out/voice.wav')], check=True)
print('voice -> out/voice.wav')
