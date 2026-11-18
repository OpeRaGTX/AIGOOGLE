#!/usr/bin/env python3
"""Wave H2: pitched-roof, furnished blueprints. Run from the mod folder: python3 tools/gen_h2_blueprints.py
Rewrites small_house, farmhouse, workshop, tavern, market_stall, barracks, watchtower and adds cottage, townhouse,
fisher_hut, l_house (appended to index.json). Authoring frame as in gen_blueprints.py: x west->east, z from the front row
(row 0 = porch/garden row next to the stake) backwards, layer 0 = floor/foundation, layer 1 = stake level.
Also validates every house (flood fill like RoomScanner) and prints an ASCII dump with --dump."""
import json, os, sys

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "hearthholm", "blueprints")
AIR = "minecraft:air"


def st(name, **props):
    ns = name if ":" in name else "minecraft:" + name
    if not props:
        return ns
    return ns + "[" + ",".join(f"{k}={str(v).lower() if isinstance(v, bool) else v}" for k, v in props.items()) + "]"


OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
VEC = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


class Grid:
    def __init__(self, w, h, d):
        self.w, self.h, self.d = w, h, d
        self.c = [[[AIR] * w for _ in range(d)] for _ in range(h)]   # c[y][z][x]

    def set(self, x, y, z, s):
        if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d:
            self.c[y][z][x] = s
        else:
            raise IndexError((x, y, z, s))

    def get(self, x, y, z):
        return self.c[y][z][x]

    def keep(self, x, z):
        for y in range(self.h):
            self.c[y][z][x] = None

    def fill(self, x0, y0, z0, x1, y1, z1, s):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    self.set(x, y, z, s)

    def save(self, name):
        pool = "LPRcDdGTCKbBlpMfhtFgSsWwXxYyZzAaEeHhIiJjkmnoqruvVNOQU"
        pal, rev = {}, {}
        layers = []
        for y in range(self.h):
            rows = []
            for z in range(self.d):
                row = ""
                for x in range(self.w):
                    s = self.c[y][z][x]
                    if s is None:
                        row += "~"
                    elif s == AIR:
                        row += "."
                    else:
                        if s not in rev:
                            ch = pool[len(rev)]
                            rev[s] = ch
                            pal[ch] = s
                        row += rev[s]
                rows.append(row)
            layers.append(rows)
        with open(os.path.join(D, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"palette": pal, "layers": layers}, f, indent=1)
            f.write("\n")
        return pal


# ---------------------------------------------------------------- shared materials
def stairs(mat, facing, half="bottom"):
    return st(mat + "_stairs", facing=facing, half=half, shape="straight")


def slab(mat, typ="bottom"):
    return st(mat + "_slab", type=typ)


def log(wood, axis="y"):
    return st(wood + "_log", axis=axis)


def door(half, hinge="left"):
    return st("oak_door", facing="north", half=half, hinge=hinge, open=False)


def trap(facing, wood="oak"):
    return st(wood + "_trapdoor", facing=facing, half="bottom", open=True)


def hh(name, **p):
    return st("hearthholm:" + name, **p)


def small(name, count=1, facing="north", on_table=True):
    return hh(name, facing=facing, count=count, on_table=on_table)


def bed(color="red"):
    return st(color + "_bed", facing="south", part="foot"), st(color + "_bed", facing="south", part="head")


LANTERN_UP = st("lantern", hanging=False)
LANTERN_DOWN = st("lantern", hanging=True)
HLANTERN = hh("hanging_lantern", hanging=True)
CAMPFIRE = st("campfire", lit=True, signal_fire=False, facing="north")


# ---------------------------------------------------------------- the gable house machine
class Style:
    def __init__(self, plinth="cobblestone", floor="oak_planks", wall="white_terracotta", corner=("oak", "y"), beam="oak",
                 roof="spruce", roof_block="spruce_planks", post=None):
        self.__dict__.update(locals())


def wing_roof_y(wing, x, z, yw):
    """(y, kind, facing) of the roof block of one wing in column (x, z) or None. kind: stair | ridge."""
    x0, z0, x1, z1, axis = wing
    if not (x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1):
        return None
    if axis == "x":
        n = z1 - z0 + 3
        t = z - (z0 - 1)
    else:
        n = x1 - x0 + 3
        t = x - (x0 - 1)
    r = (n - 1) // 2
    i = min(t, n - 1 - t)
    if i < r:
        if axis == "x":
            f = "south" if t < r else "north"
        else:
            f = "east" if t < r else "west"
        return yw + i, "stair", f
    return yw + r - 1, "ridge", None


def roof_at(wings, x, z, yw):
    best = None
    for w in wings:
        h = wing_roof_y(w, x, z, yw)
        if h and (best is None or h[0] > best[0]):
            best = h
    return best


def build_house(name, wings, W, D, st_, yw=3, doors=None, windows=(), chimney=None, interior=None, ceiling=True,
                porch=True, floors=None, extra=None, shutters=True, flowerboxes=True, door_x=None, lamp=True):
    """wings: wall rects (x0,z0,x1,z1,axis). windows: list of (x,z) wall cells (layer 2, +layer 3 when tall)."""
    H = yw + max((w[3] - w[1] + 3) // 2 if w[4] == "x" else (w[2] - w[0] + 3) // 2 for w in wings) + 3
    g = Grid(W, H, D)
    F = set()
    for x0, z0, x1, z1, _ in wings:
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                F.add((x, z))

    def nb8(x, z):
        return [(x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if dx or dz]

    perim = {c for c in F if any(n not in F for n in nb8(*c))}
    interior = F - perim
    corner = {c for c in perim if sum(1 for n in [(c[0] + 1, c[1]), (c[0] - 1, c[1]), (c[0], c[1] + 1), (c[0], c[1] - 1)] if n in F) != 2
              or ((c[0] + 1, c[1]) in F) == ((c[0] - 1, c[1]) in F) and False}
    # corners: perimeter cells whose horizontal and vertical neighbours in F do not form a straight line
    def straight(c):
        e, w_, s, n = [(c[0] + 1, c[1]) in F, (c[0] - 1, c[1]) in F, (c[0], c[1] + 1) in F, (c[0], c[1] - 1) in F]
        return (e and w_ and not s and not n) or (s and n and not e and not w_) or (e and w_ and s and n)

    def outward(c):
        for d, (dx, dz) in VEC.items():
            if (c[0] + dx, c[1] + dz) not in F:
                return d
        return None

    # layer 0: stone foundation under walls, plank floor inside
    for c in F:
        g.set(c[0], 0, c[1], st(st_.plinth) if c in perim else st(st_.floor))
    # walls
    for c in perim:
        x, z = c
        out = outward(c)
        isc = (not straight(c)) or out is None
        for y in range(1, yw + 1):
            if isc:
                g.set(x, y, z, log(st_.corner[0], "y"))
            elif y == 1:
                g.set(x, y, z, st(st_.plinth))
            elif y == yw:
                g.set(x, y, z, log(st_.beam, "x" if out in ("north", "south") else "z"))
            else:
                g.set(x, y, z, st(st_.wall))
    # timber posts every 4th cell on long walls (plaster style) -- keep windows/doors free later
    if st_.post:
        for c in perim:
            x, z = c
            out = outward(c)
            if out and straight(c) and ((x + z) % 4 == 0):
                for y in range(2, yw):
                    g.set(x, y, z, log(st_.post, "y"))
    # ceiling
    if ceiling:
        for (x, z) in interior:
            g.set(x, yw + 1, z, st(st_.floor))
    # roof
    cols = set()
    for w in wings:
        for z in range(w[1] - 1, w[3] + 2):
            for x in range(w[0] - 1, w[2] + 2):
                cols.add((x, z))
    for (x, z) in cols:
        y, kind, f = roof_at(wings, x, z, yw)
        if kind == "stair":
            g.set(x, y, z, stairs(st_.roof, f))
        else:
            g.set(x, y, z, st(st_.roof_block))
            g.set(x, y + 1, z, slab(st_.roof))
    # gable fill on walls under the roof
    for c in perim:
        x, z = c
        ry = roof_at(wings, x, z, yw)[0]
        for y in range(yw + 1, ry):
            g.set(x, y, z, st(st_.wall))
    # door
    dx = door_x
    front = None
    for (x, z) in sorted(perim, key=lambda c: c[1]):
        if x == dx and outward((x, z)) == "north":
            front = (x, z)
            break
    fx, fz = front
    g.set(fx, 1, fz, door("lower"))
    g.set(fx, 2, fz, door("upper"))
    if porch and fz >= 1:
        pz = fz - 1
        for x in (fx - 1, fx, fx + 1):
            g.set(x, 0, pz, st("oak_planks"))
        for x in (fx - 1, fx + 1):
            g.set(x, 1, pz, st("oak_fence"))
            g.set(x, 2, pz, LANTERN_UP)
    # windows
    for (x, z) in windows:
        out = outward((x, z))
        tall = yw >= 5
        ys = [2, 3] if tall else [2]
        for y in ys:
            g.set(x, y, z, st("glass_pane"))
        dxv, dzv = VEC[out]
        ox, oz = x + dxv, z + dzv
        if out in ("north", "south"):
            side = [(x - 1, z), (x + 1, z)]
        else:
            side = [(x, z - 1), (x, z + 1)]
        if shutters:
            for (sx, sz) in side:
                px, pz_ = sx + dxv, sz + dzv
                if (sx, sz) in perim and 0 <= px < W and 0 <= pz_ < D and (px, pz_) not in F and g.get(sx, 2, sz) != st("glass_pane"):
                    for y in ys:
                        g.set(px, y, pz_, trap(out))
        if flowerboxes and 0 <= ox < W and 0 <= oz < D and (ox, oz) not in F:
            g.set(ox, 1, oz, hh("flower_box", facing=out))
    # chimney: (x, z) outside column on the gable end
    if chimney:
        cx, cz = chimney
        top = max(roof_at(wings, cx, cz, yw)[0] + 2, yw + 4)
        for y in range(1, top):
            g.set(cx, y, cz, st("cobblestone") if y < yw + 2 else st("stone_bricks"))
        g.set(cx, top, cz, CAMPFIRE)
    # lamp post at the front corner
    if lamp:
        g.set(0, 1, 0, hh("lamp_post", facing="north"))
    if interior:
        interior(g, interior_ctx(g, F, perim, interior_cells=interior_set(F, perim), yw=yw, fx=fx, fz=fz))
    if extra:
        extra(g)
    return g, F, perim, interior


class interior_ctx:
    def __init__(self, g, F, perim, interior_cells, yw, fx, fz):
        self.g, self.F, self.perim, self.cells, self.yw, self.fx, self.fz = g, F, perim, interior_cells, yw, fx, fz


def interior_set(F, perim):
    return F - perim


def put_all(g, items):
    for (x, y, z, s) in items:
        g.set(x, y, z, s)


def put_bed(g, x, z, color="red", y=1):
    f, h = bed(color)
    g.set(x, y, z, f)
    g.set(x, y, z + 1, h)


# ---------------------------------------------------------------- validator (RoomScanner-like)
def passable(s):
    if s is None:
        return False
    if s == AIR:
        return True
    n = s.split("[")[0]
    return n in ("minecraft:wall_torch", "hearthholm:rug_red", "hearthholm:rug_blue") or n.endswith("_carpet")


def validate(name, g, seed):
    """Flood from seed through passable cells; True when enclosed (not leaving the box, a block above everywhere)."""
    seen = {seed}
    q = [seed]
    doors = 0
    while q:
        x, y, z = q.pop()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in seen:
                continue
            if not (0 <= n[0] < g.w and 0 <= n[1] < g.h and 0 <= n[2] < g.d):
                return False, "leaks out of the box at %s" % (n,)
            if passable(g.get(*n)):
                seen.add(n)
                q.append(n)
        if all(g.get(x, yy, z) == AIR or passable(g.get(x, yy, z)) for yy in range(y + 1, g.h)):
            return False, "roofless at %s" % ((x, y, z),)
    return True, "%d cells" % len(seen)


def dump(name, g):
    print("==", name, g.w, g.h, g.d)
    pal = {}
    for y in range(g.h):
        print(" layer", y)
        for z in range(g.d):
            row = ""
            for x in range(g.w):
                s = g.get(x, y, z)
                if s is None:
                    row += "~"
                elif s == AIR:
                    row += "."
                else:
                    n = s.split("[")[0].split(":")[1]
                    ch = {"cobblestone": "c", "oak_planks": "P", "spruce_planks": "R", "white_terracotta": "w", "oak_log": "L", "spruce_log": "l",
                          "glass_pane": "G", "oak_door": "D", "oak_fence": "f", "oak_trapdoor": "t", "oak_stairs": "s", "spruce_stairs": "S",
                          "stone_bricks": "b", "campfire": "C", "lantern": "!", "birch_planks": "B", "terracotta": "T", "stone_brick_stairs": "s"}.get(n)
                    if ch is None:
                        ch = "#" if n.endswith("_slab") else "?"
                    row += ch
            print("  ", row)


ALL = {}


def register(name, g, seeds=None):
    ALL[name] = (g, seeds)


# ================================================================ small_house
def small_house():
    st_ = Style(wall="white_terracotta", corner=("oak", "y"), beam="oak", roof="spruce", roof_block="spruce_planks")
    wings = [(1, 1, 5, 5, "x")]

    def inter(g, c):
        put_bed(g, 2, 3, "red")
        g.set(2, 1, 2, hh("stool", facing="south"))
        g.set(4, 1, 3, hh("round_table"))
        g.set(4, 2, 3, small("candles", 2))
        g.set(4, 1, 4, hh("chair", facing="north"))
        g.set(3, 1, 3, hh("rug_red", facing="north"))
        g.set(3, 2, 4, hh("wall_shelf", facing="north"))
        g.set(3, 3, 3, LANTERN_DOWN)
        g.set(2, 3, 4, st("wall_torch", facing="east")) if False else None

    g, *_ = build_house("small_house", wings, 7, 7, st_, door_x=3, windows=[(1, 3), (5, 3)], chimney=(7 - 1, 3) if False else None,
                        interior=inter)
    # chimney on the east gable
    for y in range(1, 8):
        g.set(6, y, 3, st("cobblestone") if y < 5 else st("stone_bricks"))
    g.set(6, 8, 3, CAMPFIRE)
    # back window with shutters
    g.set(3, 2, 5, st("glass_pane"))
    g.set(2, 2, 6, trap("south")); g.set(4, 2, 6, trap("south"))
    g.set(3, 1, 6, hh("flower_box", facing="south"))
    # front windows
    g.set(2, 2, 1, st("glass_pane")); g.set(4, 2, 1, st("glass_pane"))
    g.set(1, 2, 0, trap("north")); g.set(5, 2, 0, trap("north"))
    g.set(2, 1, 0, hh("flower_box", facing="north")); g.set(4, 1, 0, hh("flower_box", facing="north"))
    register("small_house", g, [(3, 1, 2)])
    return g


if __name__ == "__main__":
    pass
