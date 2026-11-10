"""Living pictures for still frames (copied from lore-kayn-vs-viego/pipeline/depth.py, palette adapted).

  python3 pipeline/living_maps.py p2
For each inbox/<part>/*.png: cover 1242x2208 (lanczos) -> public/<part>/img/<n>.png, Depth Anything V2 Small (ONNX,
work/models/depth-anything-v2-small.onnx) and masks:
  public/<part>/depth/<n>.png  8-bit depth, near = white, smoothed (for checking)
  public/<part>/maps/<n>.png   R = depth, G = mist mask (cyan/blue/violet light, background only), B = magenta haze
  public/<part>/glow/<n>.png   blurred copy of the very bright saturated pixels (eyes, beam, moon, fire) for additive glow
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


PART = sys.argv[1] if len(sys.argv) > 1 else 'p2'
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
    # mist / spirit light: cyan-blue-violet hues of mid brightness, background only (both heroines are violet too)
    fog = ramp(h, 160, 185) * (1 - ramp(h, 295, 320)) * ramp(s, 0.2, 0.4) * ramp(v, 0.1, 0.25) * (1 - ramp(v, 0.85, 0.97)) \
        * (1 - ramp(dep, 0.18, 0.35))  # far background only: in close-ups the heads sit at depth ~0.5
    # magenta haze (Xayah's feathers in the background)
    red = ramp(h, 295, 315) * (1 - ramp(h, 345, 358)) * ramp(s, 0.35, 0.6) * ramp(v, 0.1, 0.25) * (1 - ramp(dep, 0.18, 0.35))
    fog, red = blur(fog, 10), blur(red, 10)
    # very bright saturated light: eyes, scythe edge, crown, moon
    lum = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    lit = np.maximum(ramp(s, 0.4, 0.7) * ramp(v, 0.6, 0.85), ramp(lum, 0.75, 0.95))
    glow = np.stack([blur(rgb[..., c] * lit, 14) for c in range(3)], -1)
    glow = np.clip(glow / max(1e-3, np.percentile(glow.max(-1), 99.7)) * 0.9, 0, 1)
    u8 = lambda a: (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8)
    Image.fromarray(u8(dep), 'L').save(OUT / f'depth/{n}.png')
    Image.fromarray(u8(np.stack([dep, fog, red], -1)), 'RGB').save(OUT / f'maps/{n}.png')
    Image.fromarray(u8(glow), 'RGB').save(OUT / f'glow/{n}.png')
    print(n, f'fog {fog.mean():.3f} red {red.mean():.3f} lit {lit.mean():.3f}')
