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
    elif prof == "archer":
        green, dark = 0x4F8A45, 0x2E5530
        O_h = parts(False, True)["head"]
        fill_face(im, O_h["top"], lambda x, y, w, h: rgb(shade(green, "top")))
        for f in ("front", "right", "left", "back"):
            fill_face(im, O_h[f], lambda x, y, w, h, f=f: rgb(shade(green if y < 2 or f != "front" else dark, f)) if y < 3 and (y < 2 or f != "front" or x in (0, 1, 6, 7)) else None)
        fill_face(im, O_h["right"], lambda x, y, w, h: rgb(0xE8E4D0) if x in (1, 2) and y in (0, 1, 2) and (x + y) % 2 == 0 else None)   # feather
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(shade(green, f)) if f not in ("top", "bottom") and y <= 9 else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(0x5A3A22) if f in ("front", "back") and y == 9 else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0x6B4A2A) if (x + y) in (7, 8) and y < 8 else None)   # bandolier
        fill_face(im, O["body"]["back"], lambda x, y, w, h: rgb(0x7A5230) if 5 <= x <= 7 and y <= 8 else (rgb(0xD8D4C0) if x in (5, 6) and y == 0 else None))  # quiver
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: rgb(shade(0x7A5230, f)) if f not in ("top", "bottom") and 7 <= y <= 10 else None)
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: rgb(shade(dark, f)) if f not in ("top", "bottom") and y >= 6 else None)
    elif prof == "healer":
        robe, white = 0x6FBF8F, 0xF2F0E6
        O_h = parts(False, True)["head"]
        fill_face(im, O_h["top"], lambda x, y, w, h: rgb(shade(white, "top"), n=3))
        for f in ("front", "right", "left", "back"):
            fill_face(im, O_h[f], lambda x, y, w, h, f=f: rgb(shade(white, f), n=3) if y < 2 else None)
        fill_face(im, O_h["front"], lambda x, y, w, h: rgb(0xC0392B) if y == 0 and x in (3, 4) else None)   # red mark on the band
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(shade(robe, f)) if f not in ("top", "bottom") else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(white) if 1 <= x <= 6 and y >= 5 else None)   # apron
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0xC0392B) if (x in (3, 4) and 6 <= y <= 10) or (y in (8, 9) and 2 <= x <= 5) else None)
        fill_face(im, O["body"]["back"], lambda x, y, w, h: rgb(0x8C6A3E) if 2 <= x <= 5 and 3 <= y <= 7 else None)   # herb satchel
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: rgb(shade(robe, f)) if f != "bottom" and (f == "top" or y <= 6) else None)
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: rgb(shade(robe, f)) if f not in ("top", "bottom") and y < 7 else None)
    elif prof == "innkeeper":
        tunic, apron = 0x9A4F2A, 0xF0EBDD
        O_h = parts(False, True)["head"]
        fill_face(im, O_h["top"], lambda x, y, w, h: rgb(shade(0x5A3A22, "top"), n=4) if 1 <= x <= 6 and 1 <= y <= 6 else None)   # flat cap
        for f in ("front", "right", "left", "back"):
            fill_face(im, O_h[f], lambda x, y, w, h, f=f: rgb(shade(0x5A3A22, f), n=4) if y == 0 else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: rgb(shade(tunic, f)) if f not in ("top", "bottom") and y <= 8 else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(apron) if 1 <= x <= 6 and y >= 4 else None)   # long apron
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0xB8B2A0) if x in (1, 6) and y == 4 else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(0x8C6A3E) if x in (3, 4) and y in (6, 7) else None)   # pocket
        fill_face(im, O["body"]["back"], lambda x, y, w, h: rgb(apron) if y in (5, 6) else None)   # apron strings
        for arm in ("rarm", "larm"):   # rolled-up sleeves
            fill_part(im, O[arm], lambda f, x, y, w, h: rgb(shade(tunic, f)) if f != "bottom" and (f == "top" or y <= 3) else None)
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: rgb(shade(0x3E2E22, f)) if f not in ("top", "bottom") and y >= 8 else None)
    elif prof == "knight":
        steel, gold, red = 0xC4C9D0, 0xD8B04A, 0xB0302A
        helmet(im, steel, 4, band=gold, nose=True)
        O_h = parts(False, True)["head"]
        for f in ("front", "right", "left", "back"):   # plume
            fill_face(im, O_h[f], lambda x, y, w, h, f=f: rgb(shade(red, f)) if y == 0 and 2 <= x <= 5 and f in ("right", "left", "back") else None)
        fill_face(im, O_h["top"], lambda x, y, w, h: rgb(shade(red, "top")) if 3 <= x <= 4 else None)
        fill_face(im, O_h["front"], lambda x, y, w, h: (30, 30, 36, 255) if y == 3 and x in (1, 2, 5, 6) else None)   # visor slits
        plate = lambda f, x, y: rgb(shade(steel if (x + y) % 3 else 0xA6ABB4, f), n=3)
        fill_part(im, O["body"], lambda f, x, y, w, h: plate(f, x, y) if f != "bottom" else None)
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(red) if 2 <= x <= 5 and y >= 4 else None)   # tabard
        fill_face(im, O["body"]["front"], lambda x, y, w, h: rgb(gold) if (x in (3, 4) and 5 <= y <= 9) or (y == 7 and 2 <= x <= 5) else None)
        fill_face(im, O["body"]["back"], lambda x, y, w, h: rgb(red) if 2 <= x <= 5 and y >= 3 else None)
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: plate(f, x, y) if f == "top" or y <= 3 or 7 <= y <= 11 else (rgb(gold) if y == 4 else None))
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: plate(f, x, y) if f not in ("top",) and y >= 5 else (rgb(gold) if f != "bottom" and y == 4 else None))
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


# ---------------------------------------------------------------- wave C: Ashborn, rift, defence
def c3(c, k=1.0, n=0, a=255):
    r, g, b = c >> 16 & 255, c >> 8 & 255, c & 255
    j = rnd.randint(-n, n) if n else 0
    return (max(0, min(255, int(r * k) + j)), max(0, min(255, int(g * k) + j)), max(0, min(255, int(b * k) + j)), a)

def sk(f):
    return 1 + SHADE[f] / 255

def ashborn_skin(kind):
    im = img()
    P, O = parts(False), parts(False, True)
    tone = {"ash_brute": 0x6E6862, "ash_chieftain": 0x5E5853}.get(kind, 0x7A7570)
    # ash-grey skin with dark cracks and a few ember glints
    for name, part in P.items():
        def fn(f, x, y, w, h):
            v = rnd.random()
            if v < 0.05: return c3(0x2A2522, sk(f))
            if v < 0.07: return c3(0xE0682A, 1.0)
            return c3(tone, sk(f), 6)
        fill_part(im, part, fn)
    fx, fy = 8, 8
    for x in range(8):
        im.putpixel((fx + x, fy + 3), c3(0x3A3330) if x in (1, 2, 5, 6) else c3(tone, 0.9, 4))
    for x in (1, 2, 5, 6):
        im.putpixel((fx + x, fy + 4), (36, 22, 18, 255))
    for x in (2, 5):
        im.putpixel((fx + x, fy + 4), (255, 128, 40, 255))
    im.putpixel((fx + 3, fy + 6), (40, 28, 26, 255)); im.putpixel((fx + 4, fy + 6), (40, 28, 26, 255))
    im.putpixel((fx + 2, fy + 6), c3(tone, 0.7)); im.putpixel((fx + 5, fy + 6), c3(tone, 0.7))
    pal = {
        "ash_raider": dict(top=0x4A3B30, trim=0x8C2F1C, pants=0x302A26, hood=0x2B2622),
        "ash_arsonist": dict(top=0x3A3532, trim=0xC05A1E, pants=0x2A2421, hood=None),
        "ash_archer": dict(top=0x3E3A2A, trim=0x6B4A2A, pants=0x2E2A22, hood=0x34301F),
        "ash_brute": dict(top=None, trim=0x9E2B25, pants=0x2C2622, hood=None),
        "ash_chieftain": dict(top=0x2A2624, trim=0xC9A23A, pants=0x221E1C, hood=None),
    }[kind]
    # trousers and boots
    for leg in ("rleg", "lleg"):
        fill_part(im, P[leg], lambda f, x, y, w, h: None if f == "top" else c3(0x1E1A17, sk(f), 3) if y >= 9 or f == "bottom" else c3(pal["pants"], sk(f), 5))
        fill_part(im, P[leg], lambda f, x, y, w, h: c3(pal["trim"], sk(f)) if f in ("front", "right", "left", "back") and y == 9 else None)
    # torso
    if pal["top"] is not None:
        def torso(f, x, y, w, h):
            if f == "bottom": return None
            c = pal["top"]
            if f in ("front", "back") and y in (7, 8): c = pal["trim"]
            if f == "front" and x == (y % 8) and y < 7 and kind != "ash_chieftain": c = 0x5E4A3A
            if kind == "ash_arsonist" and rnd.random() < 0.08: return c3(0xD8742A)
            return c3(c, sk(f), 5)
        fill_part(im, P["body"], torso)
        for arm in ("rarm", "larm"):
            fill_part(im, P[arm], lambda f, x, y, w, h: None if f == "bottom" or (f != "top" and y > 7) else c3(pal["top"], sk(f) * (0.85 if y == 7 else 1), 5))
    else:  # brute: bare chest with red war paint, belt
        fill_face(im, P["body"]["front"], lambda x, y, w, h: c3(0x9E2B25) if (x in (1, 6) and y < 6) or (y == 3 and 2 <= x <= 5) else None)
        fill_part(im, P["body"], lambda f, x, y, w, h: c3(0x3B2A1E, sk(f)) if f not in ("top", "bottom") and y in (9, 10) else None)
        fill_face(im, P["body"]["front"], lambda x, y, w, h: c3(0xA8A8B0) if x in (3, 4) and y in (9, 10) else None)
    # hoods
    if pal["hood"] is not None:
        H = O["head"]
        fill_face(im, H["top"], lambda x, y, w, h: c3(pal["hood"], sk("top"), 4))
        for f in ("right", "left", "back"):
            fill_face(im, H[f], lambda x, y, w, h, f=f: c3(pal["hood"], sk(f), 4))
        fill_face(im, H["front"], lambda x, y, w, h: c3(pal["hood"], 1, 4) if y < 2 or x in (0, 7) else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: c3(pal["hood"], sk(f), 4) if f in ("back", "right", "left") and y < 10 else
                  (c3(pal["hood"], sk(f), 4) if f == "front" and (x in (0, 7) or y == 0) and y < 10 else None))
    if kind == "ash_arsonist":  # orange bandana over mouth, soot goggles strap
        fill_face(im, O["head"]["front"], lambda x, y, w, h: c3(0xB4461C, 1, 6) if y >= 5 else (c3(0x2A2420) if y == 3 and x in (0, 3, 4, 7) else None))
        for f in ("right", "left", "back"):
            fill_face(im, O["head"][f], lambda x, y, w, h, f=f: c3(0xB4461C, sk(f), 6) if y >= 5 else (c3(0x2A2420) if y == 3 else None))
    if kind == "ash_archer":  # quiver on the back
        fill_face(im, O["body"]["back"], lambda x, y, w, h: c3(0x6B4A2A) if 4 <= x <= 6 and 1 <= y <= 10 else (c3(0xE8E0D0) if 4 <= x <= 6 and y == 0 else None))
    if kind == "ash_brute":  # horned iron helm and fur mantle
        H = O["head"]
        fill_face(im, H["top"], lambda x, y, w, h: c3(0x55585E, 1.1, 5))
        for f in ("front", "right", "left", "back"):
            fill_face(im, H[f], lambda x, y, w, h, f=f: c3(0x55585E, sk(f), 5) if y < 3 or (f == "front" and x in (3, 4) and y < 6) else None)
        for f in ("right", "left"):
            fill_face(im, H[f], lambda x, y, w, h: (216, 207, 184, 255) if (x, y) in ((3, 1), (4, 1), (4, 0), (5, 0)) else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: c3(0x6B5440, sk(f), 10) if (f == "top" or y < 3) and f != "bottom" else None)
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: c3(0x6B5440, sk(f), 10) if f == "top" or (f != "bottom" and y < 3) else None)
    if kind == "ash_chieftain":  # spiked crown, red cloak with gold trim, gold torc
        H = O["head"]
        for f in ("front", "right", "left", "back"):
            fill_face(im, H[f], lambda x, y, w, h: (c3(0x1E1B1A) if y == 0 and x % 2 == 0 else c3(0xC9A23A) if y == 1 else None))
        fill_face(im, H["front"], lambda x, y, w, h: (255, 90, 30, 255) if (x, y) == (3, 1) or (x, y) == (4, 1) else None)
        fill_part(im, O["body"], lambda f, x, y, w, h: c3(0xA0281A, sk(f), 6) if f in ("back", "right", "left") else
                  (c3(0xC9A23A) if f == "front" and y == 0 else (c3(0xA0281A, 1, 6) if f == "front" and x in (0, 7) else None)))
        fill_face(im, O["body"]["back"], lambda x, y, w, h: c3(0xC9A23A) if y == 11 or x in (0, 7) else None)
        fill_face(im, P["body"]["front"], lambda x, y, w, h: c3(0x5A5F66, 1.1 - y * 0.02, 4) if 1 <= x <= 6 and 1 <= y <= 6 else None)
        for arm in ("rarm", "larm"):
            fill_part(im, O[arm], lambda f, x, y, w, h: c3(0x5A5F66, sk(f), 4) if f == "top" or (f != "bottom" and y < 3) else None)
    return im

def rift_wisp_tex():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(8):
        for x in range(16):
            im.putpixel((x, y), (255, 236, 255, 255) if (x + y) % 3 else (230, 170, 255, 255))
    for y in range(8, 20):
        for x in range(24):
            a = 120 + rnd.randint(-20, 30)
            im.putpixel((x, y), (150 + rnd.randint(-15, 15), 70 + rnd.randint(-15, 15), 220, a))
    for y in range(4):
        for x in range(16, 24):
            im.putpixel((x, y), (120, 255, 240, 255))
    return im

def rift_core_tex():
    frames = 6
    im = Image.new("RGBA", (16, 16 * frames))
    for fr in range(frames):
        for y in range(16):
            for x in range(16):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                import math
                swirl = math.sin(d * 1.3 - fr * 1.05 + math.atan2(y - 7.5, x - 7.5) * 2)
                k = max(0.0, 1 - d / 10)
                r = int(60 + 120 * k + 50 * swirl * k)
                g = int(20 + 60 * k * k)
                b = int(110 + 120 * k + 30 * swirl)
                if x in (0, 15) or y in (0, 15): r, g, b = 40, 20, 60
                im.putpixel((x, 16 * fr + y), (max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)), 255))
    return im

def palisade_side():
    im = Image.new("RGBA", (16, 16))
    for x in range(16):
        log = x // 4
        for y in range(16):
            k = 1 + rnd.randint(-6, 6) / 100
            if x % 4 == 0: k *= 0.6
            elif x % 4 == 3: k *= 0.82
            if (y + log * 5) % 7 == 0 and x % 4 in (1, 2): k *= 0.85
            c = (int(112 * k), int(80 * k), int(50 * k), 255)
            if y in (4, 11) and x % 4 != 0: c = (int(70 * k), int(66 * k), int(60 * k), 255)  # rope lashing
            im.putpixel((x, y), c)
    return im

def palisade_top():
    im = Image.new("RGBA", (16, 16), (60, 42, 26, 255))
    for cx in (3.5, 11.5):
        for cy in (3.5, 11.5):
            for y in range(16):
                for x in range(16):
                    d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                    if d < 3.6:
                        ring = int(d * 1.5) % 2
                        im.putpixel((x, y), (176, 136, 86, 255) if ring else (150, 112, 68, 255))
    return im

def gate_tex(top):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            k = 1 + rnd.randint(-6, 6) / 100
            if x % 4 == 0: k *= 0.62
            c = (int(118 * k), int(84 * k), int(52 * k), 255)
            if y in ((3, 12) if not top else (4,)) : c = (64, 64, 70, 255)                 # iron bands
            if top and y < 3 and x % 4 in (0, 3): c = (0, 0, 0, 0)                          # pointed tips
            if top and y == 3 and x % 4 == 0: c = (0, 0, 0, 0)
            im.putpixel((x, y), c)
    if not top:
        for p in ((12, 7), (13, 7), (12, 8)):
            im.putpixel(p, (150, 150, 160, 255))
    return im

def bell_textures():
    wood = plank_tex(0x5E3E24, rows=4, seed=71)
    bell = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1.15 - x * 0.025 + rnd.randint(-4, 4) / 100
            bell.putpixel((x, y), (int(200 * k), int(150 * k), int(50 * k), 255))
    for x in range(16): bell.putpixel((x, 13), (140, 96, 30, 255))
    return wood, bell

def lantern_tex():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if x in (0, 7) or y in (0, 8, 15) or x in (8, 15):
                im.putpixel((x, y), (40, 36, 44, 255))
            elif y < 8 and x < 8:
                im.putpixel((x, y), (220 - y * 6, 150, 255, 255))
            elif y >= 9 and x < 8:
                im.putpixel((x, y), (70, 60, 78, 255))
            else:
                im.putpixel((x, y), (60, 52, 66, 255) if (x + y) % 2 else (90, 80, 98, 255))
    return im

def banner_tex():
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = (150 + rnd.randint(-8, 8), 34, 24, 255)
            if x in (0, 15) or y == 15: c = (200, 160, 58, 255)
            im.putpixel((x, y), c)
    draw(im, ["....X....", "...XXX...", "..XX.XX..", ".XX...XX.", "..XX.XX..", "...XXX...", "....X...."], {"X": (30, 26, 26)}, 3, 3)
    draw(im, ["...Y...", "..YOY..", "...Y..."], {"Y": (255, 170, 60), "O": (255, 240, 180)}, 4, 5)
    return im

def shard_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(im, ["......LL....", ".....LPPL...", "....LPWPPL..", "...LPPWPPL..", "...LPPPPDL..", "..LPPPPDL...", "..LPPPDDL...",
              ".LPPPDDL....", ".LPPDDL.....", "..LDDL......", "...LL......."],
         {"L": (60, 20, 90), "P": (176, 92, 240), "W": (250, 220, 255), "D": (110, 50, 170)}, 2, 2)
    return im

def whistle_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(im, ["..........RR..", ".........R..R.", "........R...R.", "..BBBBBBBR.R..", ".BGGGGGGB.R...", "BGGHGGGGGB....",
              "BGGGGGGGGB....", ".BGGGGGGB.....", "..BBBBBB......"],
         {"B": (90, 60, 20), "G": (210, 160, 60), "H": (255, 235, 160), "R": (150, 40, 30)}, 1, 3)
    return im

def gate_item():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(1, 16):
        for x in range(3, 13):
            if y < 3 and (x - 3) % 3 != 1: continue
            c = (118, 84, 52, 255) if (x - 3) % 3 else (80, 56, 34, 255)
            if y in (5, 12): c = (64, 64, 70, 255)
            im.putpixel((x, y), c)
    return im

def egg_icon(base, spot):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    r = random.Random(base)
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.2, (y - 8.5) / 6.6
            if dy < 0: dx *= 1.0 + (-dy) * 0.25
            if dx * dx + dy * dy <= 1:
                c = base
                if r.random() < 0.22: c = spot
                k = 1.12 - (x + y) * 0.012
                im.putpixel((x, y), c3(c, k))
            elif dx * dx + dy * dy <= 1.22:
                im.putpixel((x, y), c3(base, 0.45))
    return im

def wave_c():
    rnd.seed(77)   # own random stream: earlier textures stay identical
    for kind in ("ash_raider", "ash_arsonist", "ash_archer", "ash_brute", "ash_chieftain"):
        save(ashborn_skin(kind), f"entity/ashborn/{kind}.png")
    save(rift_wisp_tex(), "entity/rift_wisp.png")
    save(rift_core_tex(), "block/rift_core.png")
    save(palisade_side(), "block/palisade_side.png")
    save(palisade_top(), "block/palisade_top.png")
    save(gate_tex(False), "block/palisade_gate_bottom.png")
    save(gate_tex(True), "block/palisade_gate_top.png")
    wood, bell = bell_textures()
    save(wood, "block/alarm_bell_wood.png"); save(bell, "block/alarm_bell_bell.png")
    save(lantern_tex(), "block/rift_lantern.png")
    save(banner_tex(), "block/chieftain_banner.png")
    save(shard_icon(), "item/rift_shard.png")
    save(whistle_icon(), "item/founders_whistle.png")
    save(gate_item(), "item/palisade_gate.png")
    eggs = {"ash_raider": (0x4A3B30, 0x8C2F1C), "ash_arsonist": (0x3A3532, 0xE0682A), "ash_archer": (0x3E3A2A, 0x8A6A3A),
            "ash_brute": (0x55585E, 0x9E2B25), "ash_chieftain": (0xA0281A, 0xC9A23A), "rift_wisp": (0x4A1E6E, 0xC890FF)}
    for k, (b, sp) in eggs.items():
        save(egg_icon(b, sp), f"item/{k}_spawn_egg.png")

# ---------------------------------------------------------------- wave D: party and military
def herbalist_textures():
    out = {}
    top = plank_tex(0x8A6A44, seed=81)
    draw(top, [".gg..", "gGGg.", "gGGg.", ".gg.."], {"g": (60, 120, 50), "G": (110, 180, 80)}, 1, 2)
    draw(top, ["..p..", ".pYp.", "..p.."], {"p": (210, 120, 170), "Y": (250, 220, 90)}, 10, 3)
    draw(top, ["bbb", "bBb", "bbb"], {"b": (150, 200, 220), "B": (90, 170, 120)}, 9, 9)
    draw(top, ["..LL", ".LLL", "LLL."], {"L": (80, 150, 60)}, 2, 10)
    out["herbalist_table_top"] = top
    out["herbalist_table_side"] = plank_tex(0x6E5233, seed=82)
    front = plank_tex(0x6E5233, seed=83)
    for x in range(2, 14):
        front.putpixel((x, 2), (50, 36, 22, 255))
    draw(front, ["h.h.h.h", "hhhhhhh"], {"h": (70, 140, 60)}, 4, 4)
    draw(front, ["bb.bb", "bb.bb"], {"b": (140, 190, 210)}, 5, 8)
    out["herbalist_table_front"] = front
    return out

def weapon_rack_front():
    im = plank_tex(0x6A4A2C, seed=91)
    for x in range(16):
        im.putpixel((x, 0), (40, 28, 18, 255)); im.putpixel((x, 15), (40, 28, 18, 255))
    steel, edge, hilt, shaft = (190, 196, 204), (120, 126, 136), (130, 90, 40), (150, 110, 60)
    # sword
    for y in range(2, 10):
        im.putpixel((3, y), steel + (255,)); im.putpixel((2, y), edge + (255,))
    for x in range(1, 5): im.putpixel((x, 10), hilt + (255,))
    for y in (11, 12): im.putpixel((3, y), hilt + (255,))
    # axe
    for y in range(2, 13): im.putpixel((8, y), shaft + (255,))
    draw(im, ["ss", "sss", "ss"], {"s": steel}, 9, 2)
    draw(im, ["ee", "ee"], {"e": edge}, 9, 3)
    # spear
    for y in range(4, 14): im.putpixel((13, y), shaft + (255,))
    draw(im, [".s.", "sss", ".s."], {"s": steel}, 12, 1)
    # shelf bars
    for x in range(16):
        im.putpixel((x, 13), (52, 36, 22, 255)); im.putpixel((x, 5), (52, 36, 22, 255))
    return im

def war_horn_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(im, ["..........HHH.", ".........HbbbH", "........HbBBBbH", ".......HbBhhhBbH", "......HbBh...hBH", ".....HbBh.....HH",
              "....HbBh.........", "...HbBh..........", "..HbBh...........", ".HbBh............", "HbBh.............", "HHH.............."],
         {"H": (110, 70, 25), "b": (205, 160, 70), "B": (236, 200, 110), "h": (150, 105, 40)}, 0, 2)
    for x, y in ((10, 3), (11, 4)): im.putpixel((x, y), (255, 240, 180, 255))
    draw(im, ["r", "r", "r"], {"r": (170, 40, 30)}, 3, 13)
    return im

def salve_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(im, ["..sss..", ".sSSSs.", "sSSgSSs", "sSgGgSs", "sSSgSSs", ".sSSSs.", "..sss.."], {"s": (60, 110, 70), "S": (110, 175, 110), "g": (190, 230, 150), "G": (240, 250, 200)}, 4, 6)
    draw(im, ["ccccc", "cCCCc"], {"c": (150, 100, 55), "C": (190, 140, 80)}, 5, 4)
    draw(im, ["..", ".."], {"x": (0, 0, 0)}, 0, 0)
    im.putpixel((7, 3), (130, 80, 40, 255)); im.putpixel((8, 3), (130, 80, 40, 255))
    return im

def wave_d():
    rnd.seed(41)
    for slim in (False, True):
        for prof in ("archer", "healer", "knight"):
            save(outfit(prof, slim), f"entity/settler/profession/{prof}_{'slim' if slim else 'wide'}.png")
    for name, t in herbalist_textures().items():
        save(t, f"block/{name}.png")
    save(weapon_rack_front(), "block/weapon_rack_front.png")
    save(plank_tex(0x6A4A2C, seed=92), "block/weapon_rack_side.png")
    save(plank_tex(0x4A3220, rows=2, seed=93), "block/weapon_rack_bar.png")
    save(war_horn_icon(), "item/war_horn.png")
    save(salve_icon(), "item/healing_salve.png")

# ---------------------------------------------------------------- wave E: square and market
DYE_RGB = {"white": 0xF9FFFE, "orange": 0xF9801D, "magenta": 0xC74EBD, "light_blue": 0x3AB3DA, "yellow": 0xFED83D, "lime": 0x80C71F,
           "pink": 0xF38BAA, "gray": 0x474F52, "light_gray": 0x9D9D97, "cyan": 0x169C9C, "purple": 0x8932B8, "blue": 0x3C44AA,
           "brown": 0x835432, "green": 0x5E7C16, "red": 0xB02E26, "black": 0x1D1D21}

def keg_textures():
    out = {}
    side = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1 + rnd.randint(-5, 5) / 100
            if x % 4 == 0: k *= 0.78
            c = 0x8A5A30
            if y in (2, 3, 12, 13): c, k = 0x4A4D55, 1 + rnd.randint(-4, 4) / 100
            if y in (2, 12): k *= 1.25
            side.putpixel((x, y), (int((c >> 16 & 255) * k), int((c >> 8 & 255) * k), int((c & 255) * k), 255))
    out["tavern_keg_side"] = side
    front = side.copy()
    draw(front, ["..bbbb..", ".bGGGGb.", "..bGGb..", "...GG...", "...GG...", "..GGGG.."], {"b": (70, 48, 28), "G": (201, 162, 58)}, 4, 5)
    front.putpixel((7, 11), (201, 162, 58, 255)); front.putpixel((8, 11), (160, 120, 40, 255))
    out["tavern_keg_front"] = front
    top = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            k = 1 + rnd.randint(-4, 4) / 100
            c = 0x4A4D55 if d > 7.2 else (0xB08652 if int(d) % 3 else 0x8E6A3E)
            top.putpixel((x, y), (int((c >> 16 & 255) * k), int((c >> 8 & 255) * k), int((c & 255) * k), 255))
    draw(top, ["bbbb"], {"b": (50, 34, 20)}, 6, 7)
    out["tavern_keg_top"] = top
    return out

def decor_textures():
    out = {}
    metal = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1 + rnd.randint(-6, 6) / 100 + (0.12 if x in (6, 7) else 0)
            metal.putpixel((x, y), (int(0x30 * k), int(0x34 * k), int(0x3A * k), 255))
    out["lamp_post_metal"] = metal
    glass = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15) or x == 7 or x == 8
            c = (50, 52, 58) if edge else (255, int(205 + 30 * (1 - abs(y - 7.5) / 8)), 110)
            glass.putpixel((x, y), c + (255,))
    out["lamp_post_glass"] = glass
    out["signpost_wood"] = plank_tex(0x8A6038, rows=16, seed=71)
    board = plank_tex(0xB08A58, rows=5, seed=72)
    draw(board, ["..........wwwwww..", "....wwwwwwwwwwww.."], {"w": (150, 112, 70)}, 0, 0)
    for x in range(16):
        board.putpixel((x, 0), (90, 62, 36, 255)); board.putpixel((x, 15), (90, 62, 36, 255))
    draw(board, ["....DDDDDDDD....", "....D......D....", "....D.DDDD.D....", "....D......D....", "....DDDDDDDD...."], {"D": (80, 54, 30)}, 0, 5)
    out["signpost_board"] = board
    out["bench_wood"] = plank_tex(0x9A6E40, rows=4, seed=73)
    out["well_stone"] = stone_tex(0x8E8E88, 74, True)
    out["well_water"] = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1 + rnd.randint(-6, 6) / 100 + (0.1 if (x + y * 2) % 7 == 0 else 0)
            out["well_water"].putpixel((x, y), (int(0x3A * k), int(0x78 * k), min(255, int(0xC8 * k)), 255))
    out["well_roof"] = plank_tex(0x6E3E2A, rows=4, seed=75)
    out["counter_side"] = plank_tex(0x7C5233, rows=6, seed=76)
    ctop = plank_tex(0xB08A58, rows=4, seed=77)
    for x in range(16):
        ctop.putpixel((x, 0), (120, 86, 50, 255)); ctop.putpixel((x, 15), (120, 86, 50, 255))
    out["counter_top"] = ctop
    cfront = plank_tex(0x7C5233, rows=6, seed=78)
    draw(cfront, ["DDDDDDDDDDDD", "D..........D", "D..........D", "D..........D", "DDDDDDDDDDDD"], {"D": (60, 40, 24)}, 2, 3)
    draw(cfront, ["gg"], {"g": (201, 162, 58)}, 7, 6)
    out["counter_front"] = cfront
    out["flower_box_wood"] = plank_tex(0x7A5230, rows=3, seed=79)
    soil = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1 + rnd.randint(-8, 8) / 100
            soil.putpixel((x, y), (int(0x4C * k), int(0x8A * k), int(0x3C * k), 255))
    r = random.Random(80)
    for _ in range(14):
        x, y = r.randint(1, 14), r.randint(1, 14)
        c = r.choice([(214, 60, 60), (240, 210, 70), (240, 240, 245), (150, 100, 220), (240, 140, 60)])
        soil.putpixel((x, y), c + (255,)); soil.putpixel((x + 1 if x < 15 else x, y), (70, 150, 60, 255))
    out["flower_box_top"] = soil
    # workshop signs: a board with a small pictogram
    pal = {"s": (190, 195, 205), "b": (110, 74, 40), "k": (60, 60, 66), "w": (240, 240, 240), "r": (176, 80, 60), "y": (225, 190, 70),
           "g": (110, 110, 118), "o": (225, 130, 40), "f": (250, 245, 225)}
    icons = {
        "carpenter": ["................", "....ssssssss....", "...sksksksksk...", "...ssssssssss...", "..bb............", "..bb............", "................", "......rrrr......", "......rbbr......", "......rrrr......"],
        "mason": ["................", "..rrrr.rrrr.rr..", "..rrrr.rrrr.rr..", "................", "rr.rrrr.rrrr.rrr", "rr.rrrr.rrrr.rrr", "................", "..rrrr.rrrr.rr..", "..rrrr.rrrr.rr..", "................"],
        "smith": ["................", "...gggggggggg...", "..gggggggggggg..", "...ggggggggggg..", ".....gggggg.....", ".....gggggg.....", "....gggggggg....", "...gggggggggg...", "................", "................"],
        "cook": ["..w...w....w....", "...w...w..w.....", "................", "..kkkkkkkkkkk...", ".kkggggggggkkk..", ".kkkkkkkkkkkk...", "..kkkkkkkkkk....", "...kk....kk.....", "................", "................"],
        "tavern": ["................", "....fffffff.....", "...fffffffff....", "....yyyyyyyy....", "....yyyyyyyy.oo.", "....yyyyyyyy.o.o", "....yyyyyyyy.o.o", "....yyyyyyyy.oo.", ".....yyyyyyy....", "................"],
    }
    for kind, rows in icons.items():
        im = plank_tex(0xB89263, rows=16, seed=90 + len(kind))
        for x in range(16):
            im.putpixel((x, 0), (90, 62, 36, 255)); im.putpixel((x, 15), (90, 62, 36, 255))
        for y in range(16):
            im.putpixel((0, y), (90, 62, 36, 255)); im.putpixel((15, y), (90, 62, 36, 255))
        draw(im, rows, pal, 0, 3)
        out[f"sign_{kind}"] = im
    out["sign_back"] = plank_tex(0x7A5230, rows=16, seed=95)
    # bulletin board: pinned notes
    bb = plank_tex(0xA37A4A, rows=4, seed=96)
    notes = [(2, 2, 5, 6, (238, 228, 196)), (8, 3, 6, 5, (226, 214, 176)), (3, 9, 6, 5, (232, 222, 188)), (10, 9, 4, 5, (244, 236, 208))]
    for nx, ny, nw, nh, c in notes:
        for yy in range(ny, ny + nh):
            for xx in range(nx, nx + nw):
                bb.putpixel((xx, yy), c + (255,))
        for xx in range(nx + 1, nx + nw - 1, 2):
            bb.putpixel((xx, ny + 2), (110, 90, 70, 255)); 
        bb.putpixel((nx + nw // 2, ny), (190, 50, 40, 255))
    for x in range(16):
        bb.putpixel((x, 0), (70, 46, 26, 255)); bb.putpixel((x, 15), (70, 46, 26, 255))
    out["bulletin_board_front"] = bb
    # market stall awning and crate
    aw = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = (190, 52, 44) if (x // 4) % 2 == 0 else (240, 234, 220)
            k = 1 + rnd.randint(-4, 4) / 100
            aw.putpixel((x, y), (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255))
    out["market_stall_awning"] = aw
    crate = plank_tex(0x9A6E40, rows=4, seed=97)
    for i in range(16):
        crate.putpixel((i, i), (70, 48, 28, 255)); crate.putpixel((15 - i, i), (70, 48, 28, 255))
        crate.putpixel((i, 0), (70, 48, 28, 255)); crate.putpixel((i, 15), (70, 48, 28, 255))
        crate.putpixel((0, i), (70, 48, 28, 255)); crate.putpixel((15, i), (70, 48, 28, 255))
    out["market_stall_crate"] = crate
    out["market_stall_wood"] = plank_tex(0x8A5E36, rows=4, seed=98)
    return out

def banner_cloth(dye, emblem):
    base = DYE_RGB[dye]
    lum = (0.3 * (base >> 16 & 255) + 0.59 * (base >> 8 & 255) + 0.11 * (base & 255))
    ink = 0x1D1D21 if lum > 150 else 0xF4E6A8
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1 + rnd.randint(-4, 4) / 100 + (0.06 if y % 4 == 0 else 0)
            im.putpixel((x, y), (min(255, int((base >> 16 & 255) * k)), min(255, int((base >> 8 & 255) * k)), min(255, int((base & 255) * k)), 255))
    for x in range(16):   # hem
        im.putpixel((x, 15), (int((base >> 16 & 255) * .6), int((base >> 8 & 255) * .6), int((base & 255) * .6), 255))
    shapes = {
        0: ["......I.........", ".....III........", "....IIIII.......", "...IIIIIII......", "...IIIIIII......", "....IIIII.......", ".....III........"],
        1: ["................", "................", "IIIIIIIIIIIIIIII", "................", "................", "IIIIIIIIIIIIIIII", "................"],
        2: ["......I.........", "......I.........", ".....III........", "IIIIIIIIIII.....", "...IIIII........", "..III.III.......", ".II.....II......"],
        3: ["...IIIIIIII.....", "..I........I....", "..I..IIII..I....", "..I..I..I..I....", "..I..IIII..I....", "..I........I....", "...IIIIIIII....."],
    }
    # centre the 7-row motif horizontally: shift by 2
    for j, row in enumerate(shapes[emblem]):
        for i, ch in enumerate(row):
            if ch == "I":
                x = i + (0 if emblem == 1 else 0)
                im.putpixel((x if emblem == 1 else min(15, x + 0), 4 + j), (ink >> 16 & 255, ink >> 8 & 255, ink & 255, 255))
    return im

def trader_skin():
    im = img()
    P, O = parts(False), parts(False, True)
    skin, cloak, trim = 0xD9A77E, 0x2F6F78, 0xC9A23A
    for part in P.values():
        fill_part(im, part, lambda f, x, y, w, h: c3(skin, sk(f), 5))
    fx, fy = 8, 8
    for x in (1, 2, 5, 6):
        im.putpixel((fx + x, fy + 4), (250, 250, 250, 255))
    for x in (2, 5):
        im.putpixel((fx + x, fy + 4), (60, 40, 30, 255))
    for x in (3, 4):
        im.putpixel((fx + x, fy + 6), (150, 90, 70, 255))
    im.putpixel((fx + 3, fy + 5), (190, 130, 100, 255)); im.putpixel((fx + 4, fy + 5), (190, 130, 100, 255))
    # trousers, boots, tunic
    for leg in ("rleg", "lleg"):
        fill_part(im, P[leg], lambda f, x, y, w, h: None if f == "top" else c3(0x3A2A1E, sk(f), 3) if y >= 9 or f == "bottom" else c3(0x6B5236, sk(f), 5))
    fill_part(im, P["body"], lambda f, x, y, w, h: None if f == "bottom" else c3(0xB8553A, sk(f), 5))
    fill_part(im, P["body"], lambda f, x, y, w, h: c3(0x4A3220, sk(f)) if f in ("front", "back", "right", "left") and y == 9 else None)
    for arm in ("rarm", "larm"):
        fill_part(im, P[arm], lambda f, x, y, w, h: None if f == "bottom" or (f != "top" and y > 7) else c3(0xB8553A, sk(f), 5))
    # hooded cloak (outer layers)
    H = O["head"]
    fill_face(im, H["top"], lambda x, y, w, h: c3(cloak, sk("top"), 4))
    for f in ("right", "left", "back"):
        fill_face(im, H[f], lambda x, y, w, h, f=f: c3(cloak, sk(f), 4))
    fill_face(im, H["front"], lambda x, y, w, h: c3(cloak, 1, 4) if y < 2 or x in (0, 7) else (c3(trim) if y == 2 and 1 <= x <= 6 and False else None))
    fill_part(im, O["body"], lambda f, x, y, w, h: c3(cloak, sk(f), 4) if f in ("back", "right", "left") and y < 11 else
              (c3(cloak, sk(f), 4) if f == "front" and (x in (0, 1, 6, 7) or y == 0) and y < 11 else None))
    fill_face(im, O["body"]["front"], lambda x, y, w, h: c3(trim) if y == 0 and x in (3, 4) else None)
    fill_face(im, O["body"]["back"], lambda x, y, w, h: c3(0x8C6A3E, 1, 4) if 2 <= x <= 5 and 3 <= y <= 8 else (c3(0x5E4426) if 2 <= x <= 5 and y == 3 else None))   # pack
    for arm in ("rarm", "larm"):
        fill_part(im, O[arm], lambda f, x, y, w, h: c3(cloak, sk(f), 4) if f == "top" or (f != "bottom" and y < 6) else None)
    return im

def chronicle_icon():
    """A thick leather book with a brass clasp and a gold hearth flame."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(im, ["..LLLLLLLLLLL..", ".LbbbbbbbbbbbL.", ".LbBBBBBBBBBbL.", ".LbBBBBgBBBBbL.", ".LbBBBgGgBBBbL.", ".LbBBgGyGgBBbL.",
              ".LbBBgGyGgBBbL.", ".LbBBBgGgBBBbL.", ".LbBBBBgBBBBbL.", ".LbBBBBBBBBBbL.", ".LbbbbbbbbbbbL.", ".LpppppppppppL.",
              "..LLLLLLLLLLL.."],
         {"L": (60, 36, 20), "b": (120, 66, 36), "B": (146, 84, 46), "g": (206, 162, 70), "G": (240, 200, 100), "y": (255, 236, 150), "p": (226, 214, 180)}, 0, 1)
    for y in range(3, 12): im.putpixel((14, y), (206, 162, 70, 255))
    return im

def wave_e():
    rnd.seed(55)
    for name, t in keg_textures().items():
        save(t, f"block/{name}.png")
    for name, t in decor_textures().items():
        save(t, f"block/{name}.png")
    for dye in DYE_RGB:
        for e in range(4):
            save(banner_cloth(dye, e), f"block/banner_{dye}_{e}.png")
    save(trader_skin(), "entity/trader.png")
    for slim in (False, True):
        save(outfit("innkeeper", slim), f"entity/settler/profession/innkeeper_{'slim' if slim else 'wide'}.png")

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
    wave_c()
    wave_d()
    wave_e()
    save(chronicle_icon(), "item/founders_chronicle.png")   # wave F
    import sys
    sys.path.insert(0, os.path.dirname(__file__))
    import gen_h1_tex
    gen_h1_tex.run(sys.modules[__name__])   # wave H1: polish + new art
    import gen_h2_skins
    gen_h2_skins.run(sys.modules[__name__])   # wave H2: new hair, outfits, headwear
    import gen_h3_skins
    gen_h3_skins.run(sys.modules[__name__])   # wave H3: long women's hair, faces, accent-only profession overlays
    print("textures written to", os.path.normpath(OUT))

if __name__ == "__main__":
    main()
