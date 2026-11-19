"""Wave H3 settler art: long, voluminous women's hair (all 10 styles, painted on the outer layers and the jacket back/front so it falls over the
shoulders and back), softer faces (lashes, lips, blush) and a subtle jaw for men, richer dress trims, and profession overlays reduced to accents
(hat, apron, belt, tool, tabard stripe) so the base outfit stays visible. Called from gen_textures.main() after wave H2: run(gt).
Layout: parts(slim) = base layer, parts(slim, True) = outer layer (hat / jacket / sleeves / pants)."""


def run(m):
    img, parts, fill_face, fill_part, gray, noise, SHADE, hair_val, save = m.img, m.parts, m.fill_face, m.fill_part, m.gray, m.noise, m.SHADE, m.hair_val, m.save
    m.rnd.seed(3030)
    P, O = parts(False), parts(False, True)
    H, OH, B, OB = P["head"], O["head"], P["body"], O["body"]

    def hv(x, y, hl=False):
        return hair_val(x, y, hl)

    def strand(x, y, f):   # a lock: slightly darker core, bright edge
        v = 188 + noise(8) + (24 if (x + y) % 4 == 0 else 0) + (10 if f == "front" else 0)
        return gray(v)

    def braid(x, y, c0):   # braid links: diamond pattern
        link = (y + (x - c0) % 2) % 3
        return gray(150 if link == 0 else (205 if link == 1 else 185) + noise(5))

    # ------------------------------------------------------------------ head scaffolding
    def head(front_rows=1, bangs=None, side_rows=6, back_rows=7, top=True):
        im = img()
        if top:
            fill_face(im, H["top"], lambda x, y, w, h: hv(x, y, True))
        fill_face(im, H["front"], lambda x, y, w, h: hv(x, y) if (y < front_rows or (bangs and bangs(x, y))) else None)
        for s in ("right", "left"):
            fill_face(im, H[s], lambda x, y, w, h: hv(x, y) if y < side_rows else None)
        fill_face(im, H["back"], lambda x, y, w, h: hv(x, y) if y < back_rows else None)
        return im

    def shell(im, top=True, sides=lambda x, y: y >= 1, back=lambda x, y: True, front=None):
        """The outer (hat) layer: volume on top, around the ears and at the back."""
        if top:
            fill_face(im, OH["top"], lambda x, y, w, h: hv(x, y, True))
        for s in ("right", "left"):
            fill_face(im, OH[s], lambda x, y, w, h: hv(x, y, True) if sides(x, y) else None)
        fill_face(im, OH["back"], lambda x, y, w, h: hv(x, y, True) if back(x, y) else None)
        if front:
            fill_face(im, OH["front"], lambda x, y, w, h: hv(x, y, True) if front(x, y) else None)

    def back_cape(im, rows, width_fn):
        """Hair down the back, painted on the jacket layer so a dress does not hide it."""
        fill_face(im, OB["back"], lambda x, y, w, h: strand(x, y, "back") if y < rows and width_fn(y) [0] <= x <= width_fn(y)[1] else None)

    def tress(im, xs, rows, taper=None, style="strand"):
        """A lock falling over the shoulder on the front of the jacket layer; xs = columns, taper(y)-> columns kept."""
        def fn(x, y, w, h):
            if y >= rows or x not in xs:
                return None
            if taper and not taper(x, y):
                return None
            return strand(x, y, "front") if style == "strand" else braid(x, y, xs[0])
        fill_face(im, OB["front"], fn)
        # a thin strip on the shoulder seen from the side keeps it from looking pasted on
        for s, edge, col in (("right", 3, 0), ("left", 0, 7)):
            if col in xs:
                fill_face(im, OB[s], lambda x, y, w, h, edge=edge: strand(x, y, "front") if x == edge and y < min(rows, 3) else None)

    sym = lambda a, b: (a, b)
    acc = {}

    # f0: long straight hair, centre parting, to the waist
    im = head(1, bangs=lambda x, y: (x in (0, 7) and y < 7) or (y == 1 and x in (0, 1, 2, 5, 6, 7)) or (y == 2 and x in (0, 1, 6, 7)), side_rows=8, back_rows=8)
    shell(im, sides=lambda x, y: y >= 1)
    back_cape(im, 12, lambda y: (0, 7) if y < 6 else ((1, 6) if y < 9 else (2, 5)))
    tress(im, (0, 1, 6, 7), 11, taper=lambda x, y: y < 7 or x in (0, 7) or y < 9)
    save(im, "entity/settler/hair/f0.png")

    # f1: ginger side braid over the shoulder, tied with a ribbon
    im = head(1, bangs=lambda x, y: (x in (0, 7) and y < 5) or (y == 2 and x <= 2), side_rows=5, back_rows=7)
    shell(im, top=True, sides=lambda x, y: y >= 1 and y < 6, back=lambda x, y: y < 7)
    back_cape(im, 7, lambda y: (1, 6) if y < 4 else (3, 4))
    tress(im, (1, 2), 12, style="braid")
    tress(im, (6, 7), 4, taper=lambda x, y: x == 7 or y < 3)
    a = img()
    fill_face(a, OB["front"], lambda x, y, w, h: (200, 60, 70, 255) if y == 11 and x in (1, 2) else None)
    acc[1] = a
    save(im, "entity/settler/hair/f1.png")

    # f2: wavy shoulder-length hair
    im = head(2, bangs=lambda x, y: y == 2 and x % 2 == 0 or (x in (0, 7) and y < 7), side_rows=8, back_rows=8)
    shell(im, sides=lambda x, y: 2 <= y <= 7, back=lambda x, y: y < 8)
    back_cape(im, 7, lambda y: (0, 7) if y < 5 else ((1, 6) if (y % 2) else (0, 7)))
    tress(im, (0, 1, 6, 7), 7, taper=lambda x, y: y < 5 or (x + y) % 2 == 0)
    save(im, "entity/settler/hair/f2.png")

    # f3: bun on the crown, loose locks beside the face
    im = head(2, bangs=lambda x, y: (y == 2 and x in (0, 1, 6, 7)) or (x in (0, 7) and y < 6), side_rows=5, back_rows=6)
    shell(im, sides=lambda x, y: 1 <= y <= 4, back=lambda x, y: y < 5)
    fill_face(im, OH["top"], lambda x, y, w, h: hv(x, y, True))
    fill_face(im, OH["back"], lambda x, y, w, h: hv(x, y, True) if 1 <= x <= 6 and y <= 4 else None)
    fill_face(im, OH["right"], lambda x, y, w, h: hv(x, y, True) if x <= 3 and y <= 2 else None)   # the bun's sides (back half of each side face)
    fill_face(im, OH["left"], lambda x, y, w, h: hv(x, y, True) if x >= 4 and y <= 2 else None)
    back_cape(im, 3, lambda y: (2, 5))
    tress(im, (0, 7), 7, taper=lambda x, y: y < 5 or y % 2 == 0)
    a = img()
    fill_face(a, OH["back"], lambda x, y, w, h: (210, 170, 70, 255) if y == 5 and 2 <= x <= 5 else None)   # a gold hair band under the bun
    acc[3] = a
    save(im, "entity/settler/hair/f3.png")

    # f4: ponytail swinging over the shoulder
    im = head(1, bangs=lambda x, y: (x in (0, 7) and y < 5) or (y == 2 and x <= 3), side_rows=4, back_rows=6)
    shell(im, sides=lambda x, y: 1 <= y <= 4, back=lambda x, y: y < 7)
    fill_face(im, OH["back"], lambda x, y, w, h: strand(x, y, "back") if 3 <= x <= 4 and y <= 7 else None)
    back_cape(im, 10, lambda y: (3, 4))
    tress(im, (6, 7), 9, taper=lambda x, y: y < 5 or x == 7 or y < 8)
    a = img()
    fill_face(a, OH["back"], lambda x, y, w, h: (190, 60, 60, 255) if y == 2 and 3 <= x <= 4 else None)
    acc[4] = a
    save(im, "entity/settler/hair/f4.png")

    # f5: long hair with a deep side parting, heavier on one side
    im = head(1, bangs=lambda x, y: (x in (0, 7) and y < 7) or (y == 1 and x <= 5) or (y == 2 and x <= 3) or (y == 3 and x <= 1), side_rows=8, back_rows=8)
    shell(im, sides=lambda x, y: y >= 1)
    back_cape(im, 12, lambda y: (0, 7) if y < 6 else ((1, 6) if y < 9 else (1, 5)))
    tress(im, (0, 1, 2), 11, taper=lambda x, y: y < 8 or x <= 1)
    tress(im, (6, 7), 9, taper=lambda x, y: y < 6 or x == 7)
    save(im, "entity/settler/hair/f5.png")

    # f6: two braids over the shoulders with ribbons
    im = head(1, bangs=lambda x, y: (x in (0, 7) and y < 4) or (y == 2 and x in (1, 2, 5, 6)), side_rows=5, back_rows=6)
    shell(im, sides=lambda x, y: 1 <= y <= 5, back=lambda x, y: y < 6)
    back_cape(im, 4, lambda y: (1, 6))
    tress(im, (0, 1), 12, style="braid")
    tress(im, (6, 7), 12, style="braid")
    a = img()
    fill_face(a, OB["front"], lambda x, y, w, h: (70, 140, 200, 255) if y == 11 and (x in (0, 1) or x in (6, 7)) else None)
    fill_face(a, OB["front"], lambda x, y, w, h: (70, 140, 200, 255) if y == 3 and (x in (0, 1) or x in (6, 7)) else None)
    acc[6] = a
    save(im, "entity/settler/hair/f6.png")

    # f7: low bun at the nape and a straight fringe
    im = head(3, bangs=lambda x, y: y <= 2 and (x in (0, 7) or True), side_rows=6, back_rows=6)
    shell(im, sides=lambda x, y: 2 <= y <= 6, back=lambda x, y: y < 7)
    fill_face(im, OH["back"], lambda x, y, w, h: hv(x, y, True) if 2 <= x <= 5 and 3 <= y <= 7 else None)
    fill_face(im, OB["back"], lambda x, y, w, h: strand(x, y, "back") if 2 <= x <= 5 and y < 3 else None)
    tress(im, (0, 7), 6, taper=lambda x, y: y < 4 or y % 2 == 0)
    save(im, "entity/settler/hair/f7.png")

    # f8: chin-length bob that curls onto the shoulders
    im = head(3, bangs=lambda x, y: y <= 2 or (x in (0, 7) and y < 7), side_rows=7, back_rows=7)
    shell(im, sides=lambda x, y: y < 8, back=lambda x, y: y < 8, front=lambda x, y: x in (0, 7) and 2 <= y <= 6)
    back_cape(im, 3, lambda y: (1, 6))
    tress(im, (0, 1, 6, 7), 3, taper=lambda x, y: y < 2 or x in (0, 7))
    save(im, "entity/settler/hair/f8.png")

    # f9: high ponytail down the back
    im = head(1, bangs=lambda x, y: (y == 1 and x % 3 != 1) or (x in (0, 7) and y < 4), side_rows=3, back_rows=5)
    shell(im, sides=lambda x, y: 1 <= y <= 3, back=lambda x, y: y < 5)
    fill_face(im, OH["back"], lambda x, y, w, h: strand(x, y, "back") if 3 <= x <= 4 and y <= 7 else None)
    back_cape(im, 11, lambda y: (3, 4) if y < 8 else (3, 4))
    tress(im, (0, 7), 5, taper=lambda x, y: y < 3 or y % 2 == 0)
    a = img()
    fill_face(a, OH["back"], lambda x, y, w, h: (230, 190, 70, 255) if y == 1 and 3 <= x <= 4 else None)
    acc[9] = a
    save(im, "entity/settler/hair/f9.png")
    for k in range(10):
        save(acc.get(k, img()), f"entity/settler/hair/f{k}_acc.png")

    # ------------------------------------------------------------------ faces
    f0x, f0y = H["front"][0], H["front"][1]
    f = img()
    px = lambda x, y, c: f.putpixel((f0x + x, f0y + y), c)
    for x in (1, 6):
        px(x, 3, (45, 32, 36, 255))          # upper lashes over the outer half of each eye
    for x in (0, 7):
        px(x, 4, (60, 42, 44, 255))          # lash flick at the outer corners
    for x in (1, 2, 5, 6):
        px(x, 5, (226, 110, 118, 70))        # blush
    for x in (3, 4):
        px(x, 6, (196, 66, 80, 255))         # lips
    px(3, 5, (232, 150, 150, 60)); px(4, 5, (232, 150, 150, 60))
    save(f, "entity/settler/face_f.png")
    f = img()
    px = lambda x, y, c: f.putpixel((f0x + x, f0y + y), c)
    for x in (1, 2, 5, 6):
        px(x, 2, (40, 30, 26, 130))          # heavier brows
    for x in (0, 1, 6, 7):
        px(x, 6, (60, 40, 30, 36)); px(x, 7, (60, 40, 30, 50))
    for x in range(2, 6):
        px(x, 7, (60, 40, 30, 46))           # square jaw
    save(f, "entity/settler/face_m.png")

    # ------------------------------------------------------------------ dress trims (untinted accents)
    for k in (1, 2, 3):
        path = f"entity/settler/outfit/o{k}_acc.png"
        try:
            from PIL import Image
            a = Image.open(__import__("os").path.join(m.OUT, path)).convert("RGBA")
        except Exception:
            a = img()
        if k == 1:   # sash at the waist, lace hem on the skirt layer, lace neckline
            fill_face(a, OB["front"], lambda x, y, w, h: (120, 70, 50, 255) if y == 6 else None)
            fill_face(a, OB["back"], lambda x, y, w, h: (120, 70, 50, 255) if y == 6 else None)
            fill_face(a, OB["front"], lambda x, y, w, h: (246, 240, 226, 255) if y == 0 and x in (2, 5) else None)
            for leg in ("rleg", "lleg"):
                fill_part(a, O[leg], lambda f_, x, y, w, h: (244, 236, 214, 255) if f_ not in ("top", "bottom") and y == 10 else None)
        if k == 2:   # blouse collar and a hem stripe
            fill_face(a, OB["front"], lambda x, y, w, h: (246, 240, 226, 255) if y == 0 and 2 <= x <= 5 else None)
            for leg in ("rleg", "lleg"):
                fill_part(a, O[leg], lambda f_, x, y, w, h: (236, 200, 120, 255) if f_ not in ("top", "bottom") and y == 5 else None)
        a.save(__import__("os").path.join(m.OUT, path))

    # ------------------------------------------------------------------ profession overlays as accents
    def lum(c):
        return c[0] * 0.3 + c[1] * 0.6 + c[2] * 0.1

    def is_red(c):
        return c[0] > 140 and c[1] < 90 and c[2] < 90

    def keep_fn(prof):
        FB, SIDES = ("front", "back"), ("front", "back", "right", "left")
        K = {
            "miner": lambda p, f_, x, y, c: p == "body" and f_ in FB and ((x in (1, 6) and y < 7) or y == 10),
            "lumberjack": lambda p, f_, x, y, c: (p == "body" and f_ in SIDES and y <= 2) or (p in ("rarm", "larm") and f_ == "top"),
            "farmer": lambda p, f_, x, y, c: p == "body" and f_ in FB and ((x in (1, 6) and y <= 5) or (f_ == "front" and 2 <= x <= 5 and 6 <= y <= 8)),
            "fisher": lambda p, f_, x, y, c: (p == "body" and f_ == "front" and x in (1, 6) and y <= 6) or (p in ("rleg", "lleg") and f_ not in ("top", "bottom") and y >= 9) or (p in ("rleg", "lleg") and f_ == "bottom"),
            "builder": lambda p, f_, x, y, c: p == "body" and f_ != "top" and f_ != "bottom" and (y == 5 or y in (10, 11) or (f_ in FB and x in (2, 5) and y <= 4)),
            "guard": lambda p, f_, x, y, c: (p == "body" and f_ in FB and 2 <= x <= 5 and 1 <= y <= 11) or (p in ("rarm", "larm") and f_ == "top"),
            "warrior": lambda p, f_, x, y, c: (p == "body" and f_ in FB and is_red(c)) or (p in ("rarm", "larm") and 6 <= y <= 8 and f_ not in ("top", "bottom")) or (p in ("rleg", "lleg") and f_ not in ("top", "bottom") and 5 <= y <= 9),
            "carpenter": lambda p, f_, x, y, c: (p == "body" and f_ == "front" and ((x in (1, 6) and y < 3) or (2 <= x <= 5 and y >= 3))) or (p == "body" and f_ == "back"),
            "mason": lambda p, f_, x, y, c: ((p == "body" or p in ("rarm", "larm")) and lum(c) > 215) or (p in ("rleg", "lleg") and f_ == "front" and y in (4, 5)),
            "smith": lambda p, f_, x, y, c: (p == "body" and f_ == "front" and 2 <= x <= 5 and y >= 2) or (p == "body" and f_ == "front" and y == 0) or (p == "body" and f_ == "back" and y == 8) or (p in ("rarm", "larm") and f_ not in ("top", "bottom") and y >= 7),
            "cook": lambda p, f_, x, y, c: (p == "body" and f_ == "front" and 2 <= x <= 5 and y >= 3) or (p == "body" and f_ == "front" and y < 2 and 2 <= x <= 5) or (p == "body" and f_ == "back" and y == 9),
            "hauler": lambda p, f_, x, y, c: p == "body",
            "archer": lambda p, f_, x, y, c: (p == "body" and f_ in SIDES and y == 9) or (p == "body" and f_ == "front" and (x + y) in (7, 8) and y < 8) or (p == "body" and f_ == "back" and ((5 <= x <= 7 and y <= 8) or (x in (5, 6) and y == 0))) or (p in ("rarm", "larm") and f_ not in ("top", "bottom") and 7 <= y <= 10),
            "healer": lambda p, f_, x, y, c: (p == "body" and f_ == "front" and 2 <= x <= 5 and y >= 5) or (p == "body" and f_ == "back" and 2 <= x <= 5 and 3 <= y <= 7),
            "innkeeper": lambda p, f_, x, y, c: (p == "body" and f_ == "front" and 2 <= x <= 5 and y >= 4) or (p == "body" and f_ == "back" and y in (5, 6)),
            "knight": lambda p, f_, x, y, c: (p == "body" and f_ == "front" and 2 <= x <= 5 and y >= 4) or (p == "body" and f_ == "back" and 2 <= x <= 5 and y >= 3) or (p == "body" and f_ != "bottom" and y <= 1) or (p in ("rarm", "larm") and (f_ == "top" or y == 4) and f_ != "bottom") or (p in ("rleg", "lleg") and f_ not in ("top", "bottom") and y == 4),
        }
        return K[prof]

    PROFS = ("miner", "lumberjack", "farmer", "fisher", "builder", "guard", "warrior", "carpenter", "mason", "smith", "cook", "hauler",
             "archer", "healer", "innkeeper", "knight")
    for prof in PROFS:
        for slim in (False, True):
            full = m.outfit(prof, slim)
            keep = keep_fn(prof)
            res = img()
            Pp, Op = parts(slim), parts(slim, True)
            for f_, (x0, y0, w, h) in Op["head"].items():   # hats stay as they are
                for y in range(h):
                    for x in range(w):
                        c = full.getpixel((x0 + x, y0 + y))
                        if c[3]:
                            res.putpixel((x0 + x, y0 + y), c)
            for pname in ("body", "rarm", "larm", "rleg", "lleg"):
                for f_, (x0, y0, w, h) in Op[pname].items():
                    for y in range(h):
                        for x in range(w):
                            c = full.getpixel((x0 + x, y0 + y))
                            if c[3] and keep(pname, f_, x, y, c):
                                res.putpixel((x0 + x, y0 + y), c)
            if prof == "miner":   # coal dust on the knees lives on the base layer
                for pname in ("rleg", "lleg"):
                    for f_, (x0, y0, w, h) in Pp[pname].items():
                        for y in range(h):
                            for x in range(w):
                                c = full.getpixel((x0 + x, y0 + y))
                                if c[3] == 140:
                                    res.putpixel((x0 + x, y0 + y), c)
            save(res, f"entity/settler/profession/{prof}_{'slim' if slim else 'wide'}.png")
    print("H3 skin layers written")
