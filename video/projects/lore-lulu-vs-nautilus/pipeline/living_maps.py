"""Living pictures for still frames (copied from lore-yunara-vs-xayah/pipeline/living_maps.py, palette adapted).

  python3 pipeline/living_maps.py p1
For each inbox/<part>/*.png: cover 1242x2208 (lanczos, keeps the pixel-art edges) -> public/<part>/img/<n>.png,
Depth Anything V2 Small (ONNX, work/models/depth-anything-v2-small.onnx) and masks:
  public/<part>/depth/<n>.png  8-bit depth, near = white, smoothed (for checking)
  public/<part>/maps/<n>.png   R = depth, G = water / mist mask (teal-blue-green, background only), B = lilac-pink light
  public/<part>/glow/<n>.png   blurred copy of the very bright saturated pixels (helmet window, surface light, sparks)
"""
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
W, H = 1242, 2208
GROW = 22
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
    d = Image.fromarray((np.clip(d, 0, 1) * 255 + 0.5).astype(np.uint8), 'L').resize((W, H), Image.BICUBIC)
    # near planes grow by GROW px before the blur: the depth step (where parallax stretches the picture) then lies in the
    # background around a figure, not on its outline; hard pixel-art edges, thin fingers and chains would smear otherwise
    d = np.asarray(d.filter(ImageFilter.MaxFilter(2 * GROW + 1)), np.float32) / 255
    return np.clip(blur(d, 8), 0, 1)


PART = sys.argv[1] if len(sys.argv) > 1 else 'p1'
OUT = ROOT / 'public' / PART
names = sorted(p.stem for p in (ROOT / 'inbox' / PART).glob('*.png'))
for d in ('img', 'depth', 'maps', 'glow'):
    (OUT / d).mkdir(parents=True, exist_ok=True)
for n in names:
    src = Image.open(ROOT / f'inbox/{PART}/{n}.png').convert('RGB')
    k = max(W / src.width, H / src.height)
    big = src.resize((round(src.width * k), round(src.height * k)), Image.LANCZOS)
    big = big.crop(((big.width - W) // 2, (big.height - H) // 2, (big.width - W) // 2 + W, (big.height - H) // 2 + H))
    big.save(OUT / f'img/{n}.png')
    dep = depth_of(ROOT / f'inbox/{PART}/{n}.png')
    rgb = np.asarray(big, np.float32) / 255
    hsv = np.asarray(big.convert('HSV'), np.float32) / 255
    h, s, v = hsv[..., 0] * 360, hsv[..., 1], hsv[..., 2]
    # water / mist: green-teal-blue of mid brightness, far background only (the characters sit nearer and must not flow)
    fog = ramp(h, 95, 130) * (1 - ramp(h, 255, 275)) * ramp(s, 0.2, 0.4) * ramp(v, 0.1, 0.25) * (1 - ramp(v, 0.85, 0.97)) \
        * (1 - ramp(dep, 0.18, 0.35))
    # lilac / pink light in the background (the glow on the surface, Glade magic)
    red = ramp(h, 265, 285) * (1 - ramp(h, 340, 358)) * ramp(s, 0.3, 0.5) * ramp(v, 0.2, 0.4) * (1 - ramp(dep, 0.18, 0.35))
    fog, red = blur(fog, 10), blur(red, 10)
    # very bright saturated light: helmet window, surface light, sparks
    lum = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    lit = np.maximum(ramp(s, 0.4, 0.7) * ramp(v, 0.6, 0.85), ramp(lum, 0.75, 0.95))
    glow = np.stack([blur(rgb[..., c] * lit, 14) for c in range(3)], -1)
    glow = np.clip(glow / max(1e-3, np.percentile(glow.max(-1), 99.7)) * 0.9, 0, 1)
    u8 = lambda a: (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8)
    Image.fromarray(u8(dep), 'L').save(OUT / f'depth/{n}.png')
    Image.fromarray(u8(np.stack([dep, fog, red], -1)), 'RGB').save(OUT / f'maps/{n}.png')
    Image.fromarray(u8(glow), 'RGB').save(OUT / f'glow/{n}.png')
    print(n, f'fog {fog.mean():.3f} red {red.mean():.3f} lit {lit.mean():.3f}')
