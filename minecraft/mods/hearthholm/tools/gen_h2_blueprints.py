#!/usr/bin/env python3
"""Wave H2: pitched-roof, furnished blueprints. Run from the mod folder: python3 tools/gen_h2_blueprints.py [--dump name]
Rewrites small_house, farmhouse, workshop, tavern, market_stall, barracks, watchtower and adds cottage, townhouse,
fisher_hut, l_house (appended to index.json). Authoring frame as in gen_blueprints.py: x west->east, z from the front row
(row 0 = porch/garden row next to the stake) backwards, layer 0 = floor/foundation, layer 1 = stake level.
Every house is validated like RoomScanner does (flood fill: enclosed, roofed, has a door)."""
import json, os, sys

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "hearthholm", "blueprints")
AIR = "minecraft:air"
VEC = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def st(name, **props):
    ns = name if ":" in name else "minecraft:" + name
    if not props:
        return ns
    return ns + "[" + ",".join(f"{k}={str(v).lower() if isinstance(v, bool) else v}" for k, v in props.items()) + "]"


def stairs(mat, facing, half="bottom"):
    return st(mat + "_stairs", facing=facing, half=half, shape="straight")


def slab(mat, typ="bottom"):
    return st(mat + "_slab", type=typ)


def log(wood, axis="y"):
    return st(wood + "_log", axis=axis)


def door(half):
    return st("oak_door", facing="north", half=half, hinge="left", open=False)


def trap(facing):
    return st("oak_trapdoor", facing=facing, half="bottom", open=True)


def hh(name, **p):
    return st("hearthholm:" + name, **p)


def small(name, count=1, facing="north", on_table=True):
    return hh(name, facing=facing, count=count, on_table=on_table)


LANTERN_UP = st("lantern", hanging=False)
LANTERN_DOWN = st("lantern", hanging=True)
CAMPFIRE = st("campfire", lit=True, signal_fire=False, facing="north")
PANE = st("glass_pane")


class Grid:
    def __init__(self, w, h, d):
        self.w, self.h, self.d = w, h, d
        self.c = [[[AIR] * w for _ in range(d)] for _ in range(h)]

    def set(self, x, y, z, s, force=True):
        if not (0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d):
            raise IndexError((x, y, z, s))
        if not force and self.c[y][z][x] != AIR:
            raise ValueError("cell taken %s by %s" % ((x, y, z, s), self.c[y][z][x]))
        self.c[y][z][x] = s

    def get(self, x, y, z):
        return self.c[y][z][x]

    def fill(self, x0, y0, z0, x1, y1, z1, s):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    self.set(x, y, z, s)

    def furn(self, x, y, z, s):
        """furniture: only on a free cell"""
        self.set(x, y, z, s, force=False)

    def bed(self, x, z, color="red", y=1):
        self.furn(x, y, z, st(color + "_bed", facing="south", part="foot"))
        self.furn(x, y, z + 1, st(color + "_bed", facing="south", part="head"))

    def save(self, name):
        pool = "LPRcDdGTCKbBlpMfhtFgSsWwXxYyZzAaEeHIiJjkmnoqruvVNOQU0123456789@#$%&*+=?!/<>"
        assert len(set(pool)) == len(pool)
        pal, rev, layers = {}, {}, []
        for y in range(self.h):
            rows = []
            for z in range(self.d):
                row = ""
                for x in range(self.w):
                    s = self.c[y][z][x]
                    if y == 0 and s == AIR:
                        s = None   # ground layer outside the footprint stays as it is (no trench around the house)
                    if s is None:
                        row += "~"
                    elif s == AIR:
                        row += "."
                    else:
                        if s not in rev:
                            rev[s] = pool[len(rev)]
                            pal[rev[s]] = s
                        row += rev[s]
                rows.append(row)
            layers.append(rows)
        with open(os.path.join(D, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"palette": pal, "layers": layers}, f, indent=1)
            f.write("\n")


class Style:
    def __init__(self, plinth="cobblestone", floor="oak_planks", wall="white_terracotta", corner="oak", beam="oak", post=None,
                 roof="spruce", roof_block="spruce_planks"):
        self.plinth, self.floor, self.wall, self.corner, self.beam, self.post, self.roof, self.roof_block = plinth, floor, wall, corner, beam, post, roof, roof_block


def wing_roof(wing, x, z, yw):
    x0, z0, x1, z1, axis = wing
    if not (x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1):
        return None
    n, t = (z1 - z0 + 3, z - (z0 - 1)) if axis == "x" else (x1 - x0 + 3, x - (x0 - 1))
    r = (n - 1) // 2
    i = min(t, n - 1 - t)
    if i < r:
        f = ("south" if t < r else "north") if axis == "x" else ("east" if t < r else "west")
        return yw + i, "stair", f
    return yw + r - 1, "ridge", None


def roof_at(wings, x, z, yw):
    best = None
    for w in wings:
        h = wing_roof(w, x, z, yw)
        if h and (best is None or h[0] > best[0]):
            best = h
    return best


def build_house(wings, W, D, s, door_x, yw=3, windows=(), chimney=None, ceiling=True, awning=False, lamp=True):
    rmax = max((w[3] - w[1] + 3) // 2 if w[4] == "x" else (w[2] - w[0] + 3) // 2 for w in wings)
    g = Grid(W, yw + rmax + 4, D)
    F = {(x, z) for x0, z0, x1, z1, _ in wings for z in range(z0, z1 + 1) for x in range(x0, x1 + 1)}
    nb8 = lambda x, z: [(x + a, z + b) for a in (-1, 0, 1) for b in (-1, 0, 1) if a or b]
    perim = {c for c in F if any(n not in F for n in nb8(*c))}
    inner = F - perim

    def has(c, dx, dz):
        return (c[0] + dx, c[1] + dz) in F

    def straight(c):
        return sum((has(c, 1, 0), has(c, -1, 0), has(c, 0, 1), has(c, 0, -1))) == 3

    def outward(c):
        for d, (dx, dz) in VEC.items():
            if not has(c, dx, dz):
                return d
        return None

    for c in F:
        g.set(c[0], 0, c[1], st(s.plinth) if c in perim else st(s.floor))
    for c in perim:
        x, z = c
        out = outward(c)
        corner = not straight(c) or out is None
        for y in range(1, yw + 1):
            if corner:
                g.set(x, y, z, log(s.corner))
            elif y == 1:
                g.set(x, y, z, st(s.plinth))
            elif y == yw:
                g.set(x, y, z, log(s.beam, "x" if out in ("north", "south") else "z"))
            else:
                g.set(x, y, z, st(s.wall))
    if s.post:
        for c in perim:
            if straight(c) and (c[0] + c[1]) % 4 == 0:
                for y in range(2, yw):
                    g.set(c[0], y, c[1], log(s.post))
    if ceiling:
        for (x, z) in inner:
            g.set(x, yw + 1, z, st(s.floor))
    cols = {(x, z) for w in wings for z in range(w[1] - 1, w[3] + 2) for x in range(w[0] - 1, w[2] + 2)}
    for (x, z) in cols:
        y, kind, f = roof_at(wings, x, z, yw)
        if kind == "stair":
            g.set(x, y, z, stairs(s.roof, f))
        else:
            g.set(x, y, z, st(s.roof_block))
            g.set(x, y + 1, z, slab(s.roof))
    for (x, z) in perim:
        for y in range(yw + 1, roof_at(wings, x, z, yw)[0]):
            g.set(x, y, z, st(s.wall))
    fx, fz = next((x, z) for (x, z) in sorted(perim, key=lambda c: c[1]) if x == door_x and outward((x, z)) == "north")
    g.set(fx, 1, fz, door("lower"))
    g.set(fx, 2, fz, door("upper"))
    for x in (fx - 1, fx, fx + 1):
        g.set(x, 0, fz - 1, st("oak_planks"))
    for x in (fx - 1, fx + 1):
        g.set(x, 1, fz - 1, st("oak_fence"))
        g.set(x, 2, fz - 1, LANTERN_UP)
    if awning:
        for x in (fx - 1, fx, fx + 1):
            g.set(x, 3, fz - 1, stairs("oak", "south"))
    if lamp:
        g.set(0, 1, 0, hh("lamp_post", facing="north"))
    for w in windows:
        x, z = w[0], w[1]
        ys = w[2] if len(w) > 2 else ((2, 3) if yw == 5 else (2,))
        out = outward((x, z))
        dx, dz = VEC[out]
        for y in ys:
            g.set(x, y, z, PANE)
        side = [(x - 1, z), (x + 1, z)] if out in ("north", "south") else [(x, z - 1), (x, z + 1)]
        for (sx, sz) in side:
            px, pz = sx + dx, sz + dz
            if (sx, sz) in perim and 0 <= px < W and 0 <= pz < D and (px, pz) not in F and g.get(sx, ys[0], sz) != PANE:
                for y in ys:
                    if g.get(px, y, pz) == AIR:
                        g.set(px, y, pz, trap(out))
        ox, oz = x + dx, z + dz
        if 0 <= ox < W and 0 <= oz < D and (ox, oz) not in F and g.get(ox, ys[0] - 1, oz) == AIR:
            g.set(ox, ys[0] - 1, oz, hh("flower_box", facing=out))
    if chimney:
        cx, cz = chimney
        top = max(roof_at(wings, cx, cz, yw)[0] + 2, yw + 4)
        for y in range(1, top):
            g.set(cx, y, cz, st("cobblestone") if y < yw + 2 else st("stone_bricks"))
        g.set(cx, top, cz, CAMPFIRE)
    return g, F, perim, inner


# ---------------------------------------------------------------- validator (RoomScanner-like flood fill)
def passable(s):
    if s == AIR:
        return True
    n = s.split("[")[0]
    return n in ("minecraft:wall_torch", "hearthholm:rug_red", "hearthholm:rug_blue") or n.endswith("_carpet")


def validate(name, g, seed):
    seen, q = {seed}, [seed]
    doors = set()
    while q:
        x, y, z = q.pop()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in seen:
                continue
            if not (0 <= n[0] < g.w and 0 <= n[1] < g.h and 0 <= n[2] < g.d):
                raise SystemExit(f"{name}: room leaks out of the box at {n}")
            if g.get(*n) is not None and passable(g.get(*n)):
                seen.add(n)
                q.append(n)
            elif "_door[" in g.get(*n) and "half=lower" in g.get(*n):
                doors.add(n)
        if all(passable(g.get(x, yy, z)) for yy in range(y + 1, g.h)):
            raise SystemExit(f"{name}: roofless at {(x, y, z)}")
    if not doors:
        raise SystemExit(f"{name}: room has no door")
    print(f"{name}: {g.w}x{g.h}x{g.d} enclosed, {len(seen)} room cells, {len(doors)} door(s)")


def dump(name, g):
    ch = {"cobblestone": "c", "oak_planks": "P", "spruce_planks": "R", "white_terracotta": "w", "oak_log": "L", "spruce_log": "l", "glass_pane": "G",
          "oak_door": "D", "oak_fence": "f", "oak_trapdoor": "t", "oak_stairs": "s", "spruce_stairs": "S", "stone_bricks": "b", "campfire": "C",
          "lantern": "!", "birch_planks": "B", "terracotta": "T", "stone_brick_stairs": "u", "cobblestone_stairs": "v"}
    print("==", name)
    for y in range(g.h):
        print(" layer", y)
        for z in range(g.d):
            print("  ", "".join("." if g.get(x, y, z) == AIR else ch.get(g.get(x, y, z).split("[")[0].split(":")[1],
                  "#" if g.get(x, y, z).split("[")[0].endswith("_slab") else "?") for x in range(g.w)))


OUT = {}


def done(name, g, seed=None):
    OUT[name] = g
    if seed:
        validate(name, g, seed)


# ================================================================ small_house 7x7, white plaster, spruce roof
def small_house():
    s = Style()
    g, *_ = build_house([(1, 1, 5, 5, "x")], 7, 7, s, 3, windows=[(1, 3), (3, 5)], chimney=(6, 3))
    g.bed(2, 3, "red")
    g.furn(2, 1, 2, hh("stool", facing="south"))
    g.furn(4, 1, 3, hh("round_table"))
    g.furn(4, 2, 3, small("candles", 2))
    g.furn(4, 1, 4, hh("chair", facing="north"))
    g.furn(3, 1, 3, hh("rug_red", facing="north"))
    g.furn(3, 2, 4, hh("wall_shelf", facing="north"))
    g.furn(3, 3, 3, LANTERN_DOWN)
    done("small_house", g, (3, 1, 3))


# ================================================================ farmhouse 11x9, planks, oak roof
def farmhouse():
    s = Style(wall="oak_planks", corner="spruce", beam="spruce", post="spruce", roof="oak", roof_block="oak_planks", floor="spruce_planks")
    g, *_ = build_house([(1, 1, 9, 7, "x")], 11, 9, s, 5, windows=[(2, 1), (8, 1), (9, 4), (3, 7), (7, 7)], chimney=(0, 4))
    g.bed(2, 5, "green")
    g.bed(3, 5, "green")
    g.furn(2, 1, 2, st("chest", facing="south"))
    g.furn(3, 1, 2, hh("crate"))
    g.furn(8, 1, 2, st("barrel", facing="up"))
    for x in (6, 7, 8):
        g.furn(x, 1, 4, hh("long_table", axis="x"))
    g.furn(6, 2, 4, small("mug", 2))
    g.furn(7, 2, 4, small("plate_bread"))
    g.furn(8, 2, 4, small("candles", 3))
    g.furn(6, 1, 3, hh("chair", facing="south"))
    g.furn(8, 1, 3, hh("stool", facing="south"))
    g.furn(7, 1, 5, hh("chair", facing="north"))
    g.furn(5, 1, 3, hh("rug_red", facing="north"))
    g.furn(5, 1, 4, hh("rug_red", facing="north"))
    g.furn(5, 1, 5, hh("rug_red", facing="north"))
    g.furn(5, 2, 6, hh("wall_shelf", facing="north"))
    g.furn(7, 2, 6, hh("hanging_herbs", facing="north"))
    g.furn(8, 1, 6, hh("firewood_pile", facing="north"))
    g.furn(8, 1, 5, hh("crate"))
    g.furn(5, 3, 3, LANTERN_DOWN)
    g.furn(7, 3, 5, LANTERN_DOWN)
    done("farmhouse", g, (5, 1, 3))


# ================================================================ cottage 9x7, birch + spruce frame
def cottage():
    s = Style(wall="birch_planks", corner="spruce", beam="spruce", roof="oak", roof_block="oak_planks")
    g, *_ = build_house([(1, 1, 7, 5, "x")], 9, 7, s, 4, windows=[(2, 1), (6, 1), (7, 3)], chimney=(0, 3))
    g.bed(2, 3, "blue")
    g.furn(3, 1, 4, hh("crate"))
    g.furn(2, 1, 2, hh("stool", facing="south"))
    g.furn(6, 1, 3, hh("round_table"))
    g.furn(6, 2, 3, small("mug", 1))
    g.furn(5, 1, 3, hh("chair", facing="east"))
    g.furn(6, 1, 4, hh("firewood_pile", facing="north"))
    g.furn(4, 1, 3, hh("rug_blue", facing="north"))
    g.furn(4, 2, 4, hh("wall_shelf", facing="north"))
    g.furn(3, 2, 4, hh("hanging_herbs", facing="north"))
    g.furn(4, 3, 3, LANTERN_DOWN)
    done("cottage", g, (4, 1, 3))


# ================================================================ workshop 9x7, planks + slate roof
def workshop():
    s = Style(wall="oak_planks", corner="oak", beam="spruce", post="spruce", roof="stone_brick", roof_block="stone_bricks")
    g, *_ = build_house([(1, 1, 7, 5, "x")], 9, 7, s, 4, windows=[(2, 1), (6, 1), (1, 3), (4, 5)], chimney=(8, 3))
    g.furn(5, 1, 4, hh("carpenter_bench", facing="north"))
    g.furn(6, 1, 4, st("chest", facing="north"))
    g.furn(2, 1, 4, hh("firewood_pile", facing="north"))
    g.furn(2, 1, 2, hh("crate"))
    g.furn(6, 1, 2, st("barrel", facing="up"))
    g.furn(3, 1, 3, hh("round_table"))
    g.furn(3, 2, 3, small("candlestick"))
    g.furn(2, 1, 3, hh("stool", facing="east"))
    g.furn(4, 1, 3, hh("rug_red", facing="north"))
    g.furn(3, 2, 4, hh("wall_shelf", facing="north"))
    g.furn(4, 3, 4, LANTERN_DOWN)
    g.furn(6, 3, 3, LANTERN_DOWN)
    # outdoor: log pile and crates by the west wall
    g.furn(0, 1, 2, hh("firewood_pile", facing="west"))
    g.furn(0, 1, 4, hh("crate"))
    done("workshop", g, (4, 1, 3))


# ================================================================ barracks 11x7, stone, grey roof
def barracks():
    s = Style(wall="stone_bricks", corner="spruce", beam="spruce", post=None, roof="cobblestone", roof_block="cobblestone", floor="spruce_planks")
    g, *_ = build_house([(1, 1, 9, 5, "x")], 11, 7, s, 5, windows=[(2, 1), (8, 1), (1, 3), (3, 5), (7, 5)], chimney=(10, 3))
    for x in (2, 4, 6, 8):
        g.bed(x, 3, "blue")
    g.furn(3, 1, 4, st("chest", facing="north"))
    g.furn(7, 1, 4, st("chest", facing="north"))
    g.furn(3, 1, 2, hh("weapon_rack", facing="north"))
    g.furn(7, 1, 2, hh("weapon_rack", facing="north"))
    g.furn(5, 1, 3, hh("rug_blue", facing="north"))
    g.furn(5, 1, 4, hh("rug_blue", facing="north"))
    g.furn(2, 1, 2, hh("stool", facing="south"))
    g.furn(8, 1, 2, hh("stool", facing="south"))
    g.furn(3, 3, 3, LANTERN_DOWN)
    g.furn(7, 3, 3, LANTERN_DOWN)
    done("barracks", g, (5, 1, 3))


# ================================================================ townhouse 7x9, two floors, gable to the street
def townhouse():
    s = Style(wall="terracotta", corner="oak", beam="spruce", post=None, roof="spruce", roof_block="spruce_planks", floor="oak_planks")
    wins = [(1, 3, (2,)), (5, 3, (2,)), (5, 5, (2,)), (1, 5, (2,)), (3, 7, (2,)), (2, 1, (6,)), (3, 1, (6,)), (4, 1, (6,)),
            (1, 3, (6,)), (5, 3, (6,)), (1, 5, (6,)), (5, 5, (6,)), (3, 7, (6,))]
    g, F, perim, inner = build_house([(1, 1, 5, 7, "z")], 7, 9, s, 3, yw=7, windows=wins, chimney=(0, 4), awning=True, lamp=True)
    # middle floor: band of beams around, plank floor with a hole for the ladder
    for (x, z) in perim:
        n = g.get(x, 4, z)
        if "_log" in n and "axis=y" not in n:
            continue
        if "_log" in n:
            continue
        g.set(x, 4, z, log("spruce", "x" if z in (1, 7) else "z"))
    for (x, z) in inner:
        if (x, z) != (4, 6):
            g.set(x, 4, z, st("oak_planks"))
    for y in range(1, 6):
        g.set(4, y, 6, st("ladder", facing="north"))
    g.set(4, 4, 5, AIR)        # open floor cell next to the ladder: the two floors are one room for the scanner
    g.set(3, 8, 1, PANE)       # attic window in the front gable
    g.set(3, 8, 7, PANE)
    # ground floor
    g.furn(2, 1, 2, st("chest", facing="south"))
    g.furn(3, 1, 3, hh("round_table"))
    g.furn(3, 2, 3, small("plate_roast"))
    g.furn(2, 1, 3, hh("chair", facing="east"))
    g.furn(4, 1, 3, hh("chair", facing="west"))
    g.furn(3, 1, 5, hh("rug_red", facing="north"))
    g.furn(2, 2, 6, hh("wall_shelf", facing="north"))
    g.furn(2, 1, 6, st("barrel", facing="up"))
    g.furn(3, 3, 4, LANTERN_DOWN)
    # upper floor
    g.bed(2, 5, "red", y=5)
    g.furn(3, 5, 6, hh("crate"))
    g.furn(2, 5, 2, hh("stool", facing="south"))
    g.furn(4, 5, 3, hh("round_table"))
    g.furn(4, 6, 3, small("candles", 2))
    g.furn(3, 5, 3, hh("chair", facing="east"))
    g.furn(3, 5, 4, hh("rug_blue", facing="north"))
    g.furn(3, 6, 6, hh("wall_shelf", facing="north"))
    g.furn(3, 7, 4, LANTERN_DOWN)
    done("townhouse", g, (3, 1, 3))


# ================================================================ fisher hut 7x7, dark planks, drying rack
def fisher_hut():
    s = Style(wall="spruce_planks", corner="spruce", beam="spruce", roof="oak", roof_block="oak_planks", floor="spruce_planks")
    g, *_ = build_house([(1, 1, 5, 5, "x")], 7, 7, s, 3, windows=[(1, 3), (5, 3), (3, 5)], chimney=None, lamp=False)
    g.bed(2, 3, "blue")
    g.furn(4, 1, 4, st("barrel", facing="up"))
    g.furn(4, 1, 3, hh("crate"))
    g.furn(4, 2, 3, small("plate_fish"))
    g.furn(2, 1, 2, hh("stool", facing="south"))
    g.furn(3, 1, 3, hh("rug_blue", facing="north"))
    g.furn(3, 2, 4, hh("wall_shelf", facing="north"))
    g.furn(3, 3, 3, LANTERN_DOWN)
    # outside: barrels and crates by the door, fish drying rack under the east eave, lantern post
    g.furn(1, 1, 0, st("barrel", facing="up"))
    g.furn(5, 1, 0, hh("crate"))
    for z in (2, 4):
        for y in (1, 2, 3):
            g.set(6, y, z, st("oak_fence"))
    g.set(6, 3, 3, st("oak_fence"))
    g.set(6, 2, 3, st("oak_trapdoor", facing="east", half="bottom", open=False)) if False else None
    g.set(6, 1, 3, hh("barrel_rack", facing="west"))
    g.set(0, 1, 0, hh("lamp_post", facing="north"))
    done("fisher_hut", g, (3, 1, 3))


# ================================================================ L-shaped house 11x13, cross gables
def l_house():
    s = Style(wall="white_terracotta", corner="oak", beam="oak", post="oak", roof="spruce", roof_block="spruce_planks")
    wings = [(1, 1, 9, 5, "x"), (1, 3, 5, 11, "z")]
    wins = [(2, 1), (8, 1), (1, 3), (1, 7), (5, 8), (3, 11), (9, 3)]
    g, *_ = build_house(wings, 11, 13, s, 5, windows=wins, chimney=(10, 4))
    # living area (wing A)
    for x in (6, 7, 8):
        g.furn(x, 1, 3, hh("long_table", axis="x"))
    g.furn(6, 2, 3, small("mug", 2))
    g.furn(7, 2, 3, small("plate_roast"))
    g.furn(8, 2, 3, small("candlestick"))
    g.furn(6, 1, 2, hh("chair", facing="south"))
    g.furn(8, 1, 2, hh("chair", facing="south"))
    g.furn(6, 1, 4, hh("chair", facing="north"))
    g.furn(8, 1, 4, hh("stool", facing="north"))
    g.furn(3, 1, 3, hh("rug_red", facing="north"))
    g.furn(4, 1, 3, hh("rug_red", facing="north"))
    g.furn(7, 2, 4, hh("wall_shelf", facing="north"))
    g.furn(8, 1, 2, st("barrel", facing="up")) if False else None
    g.furn(2, 1, 2, st("chest", facing="south"))
    g.furn(4, 3, 3, LANTERN_DOWN)
    g.furn(7, 3, 3, LANTERN_DOWN)
    # bedroom (wing B)
    g.bed(2, 9, "red")
    g.bed(4, 9, "green")
    g.furn(3, 1, 10, st("chest", facing="north"))
    g.furn(3, 2, 10, hh("wall_shelf", facing="north"))
    g.furn(3, 1, 7, hh("rug_blue", facing="north"))
    g.furn(3, 1, 8, hh("rug_blue", facing="north"))
    g.furn(2, 1, 6, hh("crate"))
    g.furn(4, 1, 6, hh("stool", facing="south"))
    g.furn(3, 3, 8, LANTERN_DOWN)
    done("l_house", g, (3, 1, 8))


# ================================================================ tavern 13x11, big hall + gallery
def tavern():
    s = Style(wall="white_terracotta", corner="spruce", beam="spruce", post="spruce", roof="oak", roof_block="oak_planks", floor="oak_planks")
    wins = [(2, 1), (10, 1), (1, 3), (1, 5), (11, 4), (11, 7), (3, 9), (9, 9)]
    g, *_ = build_house([(1, 1, 11, 9, "x")], 13, 11, s, 6, yw=5, windows=wins, chimney=(12, 5), ceiling=False, awning=False)
    # counter along the back wall with the keg in the middle, barrel racks at the ends
    for x in (3, 4, 5, 7, 8, 9):
        g.furn(x, 1, 8, hh("counter", facing="north"))
    g.furn(6, 1, 8, hh("tavern_keg", facing="north"))
    g.furn(3, 2, 8, small("bottles", 3))
    g.furn(4, 2, 8, small("mug", 2))
    g.furn(5, 2, 8, small("plate_bread"))
    g.furn(7, 2, 8, small("mug", 3))
    g.furn(8, 2, 8, small("candlestick"))
    g.furn(9, 2, 8, small("bottles", 2))
    g.furn(10, 1, 8, hh("barrel_rack", facing="north"))
    g.furn(10, 1, 7, hh("barrel_rack", facing="north"))
    for x in (4, 6, 8):
        g.furn(x, 3, 8, LANTERN_DOWN)
    # gallery: platform at layer 4 over the counter, ladder at the west wall, fence rail
    for z in (6, 7, 8):
        for x in range(2, 11):
            if (x, z) != (2, 7):
                g.set(x, 4, z, st("oak_planks"))
    for y in range(1, 6):
        g.set(2, y, 7, st("ladder", facing="east"))
    for x in range(2, 11):
        g.set(x, 5, 6, st("oak_fence"))
    g.set(10, 5, 7, st("oak_fence")) if False else None
    g.furn(5, 5, 8, hh("round_table"))
    g.furn(5, 6, 8, small("candles", 3))
    g.furn(4, 5, 8, hh("chair", facing="east"))
    g.furn(6, 5, 8, hh("chair", facing="west"))
    g.furn(8, 5, 8, hh("round_table"))
    g.furn(8, 6, 8, small("mug", 2))
    g.furn(7, 5, 8, hh("stool", facing="east"))
    g.furn(9, 5, 8, hh("stool", facing="west"))
    g.furn(3, 5, 7, hh("crate"))
    g.furn(10, 5, 8, hh("barrel_rack", facing="north"))
    # hall: long tables with chairs, rugs in the aisle
    for x in (3, 4, 5, 7, 8, 9):
        g.furn(x, 1, 4, hh("long_table", axis="x"))
    for x, seat in ((3, "chair"), (5, "chair"), (7, "chair"), (9, "chair"), (4, "stool"), (8, "stool")):
        g.furn(x, 1, 3, hh(seat, facing="south"))
        g.furn(x, 1, 5, hh(seat, facing="north"))
    g.furn(3, 2, 4, small("mug", 2))
    g.furn(4, 2, 4, small("plate_roast"))
    g.furn(5, 2, 4, small("bottles", 1, "east"))
    g.furn(7, 2, 4, small("plate_fish"))
    g.furn(8, 2, 4, small("candles", 2))
    g.furn(9, 2, 4, small("mug", 3))
    for z in (2, 3, 4, 5, 6):
        g.furn(6, 1, z, hh("rug_red", facing="north"))
    g.furn(2, 1, 2, hh("firewood_pile", facing="east"))
    g.furn(2, 1, 4, hh("crate"))
    g.furn(10, 1, 2, hh("crate"))
    g.furn(10, 1, 5, hh("barrel_rack", facing="west"))
    g.furn(2, 2, 3, hh("hanging_herbs", facing="east")) if False else None
    # tie beams across the hall with hanging lanterns
    for z in (3, 5):
        for x in range(2, 11):
            if g.get(x, 5, z) == AIR:
                g.set(x, 5, z, log("spruce", "x"))
    for x in (3, 6, 9):
        g.set(x, 4, 3, LANTERN_DOWN)
    for x in (4, 8):
        g.set(x, 4, 5, LANTERN_DOWN)
    done("tavern", g, (6, 1, 2))


# ================================================================ market stall 7x6, striped wool awning
def market_stall():
    g = Grid(7, 5, 6)
    g.fill(0, 0, 0, 6, 0, 5, st("gravel"))
    for x in range(1, 6):
        g.set(x, 0, 3, st("cobblestone"))
    # posts: front pair and back wall
    for x in (0, 6):
        for y in (1, 2, 3):
            g.set(x, y, 1, st("oak_fence"))
    g.fill(0, 1, 5, 6, 3, 5, st("spruce_planks"))
    for x in (0, 6):
        g.fill(x, 1, 5, x, 3, 5, log("oak"))
    # awning: red and white stripes, sloping down at the front
    for x in range(0, 7):
        wool = st("red_wool") if x % 2 == 0 else st("white_wool")
        for z in range(1, 6):
            g.set(x, 4, z, wool)
        g.set(x, 3, 0, wool) if False else None
    for x in range(0, 7):
        g.set(x, 3, 1, st("red_wool") if x % 2 == 0 else st("white_wool")) if x in (1, 2, 3, 4, 5) else None
    # counter row with the market block in the middle (kept at (3,1,3) for the caravan)
    for x in (1, 2, 4, 5):
        g.set(x, 1, 3, hh("counter", facing="north"))
    g.set(3, 1, 3, hh("market_stall", facing="north"))
    g.set(1, 2, 3, small("mug", 2))
    g.set(2, 2, 3, small("plate_bread"))
    g.set(4, 2, 3, small("bottles", 2))
    g.set(5, 2, 3, small("candlestick"))
    # stock behind the counter
    g.set(1, 1, 4, hh("crate"))
    g.set(2, 1, 4, hh("crate"))
    g.set(5, 1, 4, hh("barrel_rack", facing="north"))
    g.set(3, 2, 4, hh("hanging_herbs", facing="north"))
    g.set(2, 2, 4, hh("wall_shelf", facing="north"))
    g.set(4, 2, 4, hh("wall_shelf", facing="north"))
    g.set(3, 3, 2, LANTERN_DOWN)
    # front: lamp posts, bench and flower boxes
    g.set(0, 1, 0, hh("lamp_post", facing="north"))
    g.set(6, 1, 0, hh("lamp_post", facing="north"))
    g.set(1, 1, 1, hh("flower_box", facing="north"))
    g.set(5, 1, 1, hh("flower_box", facing="north"))
    g.set(2, 1, 2, hh("bench", facing="east")) if False else None
    done("market_stall", g)


# ================================================================ watchtower 7x7, stone shaft, deck and pyramid roof
def watchtower():
    g = Grid(7, 14, 7)
    g.fill(0, 0, 0, 6, 0, 6, st("cobblestone"))
    for z in range(1, 6):
        for x in range(1, 6):
            wall = x in (1, 5) or z in (1, 5)
            if not wall:
                continue
            corner = x in (1, 5) and z in (1, 5)
            for y in range(1, 5):
                g.set(x, y, z, log("oak") if corner else (st("cobblestone") if y <= 2 else st("stone_bricks")))
    g.fill(1, 1, 1, 5, 1, 1, st("cobblestone"))
    g.set(3, 1, 1, door("lower"))
    g.set(3, 2, 1, door("upper"))
    for (x, z) in ((1, 3), (5, 3)):
        g.set(x, 3, z, PANE)
    g.set(2, 1, 0, st("oak_fence")); g.set(4, 1, 0, st("oak_fence"))
    g.set(2, 2, 0, LANTERN_UP); g.set(4, 2, 0, LANTERN_UP)
    g.set(0, 1, 0, hh("lamp_post", facing="north"))
    # deck (layer 5) with a hole for the ladder
    for z in range(0, 7):
        for x in range(0, 7):
            if (x, z) != (3, 4):
                g.set(x, 5, z, st("oak_planks"))
    # a ring of stairs under the deck edge looks like corbels
    for y in range(1, 7):
        g.set(3, y, 4, st("ladder", facing="north"))
    g.set(2, 2, 3, st("wall_torch", facing="east"))
    for z in range(0, 7):
        for x in range(0, 7):
            if x in (0, 6) or z in (0, 6):
                g.set(x, 6, z, st("oak_fence"))
    for (x, z) in ((0, 0), (6, 0), (0, 6), (6, 6)):
        for y in range(6, 9):
            g.set(x, y, z, log("oak"))
    g.set(3, 6, 3, hh("alarm_bell", facing="north"))
    for (x, z) in ((1, 0), (5, 0), (1, 6), (5, 6)):
        g.set(x, 7, z, LANTERN_UP) if False else None
    # pyramid roof
    for k, y in enumerate((9, 10, 11)):
        for z in range(k, 7 - k):
            for x in range(k, 7 - k):
                if x in (k, 6 - k) or z in (k, 6 - k):
                    f = "south" if z == k else "north" if z == 6 - k else "east" if x == k else "west"
                    g.set(x, y, z, stairs("spruce", f))
                else:
                    g.set(x, y, z, st("spruce_planks"))
    g.set(3, 12, 3, slab("spruce"))
    done("watchtower", g)


for fn in (small_house, farmhouse, cottage, workshop, barracks, townhouse, fisher_hut, l_house, tavern, market_stall, watchtower):
    fn()

if len(sys.argv) > 2 and sys.argv[1] == "--dump":
    dump(sys.argv[2], OUT[sys.argv[2]])
    raise SystemExit
for n, g in OUT.items():
    g.save(n)
idx = os.path.join(D, "index.json")
data = json.load(open(idx))
for n in ("cottage", "townhouse", "fisher_hut", "l_house"):
    if n not in data["blueprints"]:
        data["blueprints"].append(n)
with open(idx, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=1)
    f.write("\n")
print("blueprints:", data["blueprints"])
