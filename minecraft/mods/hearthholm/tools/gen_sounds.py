#!/usr/bin/env python3
"""Synthesises the workshop sounds (saw, chisel, hammer, sizzle) as short mono OGGs.
Run from the mod folder: python3 tools/gen_sounds.py   (needs ffmpeg with libvorbis)"""
import math, os, random, struct, subprocess, tempfile, wave
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "hearthholm", "sounds", "workshop")
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

def write(name, samples):
    peak = max(1e-6, max(abs(s) for s in samples))
    data = b"".join(struct.pack("<h", int(max(-1, min(1, s / peak * 0.8)) * 32767)) for s in samples)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as wf:
        wf.setnchannels(1); wf.setsampwidth(2); wf.setframerate(RATE); wf.writeframes(data)
    os.makedirs(OUT, exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(path)

for kind, fn in {"saw": saw, "chisel": chisel, "hammer": hammer, "sizzle": sizzle}.items():
    for v in (1, 2):
        write(f"{kind}{v}", fn(random.Random(v * 31 + len(kind))))
print("sounds written to", os.path.normpath(OUT))
