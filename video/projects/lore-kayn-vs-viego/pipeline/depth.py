"""Depth + effect masks for the v2 «living picture» shader.

Depth Anything V2 Small (ONNX, work/models/depth-anything-v2-small.onnx) on each inbox PNG, then on the graded
public/<img>.png (1242x2208):
  public/depth/<img>.png  8-bit depth, near = white, smoothed (for checking)
  public/maps/<img>.png   R = depth, G = green/teal fog mask, B = red haze mask (background only), all smoothed
  public/glow/<img>.png   blurred copy of the very bright saturated pixels (eyes, scythe, crown, moon) for additive glow
Run from the project dir: python3 pipeline/depth.py [img ...]
"""
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
W, H = 1242, 2208
sess = ort.InferenceSession(str(ROOT / 'work/models/depth-anything-v2-small.onnx'), providers=['CPUExecutionProvider'])
MEAN = np.array([0.485, 0.456, 0.406], np.float32)
STD = np.array([0.229, 0.224, 0.225], np.float32)


def blur(a: np.ndarray, r: int) -> np.ndarray:
    """~Gaussian blur: three box passes per axis (edge-padded)."""
    a = a.astype(np.float32)
    for axis in (0, 1):
        for _ in range(3):
            pad = [(0, 0)] * a.ndim
            pad[axis] = (r + 1, r)
            c = np.cumsum(np.pad(a, pad, mode='edge'), axis=axis)
            a = (np.take(c, range(2 * r + 1, c.shape[axis]), axis=axis) - np.take(c, range(0, c.shape[axis] - 2 * r - 1), axis=axis)) / (2 * r + 1)
    return a


def ramp(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def depth_of(src: Path) -> np.ndarray:
    img = Image.open(src).convert('RGB').resize((518, 924), Image.BICUBIC)  # multiples of 14, ~9:16
    x = ((np.asarray(img, np.float32) / 255 - MEAN) / STD).transpose(2, 0, 1)[None]
    d = sess.run(None, {'pixel_values': x})[0][0]
    lo, hi = np.percentile(d, [2, 98])
    d = np.clip((d - lo) / (hi - lo), 0, 1)
    d = np.asarray(Image.fromarray(d.astype(np.float32), 'F').resize((W, H), Image.BICUBIC))
    return np.clip(blur(d, 6), 0, 1)


names = sys.argv[1:] or sorted(p.stem for p in (ROOT / 'inbox').glob('*.png'))
for d in ('depth', 'maps', 'glow'):
    (ROOT / 'public' / d).mkdir(parents=True, exist_ok=True)
for n in names:
    dep = depth_of(ROOT / f'inbox/{n}.png')
    rgb = np.asarray(Image.open(ROOT / f'public/{n}.png').convert('RGB'), np.float32) / 255
    hsv = np.asarray(Image.open(ROOT / f'public/{n}.png').convert('RGB').convert('HSV'), np.float32) / 255
    h, s, v = hsv[..., 0] * 360, hsv[..., 1], hsv[..., 2]
    # green / teal mist of mid brightness
    fog = ramp(h, 80, 110) * (1 - ramp(h, 185, 215)) * ramp(s, 0.18, 0.4) * ramp(v, 0.08, 0.22) * (1 - ramp(v, 0.8, 0.95)) \
        * (1 - 0.7 * ramp(dep, 0.6, 0.85))  # near characters flow much less (faces stay put)
    # red haze: red hues, mid brightness, background only (keeps Kayn's body still)
    red = (1 - ramp(h, 12, 30) + ramp(h, 320, 345)).clip(0, 1) * ramp(s, 0.35, 0.6) * ramp(v, 0.08, 0.2) \
        * (1 - ramp(v, 0.7, 0.9)) * (1 - ramp(dep, 0.35, 0.55))
    fog, red = blur(fog, 10), blur(red, 10)
    # very bright saturated light: eyes, scythe edge, crown, moon
    lum = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    lit = np.maximum(ramp(s, 0.4, 0.7) * ramp(v, 0.6, 0.85), ramp(lum, 0.75, 0.95))
    glow = np.stack([blur(rgb[..., c] * lit, 14) for c in range(3)], -1)
    glow = np.clip(glow / max(1e-3, np.percentile(glow.max(-1), 99.7)) * 0.9, 0, 1)
    u8 = lambda a: (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8)
    Image.fromarray(u8(dep), 'L').save(ROOT / f'public/depth/{n}.png')
    Image.fromarray(u8(np.stack([dep, fog, red], -1)), 'RGB').save(ROOT / f'public/maps/{n}.png')
    Image.fromarray(u8(glow), 'RGB').save(ROOT / f'public/glow/{n}.png')
    print(n, f'fog {fog.mean():.3f} red {red.mean():.3f} lit {lit.mean():.3f}')
