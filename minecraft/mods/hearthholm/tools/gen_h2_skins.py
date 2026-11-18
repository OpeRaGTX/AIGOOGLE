"""Wave H2 settler layers: new hair styles (f5-f9, m5-m7), outfits (o1-o6 + accents), headwear (h1-h4), wrinkles for elders.
Called from gen_textures.main() last: run(gt). All art is original and drawn here; grayscale layers get tinted at runtime."""


def run(m):
    img, parts, fill_face, fill_part, gray, noise, SHADE, hair_val, head_hair = m.img, m.parts, m.fill_face, m.fill_part, m.gray, m.noise, m.SHADE, m.hair_val, m.head_hair
    save = m.save
    m.rnd.seed(2026)
    P, O = parts(False), parts(False, True)
    OH = O["head"]

    def g(f, base, n=4):
        return gray(base + SHADE[f] + noise(n))

    # ------------------------------------------------------------ hair
    def f5():   # long straight hair with a side parting, down to the waist
        im = head_hair(1, 8, 8, bangs=lambda x, y: (x in (0, 7) and y < 7) or (y == 2 and x <= 3) or (y == 1 and x <= 5), body_back_rows=10,
                       outer={"back": lambda x, y: True, "right": lambda x, y: y >= 2, "left": lambda x, y: y >= 2})
        fill_face(im, P["body"]["front"], lambda x, y, w, h: hair_val(x, y) if (x == 0 or x == 7) and y < 5 else None)
        return im

    def f6():   # two braids over the shoulders
        im = head_hair(2, 4, 6, bangs=lambda x, y: y == 2 and x % 2 == 1, outer={"back": lambda x, y: y >= 2 and y < 6})
        B = P["body"]
        for xs in ((0, 1), (6, 7)):
            fill_face(im, B["front"], lambda x, y, w, h, xs=xs: hair_val(x, y, y % 3 == 0) if x in xs and y < 10 else None)
        fill_face(im, B["back"], lambda x, y, w, h: hair_val(x, y, y % 3 == 0) if x in (1, 6) and y < 9 else None)
        return im

    def f7():   # low bun at the nape and a straight fringe
        im = head_hair(2, 4, 6, bangs=lambda x, y: y == 2 and 1 <= x <= 6)
        fill_face(im, OH["back"], lambda x, y, w, h: hair_val(x, y, True) if 2 <= x <= 5 and 3 <= y <= 7 else None)
        fill_face(im, OH["right"], lambda x, y, w, h: hair_val(x, y) if 6 <= x <= 7 and 4 <= y <= 6 else None)
        fill_face(im, OH["left"], lambda x, y, w, h: hair_val(x, y) if 0 <= x <= 1 and 4 <= y <= 6 else None)
        return im

    def f8():   # chin-length bob with a full fringe
        return head_hair(3, 6, 6, bangs=lambda x, y: y <= 2, outer={"back": lambda x, y: y < 6, "right": lambda x, y: 2 <= y < 6, "left": lambda x, y: 2 <= y < 6})

    def f9():   # high ponytail
        im = head_hair(1, 4, 6, bangs=lambda x, y: y == 1 and x % 3 != 1)
        fill_face(im, OH["back"], lambda x, y, w, h: hair_val(x, y, True) if 3 <= x <= 4 and y <= 7 else None)
        fill_face(im, P["body"]["back"], lambda x, y, w, h: hair_val(x, y) if 3 <= x <= 4 and y < 6 else None)
        return im

    def m5():   # long hair tied at the neck
        im = head_hair(1, 5, 8, bangs=lambda x, y: x in (0, 7) and y < 5, outer={"back": lambda x, y: y < 8})
        fill_face(im, P["body"]["back"], lambda x, y, w, h: hair_val(x, y) if 2 <= x <= 5 and y < 4 else None)
        fill_face(im, P["body"]["back"], lambda x, y, w, h: (60, 40, 30, 255) if 2 <= x <= 5 and y == 1 else None)
        return im

    def m6():   # receding: hair only at the sides and the back
        im = head_hair(0, 5, 6)
        fill_face(im, P["head"]["top"], lambda x, y, w, h: None)
        fill_face(im, P["head"]["top"], lambda x, y, w, h: hair_val(x, y) if x <= 1 or x >= 6 or y >= 5 else None)
        return im

    def m7():   # curly mop
        im = head_hair(2, 4, 5, bangs=lambda x, y: y <= 2 and x % 2 == 0)
        fill_face(im, OH["top"], lambda x, y, w, h: hair_val(x, y, True) if (x + y) % 3 != 0 else None)
        for f in ("front", "right", "left", "back"):
            fill_face(im, OH[f], lambda x, y, w, h: hair_val(x, y, True) if y < 3 and (x + y) % 2 == 0 else None)
        return im

    for k, fn in ((5, f5), (6, f6), (7, f7), (8, f8), (9, f9)):
        save(fn(), f"entity/settler/hair/f{k}.png")
    for k, fn in ((5, m5), (6, m6), (7, m7)):
        save(fn(), f"entity/settler/hair/m{k}.png")

    # ------------------------------------------------------------ outfits (tinted layer + untinted accent)
    def skirt_legs(im, rows, hem=True, flare=False):
        for leg in ("rleg", "lleg"):
            fill_part(im, O[leg], lambda f, x, y, w, h: (g(f, 200) if not (hem and y == rows - 1) else gray(150 + SHADE[f] // 2)) if f not in ("top", "bottom") and y < rows else None)

    def body_outer(im, rows_fn, front_skip=None):
        def fn(f, x, y, w, h):
            if f in ("top", "bottom"):
                return None
            if not rows_fn(f, x, y):
                return None
            if front_skip and f == "front" and front_skip(x, y):
                return None
            return g(f, 202)
        fill_part(im, O["body"], fn)

    outs = {}
    # 1 dress: bodice, waist seam, long skirt to the ankles, V neck
    im = img()
    body_outer(im, lambda f, x, y: True, front_skip=lambda x, y: y == 0 and 2 <= x <= 5)
    fill_face(im, O["body"]["front"], lambda x, y, w, h: gray(150) if y == 6 else None)
    fill_face(im, O["body"]["back"], lambda x, y, w, h: gray(150) if y == 6 else None)
    skirt_legs(im, 10)
    outs[1] = (im, None)
    # 2 skirt over the blouse: hips and legs down to the knee, with a belt
    im = img()
    body_outer(im, lambda f, x, y: y >= 7)
    fill_face(im, O["body"]["front"], lambda x, y, w, h: gray(120) if y == 7 else None)
    skirt_legs(im, 6)
    outs[2] = (im, None)
    # 3 pinafore: straps and a skirt, white apron in the accent layer
    im = img()
    body_outer(im, lambda f, x, y: (y >= 6) or (f in ("front", "back") and x in (1, 2, 5, 6) and y < 6))
    skirt_legs(im, 9, hem=False)
    acc = img()
    fill_face(acc, O["body"]["front"], lambda x, y, w, h: (240, 236, 224, 255) if 2 <= x <= 5 and y >= 2 else None)
    for leg in ("rleg", "lleg"):
        fill_face(acc, O[leg]["front"], lambda x, y, w, h: (240, 236, 224, 255) if y < 8 else None)
    fill_face(acc, O["body"]["back"], lambda x, y, w, h: (225, 220, 205, 255) if y == 6 and x in (1, 2, 5, 6) else None)
    outs[3] = (im, acc)
    # 4 vest: open front, buttons in the accent layer
    im = img()
    body_outer(im, lambda f, x, y: y < 9, front_skip=lambda x, y: 3 <= x <= 4)
    acc = img()
    fill_face(acc, O["body"]["front"], lambda x, y, w, h: (200, 170, 70, 255) if x in (2, 5) and y in (2, 4, 6) else None)
    outs[4] = (im, acc)
    # 5 belted tunic: reaches the thighs, dark belt in the accent layer
    im = img()
    body_outer(im, lambda f, x, y: True)
    for leg in ("rleg", "lleg"):
        fill_part(im, O[leg], lambda f, x, y, w, h: g(f, 196) if f not in ("top", "bottom") and y < 3 else None)
    acc = img()
    for f in ("front", "back", "right", "left"):
        fill_face(acc, O["body"][f], lambda x, y, w, h, f=f: (70, 48, 30, 255) if y == 8 else None)
    fill_face(acc, O["body"]["front"], lambda x, y, w, h: (210, 180, 80, 255) if y == 8 and x in (3, 4) else None)
    outs[5] = (im, acc)
    # 6 long robe: body, sleeves and legs to the ankles, rope belt
    im = img()
    body_outer(im, lambda f, x, y: True)
    skirt_legs(im, 11)
    for arm in ("rarm", "larm"):
        fill_part(im, O[arm], lambda f, x, y, w, h: g(f, 196) if f != "bottom" and y < 9 else None)
    acc = img()
    for f in ("front", "back", "right", "left"):
        fill_face(acc, O["body"][f], lambda x, y, w, h: (150, 120, 70, 255) if y == 7 else None)
    outs[6] = (im, acc)
    for k, (t, a) in outs.items():
        save(t, f"entity/settler/outfit/o{k}.png")
        save(a if a is not None else img(), f"entity/settler/outfit/o{k}_acc.png")

    # ------------------------------------------------------------ headwear (tinted)
    H = O["front"] if False else None

    def cap(top_rows, sides_rows, back_rows, front_rows, rib=None):
        im = img()
        Oh = O["head"]
        fill_face(im, Oh["top"], lambda x, y, w, h: g("top", 204))
        fill_face(im, Oh["front"], lambda x, y, w, h: g("front", 204) if y < front_rows else None)
        for f in ("right", "left"):
            fill_face(im, Oh[f], lambda x, y, w, h, f=f: g(f, 204) if y < sides_rows else None)
        fill_face(im, Oh["back"], lambda x, y, w, h: g("back", 204) if y < back_rows else None)
        if rib:
            for f, rows in (("front", front_rows), ("right", sides_rows), ("left", sides_rows), ("back", back_rows)):
                fill_face(im, Oh[f], lambda x, y, w, h, rows=rows: gray(150) if y == rows - 1 else None)
        return im
    # 1 headscarf: covers the crown, tied at the back, hair peeks out in front
    im = cap(1, 3, 5, 2)
    fill_face(im, O["head"]["back"], lambda x, y, w, h: g("back", 190) if 3 <= x <= 4 and 5 <= y <= 7 else None)
    save(im, "entity/settler/head/h1.png")
    # 2 flat cap with a short brim
    im = cap(1, 2, 2, 2)
    fill_face(im, O["head"]["front"], lambda x, y, w, h: gray(120) if y == 2 and 1 <= x <= 6 else None)
    save(im, "entity/settler/head/h2.png")
    # 3 woollen cap with a ribbed edge
    im = cap(1, 3, 3, 3, rib=True)
    save(im, "entity/settler/head/h3.png")
    # 4 hooded shawl: hood around the head, shoulders covered
    im = cap(1, 7, 9, 1)
    fill_face(im, O["head"]["front"], lambda x, y, w, h: g("front", 200) if (x in (0, 1, 6, 7) and y < 6) or y == 0 else None)
    fill_face(im, O["body"]["back"], lambda x, y, w, h: g("back", 200) if y < 5 else None)
    for s in ("front",):
        fill_face(im, O["body"][s], lambda x, y, w, h: g("front", 200) if (x < 2 or x > 5) and y < 4 else None)
    save(im, "entity/settler/head/h4.png")

    # ------------------------------------------------------------ elders: face lines (untinted, translucent)
    im = img()
    fill_face(im, P["head"]["front"], lambda x, y, w, h: (40, 30, 30, 70) if (y == 2 and 2 <= x <= 5 and x % 2 == 0) or (y == 4 and x in (0, 7)) or (y == 5 and x in (1, 6)) or (y == 6 and x in (2, 5)) else None)
    save(im, "entity/settler/elder.png")
    print("H2 skin layers written")
