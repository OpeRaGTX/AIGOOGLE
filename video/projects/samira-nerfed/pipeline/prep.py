#!/usr/bin/env python3
"""prep.py — bake the edit timeline for Remotion (stdlib only, needs ffmpeg/ffprobe on PATH).

  inbox/gameplay.mp4 + src/edl.json  ->  public/gameplay.mp4  retimed 60 fps timeline: cuts, speed, grade, RGB shift
                                         public/track.wav     music from edl.music.start, target LUFS, fade-out
                                         src/meta.json        frame numbers + geometry the composition reads

Usage: prep.py [--src FILE] [--grade FFMPEG_FILTERS] [--force]
Timeline positions in edl.json are either a number k (step on the 16th-note grid: t = phase + k*step) or {"f": frame}.
A segment without "src" continues the previous one (speed ramp without a cut). Segments are cached in work/segs/.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
import re
import subprocess
import sys
from fractions import Fraction
from pathlib import Path

P = Path(__file__).resolve().parent.parent
MCI = "minterpolate=fps={fps}:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1"
RGB_PRE, RGB_POST, RGB_AMP = 2, 6, 10  # frames before/after an rgb mark, peak shift in baked px


def sh(cmd, capture=False):
    r = subprocess.run(cmd, stdout=subprocess.PIPE if capture else None, stderr=subprocess.PIPE if capture else None, text=True)
    if r.returncode:
        sys.exit(f"failed: {' '.join(map(str, cmd))}\n{r.stderr or ''}")
    return r


def probe(src):
    j = json.loads(sh(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_streams", "-show_format", "-of", "json", str(src)], True).stdout)
    s = j["streams"][0]
    w, h = s["width"], s["height"]
    rot = 0
    for sd in s.get("side_data_list", []):
        rot = int(sd.get("rotation", rot))
    if abs(rot) % 180 == 90:
        w, h = h, w
    fps = Fraction(s.get("avg_frame_rate") or s["r_frame_rate"])
    # screen recordings are often VFR: snap the average to a standard rate
    nominal = min((24, 25, 30, 48, 50, 60, 90, 120), key=lambda r: abs(r - float(fps)))
    return {"w": w, "h": h, "fps": nominal, "avgFps": round(float(fps), 3), "duration": float(j["format"]["duration"]),
            "pix_fmt": s.get("pix_fmt"), "color": s.get("color_space")}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=str(P / "inbox" / "gameplay.mp4"))
    ap.add_argument("--edl", default=str(P / "src" / "edl.json"))
    ap.add_argument("--grade", default="")
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    src = Path(a.src)
    if not src.is_file():
        sys.exit(f"missing {src} — put the gameplay recording there")
    edl = json.loads(Path(a.edl).read_text(encoding="utf-8"))
    FPS = edl["fps"]
    mu = edl["music"]

    def frame(pos):
        return int(pos["f"]) if isinstance(pos, dict) else round((mu["phase"] + pos * mu["step"]) * FPS)

    info = probe(src)
    print(f"source: {info['w']}x{info['h']} {info['avgFps']} fps (nominal {info['fps']}) {info['duration']:.1f} s {info['pix_fmt']}")
    # baked region (normalised precrop -> even source px)
    px, py, pw, ph = edl.get("precrop", [0, 0, 1, 1])
    rx, ry = int(px * info["w"]) // 2 * 2, int(py * info["h"]) // 2 * 2
    rw, rh = int(pw * info["w"]) // 2 * 2, int(ph * info["h"]) // 2 * 2

    # --- timeline
    starts = [frame(s["at"]) for s in edl["segments"]]
    total = frame(edl["end"])
    if starts[0] != 0 or any(b <= a_ for a_, b in zip(starts, starts[1:] + [total])):
        sys.exit(f"segment starts must begin at frame 0 and increase: {starts} end {total}")
    segs, prev_out = [], None
    for s, f0, f1 in zip(edl["segments"], starts, starts[1:] + [total]):
        n = f1 - f0
        src_in = float(s["src"]) if "src" in s else prev_out
        if src_in is None:
            sys.exit("the first segment needs \"src\"")
        speed = (float(s["until"]) - src_in) / (n / FPS) if "until" in s else float(s.get("speed", 1))
        if not 0.2 <= speed <= 4:
            sys.exit(f"segment at frame {f0}: speed {speed:.2f} out of range")
        prev_out = src_in + n / FPS * speed
        if prev_out > info["duration"] + 0.05:
            sys.exit(f"segment at frame {f0} reads past the end of the source ({prev_out:.2f} > {info['duration']:.2f})")
        segs.append({"f0": f0, "n": n, "src": round(src_in, 4), "speed": round(speed, 4), "interp": s.get("interp", edl.get("interp", "mci"))})
    fx = {k: sorted(frame(x) for x in v) for k, v in edl.get("fx", {}).items()}

    # rgb shift amplitude per timeline frame (rises into the mark, decays after it)
    rgb = {}
    for m in fx.get("rgb", []):
        for d in range(-RGB_PRE, RGB_POST):
            amp = round(edl.get("rgbAmp", RGB_AMP) * ((d + RGB_PRE + 1) / (RGB_PRE + 1) if d < 0 else 1 - d / RGB_POST))
            if amp > 0 and 0 <= m + d < total:
                rgb[m + d] = max(rgb.get(m + d, 0), amp)

    up = int(edl.get("upscale", 1))
    # --- segments
    work = P / "work" / "segs"
    work.mkdir(parents=True, exist_ok=True)
    (P / "public").mkdir(exist_ok=True)
    stamp = f"{src.stat().st_size}:{src.stat().st_mtime_ns}"
    hd = info["color"] == "bt709" or (info["color"] in (None, "unknown") and info["h"] >= 720)
    mtx = "bt709" if hd else "bt601"
    tags = ["-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-color_range", "tv"] if hd else []
    files, jobs = [], []
    for i, s in enumerate(segs):
        eff = info["fps"] * s["speed"]  # frame rate after the speed change
        vf = []  # no cfr pre-filter: minterpolate works from real timestamps, so dropped frames of a VFR recording get filled
        if (rx, ry, rw, rh) != (0, 0, info["w"], info["h"]):
            vf.append(f"crop={rw}:{rh}:{rx}:{ry}")
        vf.append(f"setpts=(PTS-STARTPTS)/{s['speed']}")
        vf.append(MCI.format(fps=FPS) if eff < FPS - 0.5 and s["interp"] == "mci" else f"fps={FPS}")
        vf += ["tpad=stop_mode=clone:stop_duration=2", f"trim=end_frame={s['n']}", f"setpts=N/({FPS}*TB)"]
        vf += [f"scale=in_color_matrix={mtx}:out_color_matrix={mtx}", "format=gbrp"]  # explicit matrix: untagged HD is bt709, not swscale's bt601
        if a.grade:
            vf.append(a.grade)
        if up > 1:  # low-res source: upscale here (lanczos + light sharpening) instead of in the browser
            vf.append(f"scale={rw * up}:{rh * up}:flags=lanczos+accurate_rnd")
            if edl.get("sharpen"):
                vf.append(f"unsharp=5:5:{edl['sharpen']}:5:5:0")
        amps = {}
        for f in range(s["f0"], s["f0"] + s["n"]):
            if f in rgb:
                amps.setdefault(rgb[f], []).append(f - s["f0"])
        for amp, fr in sorted(amps.items()):
            vf.append(f"rgbashift=rh=-{amp}:bh={amp}:enable='{'+'.join(f'eq(n,{x})' for x in fr)}'")
        vf += [f"scale=in_color_matrix={mtx}:out_color_matrix={mtx}:out_range=tv", "format=yuv420p"]
        chain = ",".join(vf)
        key = hashlib.sha1(f"{stamp}|{s['src']}|{chain}".encode()).hexdigest()[:12]
        out = work / f"seg_{key}.mp4"
        files.append(out)
        if out.is_file() and not a.force:
            continue
        label = f"seg {i:2d}  f{s['f0']:4d}+{s['n']:3d}  src {s['src']:7.2f}s  x{s['speed']:<4g} {'mci' if 'minterpolate' in chain else 'fps'}"
        tmp = out.with_suffix(".part.mp4")
        jobs.append((label, out, tmp, ["ffmpeg", "-v", "error", "-y", "-ss", f"{s['src']:.4f}", "-t", f"{s['n'] / FPS * s['speed'] + 1:.4f}", "-i", str(src),
                    "-an", "-vf", chain, "-frames:v", str(s["n"]), "-r", str(FPS), "-c:v", "libx264", "-preset", "veryfast", "-crf", "12",
                    "-g", "30", "-bf", "0", *tags, str(tmp)]))

    def bake(job):  # minterpolate is single-threaded: run segments side by side
        label, out, tmp, cmd = job
        sh(cmd)
        tmp.replace(out)
        print(label, flush=True)

    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, (os.cpu_count() or 2))) as ex:
        list(ex.map(bake, jobs))
    lst = work / "concat.txt"
    lst.write_text("".join(f"file '{f.as_posix()}'\n" for f in files), encoding="utf-8")
    game = P / "public" / "gameplay.mp4"
    sh(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(lst), "-c", "copy", "-movflags", "+faststart", str(game)])
    got = int(sh(["ffprobe", "-v", "error", "-count_packets", "-select_streams", "v:0", "-show_entries", "stream=nb_read_packets", "-of", "csv=p=0", str(game)], True).stdout.strip())
    if got != total:
        sys.exit(f"baked timeline has {got} frames, expected {total}")

    # --- music: section from music.start, static gain to the target loudness, limiter at -1 dBTP, fade-out
    track, dur = P / "inbox" / mu["file"], total / FPS
    cut = ["-ss", str(mu["start"]), "-t", f"{dur:.4f}", "-i", str(track)]

    def loud(af):
        e = sh(["ffmpeg", "-hide_banner", "-nostats", *cut, "-af", af + "ebur128=peak=true", "-f", "null", "-"], True).stderr
        tail = e[e.rindex("Summary:"):]
        return float(re.search(r"I:\s+(-?[\d.]+) LUFS", tail).group(1)), float(re.search(r"Peak:\s+(-?[\d.]+) dBFS", tail).group(1))

    i0, _ = loud("")
    gain, chain = mu["lufs"] - i0, ""
    for _ in range(3):  # the limiter eats a little loudness: correct the gain
        chain = f"volume={gain:.2f}dB,alimiter=limit=0.891:level=false:attack=2:release=40,"
        i1, pk = loud(chain)
        if abs(mu["lufs"] - i1) < 0.1:
            break
        gain += mu["lufs"] - i1
    sh(["ffmpeg", "-v", "error", "-y", *cut, "-af", f"{chain}afade=t=out:st={dur - mu['fade']:.4f}:d={mu['fade']}", "-ar", "48000", "-c:a", "pcm_s16le",
        str(P / "public" / "track.wav")])
    print(f"music: {i0:.1f} -> {i1:.1f} LUFS (gain {gain:+.1f} dB, peak {pk:.1f} dBFS)")

    meta = {"_": "generated by pipeline/prep.py from edl.json — do not edit", "fps": FPS, "frames": total, "text": edl["text"],
            "src": {"w": info["w"], "h": info["h"], "fps": info["fps"]}, "baked": {"x": rx, "y": ry, "w": rw, "h": rh},
            "window": edl["window"], "keys": edl["keys"], "segments": [{k: s[k] for k in ("f0", "n", "src", "speed")} for s in segs], "fx": fx}
    (P / "src" / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent="\t") + "\n", encoding="utf-8")
    print(f"ok: {total} frames ({dur:.2f} s), {len(segs)} segments -> public/gameplay.mp4, public/track.wav, src/meta.json")


if __name__ == "__main__":
    main()
