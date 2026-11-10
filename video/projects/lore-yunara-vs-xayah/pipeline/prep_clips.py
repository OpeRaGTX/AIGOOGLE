"""EDL -> one frame-exact 30 fps background video (public/<part>/bg.mp4) + public/<part>/edl.json for Remotion.

  python3 pipeline/prep_clips.py p1
Each segment: clip from src_in, speed (setpts, no frame interpolation: fps filter picks nearest frames 24->30),
cover 1080x1920, then the last frame is held for `hold` s (the slow 1.00->1.04 push on holds is done in Remotion).
Segment i covers frames [round(start*30), round((start+dur)*30)) so the cuts land exactly on the EDL times.
"""
import json, shutil, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PART = sys.argv[1] if len(sys.argv) > 1 else 'p1'
edl = json.loads((ROOT / f'inbox/{PART}/edl.json').read_text(encoding='utf-8'))
FPS = edl['fps']
work = ROOT / f'work/{PART}/seg'
work.mkdir(parents=True, exist_ok=True)
out = ROOT / f'public/{PART}'
out.mkdir(parents=True, exist_ok=True)

lst = []
for i, s in enumerate(edl['segments']):
    f0, f1 = round(s['start'] * FPS), round((s['start'] + s['dur']) * FPS)
    n_hold = round(s['hold'] * FPS)
    n = f1 - f0
    play_src = (n - n_hold) / FPS * (s.get('speed') or 1)  # seconds of source consumed
    seg = work / f'{i:02d}.mp4'
    src = ROOT / 'inbox' / s['file'] if 'file' in s else ROOT / f"inbox/{PART}/{s['clip']}.mp4"
    cover = 'scale=1080:1920:force_original_aspect_ratio=increase:flags=lanczos,crop=1080:1920,setsar=1'
    if s.get('type') == 'image':  # placeholder still; Remotion draws the living picture over it
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-loop', '1', '-i', str(src), '-vf', f'{cover},fps={FPS}', '-frames:v', str(n),
                        '-c:v', 'libx264', '-preset', 'fast', '-crf', '12', '-pix_fmt', 'yuv420p', '-r', str(FPS), str(seg)], check=True)
    else:
        vf = f"setpts=(PTS-STARTPTS)/{s['speed']},fps={FPS},{cover},tpad=stop_mode=clone:stop={n_hold + FPS}"
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', f"{s['src_in']:.3f}", '-t', f'{play_src + 0.2:.3f}',
                        '-i', str(src), '-an', '-vf', vf, '-frames:v', str(n),
                        '-c:v', 'libx264', '-preset', 'fast', '-crf', '12', '-pix_fmt', 'yuv420p', '-r', str(FPS), str(seg)], check=True)
    got = int(subprocess.run(['ffprobe', '-v', 'error', '-count_frames', '-select_streams', 'v', '-show_entries',
                              'stream=nb_read_frames', '-of', 'csv=p=0', str(seg)], capture_output=True, text=True).stdout)
    print(f"{i:2d} {s.get('file', s.get('clip'))} {s['cue']:<15} frames {f0}-{f1} ({n}, hold {n_hold}) src {s['src_in']}+{play_src:.2f}s x{s.get('speed')}"
          + ('' if got == n else f'  !! got {got}'))
    lst.append(f"file '{seg}'")
(work / 'list.txt').write_text('\n'.join(lst))
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', str(work / 'list.txt'), '-c', 'copy',
                str(out / 'bg.mp4')], check=True)
shutil.copy(ROOT / f'inbox/{PART}/edl.json', out / 'edl.json')
print('->', out / 'bg.mp4')
