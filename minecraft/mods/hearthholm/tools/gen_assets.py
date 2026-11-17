#!/usr/bin/env python3
"""Writes Hearthholm block models, blockstates, item definitions, loot tables and recipes.
Run from the mod folder: python3 tools/gen_assets.py"""
import json, os
R = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
NS = "hearthholm"

def w(rel, data):
    p = os.path.join(R, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")

def facing_states(model, rot):
    return {"variants": {f"facing={d}": ({"model": model, "y": rot[d]} if rot[d] else {"model": model}) for d in rot}}

STD = {"north": 0, "east": 90, "south": 180, "west": 270}
OPP = {"north": 180, "east": 270, "south": 0, "west": 90}
DISPLAY = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
           "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
           "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
           "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
           "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
           "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}

def item(name):
    w(f"assets/{NS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})

def loot(name):
    w(f"data/{NS}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
        "condition": {"type": "minecraft:survives_explosion"}, "rolls": 1,
        "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}], "random_sequence": f"{NS}:blocks/{name}"})

def face(tex, uv=None, cull=None):
    f = {"texture": tex}
    if uv: f["uv"] = uv
    if cull: f["cullface"] = cull
    return f

# town hall desk
w(f"assets/{NS}/models/block/town_hall_desk.json", {
    "parent": "block/block", "display": DISPLAY,
    "textures": {"particle": f"{NS}:block/town_hall_desk_side", "top": f"{NS}:block/town_hall_desk_top",
                 "side": f"{NS}:block/town_hall_desk_side", "front": f"{NS}:block/town_hall_desk_front"},
    "elements": [
        {"from": [0, 11, 0], "to": [16, 14, 16], "faces": {"up": face("#top"), "down": face("#side"),
            "north": face("#side", [0, 2, 16, 5]), "south": face("#side", [0, 2, 16, 5]), "east": face("#side", [0, 2, 16, 5]), "west": face("#side", [0, 2, 16, 5])}},
        {"from": [1, 0, 1], "to": [15, 11, 15], "faces": {"down": face("#side", cull="down"),
            "north": face("#front", [1, 5, 15, 16]), "south": face("#side", [1, 5, 15, 16]), "east": face("#side", [1, 5, 15, 16]), "west": face("#side", [1, 5, 15, 16])}}]})
w(f"assets/{NS}/blockstates/town_hall_desk.json", facing_states(f"{NS}:block/town_hall_desk", OPP))

# warehouse ledger
w(f"assets/{NS}/models/block/warehouse_ledger.json", {
    "parent": "block/block", "display": DISPLAY,
    "textures": {"particle": f"{NS}:block/warehouse_ledger_side", "top": f"{NS}:block/warehouse_ledger_top", "side": f"{NS}:block/warehouse_ledger_side"},
    "elements": [{"from": [1, 0, 1], "to": [15, 12, 15], "faces": {"up": face("#top"), "down": face("#side", cull="down"),
        "north": face("#side", [1, 4, 15, 16]), "south": face("#side", [1, 4, 15, 16]), "east": face("#side", [1, 4, 15, 16]), "west": face("#side", [1, 4, 15, 16])}}]})
w(f"assets/{NS}/blockstates/warehouse_ledger.json", facing_states(f"{NS}:block/warehouse_ledger", STD))

# marker template: post + two-sided sign board
w(f"assets/{NS}/models/block/marker_template.json", {
    "parent": "block/block", "display": DISPLAY,
    "textures": {"particle": "#post"},
    "elements": [
        {"from": [7, 0, 7], "to": [9, 16, 9], "faces": {d: face("#post", [7, 0, 9, 16]) for d in ("north", "south", "east", "west")} | {"up": face("#post", [7, 7, 9, 9]), "down": face("#post", [7, 7, 9, 9], "down")}},
        {"from": [1, 6, 7.5], "to": [15, 15, 8.5], "faces": {"north": face("#sign", [0, 2, 16, 14]), "south": face("#sign", [0, 2, 16, 14]),
            "east": face("#post", [0, 0, 1, 9]), "west": face("#post", [0, 0, 1, 9]), "up": face("#post", [0, 0, 16, 1]), "down": face("#post", [0, 0, 16, 1])}}]})
MARKERS = {"construction_stake": "construction", "mine_marker": "mine", "forest_marker": "forest", "farm_marker": "farm",
           "fishing_marker": "fishing", "patrol_post": "patrol", "territory_banner": "territory"}
for name, sign in MARKERS.items():
    w(f"assets/{NS}/models/block/{name}.json", {"parent": f"{NS}:block/marker_template",
        "textures": {"post": f"{NS}:block/marker_post", "sign": f"{NS}:block/{sign}_sign"}})
    w(f"assets/{NS}/blockstates/{name}.json", facing_states(f"{NS}:block/{name}", STD))

# ---- workshops (wave A)
def cube(fr, to, tex, faces=("north", "south", "east", "west", "up", "down"), uvs=None):
    f = {}
    for d in faces:
        t = tex[d] if isinstance(tex, dict) else tex
        f[d] = face(t, (uvs or {}).get(d))
    return {"from": fr, "to": to, "faces": f}

def side_uv(fr, to, d):
    x0, y0, z0 = fr; x1, y1, z1 = to
    v0, v1 = (16 - y1, 16 - y0) if y1 <= 16 else (0, y1 - y0)
    if d in ("north", "south"): return [x0, v0, x1, v1]
    if d in ("east", "west"): return [z0, v0, z1, v1]
    return [x0, z0, x1, z1]

def box_el(fr, to, tex):
    return {"from": fr, "to": to, "faces": {d: face(tex[d] if isinstance(tex, dict) else tex, side_uv(fr, to, d))
                                            for d in ("north", "south", "east", "west", "up", "down")}}

def block_model(name, textures, elements):
    w(f"assets/{NS}/models/block/{name}.json", {"parent": "block/block", "display": DISPLAY, "textures": textures, "elements": elements})
    w(f"assets/{NS}/blockstates/{name}.json", facing_states(f"{NS}:block/{name}", STD))

legs = [box_el([x, 0, z], [x + 2, 11, z + 2], "#side") for x in (1, 13) for z in (1, 13)]
block_model("carpenter_bench", {"particle": f"{NS}:block/carpenter_bench_side", "top": f"{NS}:block/carpenter_bench_top",
                                "side": f"{NS}:block/carpenter_bench_side", "front": f"{NS}:block/carpenter_bench_front"},
            [box_el([0, 11, 0], [16, 14, 16], {"up": "#top", "down": "#side", "north": "#front", "south": "#side", "east": "#side", "west": "#side"}),
             *legs, box_el([2, 3, 2], [14, 4, 14], "#side")])
block_model("mason_table", {"particle": f"{NS}:block/mason_table_side", "top": f"{NS}:block/mason_table_top", "side": f"{NS}:block/mason_table_side"},
            [box_el([1, 0, 1], [15, 10, 15], "#side"), box_el([0, 10, 0], [16, 14, 16], {"up": "#top", "down": "#side", "north": "#side", "south": "#side", "east": "#side", "west": "#side"})])
block_model("smith_anvil", {"particle": f"{NS}:block/smith_anvil_side", "top": f"{NS}:block/smith_anvil_top", "side": f"{NS}:block/smith_anvil_side",
                            "stump": f"{NS}:block/smith_anvil_stump"},
            [box_el([2, 0, 2], [14, 5, 14], "#stump"), box_el([4, 5, 4], [12, 7, 12], "#side"), box_el([6, 7, 5], [10, 9, 11], "#side"),
             box_el([1, 9, 4], [15, 12, 12], {"up": "#top", "down": "#side", "north": "#side", "south": "#side", "east": "#side", "west": "#side"})])
block_model("cook_stove", {"particle": f"{NS}:block/cook_stove_side", "top": f"{NS}:block/cook_stove_top", "side": f"{NS}:block/cook_stove_side",
                           "front": f"{NS}:block/cook_stove_front", "pot": f"{NS}:block/cook_stove_pot"},
            [box_el([0, 0, 0], [16, 16, 16], {"up": "#top", "down": "#side", "north": "#front", "south": "#side", "east": "#side", "west": "#side"}),
             box_el([4, 16, 4], [11, 20, 11], "#pot"), box_el([12, 16, 11], [15, 24, 14], "#side")])
block_model("order_board", {"particle": f"{NS}:block/marker_post", "post": f"{NS}:block/marker_post", "board": f"{NS}:block/order_board_front",
                            "back": f"{NS}:block/order_board_back"},
            [box_el([1, 0, 7], [3, 16, 9], "#post"), box_el([13, 0, 7], [15, 16, 9], "#post"),
             {"from": [0, 5, 7.5], "to": [16, 16, 8.5], "faces": {"north": face("#board", [0, 0, 16, 11]), "south": face("#back", [0, 0, 16, 11]),
                 "east": face("#post", [0, 0, 1, 11]), "west": face("#post", [0, 0, 1, 11]), "up": face("#post", [0, 0, 16, 1]), "down": face("#post", [0, 0, 16, 1])}}])
WORKSHOPS = ["carpenter_bench", "mason_table", "smith_anvil", "cook_stove", "order_board"]

# hearth mark (plain item)
w(f"assets/{NS}/models/item/hearth_mark.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/hearth_mark"}})
w(f"assets/{NS}/items/hearth_mark.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/hearth_mark"}})

for name in ["town_hall_desk", "warehouse_ledger", *MARKERS, *WORKSHOPS]:
    item(name)
    loot(name)

# recipes
def shaped(name, pattern, key, count=1):
    w(f"data/{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "key": key,
        "pattern": pattern, "result": {"count": count, "id": f"{NS}:{name}"}})

def shapeless(name, ingredients, count=1):
    w(f"data/{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ingredients, "result": {"count": count, "id": f"{NS}:{name}"}})

shaped("town_hall_desk", ["PRP", "LCL", "L L"], {"P": "#minecraft:planks", "R": "minecraft:paper", "L": "#minecraft:logs", "C": "minecraft:crafting_table"})
shapeless("warehouse_ledger", ["minecraft:chest", "minecraft:paper", "minecraft:stick"])
for name, extra in {"mine_marker": "minecraft:cobblestone", "forest_marker": "#minecraft:saplings", "farm_marker": "minecraft:wheat_seeds",
                    "fishing_marker": "minecraft:string", "patrol_post": "minecraft:iron_ingot", "territory_banner": "#minecraft:wool",
                    "construction_stake": "minecraft:paper"}.items():
    shapeless(name, ["minecraft:stick", "minecraft:stick", extra])
shapeless("carpenter_bench", ["minecraft:crafting_table", "#minecraft:logs", "minecraft:stick"])
shapeless("mason_table", ["minecraft:crafting_table", "minecraft:cobblestone", "minecraft:cobblestone", "minecraft:stone"])
shaped("smith_anvil", ["III", " C ", "CCC"], {"I": "minecraft:iron_ingot", "C": "minecraft:cobblestone"})
shapeless("cook_stove", ["minecraft:furnace", "minecraft:bowl", "minecraft:cobblestone"])
shapeless("order_board", ["#minecraft:planks", "#minecraft:planks", "minecraft:paper", "minecraft:paper", "minecraft:stick"])

# sounds
w(f"assets/{NS}/sounds.json", {f"workshop.{n}": {"sounds": [f"{NS}:workshop/{n}{i}" for i in (1, 2)], "subtitle": f"subtitles.{NS}.workshop.{n}"}
                               for n in ("saw", "chisel", "hammer", "sizzle")})
print("assets written")
