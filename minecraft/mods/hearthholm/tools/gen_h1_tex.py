"""Wave H1 textures: richer art for existing blocks/items (polish pass + redraws) and all new coziness content.
Called from gen_textures.main() after every older generator: run(gt) where gt is the gen_textures module."""
import math, os, random
from PIL import Image, ImageDraw

T = {}   # new block textures: name -> image
I = {}   # new item textures


def mk(w=16, h=16):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def hexc(h):
    return (h >> 16 & 255, h >> 8 & 255, h & 255)


def mulc(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), tuple(c) if len(c) == 4 else tuple(c) + (255,))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def wood(base, seed=0, rows=4, vertical=False, nails=True):
    """Boards with a lit top edge, a dark seam, wavy grain, knots and nails."""
    r = random.Random(seed)
    im = mk()
    c0 = hexc(base) if isinstance(base, int) else base
    for b in range(0, 16, rows):
        tone = 1 + r.uniform(-0.07, 0.07)
        for y in range(b, min(b + rows, 16)):
            for x in range(16):
                i = y - b
                k = tone
                if i == 0: k *= 1.12
                if i == rows - 1: k *= 0.68
                k *= 1 + 0.06 * math.sin((x + b * 3) * 0.55 + math.sin(y * 1.7 + b) * 1.3)
                if r.random() < 0.05: k *= 0.87
                px(im, x, y, mulc(c0, k))
        if rows >= 3 and nails:
            px(im, 1, b + rows // 2, mulc(c0, 0.5)); px(im, 14, b + rows // 2, mulc(c0, 0.5))
        if rows >= 4 and r.random() < 0.5:
            kx, ky = r.randint(4, 11), b + rows // 2
            px(im, kx, ky, mulc(c0, 0.62)); px(im, kx + 1, ky, mulc(c0, 0.75))
    return im.transpose(Image.ROTATE_90) if vertical else im


def stone(base, seed=0, bricks=True):
    r = random.Random(seed)
    im = mk()
    c0 = hexc(base)
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 4 if row % 2 else 0
            k = 1 + r.uniform(-0.07, 0.07)
            if bricks:
                if y % 4 == 3: k *= 0.66
                elif (x + off) % 8 == 7: k *= 0.7
                elif y % 4 == 0: k *= 1.1
                elif (x + off) % 8 == 0: k *= 1.07
            px(im, x, y, mulc(c0, k))
    return im


def metal(base, seed=0, vertical=True):
    r = random.Random(seed)
    im = mk()
    c0 = hexc(base)
    for y in range(16):
        for x in range(16):
            u = x if vertical else y
            k = 1 + r.uniform(-0.04, 0.04) + (0.35 if u in (3, 4) else 0) + (0.15 if u == 5 else 0) - (0.3 if u in (13, 14) else 0)
            px(im, x, y, mulc(c0, k))
    return im


def outline(im, col=(38, 24, 18), fill_inside=False):
    """Outer 1px outline around the opaque shape."""
    src = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < im.width and 0 <= yy < im.height and src.getpixel((xx, yy))[3] > 200:
                        im.putpixel((x, y), col + (255,))
                        break
    return im


def inner_outline(im, k=0.62):
    src = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            p = src.getpixel((x, y))
            if p[3] < 200: continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if not (0 <= xx < im.width and 0 <= yy < im.height) or src.getpixel((xx, yy))[3] < 100:
                    im.putpixel((x, y), mulc(p, k))
                    break
    return im


def lit(im, amt=0.16):
    """Top-left light, bottom-right shade across the opaque part of an item icon."""
    for y in range(im.height):
        for x in range(im.width):
            p = im.getpixel((x, y))
            if p[3] > 200:
                k = 1 + amt * ((7.5 - x) + (7.5 - y)) / 15
                im.putpixel((x, y), mulc(p, k))
    return im


def bevel(im, hi=1.14, lo=0.78):
    for i in range(16):
        im.putpixel((i, 0), mulc(im.getpixel((i, 0)), hi)); im.putpixel((0, i), mulc(im.getpixel((0, i)), hi))
        im.putpixel((i, 15), mulc(im.getpixel((i, 15)), lo)); im.putpixel((15, i), mulc(im.getpixel((15, i)), lo))
    return im


def draw(im, rows, pal, ox=0, oy=0):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != "." and ch in pal:
                px(im, ox + i, oy + j, pal[ch])


# --------------------------------------------------------------------------- polish of existing art
def is_woody(p):
    r, g, b = p[:3]
    return r > g > b and r - b > 25 and 40 < r < 235 and (r - g) < 70


def polish_tile(im, seed):
    """Wood grain + metal highlights + bevel for opaque tiles, inner outline for cut-out tiles."""
    r = random.Random(seed)
    opaque = all(im.getpixel((x, y))[3] > 200 for x in range(16) for y in range(16))
    for y in range(16):
        for x in range(16):
            p = im.getpixel((x, y))
            if p[3] < 200: continue
            if is_woody(p):
                g = math.sin(x * 0.7 + math.sin(y * 1.9) * 1.4 + (y // 5) * 2.0)
                k = 1 + 0.045 * g + (0.03 if r.random() < 0.1 else 0)
                im.putpixel((x, y), mulc(p, k))
            elif max(p[:3]) - min(p[:3]) < 26 and 70 < p[0] < 215 and (x * 3 + y * 2) % 13 == 0:
                im.putpixel((x, y), mulc(p, 1.28))     # metal / stone sparkle
    if opaque:
        bevel(im)
    else:
        inner_outline(im)
    return im


def polish_item(im):
    return lit(outline(im) if False else im, 0.14) if False else lit(outline_keep(im), 0.14)


def outline_keep(im):
    """Add an outline only when the icon does not already have a dark rim."""
    return outline(im, (36, 24, 20))


POLISH_BLOCKS = """alarm_bell_bell alarm_bell_wood bench_wood bulletin_board_front carpenter_bench_front carpenter_bench_side carpenter_bench_top
cook_stove_front cook_stove_pot cook_stove_side cook_stove_top herbalist_table_front herbalist_table_side herbalist_table_top lamp_post_glass
lamp_post_metal marker_post market_stall_awning market_stall_crate market_stall_wood mason_table_side mason_table_top order_board_back order_board_front
palisade_side palisade_top sign_back sign_carpenter sign_cook sign_mason sign_smith sign_tavern signpost_board signpost_wood smith_anvil_side
smith_anvil_stump smith_anvil_top warehouse_ledger_side warehouse_ledger_top weapon_rack_bar weapon_rack_front weapon_rack_side construction_sign
farm_sign fishing_sign forest_sign mine_sign patrol_sign territory_sign""".split()
POLISH_ITEMS = "hearth_mark war_horn healing_salve founders_chronicle rift_shard founders_whistle".split()


# --------------------------------------------------------------------------- hand redraws of key blocks
def redraw_existing():
    out = {}
    # town hall desk: lacquered top with a map, drawer fronts with brass handles
    top = wood(0x93603A, seed=1, rows=4)
    rect(top, 2, 2, 13, 13, (226, 208, 164))
    for x in range(2, 14):
        px(top, x, 2, (250, 236, 196)); px(top, x, 13, (176, 150, 108))
    for y in range(2, 14):
        px(top, 2, y, (250, 236, 196)); px(top, 13, y, (176, 150, 108))
    for (x, y) in [(4, 5), (5, 6), (6, 6), (7, 7), (8, 7), (9, 6), (10, 5), (10, 8), (9, 9)]:
        px(top, x, y, (128, 66, 40))
    px(top, 10, 4, (190, 40, 36)); px(top, 10, 5, (190, 40, 36))               # red pin
    rect(top, 11, 10, 12, 11, (28, 26, 40)); px(top, 11, 10, (70, 70, 90))      # ink well
    for i in range(4): px(top, 4 + i, 11 - i // 2, (240, 240, 235))             # quill
    px(top, 8, 11, (200, 60, 50))
    out["town_hall_desk_top"] = top
    side = wood(0x5E3A22, seed=2, rows=5)
    for x in range(16): px(side, x, 0, (136, 92, 56))
    out["town_hall_desk_side"] = side
    front = wood(0x5E3A22, seed=3, rows=5)
    for x in range(16): px(front, x, 0, (136, 92, 56))
    for (x0, y0, x1, y1) in [(2, 3, 7, 8), (8, 3, 13, 8), (2, 9, 13, 14)]:
        rect(front, x0, y0, x1, y1, (84, 54, 32))
        for x in range(x0, x1 + 1): px(front, x, y0, (116, 78, 48)); px(front, x, y1, (44, 28, 18))
        for y in range(y0, y1 + 1): px(front, x0, y, (110, 74, 46)); px(front, x1, y, (44, 28, 18))
    for (x, y) in [(4, 5), (5, 5), (10, 5), (11, 5), (6, 11), (7, 11), (8, 11), (9, 11)]:
        px(front, x, y, (226, 182, 78)); px(front, x, y + 1, (140, 106, 40))
    out["town_hall_desk_front"] = front
    # tavern keg: staves, two iron hoops with rivets, tap, brand
    r = random.Random(3)
    side = mk()
    for y in range(16):
        for x in range(16):
            k = 1 + r.uniform(-0.05, 0.05) + (0.12 if x % 4 == 1 else 0) - (0.3 if x % 4 == 0 else 0)
            c = 0x8F5E32
            if y in (2, 3, 12, 13): c, k = 0x50545E, 1 + r.uniform(-0.04, 0.04) + (0.5 if y == 2 or y == 12 else 0)
            px(side, x, y, mulc(hexc(c), k))
    for x in (2, 7, 12):
        for y in (2, 12): px(side, x, y, (170, 175, 185))
    out["tavern_keg_side"] = side
    front = side.copy()
    rect(front, 4, 5, 11, 10, (214, 176, 84))
    rect(front, 5, 6, 10, 9, (234, 206, 120))
    for x in range(4, 12): px(front, x, 5, (150, 110, 44)); px(front, x, 10, (150, 110, 44))
    for y in range(5, 11): px(front, 4, y, (150, 110, 44)); px(front, 11, y, (150, 110, 44))
    draw(front, ["..bb..", ".bbbb.", "..bb..", ".bbbb."], {"b": (110, 68, 30)}, 5, 6)
    out["tavern_keg_front"] = front
    top = mk()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            k = 1 + r.uniform(-0.04, 0.04)
            if d > 7.3: c = (0x50, 0x54, 0x5E)
            elif d > 6.2: c = (0x6E, 0x72, 0x7C)
            else: c = mulc((0xB8, 0x8C, 0x56), k * (0.82 if int(d) % 3 == 0 else 1.0))
            px(top, x, y, mulc(c, k) if len(c) == 3 else c)
    rect(top, 6, 7, 9, 8, (60, 40, 22)); px(top, 7, 7, (90, 62, 34))
    out["tavern_keg_top"] = top
    # lamp post: wrought iron with highlight and a warm glowing lantern pane
    metal_t = metal(0x3A3E46, seed=5)
    out["lamp_post_metal"] = metal_t
    glass = mk()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15) or x in (7, 8)
            if edge: c = (46, 48, 56) if x != 0 and y != 0 else (84, 86, 96)
            else:
                t = 1 - abs(y - 7.5) / 9
                c = (255, int(190 + 50 * t), int(80 + 70 * t))
                if x in (1, 2) and 1 < y < 8: c = (255, 250, 200)
            px(glass, x, y, c)
    out["lamp_post_glass"] = glass
    # well: mossy stone, bright water, shingled roof
    ws = stone(0x92928C, seed=74)
    for (x, y) in [(2, 3), (3, 3), (9, 7), (10, 7), (10, 8), (5, 13), (6, 13), (13, 2)]: px(ws, x, y, (92, 128, 74))
    out["well_stone"] = ws
    w = mk()
    for y in range(16):
        for x in range(16):
            k = 1 + 0.07 * math.sin(x * 0.9 + y * 0.5) + r.uniform(-0.03, 0.03)
            c = mulc((0x38, 0x80, 0xD2), k)
            if (x * 5 + y * 3) % 17 == 0: c = (170, 220, 250, 255)
            px(w, x, y, c)
    out["well_water"] = w
    roof = mk()
    for y in range(16):
        row = y // 4
        for x in range(16):
            xx = (x + (4 if row % 2 else 0)) % 8
            k = 1 + r.uniform(-0.06, 0.06)
            if y % 4 == 3: k *= 0.6
            elif y % 4 == 0: k *= 1.18
            if xx == 7: k *= 0.72
            px(roof, x, y, mulc((0x84, 0x44, 0x2E), k))
    out["well_roof"] = roof
    # counter / flower box / bench: nicer boards
    out["counter_side"] = wood(0x7C5233, seed=76, rows=6)
    ct = wood(0xB48C58, seed=77, rows=4)
    for x in range(16): px(ct, x, 0, (140, 100, 58)); px(ct, x, 15, (140, 100, 58))
    for y in range(16): px(ct, 0, y, (140, 100, 58)); px(ct, 15, y, (140, 100, 58))
    out["counter_top"] = ct
    cf = wood(0x7C5233, seed=78, rows=6)
    for (x0, y0, x1, y1) in [(2, 3, 13, 12)]:
        rect(cf, x0 + 1, y0 + 1, x1 - 1, y1 - 1, (96, 62, 38))
        for x in range(x0, x1 + 1): px(cf, x, y0, (52, 34, 20)); px(cf, x, y1, (150, 110, 70))
        for y in range(y0, y1 + 1): px(cf, x0, y, (52, 34, 20)); px(cf, x1, y, (150, 110, 70))
    rect(cf, 6, 6, 9, 8, (224, 180, 80)); rect(cf, 7, 7, 8, 7, (150, 110, 40))
    out["counter_front"] = cf
    out["flower_box_wood"] = wood(0x82583A, seed=79, rows=3)
    out["bench_wood"] = wood(0xA27444, seed=73, rows=4)
    return out


# --------------------------------------------------------------------------- new block textures
def new_blocks():
    t = {}
    # wells
    t["well_post"] = wood(0x7A5232, seed=201, rows=16, vertical=True)
    rope = mk()
    for y in range(16):
        for x in range(16):
            k = 1 + (0.18 if (x + y) % 4 < 2 else -0.12)
            px(rope, x, y, mulc((0xC8, 0xAA, 0x70), k))
    t["rope"] = rope
    bucket = mk()
    for y in range(16):
        for x in range(16):
            k = 1 + (0.15 if x % 4 == 1 else -0.2 if x % 4 == 0 else 0)
            c = (0x8A, 0x5C, 0x32)
            if y in (3, 4, 11, 12): c, k = (0x55, 0x58, 0x62), 1 + (0.4 if y in (3, 11) else 0)
            px(bucket, x, y, mulc(c, k))
    t["bucket"] = bucket
    t["well_axle"] = wood(0x6A4426, seed=202, rows=8, vertical=True)
    # furniture woods
    t["table_top"] = table_top()
    t["table_wood"] = wood(0x8C5E38, seed=210, rows=4)
    t["table_wood_dark"] = wood(0x634026, seed=211, rows=16, vertical=True)
    t["long_table_top"] = wood(0xA07040, seed=212, rows=4)
    for x in range(16): px(t["long_table_top"], x, 0, (206, 160, 104)); px(t["long_table_top"], x, 15, (110, 72, 40))
    t["chair_wood"] = wood(0x86583A, seed=213, rows=16, vertical=True)
    cushion = mk()
    for y in range(16):
        for x in range(16):
            k = 1 + 0.12 * math.sin((x + y) * 0.9) - (0.25 if min(x, y, 15 - x, 15 - y) == 0 else 0)
            px(cushion, x, y, mulc((0xB0, 0x36, 0x30), k))
    px(cushion, 7, 7, (240, 210, 90)); px(cushion, 8, 8, (240, 210, 90))
    t["cushion"] = cushion
    t["stool_top"] = wood(0xA47848, seed=214, rows=4)
    # crate, firewood, rack
    crate = wood(0x9B6E40, seed=220, rows=4)
    for i in range(16):
        for (a, b) in ((i, i), (15 - i, i)):
            px(crate, a, b, (84, 56, 30))
    for i in range(16):
        for (a, b) in ((i, 0), (i, 15), (0, i), (15, i)): px(crate, a, b, (70, 46, 26))
        for (a, b) in ((i, 1), (1, i)): px(crate, a, b, (150, 108, 66))
    t["crate_side"] = crate
    ctop = wood(0xA97C4A, seed=221, rows=4)
    for i in range(16):
        for (a, b) in ((i, 0), (i, 15), (0, i), (15, i)): px(ctop, a, b, (70, 46, 26))
    t["crate_top"] = ctop
    t["firewood_end"] = firewood_end()
    bark = mk()
    r = random.Random(222)
    for y in range(16):
        for x in range(16):
            k = 1 + r.uniform(-0.1, 0.1) + (0.12 if y % 4 == 1 else -0.25 if y % 4 == 3 else 0)
            px(bark, x, y, mulc((0x6A, 0x4A, 0x30), k))
    t["firewood_bark"] = bark
    bw = mk()
    for y in range(16):
        for x in range(16):
            k = 1 + r.uniform(-0.05, 0.05) + (0.12 if x % 4 == 1 else -0.3 if x % 4 == 0 else 0)
            c = (0x8F, 0x5E, 0x32)
            if x in (3, 4, 11, 12): c, k = (0x50, 0x54, 0x5E), 1 + (0.45 if x in (3, 11) else 0)
            px(bw, x, y, mulc(c, k))
    t["barrel_side"] = bw
    be = mk()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c = (0x50, 0x54, 0x5E) if d > 7.0 else ((0xB4, 0x88, 0x52) if int(d) % 3 else (0x8E, 0x66, 0x3A))
            px(be, x, y, mulc(c, 1 + r.uniform(-0.04, 0.04)))
    t["barrel_end"] = be
    # rugs
    t["rug_red"] = rug((0xA8, 0x30, 0x2C), (0xE8, 0xC8, 0x70), (0x6A, 0x1C, 0x1C), 230)
    t["rug_blue"] = rug((0x2C, 0x58, 0x94), (0xE4, 0xE0, 0xD0), (0x1C, 0x34, 0x60), 231)
    # candles
    cd = mk()
    for y in range(16):
        for x in range(16):
            k = 1 + (0.14 if x % 4 == 1 else -0.12 if x % 4 == 3 else 0)
            px(cd, x, y, mulc((0xF2, 0xE6, 0xC6), k))
    for (x, y) in [(2, 3), (6, 5), (10, 3), (13, 7)]:
        px(cd, x, y, (216, 196, 150)); px(cd, x, y + 1, (216, 196, 150))
    t["candle_wax"] = cd
    fl = mk()
    rect(fl, 0, 0, 15, 15, (0, 0, 0, 0))
    draw(fl, ["...yy...", "..yooy..", "..yoOy..", "..yooy..", "...yy..."], {"y": (255, 236, 130), "o": (255, 190, 60), "O": (255, 250, 220)}, 4, 3)
    t["candle_flame"] = fl
    t["candlestick_metal"] = metal(0xC8A24A, seed=230)
    # wall shelf
    t["shelf_wood"] = wood(0x9A6C40, seed=240, rows=4)
    jars = mk()
    for i, (c, lid) in enumerate([((0xC8, 0x3C, 0x34), (0x9A, 0x6A, 0x3E)), ((0xE0, 0xB0, 0x40), (0x9A, 0x6A, 0x3E)),
                                  ((0x4E, 0x96, 0x46), (0x70, 0x70, 0x78)), ((0x8A, 0x4E, 0xA8), (0x9A, 0x6A, 0x3E))]):
        x0, y0 = (i % 2) * 8, (i // 2) * 8
        rect(jars, x0 + 1, y0 + 2, x0 + 6, y0 + 7, mulc(c, 0.85))
        rect(jars, x0 + 1, y0 + 2, x0 + 2, y0 + 7, mulc(c, 1.25))
        rect(jars, x0 + 2, y0 + 0, x0 + 5, y0 + 1, lid)
        px(jars, x0 + 3, y0 + 4, (245, 240, 220)); px(jars, x0 + 4, y0 + 4, (245, 240, 220))     # label
    t["shelf_jars"] = jars
    # hanging herbs
    t["herbs"] = herbs()
    t["herb_string"] = wood(0x9A7A48, seed=250, rows=16, vertical=True, nails=False)
    # bottles / mugs / plates (atlases, used with uv regions)
    t["bottles"] = bottles_tex()
    t["mug"] = mug_tex()
    t["plates"] = plates_tex()
    # hanging lantern
    t["hanging_lantern"] = hang_lantern()
    # chimney cap: stone bricks like the chimney, a lighter rim, a sooty hollow
    t["chimney_cap"] = stone(0x7B7A7A, seed=240)
    t["chimney_cap_rim"] = stone(0x8C8B8A, seed=241)
    soot = mk()
    rs = random.Random(242)
    for y in range(16):
        for x in range(16):
            px(soot, x, y, mulc((0x26, 0x24, 0x24), 1 + rs.uniform(-0.25, 0.3) + (0.5 if (x * 3 + y * 5) % 11 == 0 else 0)))
    t["chimney_cap_soot"] = soot
    t["chain"] = metal(0x484C54, seed=260, vertical=True)
    return t


def table_top():
    im = mk()
    r = random.Random(211)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            k = 1 + r.uniform(-0.04, 0.04) + 0.05 * math.sin(d * 1.8)
            if d > 7.6: c = (0x6E, 0x46, 0x28)
            elif d > 6.6: c = (0xB8, 0x86, 0x52)
            else: c = (0xA4, 0x74, 0x44)
            px(im, x, y, mulc(c, k))
    for x in range(2, 14): px(im, x, 7, mulc((0x80, 0x58, 0x32), 1.0))
    return im


def firewood_end():
    im = mk()
    r = random.Random(223)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d > 7.6: c = (0x4C, 0x34, 0x20)
            elif d > 6.2: c = (0x6C, 0x4A, 0x2E)
            else:
                c = (0xCC, 0xA0, 0x68) if int(d * 0.9) % 2 else (0xB0, 0x84, 0x52)
            px(im, x, y, mulc(c, 1 + r.uniform(-0.04, 0.04)))
    px(im, 7, 7, (0x88, 0x60, 0x38)); px(im, 8, 8, (0x88, 0x60, 0x38))
    return im


def rug(base, trim, dark, seed):
    im = mk()
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            m = min(x, y, 15 - x, 15 - y)
            if m == 0: c = dark
            elif m == 1: c = trim
            elif m == 2: c = dark
            elif m == 3: c = base
            else:
                c = trim if (abs(x - 7.5) + abs(y - 7.5) < 3.5 and (x + y) % 2 == 0) else (dark if abs(x - 7.5) + abs(y - 7.5) < 5 else base)
            px(im, x, y, mulc(c, 1 + r.uniform(-0.05, 0.05)))
    for x in range(0, 16, 2):
        px(im, x, 0, trim); px(im, x, 15, trim)     # fringe
    return im


def herbs():
    im = mk()
    r = random.Random(251)
    bunches = [((0x4E, 0x96, 0x46), (0x78, 0xB8, 0x5A)), ((0x8A, 0x4E, 0xA8), (0xB0, 0x7A, 0xD0)), ((0xC8, 0x5A, 0x3C), (0xE8, 0x90, 0x58)), ((0x90, 0xA8, 0x40), (0xC0, 0xD0, 0x60))]
    for i, (c1, c2) in enumerate(bunches):
        x0, y0 = (i % 2) * 8, (i // 2) * 8
        for y in range(8):
            w = 1 + int(y * 0.5) if y < 5 else 5 - (y - 4)
            for x in range(4 - w // 2 - 1, 4 + w // 2 + 1):
                if r.random() < 0.88:
                    px(im, x0 + x, y0 + y, mulc(c1 if r.random() < 0.55 else c2, 1 + r.uniform(-0.12, 0.12)))
        px(im, x0 + 3, y0, (0xB0, 0x90, 0x58)); px(im, x0 + 4, y0, (0xB0, 0x90, 0x58))
    return im


def bottles_tex():
    im = mk()
    cols = [((0x2E, 0x7A, 0x3C), (0x9A, 0xE0, 0x9A)), ((0x9A, 0x58, 0x1C), (0xE8, 0xB0, 0x58)), ((0x2C, 0x58, 0x9A), (0x8C, 0xC0, 0xF0))]
    for i, (c, hi) in enumerate(cols):
        x0 = i * 5
        rect(im, x0, 3, x0 + 3, 11, mulc(c, 0.9))              # body
        rect(im, x0 + 1, 0, x0 + 2, 3, mulc(c, 0.85))          # neck
        rect(im, x0 + 1, 0, x0 + 2, 0, (0xB0, 0x86, 0x4C))     # cork
        px(im, x0 + 1, 4, hi); px(im, x0 + 1, 5, hi); px(im, x0 + 1, 6, hi)
        rect(im, x0, 7, x0 + 3, 9, (0xE8, 0xDC, 0xBC)); px(im, x0 + 1, 8, c)         # label
        for x in range(x0, x0 + 4): px(im, x, 11, mulc(c, 0.55))
    rect(im, 0, 13, 3, 15, (0x70, 0x42, 0x28))   # bottom cap colour region for undersides
    return im


def mug_tex():
    im = mk()
    for y in range(16):
        for x in range(16):
            if x < 8 and y < 8:     # side of the mug (wood + hoop)
                k = 1 + (0.15 if x % 3 == 1 else -0.18 if x % 3 == 0 else 0)
                c = (0x8E, 0x62, 0x3A)
                if y in (2, 5): c, k = (0xA8, 0xAC, 0xB4), 1.0
                px(im, x, y, mulc(c, k))
    for y in range(8, 16):
        for x in range(8):   # top: frothy ale
            d = ((x - 3.5) ** 2 + (y - 11.5) ** 2) ** 0.5
            c = (0xA8, 0xAC, 0xB4) if d > 3.6 else ((0xF4, 0xEC, 0xCC) if (x + y) % 3 else (0xE4, 0xD0, 0x90))
            px(im, x, y, c)
    rect(im, 8, 0, 11, 7, (0x8E, 0x62, 0x3A)); rect(im, 8, 0, 8, 7, (0xB4, 0x84, 0x54))  # handle
    return im


def plates_tex():
    im = mk()
    for y in range(8):
        for x in range(8):      # plate top, white glaze with blue rim
            d = ((x - 3.5) ** 2 + (y - 3.5) ** 2) ** 0.5
            c = (0x4C, 0x70, 0xB0) if 3.0 < d < 3.8 else ((0xF2, 0xF0, 0xE8) if d <= 3.8 else (0, 0, 0, 0))
            px(im, x, y, c)
    rect(im, 8, 0, 15, 1, (0xE0, 0xDC, 0xD0))                         # plate side
    rect(im, 8, 0, 15, 0, (0xF6, 0xF4, 0xEC))
    # bread loaf (top / side)
    rect(im, 0, 8, 7, 15, (0xC4, 0x84, 0x3C))
    for (x, y) in [(2, 10), (3, 10), (4, 11), (5, 11), (3, 12), (4, 12), (5, 13)]: px(im, x, y, (0xE8, 0xB4, 0x5C))
    rect(im, 8, 8, 15, 11, (0xB8, 0x78, 0x34))
    for x in range(8, 16): px(im, x, 8, (0xD8, 0xA0, 0x50))
    # roast (drumstick-like block)
    rect(im, 8, 12, 15, 15, (0x7E, 0x44, 0x24))
    for (x, y) in [(9, 12), (11, 13), (13, 12), (10, 14), (14, 14)]: px(im, x, y, (0xB8, 0x6C, 0x38))
    # fish (top view region)
    rect(im, 8, 2, 13, 5, (0xB0, 0x78, 0x48))
    for (x, y) in [(9, 3), (10, 3), (11, 4), (12, 3)]: px(im, x, y, (0xE0, 0xA6, 0x64))
    px(im, 8, 3, (0xF0, 0xF0, 0xE8))
    rect(im, 14, 2, 15, 5, (0x8E, 0x58, 0x30))
    return im


def hang_lantern():
    im = mk()
    for y in range(16):
        for x in range(16):
            if y < 8:    # glowing glass
                edge = x in (0, 7) or y in (0,) or x in (3, 4) and False
                c = (0x3C, 0x3E, 0x46) if x in (0, 7) else (255, int(188 + 44 * (1 - abs(y - 3.5) / 4)), 90)
                if x == 1 and 1 <= y <= 5: c = (255, 248, 190)
                px(im, x, y, c)
            elif x < 8:   # frame cap / base
                px(im, x, y, mulc((0x3C, 0x3E, 0x46), 1 + (0.4 if y == 8 else 0)))
            else:
                px(im, x, y, mulc((0x3C, 0x3E, 0x46), 1.1))
    return im


# --------------------------------------------------------------------------- food and item icons
def canvas():
    return mk(), None


def ell(d, x0, y0, x1, y1, c):
    d.ellipse([x0, y0, x1, y1], fill=c + (255,))


def finish(im, amt=0.15):
    return lit(outline(im, (44, 28, 20)), amt)


def food_icons():
    R = {}
    # fried fish
    im = mk(); d = ImageDraw.Draw(im)
    d.polygon([(11, 8), (15, 4), (15, 12)], fill=(214, 140, 52, 255))
    ell(d, 1, 4, 12, 12, (232, 172, 66))
    d.line([(3, 6), (10, 6)], fill=(250, 214, 118, 255)); d.line([(3, 10), (10, 10)], fill=(196, 120, 40, 255))
    for x in (4, 6, 8): px(im, x, 8, (178, 104, 36))
    px(im, 3, 7, (255, 255, 255)); px(im, 3, 8, (40, 24, 20))
    px(im, 6, 5, (255, 236, 160)); px(im, 9, 7, (255, 236, 160))
    R["fried_fish"] = finish(im)
    # smoked fish: dark golden, on a string
    im = mk(); d = ImageDraw.Draw(im)
    d.polygon([(11, 8), (15, 5), (15, 12)], fill=(120, 70, 36, 255))
    ell(d, 1, 4, 12, 12, (164, 100, 48))
    d.line([(3, 6), (10, 6)], fill=(200, 140, 72, 255)); d.line([(3, 10), (10, 10)], fill=(110, 64, 30, 255))
    for x in (4, 6, 8): d.line([(x, 6), (x, 10)], fill=(124, 72, 34, 255))
    px(im, 3, 7, (250, 240, 220)); px(im, 3, 8, (30, 20, 16))
    d.line([(1, 1), (14, 1)], fill=(196, 170, 110, 255)); d.line([(5, 1), (5, 4)], fill=(196, 170, 110, 255))
    R["smoked_fish"] = finish(im)
    # fish pie: crust with a tail poking out
    im = mk(); d = ImageDraw.Draw(im)
    ell(d, 1, 6, 14, 14, (176, 120, 58))
    ell(d, 2, 5, 13, 11, (226, 170, 88))
    for (x, y) in [(4, 7), (6, 6), (9, 6), (11, 7), (5, 9), (8, 8), (10, 9)]: px(im, x, y, (250, 214, 140))
    d.polygon([(8, 3), (11, 0), (13, 4)], fill=(120, 150, 170, 255)); d.line([(8, 4), (12, 4)], fill=(200, 215, 225, 255))
    R["fish_pie"] = finish(im)
    # bowls: stew, soup, porridge
    def bowl(inner, spots, chunk=None, steam=True):
        im = mk(); d = ImageDraw.Draw(im)
        d.pieslice([1, 3, 14, 17], 0, 180, fill=(150, 98, 60, 255))
        d.pieslice([2, 4, 13, 16], 0, 180, fill=(176, 124, 80, 255))
        ell(d, 1, 4, 14, 9, (112, 70, 42))
        ell(d, 2, 5, 13, 8, inner)
        for (x, y, c) in spots: px(im, x, y, c)
        d.line([(4, 12), (11, 12)], fill=(120, 76, 44, 255))
        im = finish(im)
        if steam:
            for (x, y) in [(6, 2), (7, 1), (9, 2), (10, 1), (6, 0)]: px(im, x, y, (235, 235, 240, 190))
        return im
    R["meat_stew"] = bowl((120, 66, 36), [(4, 6, (196, 120, 90)), (7, 7, (228, 142, 40)), (10, 6, (196, 120, 90)), (6, 6, (96, 150, 60)), (9, 7, (96, 150, 60)), (11, 7, (228, 142, 40))])
    R["fish_soup"] = bowl((226, 150, 56), [(4, 6, (255, 220, 140)), (7, 7, (250, 190, 90)), (10, 6, (255, 220, 140)), (6, 6, (120, 170, 80)), (8, 6, (240, 240, 235)), (9, 7, (230, 120, 60))])
    R["porridge"] = bowl((236, 218, 172), [(5, 6, (150, 40, 80)), (8, 6, (150, 40, 80)), (10, 7, (96, 60, 160)), (7, 7, (252, 240, 200)), (6, 7, (200, 160, 80))])
    # pies
    def pie(filling, top_dots, lattice=None):
        im = mk(); d = ImageDraw.Draw(im)
        ell(d, 0, 6, 15, 14, (150, 96, 44))
        ell(d, 1, 5, 14, 11, (212, 150, 74))
        ell(d, 3, 6, 12, 10, filling)
        for (x, y, c) in top_dots: px(im, x, y, c)
        if lattice:
            for x in range(4, 12, 2): d.line([(x, 6), (x + 1, 10)], fill=lattice + (255,))
            for x in range(4, 12, 2): d.line([(x + 1, 6), (x, 10)], fill=lattice + (255,))
        for x in range(2, 14, 2): px(im, x, 11, (240, 190, 110))
        return finish(im)
    R["berry_pie"] = pie((120, 36, 90), [(5, 7, (170, 60, 130)), (8, 8, (170, 60, 130)), (10, 7, (170, 60, 130)), (6, 9, (80, 20, 70))], (232, 178, 96))
    R["apple_pie"] = pie((196, 120, 50), [(5, 7, (250, 220, 110)), (9, 8, (230, 70, 50)), (7, 9, (250, 220, 110))], (236, 186, 104))
    R["meat_pie"] = pie((150, 100, 56), [(7, 8, (60, 30, 16)), (8, 8, (60, 30, 16)), (5, 7, (200, 150, 90)), (10, 9, (200, 150, 90))], None)
    # sausage links
    im = mk(); d = ImageDraw.Draw(im)
    for (x0, y0, x1, y1) in [(1, 6, 8, 10), (6, 3, 14, 7), (4, 10, 12, 14)]:
        d.rounded_rectangle([x0, y0, x1, y1], 2, fill=(190, 84, 62, 255))
        d.line([(x0 + 1, y0 + 1), (x1 - 2, y0 + 1)], fill=(232, 130, 104, 255))
        px(im, x0 + 3, y1 - 1, (140, 52, 40)); px(im, x1 - 3, y1 - 1, (140, 52, 40))
    px(im, 6, 8, (230, 200, 130)); px(im, 6, 5, (230, 200, 130))
    R["sausage"] = finish(im)
    # cheese wedge
    im = mk(); d = ImageDraw.Draw(im)
    d.polygon([(1, 11), (14, 5), (14, 12), (1, 12)], fill=(236, 190, 56, 255))
    d.polygon([(1, 11), (14, 5), (14, 7), (1, 11)], fill=(252, 226, 110, 255))
    d.rectangle([13, 6, 14, 12], fill=(206, 154, 36, 255))
    for (x, y) in [(5, 10), (9, 9), (11, 11), (3, 11)]: px(im, x, y, (186, 136, 30)); px(im, x + 1, y, (156, 110, 24))
    R["cheese_wedge"] = finish(im)
    # rye loaf
    im = mk(); d = ImageDraw.Draw(im)
    ell(d, 0, 4, 15, 13, (106, 62, 32))
    ell(d, 1, 3, 14, 11, (140, 86, 44))
    for x in (4, 7, 10): d.line([(x, 5), (x + 2, 8)], fill=(206, 164, 104, 255))
    for (x, y) in [(3, 9), (6, 10), (11, 9), (12, 6)]: px(im, x, y, (236, 220, 180))
    R["rye_loaf"] = finish(im)
    # honey bun
    im = mk(); d = ImageDraw.Draw(im)
    ell(d, 1, 3, 14, 13, (180, 108, 44))
    ell(d, 2, 3, 13, 11, (236, 170, 70))
    d.arc([4, 5, 11, 10], 0, 300, fill=(252, 214, 110, 255))
    px(im, 7, 7, (252, 214, 110)); px(im, 4, 4, (255, 240, 190)); px(im, 11, 5, (255, 240, 190)); px(im, 6, 11, (150, 80, 30))
    R["honey_bun"] = finish(im)
    # roast vegetables
    im = mk(); d = ImageDraw.Draw(im)
    ell(d, 0, 6, 15, 15, (60, 60, 68)); ell(d, 1, 6, 14, 13, (90, 90, 100))
    d.rounded_rectangle([2, 4, 9, 6], 1, fill=(236, 130, 36, 255)); d.line([(3, 4), (8, 4)], fill=(255, 176, 90, 255))
    ell(d, 7, 6, 13, 11, (214, 164, 84)); px(im, 9, 8, (160, 112, 50)); px(im, 11, 9, (160, 112, 50))
    ell(d, 2, 8, 6, 12, (200, 60, 70)); px(im, 3, 9, (240, 110, 120))
    for (x, y) in [(8, 12), (11, 12), (13, 10), (6, 7)]: px(im, x, y, (80, 150, 56))
    R["roast_vegetables"] = finish(im)
    # bottles
    def bottle(liquid, label, foam=None, shape="tall"):
        im = mk(); d = ImageDraw.Draw(im)
        d.rectangle([6, 1, 9, 3], fill=(120, 90, 54, 255))
        d.rectangle([6, 3, 9, 5], fill=(80, 140, 90, 255) if False else (150, 190, 150, 200))
        d.rectangle([4, 5, 11, 14], fill=liquid + (255,))
        d.rectangle([5, 6, 6, 12], fill=tuple(min(255, int(v * 1.3)) for v in liquid) + (255,))
        d.rectangle([4, 8, 11, 11], fill=(236, 226, 196, 255)); d.rectangle([7, 9, 8, 10], fill=label + (255,))
        d.line([(4, 14), (11, 14)], fill=tuple(int(v * 0.6) for v in liquid) + (255,))
        if foam: d.rectangle([4, 5, 11, 6], fill=foam + (255,))
        return finish(im)
    R["ale"] = bottle((176, 106, 30), (150, 40, 30), (250, 240, 200))
    R["mead"] = bottle((226, 170, 50), (230, 190, 40))
    R["cider"] = bottle((190, 190, 70), (210, 50, 40))
    # herbal tea: a steaming cup
    im = mk(); d = ImageDraw.Draw(im)
    d.rectangle([3, 6, 11, 13], fill=(224, 220, 206, 255)); d.rectangle([3, 6, 4, 13], fill=(246, 244, 236, 255))
    d.rectangle([3, 11, 11, 13], fill=(190, 186, 172, 255)); d.rectangle([3, 8, 11, 8], fill=(80, 120, 180, 255))
    d.rectangle([4, 6, 10, 7], fill=(150, 170, 70, 255))
    d.arc([10, 7, 15, 12], 270, 90, fill=(224, 220, 206, 255), width=2)
    px(im, 6, 6, (90, 140, 60)); px(im, 8, 7, (110, 160, 70))
    im = finish(im)
    for (x, y) in [(5, 4), (6, 3), (6, 2), (9, 4), (10, 3), (10, 2)]: px(im, x, y, (235, 235, 240, 190))
    R["herbal_tea"] = im
    return R


def other_items():
    R = {}
    # hearth mark: a stamped copper coin with a flame
    im = mk(); d = ImageDraw.Draw(im)
    ell(d, 1, 1, 14, 14, (170, 100, 44)); ell(d, 2, 2, 13, 13, (226, 156, 70)); ell(d, 3, 3, 12, 12, (200, 128, 52))
    d.polygon([(8, 3), (11, 8), (10, 11), (6, 11), (5, 8), (7, 6)], fill=(255, 226, 120, 255))
    d.polygon([(8, 6), (9, 9), (8, 11), (7, 9)], fill=(255, 140, 50, 255))
    for (x, y) in [(3, 5), (4, 3), (5, 2)]: px(im, x, y, (255, 214, 140))
    R["hearth_mark"] = finish(im, 0.1)
    # war horn: curved horn with a gold band and a strap
    im = mk(); d = ImageDraw.Draw(im)
    d.polygon([(1, 11), (3, 7), (9, 3), (14, 2), (14, 7), (9, 9), (5, 14)], fill=(224, 208, 164, 255))
    d.polygon([(1, 11), (3, 7), (9, 3), (14, 2), (13, 3), (9, 5), (5, 9)], fill=(246, 236, 202, 255))
    d.polygon([(14, 2), (15, 2), (15, 8), (14, 7)], fill=(120, 76, 40, 255))
    d.rectangle([5, 5, 6, 11], fill=(214, 170, 60, 255)); d.rectangle([9, 3, 10, 8], fill=(214, 170, 60, 255))
    d.line([(2, 12), (4, 14)], fill=(120, 76, 40, 255))
    R["war_horn"] = finish(im)
    # healing salve: a little jar with a green lid and red ointment
    im = mk(); d = ImageDraw.Draw(im)
    d.rectangle([3, 7, 12, 14], fill=(200, 226, 232, 255)); d.rectangle([3, 7, 4, 14], fill=(240, 252, 255, 255))
    d.rectangle([4, 9, 11, 13], fill=(200, 50, 60, 255)); d.rectangle([5, 9, 6, 12], fill=(240, 100, 110, 255))
    d.rectangle([2, 4, 13, 7], fill=(80, 140, 70, 255)); d.rectangle([2, 4, 13, 4], fill=(130, 190, 110, 255))
    d.line([(4, 5), (11, 5)], fill=(96, 160, 84, 255))
    R["healing_salve"] = finish(im)
    # founders chronicle: leather book with a brass flame emblem and clasp
    im = mk(); d = ImageDraw.Draw(im)
    d.rectangle([2, 1, 13, 14], fill=(118, 60, 34, 255)); d.rectangle([2, 1, 3, 14], fill=(86, 42, 24, 255))
    d.rectangle([4, 2, 12, 13], fill=(150, 84, 48, 255)); d.rectangle([4, 2, 12, 2], fill=(180, 112, 66, 255))
    d.polygon([(8, 4), (10, 7), (9, 10), (7, 10), (6, 7)], fill=(236, 190, 70, 255)); d.polygon([(8, 6), (9, 8), (8, 10), (7, 8)], fill=(255, 236, 150, 255))
    d.rectangle([12, 7, 14, 9], fill=(226, 190, 80, 255)); d.rectangle([2, 12, 13, 14], fill=(250, 244, 226, 255)); d.rectangle([4, 12, 13, 12], fill=(206, 196, 168, 255))
    d.line([(2, 14), (13, 14)], fill=(90, 56, 36, 255))
    for (x, y) in [(4, 3), (11, 3), (4, 12 - 1), (11, 11)]: px(im, x, y, (226, 190, 80))
    R["founders_chronicle"] = finish(im, 0.1)
    return R


def run(gt):
    for name, im in redraw_existing().items():
        gt.save(im, f"block/{name}.png")
    # polish all older block/item tiles that were not redrawn above
    redrawn = set(redraw_existing().keys())
    for i, name in enumerate(POLISH_BLOCKS):
        if name in redrawn: continue
        p = os.path.join(gt.OUT, f"block/{name}.png")
        if os.path.exists(p):
            gt.save(polish_tile(Image.open(p).convert("RGBA"), i), f"block/{name}.png")
    for name, im in other_items().items():
        gt.save(im, f"item/{name}.png")
    for i, name in enumerate(POLISH_ITEMS):
        if name in other_items(): continue
        p = os.path.join(gt.OUT, f"item/{name}.png")
        if os.path.exists(p):
            gt.save(lit(outline(Image.open(p).convert("RGBA"), (40, 26, 22)), 0.12), f"item/{name}.png")
    for name, im in new_blocks().items():
        gt.save(im, f"block/{name}.png")
    for name, im in food_icons().items():
        gt.save(im, f"item/{name}.png")
    print("wave H textures:", len(new_blocks()), "blocks,", len(food_icons()), "foods")
