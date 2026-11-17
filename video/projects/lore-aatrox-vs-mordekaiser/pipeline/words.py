"""inbox/words.json (Whisper [word, start, end]) -> src/words.json for the captions + out/voice.wav.

  python3 pipeline/words.py
Timings are taken as delivered; only the spelling fixes from NOTES.md are applied: Железа->Железо, топлива->топливо,
Даркином/Даркина lowercase, «Бессмертный Бастион», «Царство смерти» (74.9 s). The dash tokens «–» and the Whisper
hallucination at the end («Редактор субтитров Т.Горелова») are dropped. A phrase id `ph` changes after . ? !
Voice: highpass 70 Hz + two-pass loudnorm -16 LUFS / -1.5 dBTP, NOT sped up, padded to the EDL end.
"""
import json, re, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'inbox/voice.mp3'
EDL = json.loads((ROOT / 'inbox/edl.json').read_text(encoding='utf-8'))
FPS = EDL['fps']
raw = json.loads((ROOT / 'inbox/words.json').read_text(encoding='utf-8'))

FIX = {'Железа': 'Железо', 'топлива': 'топливо', 'Даркином': 'даркином', 'Даркина': 'даркина', 'бессмертный': 'Бессмертный',
       'бастион': 'Бастион'}
words, ph = [], 0
for tok, s, e in raw:
    if tok == 'Редактор':
        break  # hallucination tail
    if not re.search(r'\w', tok):
        continue  # «–»
    m = re.match(r'^(.*?\w)([^\w]*)$', tok)
    w, p = m.group(1), m.group(2)
    w = FIX.get(w, w)
    if w == 'царство' and abs(s - 74.9) < 0.3:
        w = 'Царство'
    words.append({'w': w, 'p': p, 's': round(s, 3), 'e': round(e, 3), 'ph': ph, 'q': False})
    if re.search(r'[.?!]', p):
        ph += 1

total = round((EDL['segments'][-1]['start'] + EDL['segments'][-1]['dur']) * FPS)
(ROOT / 'src/words.json').write_text(json.dumps({'fps': FPS, 'frames': total, 'words': words}, ensure_ascii=False, indent=0),
                                    encoding='utf-8')
print(f'{len(words)} words, {ph} phrases, video {total} frames ({total / FPS:.2f}s)')

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
