"""Wave L2: pixel art for the farmer's fertilizer barrel (the heap inside it) and the fisher's bucket with a fish. Original, drawn here.
The barrel's wood and the stone with the sword reuse existing textures (vanilla barrel, wave J3 rock and iron)."""
import random
from PIL import Image


def _clamp(v):
    return max(0, min(255, int(v)))


def fert_heap():
    """Pale bone-meal fertilizer: off-white grains with a few brown and green flecks (compost)."""
    r = random.Random(81)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 218 + r.randint(-14, 10)
            c = (k, _clamp(k - 6), _clamp(k - 26))
            roll = r.random()
            if roll < 0.07: c = (120, 92, 56)       # a brown crumb
            elif roll < 0.12: c = (96, 150, 62)     # a green fleck
            elif roll < 0.2: c = (_clamp(k + 18), _clamp(k + 18), _clamp(k - 2))
            im.putpixel((x, y), c + (255,))
    return im


def bucket_metal():
    """Grey bucket metal with two darker hoops and vertical staves."""
    r = random.Random(83)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = 150 if x % 4 else 128
            if y in (1, 2, 12, 13): base -= 40          # hoops
            if y == 0: base += 30
            k = base + r.randint(-5, 5)
            im.putpixel((x, y), (_clamp(k), _clamp(k + 3), _clamp(k + 10), 255))
    return im


def bucket_water():
    r = random.Random(85)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1 + r.randint(-6, 6) / 100
            c = (58, 118, 206) if (x + y) % 5 else (86, 150, 228)
            im.putpixel((x, y), (_clamp(c[0] * k), _clamp(c[1] * k), _clamp(c[2] * k), 255))
    return im


# a 16x16 fish seen from the side, head to the left: 1 = body, 2 = belly, 3 = fin/tail, 4 = eye, 0 = nothing
_FISH = [
    "................",
    "................",
    "................",
    "................",
    ".......33.......",
    "......333.....3.",
    "..111111111..33.",
    ".11411111111333.",
    ".11111111111333.",
    "..222222222.33..",
    "...2222222...3..",
    "................",
    "................",
    "................",
    "................",
    "................",
]


def fish_body():
    """A small orange fish with a lighter belly, drawn pixel by pixel (the bucket's catch)."""
    pal = {"1": (232, 132, 44), "2": (248, 200, 120), "3": (196, 84, 28), "4": (20, 20, 24)}
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(_FISH):
        for x, ch in enumerate(row):
            if ch in pal:
                im.putpixel((x, y), pal[ch] + (255,))
    return im


def run(gt):
    gt.save(fert_heap(), "block/fert_heap.png")
    gt.save(bucket_metal(), "block/bucket_metal.png")
    gt.save(bucket_water(), "block/bucket_water.png")
    gt.save(fish_body(), "block/fish_body.png")
