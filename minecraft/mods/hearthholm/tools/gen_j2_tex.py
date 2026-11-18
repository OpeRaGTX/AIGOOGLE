"""Wave J2: pixel art of the lumberjack's chopping block (stump bark, annual rings, axe handle and head). Original, drawn here."""
import random
from PIL import Image


def bark():
    r = random.Random(41)
    im = Image.new("RGBA", (16, 16))
    for x in range(16):
        col = x % 4
        base = (96, 64, 38) if col in (0, 2) else (112, 76, 44) if col == 1 else (80, 53, 31)
        for y in range(16):
            k = 1 + r.randint(-5, 5) / 100
            if (x * 7 + y * 3) % 11 == 0: k *= 0.82
            im.putpixel((x, y), (int(base[0] * k), int(base[1] * k), int(base[2] * k), 255))
    for (x, y) in ((4, 5), (5, 5), (4, 6), (11, 11), (12, 11), (11, 12)):
        im.putpixel((x, y), (58, 38, 22, 255))
    for (x, y) in ((3, 5), (10, 11)):
        im.putpixel((x, y), (130, 92, 56, 255))
    return im


def rings():
    r = random.Random(43)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.6) ** 2 + (y - 7.4) ** 2) ** 0.5
            if d > 7.4: c = (92, 62, 36)
            elif d > 6.4: c = (130, 92, 56)
            else:
                ring = int(d * 1.15) % 2
                c = (200, 158, 100) if ring == 0 else (176, 132, 80)
                if d < 1.2: c = (150, 104, 62)
            k = 1 + r.randint(-3, 3) / 100
            im.putpixel((x, y), (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255))
    for i in range(1, 6):   # one small crack
        im.putpixel((8 + i, 8), (120, 82, 48, 255))
    return im


def handle():
    r = random.Random(47)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = (146, 104, 58) if x % 3 != 1 else (124, 86, 48)
            k = 1 + r.randint(-4, 4) / 100
            im.putpixel((x, y), (int(base[0] * k), int(base[1] * k), int(base[2] * k), 255))
    return im


def iron():
    r = random.Random(53)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = (150, 156, 164)
            if x < 4: base = (214, 220, 226)        # honed edge
            elif y > 12: base = (104, 110, 120)
            elif (x + y) % 5 == 0: base = (130, 136, 146)
            k = 1 + r.randint(-3, 3) / 100
            im.putpixel((x, y), (int(base[0] * k), int(base[1] * k), int(base[2] * k), 255))
    return im


def run(gt):
    gt.save(bark(), "block/chop_bark.png")
    gt.save(rings(), "block/chop_rings.png")
    gt.save(handle(), "block/chop_handle.png")
    gt.save(iron(), "block/chop_iron.png")
