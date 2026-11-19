"""Wave J3: pixel art of the miner's stone - a rough boulder with an iron pickaxe stuck in it. Original, drawn here."""
import random
from PIL import Image


def _clamp(v):
    return max(0, min(255, int(v)))


def rock(top=False):
    """A rough stone: blotchy grey with facets, dark cracks and a few lighter chips."""
    r = random.Random(61 if not top else 67)
    im = Image.new("RGBA", (16, 16))
    base = 132 if top else 112
    for y in range(16):
        for x in range(16):
            # blotches: coarse 4x4 noise cells plus fine grain
            blot = ((x // 4 * 7 + y // 4 * 13 + (x + y) // 5) % 5 - 2) * 5
            k = base + blot + r.randint(-7, 7)
            if (x * 5 + y * 3) % 13 == 0: k -= 14
            im.putpixel((x, y), (_clamp(k), _clamp(k + 1), _clamp(k + 5), 255))
    # facets: lighter upper-left edges, darker lower-right edges of a few chunks
    for (cx, cy, w, h) in ((1, 1, 6, 5), (8, 2, 6, 6), (2, 8, 5, 6), (9, 9, 5, 5)):
        for x in range(cx, cx + w):
            p = im.getpixel((x, cy)); im.putpixel((x, cy), (_clamp(p[0] + 20), _clamp(p[1] + 20), _clamp(p[2] + 20), 255))
            p = im.getpixel((x, min(15, cy + h))); im.putpixel((x, min(15, cy + h)), (_clamp(p[0] - 26), _clamp(p[1] - 26), _clamp(p[2] - 24), 255))
        for y in range(cy, cy + h):
            p = im.getpixel((cx, y)); im.putpixel((cx, y), (_clamp(p[0] + 14), _clamp(p[1] + 14), _clamp(p[2] + 14), 255))
            p = im.getpixel((min(15, cx + w), y)); im.putpixel((min(15, cx + w), y), (_clamp(p[0] - 22), _clamp(p[1] - 22), _clamp(p[2] - 20), 255))
    # cracks
    for (x, y) in ((3, 12), (4, 12), (4, 13), (5, 13), (11, 4), (11, 5), (12, 5), (12, 6), (7, 7), (8, 7), (8, 8)):
        im.putpixel((x, y), (54, 54, 60, 255))
    # a few ore-like specks (iron, coal) so it reads as a miner's rock
    for (x, y, c) in ((6, 3, (176, 132, 96)), (13, 12, (176, 132, 96)), (2, 5, (36, 36, 40)), (10, 14, (36, 36, 40))):
        im.putpixel((x, y), c + (255,))
    return im


def wood():
    r = random.Random(71)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = (150, 106, 58) if x % 3 != 1 else (122, 84, 46)
            k = 1 + r.randint(-4, 4) / 100
            im.putpixel((x, y), (_clamp(base[0] * k), _clamp(base[1] * k), _clamp(base[2] * k), 255))
    return im


def iron():
    r = random.Random(73)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = (168, 174, 182)
            if y < 4: base = (222, 228, 234)         # lit upper edge
            elif y > 11: base = (96, 102, 112)       # shadow
            elif (x + y) % 6 == 0: base = (140, 146, 156)
            k = 1 + r.randint(-3, 3) / 100
            im.putpixel((x, y), (_clamp(base[0] * k), _clamp(base[1] * k), _clamp(base[2] * k), 255))
    return im


def run(gt):
    gt.save(rock(), "block/mine_rock.png")
    gt.save(rock(True), "block/mine_rock_top.png")
    gt.save(wood(), "block/mine_pick_wood.png")
    gt.save(iron(), "block/mine_pick_iron.png")
