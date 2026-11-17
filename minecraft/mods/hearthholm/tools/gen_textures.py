#!/usr/bin/env python3
"""Generates Hearthholm textures (settler skin layers, profession outfits, block art, icon).

Run from the mod folder:  python3 tools/gen_textures.py
Settler layers are grayscale where the game tints them (skin, hair, eyes, clothes) and
coloured where they are used as-is (profession outfits). Layout = vanilla 64x64 player skin.
"""
import os, random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "hearthholm", "textures")
rnd = random.Random(7)

def img(w=64, h=64):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))

def save(im, rel):
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)

# ---------------------------------------------------------------- skin layout helpers
def box(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}

def parts(slim, outer=False):
    aw = 3 if slim else 4
    if not outer:
        return {"head": box(0, 0, 8, 8, 8), "body": box(16, 16, 8, 12, 4), "rarm": box(40, 16, aw, 12, 4),
                "larm": box(32, 48, aw, 12, 4), "rleg": box(0, 16, 4, 12, 4), "lleg": box(16, 48, 4, 12, 4)}
    return {"head": box(32, 0, 8, 8, 8), "body": box(16, 32, 8, 12, 4), "rarm": box(40, 32, aw, 12, 4),
            "larm": box(48, 48, aw, 12, 4), "rleg": box(0, 32, 4, 12, 4), "lleg": box(0, 48, 4, 12, 4)}

SHADE = {"front": 0, "top": 12, "bottom": -28, "right": -14, "left": -14, "back": -20}

def gray(v, a=255):
    v = max(0, min(255, int(v)))
    return (v, v, v, a)

def fill_face(im, rect, fn):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            c = fn(x, y, w, h)
            if c is not None:
                im.putpixel((x0 + x, y0 + y), c)

def fill_part(im, part, fn, faces=("top", "bottom", "right", "front", "left", "back")):
    for f in faces:
        fill_face(im, part[f], lambda x, y, w, h, f=f: fn(f, x, y, w, h))

def noise(n=6):
    return rnd.randint(-n, n)

# ---------------------------------------------------------------- base layers
def skin_layer(slim):
    im = img()
    P = parts(slim)
    for name, part in P.items():
        def fn(f, x, y, w, h, name=name):
            v = 222 + SHADE[f] + noise(4)
            if f in ("right", "left", "front", "back") and name in ("rarm", "larm", "rleg", "lleg"):
                v -= y * 0.8
            return gray(v)
        fill_part(im, part, fn)
    head = P["head"]["front"]
    x0, y0 = head[0], head[1]
    for x in (3, 4):
        im.putpixel((x0 + x, y0 + 5), gray(200))       # nose shade
        im.putpixel((x0 + x, y0 + 6), gray(150))       # mouth
    im.putpixel((x0 + 2, y0 + 6), gray(185))
    im.putpixel((x0 + 5, y0 + 6), gray(185))
    return im

def eyes_layers():
    white, iris = img(), img()
    x0, y0 = 8, 8
    for x in (1, 6):
        white.putpixel((x0 + x, y0 + 4), (245, 245, 240, 255))
    for x in (2, 5):
        iris.putpixel((x0 + x, y0 + 4), gray(235))
        white.putpixel((x0 + x, y0 + 3), (0, 0, 0, 0))
    return white, iris

def brows_layer():
    im = img()
    for x in (1, 2, 5, 6):
        im.putpixel((8 + x, 8 + 3), gray(170))
    return im

def clothes_shirt(slim):
    im = img()
    P = parts(slim)
    def body(f, x, y, w, h):
        v = 205 + SHADE[f] + noise(5)
        if f == "front":
            if y == 0 and 2 <= x <= 5: return gray(150)      # collar
            if x in (3, 4) and y % 3 == 1 and 1 <= y <= 9: return gray(160)  # buttons
            if y == h - 1: return gray(120)                  # belt line
        return gray(v)
    fill_part(im, P["body"], body)
    for arm in ("rarm", "larm"):
        def fn(f, x, y, w, h):
            if f == "bottom": return None
            if f != "top" and y > 8: return None             # hands stay bare
            v = 200 + SHADE[f] + noise(5)
            if f != "top" and y == 8: v -= 35                 # cuff
            return gray(v)
        fill_part(im, P[arm], fn)
    return im

def clothes_pants():
    im = img()
    P = parts(False)
    for leg in ("rleg", "lleg"):
        def fn(f, x, y, w, h):
            if f == "bottom": return None
            if f != "top" and y >= 10: return None
            v = 196 + SHADE[f] + noise(5)
            if f == "front" and y in (4, 5) and x in (1, 2): v -= 18   # knee
            return gray(v)
        fill_part(im, P[leg], fn)
    return im

def shoes():
    im = img()
    P = parts(False)
    for leg in ("rleg", "lleg"):
        def fn(f, x, y, w, h):
            if f == "top": return None
            if f == "bottom": return (52, 38, 28, 255)
            if y >= 10: return (74 + noise(4), 52, 36, 255) if y == 10 else (56, 40, 30, 255)
            return None
        fill_part(im, P[leg], fn)
    return im

# ---------------------------------------------------------------- hair
def hair_val(x, y, hl=False):
    base = 200 + noise(10)
    if (x + y) % 5 == 0: base += 22
    if hl: base += 15
    return gray(base)

def head_hair(rows_front, rows_side, rows_back, bangs=None, outer=None, body_back_rows=0, braid=False, bun=False, tail=False):
    im = img()
    H = parts(False)["head"]
    fill_face(im, H["top"], lambda x, y, w, h: hair_val(x, y, True))
    fill_face(im, H["front"], lambda x, y, w, h: hair_val(x, y) if (y < rows_front or (bangs and bangs(x, y))) else None)
    fill_face(im, H["right"], lambda x, y, w, h: hair_val(x, y) if y < rows_side else None)
    fill_face(im, H["left"], lambda x, y, w, h: hair_val(x, y) if y < rows_side else None)
    fill_face(im, H["back"], lambda x, y, w, h: hair_val(x, y) if y < rows_back else None)
    O = parts(False, True)["head"]
    if outer:
        for f, cond in outer.items():
            fill_face(im, O[f], lambda x, y, w, h, cond=cond: hair_val(x, y, True) if cond(x, y) else None)
    B = parts(False)["body"]
    if body_back_rows:
        fill_face(im, B["back"], lambda x, y, w, h: hair_val(x, y) if y < body_back_rows else None)
    if braid:
        fill_face(im, B["back"], lambda x, y, w, h: hair_val(x, y, y % 2 == 0) if x in (3, 4) and y < 9 else None)
    if bun:
        fill_face(im, O["top"], lambda x, y, w, h: hair_val(x, y, True) if 2 <= x <= 5 and 4 <= y <= 7 else None)
        fill_face(im, O["back"], lambda x, y, w, h: hair_val(x, y, True) if 2 <= x <= 5 and y <= 3 else None)
    if tail:
        fill_face(im, O["back"], lambda x, y, w, h: hair_val(x, y) if 3 <= x <= 4 and 2 <= y <= 7 else None)
    return im

def hairs():
    m = {
        1: head_hair(1, 3, 5),
        2: head_hair(1, 2, 4, outer={"top": lambda x, y: (x + y) % 3 != 0}),
        3: head_hair(2, 7, 8, bangs=lambda x, y: x in (0, 7) and y < 6),
        4: head_hair(2, 4, 7, bangs=lambda x, y: y == 2 and x <= 4),
    }
    f = {
        0: head_hair(1, 8, 8, bangs=lambda x, y: x in (0, 7), body_back_rows=5,
                     outer={"back": lambda x, y: True, "right": lambda x, y: y >= 2, "left": lambda x, y: y >= 2}),
        1: head_hair(2, 4, 8, braid=True),
        2: head_hair(2, 6, 6, bangs=lambda x, y: y == 2 and x % 2 == 0,
                     outer={"right": lambda x, y: 2 <= y <= 6, "left": lambda x, y: 2 <= y <= 6}),
        3: head_hair(1, 4, 6, bun=True),
        4: head_hair(2, 5, 8, tail=True),
    }
    return m, f

def beards():
    out = {}
    H = parts(False)["head"]
    def stub(f):
        im = img()
        fill_face(im, H["front"], lambda x, y, w, h: gray(150, 170) if y >= 5 and (x + y) % 2 == 0 and not (y == 6 and 2 <= x <= 5) else None)
        return im
    out[1] = stub(None)
    im = img()
    fill_face(im, H["front"], lambda x, y, w, h: hair_val(x, y) if (y >= 6 or (y == 5 and x in (0, 1, 6, 7))) and not (y == 6 and 3 <= x <= 4) else None)
    for side in ("right", "left"):
        fill_face(im, H[side], lambda x, y, w, h: hair_val(x, y) if y >= 5 else None)
    out[2] = im
    im = img()
    fill_face(im, H["front"], lambda x, y, w, h: hair_val(x, y, True) if y == 5 and 2 <= x <= 5 else (hair_val(x, y) if y == 6 and x in (1, 6) else None))
    out[3] = im
    return out

# ---------------------------------------------------------------- profession outfits (coloured)
def rgb(c, a=255, n=6):
    return (max(0, min(255, (c >> 16 & 255) + noise(n))), max(0, min(255, (c >> 8 & 255) + noise(n))),
            max(0, min(255, (c & 255) + noise(n))), a)

def shade(c, f):
    k = 1 + SHADE[f] / 255
    r, g, b = c >> 16 & 255, c >> 8 & 255, c & 255
    return (int(r * k) << 16) | (int(g * k) << 8) | int(b * k)

def helmet(im, color, rows=2, band=None, lamp=False, nose=False):
    O = parts(False, True)["head"]
    fill_face(im, O["top"], lambda x, y, w, h: rgb(shade(color, "top")))
    for f in ("front", "right", "left", "back"):
        fill_face(im, O[f], lambda x, y, w, h, f=f: rgb(shade(band if (band and y == rows - 1) else color, f)) if y < rows else None)
    if lamp:
        fill_face(im, O["front"], lambda x, y, w, h: (255, 240, 170, 255) if x in (3, 4) and y in (0, 1) else None)
        fill_face(im, O["front"], lambda x, y, w, h: (90, 90, 95, 255) if x in (2, 5) and y == 1 else None)
    if nose:
        fill_face(im, O["front"], lambda x, y, w, h: rgb(shade(color, "front")) if x in (3, 4) and 2 <= y <= 4 else None)

def outfit(prof, slim):
    im = img()
    P, O = parts(slim), parts(slim, True)
    if prof == "miner":
        helmet(im, 0xC9A227, 2, band=0x6B5A1A, lamp=True)
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(shade(0x5E636B, f)) if f != "bottom" and (x in (1, 6) or y >= 7) else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(0x6B4A2A) if f in ("front", "back") and y == 10 else None)
        for leg in ("rleg", "lleg"):
            fill_part(im, P[leg], lambda f, x, y, w, h: (60, 60, 64, 140) if f == "front" and (x + y) % 4 == 0 and y < 9 else None)
    elif prof == "lumberjack":
        def plaid(f, x, y, w, h):
            if f == "bottom": return None
            dark = (x // 2 + y // 2) % 2 == 0
            c = 0x2A1A18 if dark and (x % 4 < 2) else 0x9E2B25
            if y % 4 == 0: c = 0x2A1A18
            return rgb(shade(c, f), n=4)
        fill_part(im, O["body"], plaid)
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: plaid(f, x, y, w, h) if f == "top" or y <= 8 else None)
        helmet(im, 0x5A3A22, 2)
    elif prof == "farmer":
        O_h = parts(False, True)["head"]
        fill_face(im, O_h["top"], lambda x, y, w, h: rgb(shade(0xD9B85C, "top"), n=12))
        for f in ("front", "right", "left", "back"):
            fill_face(im, O_h[f], lambda x, y, w, h, f=f: rgb(shade(0xC9A24C if y else 0x8C6A2A, f), n=10) if y < 2 else None)
        denim = 0x41648F
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(shade(denim, f)) if f != "top" and f != "bottom" and (y >= 5 or (f in ("front", "back") and x in (1, 6))) else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0xCFA53A) if x in (1, 6) and y == 5 else None)
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: rgb(shade(denim, f)) if f not in ("top", "bottom") and y < 10 else None)
    elif prof == "fisher":
        O_h = parts(False, True)["head"]
        fill_face(im, O_h["top"], lambda x, y, w, h: rgb(shade(0x6B7445, "top")))
        for f in ("front", "right", "left", "back"):
            fill_face(im, O_h[f], lambda x, y, w, h, f=f: rgb(shade(0x5A6238, f)) if y < 2 else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(shade(0x9C8A5A, f)) if f not in ("top", "bottom") and not (f == "front" and x in (3, 4)) else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0x6E5F3C) if x in (1, 6) and y in (5, 6) else None)
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: rgb(shade(0x2F4A32, f)) if f != "top" and (f == "bottom" or y >= 6) else None)
    elif prof == "builder":
        helmet(im, 0xE8C02A, 2, band=0xB08A12)
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(shade(0xE07A2A, f)) if f not in ("top", "bottom") and y <= 8 else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(0xF2E36B) if f not in ("top", "bottom") and y == 5 else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(0x6B4A2A) if f not in ("top", "bottom") and y in (10, 11) else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0x9AA0A8) if x in (1, 6) and y == 11 else None)
    elif prof == "guard":
        helmet(im, 0xA8ADB3, 3, nose=True)
        mail = lambda f, x, y: rgb(shade(0x8C9096 if (x + y) % 2 else 0x6E7277, f), n=3)
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: mail(f, x, y) if f == "top" or y <= 7 else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: mail(f, x, y) if f not in ("bottom",) else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0x3A4F9A) if 1 <= x <= 6 and 1 <= y <= 11 else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0xE2C04A) if (x in (3, 4) and 3 <= y <= 7) or (y == 5 and 2 <= x <= 5) else None)
        fill_face(im, O["body"]["back"], lambda x, y, w, h: rgb(0x3A4F9A) if 1 <= x <= 6 and 1 <= y <= 11 else None)
    elif prof == "warrior":
        O_h = parts(False, True)["head"]
        for f in ("front", "right", "left", "back"):
            fill_face(im, O_h[f], lambda x, y, w, h, f=f: rgb(shade(0xB0302A, f)) if y == 2 else None)
        plate = lambda f: rgb(shade(0x7D7F86, f), n=8)
        fill_part(im, O["body"], lambda f, x, y, w, h: plate(f) if f != "bottom" and y <= 8 else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(0xB0302A) if f in ("front", "back") and abs((x if f == "front" else 7 - x) - y * 0.7) < 1 else None)
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: plate(f) if f == "top" or y <= 3 else (rgb(shade(0x6B4A2A, f)) if 6 <= y <= 8 else None))
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: rgb(shade(0x6B4A2A, f)) if f not in ("top", "bottom") and 5 <= y <= 9 else None)
    elif prof == "carpenter":
        helmet(im, 0x7A5230, 1)
        leather = 0x8A5A32
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(leather) if y >= 3 else (rgb(0x5E3C22) if x in (1, 6) else None))
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0x5E3C22) if 2 <= x <= 5 and y in (7, 8) else None)   # pocket
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0xE8D44A) if x == 2 and y == 6 else None)            # pencil
        fill_face(im, O["body"]["back"], lambda x, y, w, h: rgb(0x5E3C22) if abs(x - y) < 1 or abs(7 - x - y) < 1 else None)
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: rgb(shade(leather, f)) if f == "front" and y < 5 else None)
    elif prof == "mason":
        helmet(im, 0x8F8A80, 1)
        smock = 0xB8B0A0
        fill_part(im, O["body"], lambda f, x, y, w, h: (rgb(shade(smock, f)) if (x * 7 + y * 3) % 11 else rgb(0xE6E2DA)) if f not in ("top", "bottom") and y <= 10 else None)
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: rgb(shade(smock, f)) if f != "bottom" and (f == "top" or y <= 5) else None)
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: rgb(shade(0x6E6A62, f)) if f == "front" and y in (4, 5) else None)
    elif prof == "smith":
        O_h = parts(False, True)["head"]
        for f in ("front", "right", "left", "back"):
            fill_face(im, O_h[f], lambda x, y, w, h, f=f: rgb(shade(0xA0302A, f)) if y in (1, 2) else None)
        fill_face(im, O_h["back"], lambda x, y, w, h: rgb(0x7A2420) if x in (3, 4) and y in (3, 4) else None)
        apron = 0x3A2A20
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(apron, n=4) if y >= 1 and 1 <= x <= 6 else (rgb(0x2A1E18) if y == 0 and x in (2, 5) else None))
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0x6E5A48) if y in (5, 9) and 2 <= x <= 5 else None)
        fill_face(im, O["body"]["back"], lambda x, y, w, h: rgb(0x2A1E18) if y == 8 else None)
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: rgb(shade(0x4A3626, f)) if f != "top" and 7 <= y <= 11 else None)
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: rgb(shade(apron, f), n=4) if f == "front" and y < 7 else None)
    elif prof == "cook":
        O_h = parts(False, True)["head"]
        fill_face(im, O_h["top"], lambda x, y, w, h: rgb(shade(0xF4F2EC, "top"), n=4))
        for f in ("front", "right", "left", "back"):
            fill_face(im, O_h[f], lambda x, y, w, h, f=f: rgb(shade(0xF4F2EC if y else 0xFFFFFF, f), n=4) if y < 2 or (y == 2 and f != "front") else None)
        fill_face(im, O_h["front"], lambda x, y, w, h: rgb(0xD8D4CA) if y == 1 and x % 3 == 0 else None)
        white = 0xEEECE4
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(white, n=4) if y >= 2 else (rgb(0xC0392B) if 2 <= x <= 5 else None))
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0xC9C4B6) if x in (3, 4) and y in (4, 7) else None)
        fill_face(im, O["body"]["back"], lambda x, y, w, h: rgb(0xD8D4CA) if y == 9 else None)
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: rgb(shade(white, f), n=4) if f != "bottom" and (f == "top" or y <= 4) else None)
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: rgb(shade(white, f), n=4) if f == "front" and y < 6 else None)
    elif prof == "hauler":
        helmet(im, 0x5E7A3A, 1, band=0x46602A)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0x6B4A2A) if x in (1, 6) and y <= 9 else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0xB89A4A) if x in (1, 6) and y == 9 else None)
        pack = 0x8C6A3E
        fill_face(im, O["body"]["back"], lambda x, y, w, h: rgb(pack) if 1 <= x <= 6 and 1 <= y <= 9 else None)
        fill_face(im, O["body"]["back"], lambda x, y, w, h: rgb(0x5E4426) if (1 <= x <= 6 and y in (1, 4)) or (x in (3, 4) and y == 6) else None)
        for side in ("right", "left"):
            fill_face(im, O["body"][side], lambda x, y, w, h: rgb(shade(pack, "right")) if x >= 2 and 1 <= y <= 9 else None)
        fill_face(im, O["body"]["top"], lambda x, y, w, h: rgb(pack) if y >= 2 and 1 <= x <= 6 else None)
    return im

# ---------------------------------------------------------------- blocks
def plank_tex(base, w=16, h=16, rows=4, seed=0):
    r = random.Random(seed)
    im = Image.new("RGBA", (w, h))
    for y in range(h):
        row = y // rows
        for x in range(w):
            c = base
            k = 1 + (r.randint(-6, 6) + (row % 2) * -5) / 100
            if y % rows == rows - 1: k *= 0.72
            if (x + row * 5) % 13 == 0 and y % rows != rows - 1: k *= 0.85
            im.putpixel((x, y), (int((c >> 16 & 255) * k), int((c >> 8 & 255) * k), int((c & 255) * k), 255))
    return im

def put(im, pts, c):
    for (x, y) in pts:
        im.putpixel((x, y), c + (255,) if len(c) == 3 else c)

def desk_textures():
    top = plank_tex(0x8A5A32, seed=1)
    for x in range(3, 13):
        for y in range(3, 11):
            top.putpixel((x, y), (225, 205, 160, 255) if (x + y) % 7 else (200, 175, 130, 255))
    put(top, [(5, 5), (6, 6), (7, 6), (8, 7), (9, 7), (10, 6)], (120, 70, 40))   # map route
    put(top, [(11, 9), (12, 9), (11, 10), (12, 10)], (30, 30, 40))                # inkwell
    put(top, [(13, 8), (14, 7)], (235, 235, 235))                                 # quill
    side = plank_tex(0x5C3A22, seed=2)
    front = plank_tex(0x5C3A22, seed=3)
    for x in range(2, 14):
        for y in (5, 10):
            front.putpixel((x, y), (40, 26, 16, 255))
    put(front, [(7, 7), (8, 7), (7, 12), (8, 12)], (214, 170, 72))
    for x in range(16):
        side.putpixel((x, 0), (120, 80, 45, 255)); front.putpixel((x, 0), (120, 80, 45, 255))
    return top, side, front

def ledger_textures():
    side = plank_tex(0x9C7444, rows=5, seed=4)
    for i in range(16):
        side.putpixel((i, i), (90, 64, 38, 255)); side.putpixel((15 - i, i), (90, 64, 38, 255))
    top = plank_tex(0x9C7444, rows=5, seed=5)
    for x in range(2, 14):
        for y in range(4, 12):
            top.putpixel((x, y), (236, 226, 196, 255) if x != 8 else (150, 120, 80, 255))
    for y in (6, 8, 10):
        for x in list(range(3, 7)) + list(range(10, 13)):
            top.putpixel((x, y), (90, 90, 110, 255))
    return top, side

def sign_icon(kind):
    im = plank_tex(0xB89263, rows=16, seed=sum(map(ord, kind)) % 100)
    for x in range(16):
        im.putpixel((x, 0), (90, 62, 36, 255)); im.putpixel((x, 15), (90, 62, 36, 255))
    for y in range(16):
        im.putpixel((0, y), (90, 62, 36, 255)); im.putpixel((15, y), (90, 62, 36, 255))
    G = {
        "mine": ["......aaaa......", "....aa....aa....", "...a..cccc..a...", "......c..c......", ".......bb.......",
                 ".......bb.......", ".......bb.......", ".......bb.......", ".......bb.......", ".......bb......."],
        "forest": [".......g........", "......ggg.......", ".....ggGgg......", "....ggggggg.....", "...ggGgggGgg....",
                   "....ggggggg.....", "...ggggGgggg....", ".......bb.......", ".......bb.......", "......bbbb......"],
        "farm": ["..y.....y.....y.", ".yyy...yyy...yyy", "..y.....y.....y.", "..y.....y.....y.", "..g.....g.....g.",
                 ".gg....gg....gg.", "..g.....g.....g.", "..g.....g.....g.", "dddddddddddddddd", "dddddddddddddddd"],
        "fishing": ["................", "......ss........", "....sssssss..s..", "...sssseeesssss.", "..ssssssssss.ss.",
                    "...sssssssss.ss.", "....sssssss...s.", "......ss........", "wwwwwwwwwwwwwwww", ".w.w.w.w.w.w.w.w"],
        "patrol": ["....mmmmmmmm....", "...mmmmmmmmmm...", "...mmmyyyymmm...", "...mmmyyyymmm...", "...mmmmyymmmm...",
                   "....mmmmmmmm....", ".....mmmmmm.....", "......mmmm......", ".......mm.......", "................"],
        "territory": ["..bbbbbbbbbbbb..", "..rrrrrrrrrrrr..", "..rrrryyyyrrrr..", "..rrryyyyyyrrr..", "..rrrryyyyrrrr..",
                      "..rrrrrrrrrrrr..", "..rrrrrrrrrrrr..", "..rrr.rrrr.rrr..", "..rr...rr...rr..", "................"],
        "construction": ["....kkkkkkk.....", "....kkkkkkkk....", "....kkkkkkk.....", ".......bb.......", ".......bb.......",
                         ".......bb.......", ".......bb.......", ".......bb.......", "YzYzYzYzYzYzYzYz", "zYzYzYzYzYzYzYzY"],
    }
    pal = {"a": (150, 155, 165), "c": (110, 115, 125), "b": (110, 74, 40), "g": (70, 140, 60), "G": (110, 180, 80),
           "y": (225, 190, 70), "d": (110, 80, 50), "s": (140, 175, 200), "e": (30, 30, 40), "w": (60, 110, 190),
           "m": (60, 80, 160), "r": (180, 50, 42), "k": (130, 135, 145), "Y": (235, 200, 40), "z": (40, 36, 30)}
    rows = G[kind]
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                im.putpixel((i, 3 + j), pal[ch] + (255,))
    return im

def stone_tex(base, seed, bricks=False):
    r = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1 + r.randint(-8, 8) / 100
            if bricks and (y % 4 == 3 or (x + (4 if (y // 4) % 2 else 0)) % 8 == 7): k *= 0.7
            im.putpixel((x, y), (int((base >> 16 & 255) * k), int((base >> 8 & 255) * k), int((base & 255) * k), 255))
    return im

def draw(im, rows, pal, ox=0, oy=0):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                im.putpixel((ox + i, oy + j), pal[ch] + (255,))

def workshop_textures():
    out = {}
    top = plank_tex(0xA9784A, seed=11)
    draw(top, ["......SSSSSSSS..", ".....SsSsSsSsS..", "....hh.........."], {"S": (175, 180, 188), "s": (120, 125, 135), "h": (110, 70, 40)}, 0, 3)
    draw(top, ["cc..c", ".c.cc", "cc..."], {"c": (230, 205, 150)}, 2, 10)
    draw(top, ["....r", "...r.", "..r..", ".r...", "y...."], {"r": (130, 85, 45), "y": (205, 170, 70)}, 9, 9)
    out["carpenter_bench_top"] = top
    side = plank_tex(0x7C5233, seed=12)
    out["carpenter_bench_side"] = side
    front = plank_tex(0x7C5233, seed=13)
    for x in range(3, 13):
        front.putpixel((x, 1), (60, 40, 25, 255))
    draw(front, ["SS", "SS"], {"S": (150, 150, 158)}, 7, 0)
    out["carpenter_bench_front"] = front

    mt = stone_tex(0xA8A49C, 21)
    for x in range(16):
        mt.putpixel((x, 0), (90, 88, 84, 255)); mt.putpixel((x, 15), (90, 88, 84, 255))
    draw(mt, ["bbbbb.bbbb", "bbbbb.bbbb", "..........", "bb.bbbbb.b", "bb.bbbbb.b"], {"b": (150, 140, 128)}, 1, 2)
    draw(mt, ["ww....", ".ww...", "..wwkk", "...kkk"], {"w": (120, 80, 45), "k": (90, 95, 105)}, 9, 9)
    draw(mt, ["MM", "MM", ".h", ".h", ".h"], {"M": (70, 72, 78), "h": (120, 80, 45)}, 3, 9)
    out["mason_table_top"] = mt
    out["mason_table_side"] = stone_tex(0x8E8A84, 22, bricks=True)

    at = stone_tex(0x45474D, 31)
    for x in range(16):
        at.putpixel((x, 4), (95, 98, 106, 255)); at.putpixel((x, 11), (30, 31, 35, 255))
    draw(at, ["hhhhh", "hhhhh", "..w..", "..w..", "..w..", "..w.."], {"h": (110, 112, 120), "w": (120, 80, 45)}, 5, 5)
    out["smith_anvil_top"] = at
    asd = stone_tex(0x3E4046, 32)
    for x in range(16):
        asd.putpixel((x, 0), (90, 92, 100, 255))
    out["smith_anvil_side"] = asd
    stump = plank_tex(0x6B4A2A, rows=16, seed=33)
    for y in range(16):
        for x in (0, 15):
            stump.putpixel((x, y), (80, 55, 32, 255))
    out["smith_anvil_stump"] = stump

    stove_side = stone_tex(0x9C5A3C, 41, bricks=True)
    out["cook_stove_side"] = stove_side
    sf = stone_tex(0x9C5A3C, 42, bricks=True)
    for x in range(3, 13):
        for y in range(5, 14):
            edge = x in (3, 12) or y in (5, 13)
            if edge: sf.putpixel((x, y), (40, 40, 44, 255))
            else:
                fire = (255, 200 - (13 - y) * 12, 60, 255) if (x + y) % 3 else (255, 120, 30, 255)
                sf.putpixel((x, y), fire if y >= 9 else (30, 22, 18, 255))
    draw(sf, ["gggggggggg"], {"g": (70, 70, 76)}, 3, 2)
    out["cook_stove_front"] = sf
    st = stone_tex(0x4A4A50, 43)
    for x in range(16):
        for y in range(16):
            if (x % 4 == 0 or y % 4 == 0) and 1 <= x <= 14 and 1 <= y <= 14: st.putpixel((x, y), (28, 28, 32, 255))
    out["cook_stove_top"] = st
    pot = stone_tex(0x5A5E66, 44)
    for x in range(16):
        pot.putpixel((x, 0), (200, 170, 120, 255)); pot.putpixel((x, 1), (170, 120, 70, 255))
    out["cook_stove_pot"] = pot

    board = plank_tex(0x9C7444, rows=5, seed=51)
    for x in range(16):
        board.putpixel((x, 0), (90, 62, 36, 255)); board.putpixel((x, 10), (90, 62, 36, 255))
    for (px0, py0, col) in ((1, 2, (236, 226, 196)), (6, 1, (225, 235, 210)), (11, 2, (240, 220, 200)), (3, 6, (230, 225, 205)), (9, 6, (236, 226, 196))):
        for x in range(px0, px0 + 4):
            for y in range(py0, py0 + 3):
                board.putpixel((x, y), col + (255,))
        board.putpixel((px0 + 1, py0 + 1), (90, 90, 110, 255)); board.putpixel((px0 + 2, py0 + 1), (90, 90, 110, 255))
        board.putpixel((px0 + 1, py0), (200, 50, 40, 255))
    out["order_board_front"] = board
    out["order_board_back"] = plank_tex(0x7C5233, rows=5, seed=52)
    return out

def hearth_mark():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6.6:
                k = 1.0 - (x + y - 15) * 0.02
                c = (int(214 * k), int(160 * k), int(64 * k), 255) if d < 5.4 else (150, 104, 34, 255)
                im.putpixel((x, y), c)
    draw(im, ["...f....", "..ff.f..", "..fFff..", ".fFFFf..", ".fFYFf..", "..fFf...", "bbbbbbb."], {"f": (190, 70, 30), "F": (240, 130, 40), "Y": (255, 230, 120), "b": (120, 80, 30)}, 4, 4)
    im.putpixel((5, 3), (255, 240, 190, 255)); im.putpixel((4, 4), (255, 240, 190, 255))
    return im

def icon():
    im = Image.new("RGBA", (64, 64), (34, 26, 18, 255))
    for y in range(64):
        for x in range(64):
            if y > 44: im.putpixel((x, y), (70 + noise(6), 110 + noise(8), 50, 255))
            elif y < 44: im.putpixel((x, y), (40 + y, 70 + y, 120 + y, 255))
    for x in range(14, 50):
        for y in range(26, 46):
            im.putpixel((x, y), (150 + noise(5), 110, 70, 255))
    for i in range(20):
        for x in range(12 + i, 52 - i):
            im.putpixel((x, 26 - i // 1 if 26 - i >= 8 else 8), (150, 50, 40, 255))
    for x in range(28, 36):
        for y in range(34, 46):
            im.putpixel((x, y), (80, 52, 30, 255))
    for (x0, y0) in ((18, 30), (40, 30)):
        for x in range(x0, x0 + 6):
            for y in range(y0, y0 + 5):
                im.putpixel((x, y), (240, 210, 120, 255))
    return im.resize((128, 128), Image.NEAREST)

def main():
    for slim in (False, True):
        arms = "slim" if slim else "wide"
        save(skin_layer(slim), f"entity/settler/skin_{arms}.png")
        save(clothes_shirt(slim), f"entity/settler/shirt_{arms}.png")
        for prof in ("miner", "lumberjack", "farmer", "fisher", "builder", "guard", "warrior"):
            save(outfit(prof, slim), f"entity/settler/profession/{prof}_{arms}.png")
    w, i = eyes_layers()
    save(w, "entity/settler/eyes_white.png")
    save(i, "entity/settler/eyes_iris.png")
    save(brows_layer(), "entity/settler/brows.png")
    save(clothes_pants(), "entity/settler/pants.png")
    save(shoes(), "entity/settler/shoes.png")
    m, f = hairs()
    for k, v in m.items(): save(v, f"entity/settler/hair/m{k}.png")
    for k, v in f.items(): save(v, f"entity/settler/hair/f{k}.png")
    for k, v in beards().items(): save(v, f"entity/settler/beard/b{k}.png")
    top, side, front = desk_textures()
    save(top, "block/town_hall_desk_top.png"); save(side, "block/town_hall_desk_side.png"); save(front, "block/town_hall_desk_front.png")
    lt, ls = ledger_textures()
    save(lt, "block/warehouse_ledger_top.png"); save(ls, "block/warehouse_ledger_side.png")
    save(plank_tex(0x6B4A2A, rows=16, seed=9), "block/marker_post.png")
    for kind in ("mine", "forest", "farm", "fishing", "patrol", "territory", "construction"):
        save(sign_icon(kind), f"block/{kind}_sign.png")
    p = os.path.join(OUT, "..", "icon.png")
    icon().save(p)
    rnd.seed(26)   # wave A outfits use their own random stream so older textures stay identical
    for slim in (False, True):
        for prof in ("carpenter", "mason", "smith", "cook", "hauler"):
            save(outfit(prof, slim), f"entity/settler/profession/{prof}_{'slim' if slim else 'wide'}.png")
    for name, t in workshop_textures().items():
        save(t, f"block/{name}.png")
    save(hearth_mark(), "item/hearth_mark.png")
    print("textures written to", os.path.normpath(OUT))

if __name__ == "__main__":
    main()
