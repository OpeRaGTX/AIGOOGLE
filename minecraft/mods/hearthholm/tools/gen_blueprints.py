#!/usr/bin/env python3
"""Writes the wave E blueprints (tavern, market stall, farmhouse, wall section) and appends them to index.json.
Run from the mod folder: python3 tools/gen_blueprints.py. Authoring frame: x west-east, z from the front row (door) backwards, layers bottom-up;
a block facing 'north' looks towards the front."""
import json, os
D = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "hearthholm", "blueprints")
BASE = {"L": "minecraft:oak_log[axis=y]", "P": "minecraft:oak_planks", "R": "minecraft:spruce_planks", "c": "minecraft:cobblestone",
        "D": "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false]", "d": "minecraft:oak_door[facing=north,half=upper,hinge=left,open=false]",
        "G": "minecraft:glass_pane"}

def put(name, extra, layers):
    pal = {k: v for k, v in {**BASE, **extra}.items() if any(k in row for layer in layers for row in layer)}
    w = len(layers[0][0])
    for layer in layers:
        assert all(len(r) == w for r in layer), (name, layer)
    with open(os.path.join(D, name + ".json"), "w", encoding="utf-8") as f:
        json.dump({"palette": pal, "layers": layers}, f, indent=1)
        f.write("\n")

put("tavern", {
    "C": "hearthholm:counter[facing=north]", "K": "hearthholm:tavern_keg[facing=north]", "b": "hearthholm:bench[facing=east]",
    "B": "hearthholm:bench[facing=west]", "l": "minecraft:lantern[hanging=true]", "p": "hearthholm:lamp_post[facing=north]"}, [
    ["ccccccccc"] * 7,
    ["LPPPDPPPL", "P.......P", "Pb.....BP", "P.......P", "Pb.....BP", "P.CCKCC.P", "LPPPPPPPL"],
    ["LPPPdPPPL", "P.......P", "G.......G", "P.......P", "G.......G", "P.......P", "LPPGPGPPL"],
    ["LPPPPPPPL", "P.......P", "P.......P", "P...l...P", "P.......P", "P.......P", "LPPPPPPPL"],
    ["RRRRRRRRR"] * 7])

put("market_stall", {
    "g": "minecraft:gravel", "p": "hearthholm:lamp_post[facing=north]", "b": "hearthholm:bench[facing=east]", "B": "hearthholm:bench[facing=west]",
    "C": "hearthholm:counter[facing=north]", "M": "hearthholm:market_stall[facing=north]", "f": "hearthholm:flower_box[facing=north]"}, [
    ["ggggggg"] * 5,
    ["p.....p", ".......", ".b...B.", ".CCMCC.", "f.....f"]])

put("farmhouse", {
    "T": "minecraft:wall_torch[facing=south]", "f": "minecraft:green_bed[facing=south,part=foot]", "h": "minecraft:green_bed[facing=south,part=head]",
    "k": "minecraft:chest[facing=north]", "b": "minecraft:barrel[facing=up]", "H": "minecraft:hay_block[axis=y]", "e": "minecraft:white_carpet"}, [
    ["ccccccccc"] * 6,
    ["LPPPDPPPL", "P.......P", "Pf.e.e.fP", "Ph.e.e.hP", "Pb.....kP", "LPPPPPPPL"],
    ["LPPPdPPPL", "P.......P", "G.......G", "P.......P", "G.......G", "LPPGGGPPL"],
    ["LPPPPPPPL", "P...T...P", "P.......P", "P.......P", "P.......P", "LPPPPPPPL"],
    ["RRRRRRRRR"] * 6])

put("wall_section", {
    "s": "minecraft:stone_bricks", "D": "hearthholm:palisade_gate[facing=north,half=lower,hinge=left,open=false]",
    "d": "hearthholm:palisade_gate[facing=north,half=upper,hinge=left,open=false]"}, [
    ["ccccccccc"], ["ssssDssss"], ["ssssdssss"], ["sssssssss"], ["s.s.s.s.s"]])

idx = os.path.join(D, "index.json")
data = json.load(open(idx))
for n in ("tavern", "market_stall", "farmhouse", "wall_section"):
    if n not in data["blueprints"]:
        data["blueprints"].append(n)
with open(idx, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=1)
    f.write("\n")
print("blueprints:", data["blueprints"])
