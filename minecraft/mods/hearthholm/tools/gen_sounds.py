#!/usr/bin/env python3
"""Synthesises the workshop sounds (saw, chisel, hammer, sizzle) as short mono OGGs.
Run from the mod folder: python3 tools/gen_sounds.py   (needs ffmpeg with libvorbis)"""
import math, os, random, struct, subprocess, tempfile, wave
BASE = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "hearthholm", "sounds")
OUT = os.path.join(BASE, "workshop")
RATE = 22050

def env(t, a, d):
    return min(1.0, t / a) * math.exp(-t / d) if a > 0 else math.exp(-t / d)

def saw(r):
    out, lp = [], 0.0
    strokes = 2
    for i in range(int(RATE * 0.55)):
        t = i / RATE
        phase = (t * strokes / 0.55) % 1.0
        amp = math.sin(math.pi * phase) ** 0.6
        n = r.uniform(-1, 1)
        lp += 0.35 * (n - lp)
        teeth = 0.5 + 0.5 * math.sin(2 * math.pi * (180 + 60 * phase) * t)
        out.append(0.55 * amp * (n - lp) * teeth)
    return out

def chisel(r):
    out = []
    hits = [0.0, 0.17]
    for i in range(int(RATE * 0.4)):
        t = i / RATE
        v = 0.0
        for h in hits:
            if t >= h:
                u = t - h
                v += env(u, 0.0005, 0.025) * (0.6 * r.uniform(-1, 1) + 0.5 * math.sin(2 * math.pi * 2900 * u) + 0.3 * math.sin(2 * math.pi * 4700 * u))
        out.append(0.6 * v)
    return out

def hammer(r):
    out = []
    f = r.uniform(1150, 1300)
    for i in range(int(RATE * 0.7)):
        t = i / RATE
        e = env(t, 0.0008, 0.16)
        v = e * (0.5 * math.sin(2 * math.pi * f * t) + 0.3 * math.sin(2 * math.pi * f * 2.76 * t) + 0.2 * math.sin(2 * math.pi * f * 5.4 * t))
        v += env(t, 0.0003, 0.01) * r.uniform(-1, 1) * 0.8
        out.append(0.6 * v)
    return out

def sizzle(r):
    out, hp = [], 0.0
    for i in range(int(RATE * 0.9)):
        t = i / RATE
        n = r.uniform(-1, 1)
        hp = 0.6 * hp + 0.4 * n
        crackle = (r.random() < 0.004) * r.uniform(-1, 1) * 2
        amp = 0.35 * (0.7 + 0.3 * math.sin(2 * math.pi * 7 * t)) * min(1, t / 0.05) * min(1, (0.9 - t) / 0.2)
        out.append(amp * (n - hp) + 0.4 * crackle)
    return out

def write(name, samples, out=None):
    peak = max(1e-6, max(abs(s) for s in samples))
    data = b"".join(struct.pack("<h", int(max(-1, min(1, s / peak * 0.8)) * 32767)) for s in samples)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as wf:
        wf.setnchannels(1); wf.setsampwidth(2); wf.setframerate(RATE); wf.writeframes(data)
    out = out or OUT
    os.makedirs(out, exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(out, name + ".ogg")], check=True)
    os.remove(path)

# ---- wave B: social sounds (bell, murmur, festival tune) -------------------------------------
SOCIAL = os.path.join(BASE, "social")

def bell(r):
    f0 = 523.25
    parts = [(1.0, 1.0, 2.2), (2.0, 0.6, 1.5), (2.76, 0.5, 1.1), (4.07, 0.35, 0.7), (5.4, 0.2, 0.45), (0.5, 0.5, 2.6)]
    out = []
    for i in range(int(RATE * 3.2)):
        t = i / RATE
        v = sum(a * math.exp(-t / d) * math.sin(2 * math.pi * f0 * k * t + 0.3 * k) for k, a, d in parts)
        v += env(t, 0.0003, 0.02) * r.uniform(-1, 1) * 0.5
        out.append(v * min(1, (3.2 - t) / 0.3))
    return out

def murmur(r):
    """A few short voiced blips with a moving pitch and a vowel-like formant."""
    out = []
    base = r.uniform(120, 230)
    for syl in range(r.choice((3, 4))):
        dur = r.uniform(0.07, 0.12)
        f_start, f_end = base * r.uniform(0.9, 1.25), base * r.uniform(0.8, 1.3)
        formant = r.choice((500, 700, 900, 1100, 1400))
        ph = 0.0
        for i in range(int(RATE * dur)):
            t = i / RATE
            u = t / dur
            f = f_start + (f_end - f_start) * u + 3 * math.sin(2 * math.pi * 30 * t)
            ph += 2 * math.pi * f / RATE
            v = 0.0
            for h in range(1, 12):
                v += math.exp(-((h * f - formant) / 350.0) ** 2) * math.sin(h * ph) / h ** 0.3
            out.append(0.35 * v * min(1, t / 0.012) * min(1, (dur - t) / 0.03))
        out.extend([0.0] * int(RATE * r.uniform(0.03, 0.07)))
    return out

def festival(r):
    bpm, beats = 96, 16
    beat = 60 / bpm
    n = int(RATE * beat * beats)
    buf = [0.0] * n
    def hz(m): return 440 * 2 ** ((m - 69) / 12)
    def add(start, dur, f, amp, kind):
        s0 = int(start * RATE)
        for i in range(int((dur + 0.5) * RATE)):
            t = i / RATE
            if kind == "lead":
                e = min(1, t / 0.012) * (math.exp(-t / (dur * 0.9 + 0.1)) if t > dur else 1.0) * (0.75 + 0.25 * math.exp(-t / 0.25))
                vib = 1 + 0.004 * math.sin(2 * math.pi * 5.5 * t) * min(1, t / 0.2)
                v = math.sin(2 * math.pi * f * vib * t) + 0.35 * math.sin(4 * math.pi * f * vib * t) + 0.18 * math.sin(6 * math.pi * f * vib * t)
                if t > dur: e = math.exp(-(t - dur) / 0.12)
            else:
                e = min(1, t / 0.004) * math.exp(-t / 0.35)
                v = math.sin(2 * math.pi * f * t) + 0.25 * math.sin(4 * math.pi * f * t)
            buf[(s0 + i) % n] += amp * e * v
    melody = [(76, 1), (79, 1), (84, 1), (79, 1), (81, 1), (79, .5), (77, .5), (76, 2),
              (74, 1), (77, 1), (81, 1), (77, 1), (79, 1), (76, .5), (74, .5), (72, 2)]
    t = 0.0
    for m, d in melody:
        add(t * beat, d * beat * 0.92, hz(m), 0.30, "lead")
        t += d
    for bar, root in enumerate((48, 41, 50, 43)):
        for k in range(4):
            note = root if k % 2 == 0 else root + 7
            add((bar * 4 + k) * beat, beat, hz(note), 0.34, "bass")
            if k % 2 == 1:  # soft chord stab on the off-beat
                for th in (0, 4, 7):
                    add((bar * 4 + k) * beat + beat / 2, beat * 0.4, hz(root + 12 + th), 0.07, "bass")
    for b in range(beats * 2):  # tambourine ticks
        s0 = int(b * beat / 2 * RATE)
        for i in range(int(0.05 * RATE)):
            tt = i / RATE
            buf[(s0 + i) % n] += (0.09 if b % 2 else 0.05) * math.exp(-tt / 0.012) * r.uniform(-1, 1)
    return buf

for name, fn, seeds in (("bell", bell, (1,)), ("murmur", murmur, (1, 2, 3, 4)), ("festival", festival, (1,))):
    for v in seeds:
        write(f"{name}{v}" if name != "festival" else "festival", fn(random.Random(v * 17 + 5)), SOCIAL)

for kind, fn in {"saw": saw, "chisel": chisel, "hammer": hammer, "sizzle": sizzle}.items():
    for v in (1, 2):
        write(f"{kind}{v}", fn(random.Random(v * 31 + len(kind))))
print("sounds written to", os.path.normpath(OUT))
