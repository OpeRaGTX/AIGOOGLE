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


# ---- wave C: defence and threats
import zipfile as _zf, glob as _glob
def simple_state(name, model=None):
    w(f"assets/{NS}/blockstates/{name}.json", {"variants": {"": {"model": model or f"{NS}:block/{name}"}}})

def gen_item(name, tex=None):
    w(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{tex or name}"}})
    w(f"assets/{NS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})

# palisade: lashed vertical logs with four sharpened tips on top
tips = [box_el([x, 16, z], [x + 3, 19, z + 3], "#side") for x in (2, 11) for z in (2, 11)]
tips += [box_el([x + 1, 19, z + 1], [x + 2, 21, z + 2], "#side") for x in (2, 11) for z in (2, 11)]
w(f"assets/{NS}/models/block/palisade.json", {"parent": "block/block", "display": DISPLAY,
    "textures": {"particle": f"{NS}:block/palisade_side", "side": f"{NS}:block/palisade_side", "top": f"{NS}:block/palisade_top"},
    "elements": [cube([0, 0, 0], [16, 16, 16], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#top"})] + tips})
simple_state("palisade")
item("palisade"); loot("palisade")

# palisade gate: a door with our textures
for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left", "top_left_open", "top_right", "top_right_open"):
    w(f"assets/{NS}/models/block/palisade_gate_{part}.json", {"parent": f"minecraft:block/door_{part}",
        "textures": {"bottom": f"{NS}:block/palisade_gate_bottom", "top": f"{NS}:block/palisade_gate_top"}})
_jar = _glob.glob(os.path.join(os.path.dirname(__file__), "..", ".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-*/26.3/*.jar"))
_oak = json.loads(_zf.ZipFile(_jar[0]).read("assets/minecraft/blockstates/oak_door.json"))
for v in _oak["variants"].values():
    v["model"] = v["model"].replace("minecraft:block/oak_door_", f"{NS}:block/palisade_gate_")
w(f"assets/{NS}/blockstates/palisade_gate.json", _oak)
gen_item("palisade_gate")
w(f"data/{NS}/loot_table/blocks/palisade_gate.json", {"type": "minecraft:block", "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "rolls": 1,
    "entries": [{"type": "minecraft:item", "name": f"{NS}:palisade_gate", "condition": {"type": "minecraft:match_block", "blocks": f"{NS}:palisade_gate",
        "state": {"half": "lower"}}}]}], "random_sequence": f"{NS}:blocks/palisade_gate"})

# alarm bell: two posts, a cross beam and a hanging bell
block_model("alarm_bell", {"particle": f"{NS}:block/alarm_bell_wood", "wood": f"{NS}:block/alarm_bell_wood", "bell": f"{NS}:block/alarm_bell_bell"},
            [box_el([2, 0, 7], [4, 16, 9], "#wood"), box_el([12, 0, 7], [14, 16, 9], "#wood"), box_el([2, 13, 6.5], [14, 15, 9.5], "#wood"),
             box_el([5, 5, 5], [11, 12, 11], "#bell"), box_el([4, 4, 4], [12, 5, 12], "#bell"), box_el([7, 12, 7], [9, 13, 9], "#wood"),
             box_el([0, 0, 6], [16, 1, 10], "#wood")])
item("alarm_bell"); loot("alarm_bell")

# rift core (animated, glowing)
w(f"assets/{NS}/models/block/rift_core.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/rift_core"}})
simple_state("rift_core")
item("rift_core")
with open(os.path.join(R, f"assets/{NS}/textures/block/rift_core.png.mcmeta"), "w") as fh:
    json.dump({"animation": {"frametime": 3, "interpolate": True}}, fh)

# rift lantern
w(f"assets/{NS}/models/block/rift_lantern.json", {"parent": "block/block", "display": DISPLAY, "textures": {"particle": f"{NS}:block/rift_lantern", "l": f"{NS}:block/rift_lantern"},
    "elements": [{"from": [4, 0, 4], "to": [12, 8, 12], "faces": {d: face("#l", [0, 0, 8, 8]) for d in ("north", "south", "east", "west")} | {"up": face("#l", [0, 9, 8, 15]), "down": face("#l", [0, 9, 8, 15])}},
                 {"from": [5, 8, 5], "to": [11, 10, 11], "faces": {d: face("#l", [8, 0, 14, 2]) for d in ("north", "south", "east", "west", "up", "down")}},
                 {"from": [7, 10, 7], "to": [9, 12, 9], "faces": {d: face("#l", [8, 8, 10, 10]) for d in ("north", "south", "east", "west", "up", "down")}}]})
simple_state("rift_lantern")
item("rift_lantern"); loot("rift_lantern")

# chieftain's banner: pole, cross bar, cloth
block_model("chieftain_banner", {"particle": f"{NS}:block/chieftain_banner", "cloth": f"{NS}:block/chieftain_banner", "pole": f"{NS}:block/alarm_bell_wood"},
            [box_el([7, 0, 7], [9, 16, 9], "#pole"), box_el([2, 15, 7], [14, 16, 9], "#pole"),
             {"from": [3, 2, 7.5], "to": [13, 15, 8.5], "faces": {"north": face("#cloth", [2, 0, 14, 13]), "south": face("#cloth", [2, 0, 14, 13]),
                 "east": face("#cloth", [0, 0, 1, 13]), "west": face("#cloth", [0, 0, 1, 13]), "up": face("#cloth", [0, 0, 16, 1]), "down": face("#cloth", [0, 0, 16, 1])}},
             box_el([5, 0, 5], [11, 1, 11], "#pole")])
item("chieftain_banner"); loot("chieftain_banner")

for name in ("rift_shard", "founders_whistle", "founders_chronicle"):
    gen_item(name)
for k in ("ash_raider", "ash_arsonist", "ash_archer", "ash_brute", "ash_chieftain", "rift_wisp"):
    gen_item(f"{k}_spawn_egg")

shapeless("founders_chronicle", ["minecraft:book", f"{NS}:hearth_mark"])
shaped("palisade", ["LSL", "LSL"], {"L": "#minecraft:logs", "S": "minecraft:stick"}, 6)
shaped("palisade_gate", ["PP", "PI", "PP"], {"P": f"{NS}:palisade", "I": "minecraft:iron_ingot"})
shaped("alarm_bell", ["SSS", "SGS", "S S"], {"S": "minecraft:stick", "G": "minecraft:gold_ingot"})
shaped("rift_lantern", [" R ", "RLR", " R "], {"R": f"{NS}:rift_shard", "L": "minecraft:lantern"})

# sounds
w(f"assets/{NS}/sounds.json", {f"workshop.{n}": {"sounds": [f"{NS}:workshop/{n}{i}" for i in (1, 2)], "subtitle": f"subtitles.{NS}.workshop.{n}"}
                               for n in ("saw", "chisel", "hammer", "sizzle")} | {
    "social.bell": {"sounds": [f"{NS}:social/bell1"], "subtitle": f"subtitles.{NS}.social.bell"},
    "social.murmur": {"sounds": [f"{NS}:social/murmur{i}" for i in (1, 2, 3, 4)], "subtitle": f"subtitles.{NS}.social.murmur"},
    "social.festival": {"sounds": [{"name": f"{NS}:social/festival", "stream": True}], "subtitle": f"subtitles.{NS}.social.festival"}} | {
    f"threat.{n}": {"sounds": [f"{NS}:threat/{n}"], "subtitle": f"subtitles.{NS}.threat.{n}"} for n in ("raid_horn", "alarm_bell", "rift_hum", "boss_roar")})

# ---- wave D: party and military
legs_d = [box_el([x, 0, z], [x + 2, 11, z + 2], "#side") for x in (1, 13) for z in (1, 13)]
block_model("herbalist_table", {"particle": f"{NS}:block/herbalist_table_side", "top": f"{NS}:block/herbalist_table_top",
                                "side": f"{NS}:block/herbalist_table_side", "front": f"{NS}:block/herbalist_table_front"},
            [box_el([0, 11, 0], [16, 14, 16], {"up": "#top", "down": "#side", "north": "#front", "south": "#side", "east": "#side", "west": "#side"}),
             *legs_d, box_el([2, 4, 2], [14, 5, 14], "#side"),
             box_el([2, 14, 2], [6, 17, 6], "#top"), box_el([10, 14, 9], [13, 16, 12], "#top")])
block_model("weapon_rack", {"particle": f"{NS}:block/weapon_rack_side", "front": f"{NS}:block/weapon_rack_front",
                            "side": f"{NS}:block/weapon_rack_side", "bar": f"{NS}:block/weapon_rack_bar"},
            [box_el([0, 0, 12], [16, 16, 16], {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#side", "down": "#side"}),
             box_el([0, 0, 6], [2, 2, 16], "#bar"), box_el([14, 0, 6], [16, 2, 16], "#bar"),
             box_el([1, 4, 10], [15, 6, 12], "#bar"), box_el([1, 11, 10], [15, 13, 12], "#bar")])
for name in ("herbalist_table", "weapon_rack"):
    item(name)
    loot(name)
gen_item("war_horn")
gen_item("healing_salve")
shaped("weapon_rack", ["SIS", "PPP"], {"S": "minecraft:stick", "I": "minecraft:iron_ingot", "P": "#minecraft:planks"})
shapeless("herbalist_table", ["minecraft:crafting_table", "minecraft:sweet_berries", "minecraft:glass_bottle"])
shaped("war_horn", ["II ", "IGI", " IL"], {"I": "minecraft:iron_ingot", "G": "minecraft:gold_ingot", "L": "minecraft:leather"})
shapeless("healing_salve", ["minecraft:glass_bottle", "minecraft:sweet_berries", "minecraft:sweet_berries"])
_snd = json.load(open(os.path.join(R, f"assets/{NS}/sounds.json"), encoding="utf-8"))
_snd["party.horn"] = {"sounds": [f"{NS}:party/horn"], "subtitle": f"subtitles.{NS}.party.horn"}
_snd["workshop.chop"] = {"sounds": [f"{NS}:workshop/chop{i}" for i in (1, 2)], "subtitle": f"subtitles.{NS}.workshop.chop"}
_snd["workshop.pick"] = {"sounds": [f"{NS}:workshop/pick{i}" for i in (1, 2)], "subtitle": f"subtitles.{NS}.workshop.pick"}
_snd["social.level_up"] = {"sounds": [f"{NS}:social/level_up"], "subtitle": f"subtitles.{NS}.social.level_up"}
_snd["town.caravan_horn"] = {"sounds": [f"{NS}:town/caravan_horn"], "subtitle": f"subtitles.{NS}.town.caravan_horn"}
_snd["town.quest_done"] = {"sounds": [f"{NS}:town/quest_done"], "subtitle": f"subtitles.{NS}.town.quest_done"}
w(f"assets/{NS}/sounds.json", _snd)

# ---- wave E: square and market
T = lambda n: f"{NS}:block/{n}"
ALLF = ("north", "south", "east", "west", "up", "down")
block_model("tavern_keg", {"particle": T("tavern_keg_side"), "top": T("tavern_keg_top"), "side": T("tavern_keg_side"), "front": T("tavern_keg_front")},
            [box_el([1, 0, 1], [15, 14, 15], {"up": "#top", "down": "#top", "north": "#front", "south": "#side", "east": "#side", "west": "#side"}),
             box_el([6, 5, 0], [10, 8, 1], "#top")])
block_model("lamp_post", {"particle": T("lamp_post_metal"), "metal": T("lamp_post_metal"), "glass": T("lamp_post_glass")},
            [box_el([5, 0, 5], [11, 2, 11], "#metal"), box_el([7, 2, 7], [9, 11, 9], "#metal"), box_el([4, 11, 4], [12, 12, 12], "#metal"),
             box_el([5, 12, 5], [11, 15, 11], "#glass"), box_el([4, 15, 4], [12, 16, 12], "#metal")])
block_model("signpost", {"particle": T("signpost_wood"), "wood": T("signpost_wood"), "board": T("signpost_board")},
            [box_el([7, 0, 7], [9, 16, 9], "#wood"),
             {"from": [1, 9, 8.5], "to": [15, 14, 9.5], "faces": {"north": face("#board", [1, 2, 15, 7]), "south": face("#board", [1, 2, 15, 7]),
                 "east": face("#wood", [0, 0, 1, 5]), "west": face("#wood", [0, 0, 1, 5]), "up": face("#wood", [0, 0, 14, 1]), "down": face("#wood", [0, 0, 14, 1])}},
             {"from": [3, 3, 6.5], "to": [13, 7, 7.5], "faces": {"north": face("#board", [3, 8, 13, 12]), "south": face("#board", [3, 8, 13, 12]),
                 "east": face("#wood", [0, 0, 1, 4]), "west": face("#wood", [0, 0, 1, 4]), "up": face("#wood", [0, 0, 10, 1]), "down": face("#wood", [0, 0, 10, 1])}}])
block_model("bench", {"particle": T("bench_wood"), "wood": T("bench_wood")},
            [box_el([1, 6, 4], [15, 8, 12], "#wood"), box_el([1, 8, 11], [15, 14, 12], "#wood"),
             *[box_el([x, 0, z], [x + 2, 6, z + 2], "#wood") for x in (2, 12) for z in (4, 10)]])
block_model("well", {"particle": T("well_stone"), "stone": T("well_stone"), "water": T("well_water"), "roof": T("well_roof")},
            [box_el([0, 0, 0], [16, 7, 3], "#stone"), box_el([0, 0, 13], [16, 7, 16], "#stone"),
             box_el([0, 0, 3], [3, 7, 13], "#stone"), box_el([13, 0, 3], [16, 7, 13], "#stone"),
             box_el([3, 0, 3], [13, 5, 13], "#water"),
             *[box_el([x, 7, z], [x + 2, 14, z + 2], "#roof") for x in (0, 14) for z in (0, 14)],
             box_el([0, 14, 0], [16, 16, 16], "#roof")])
block_model("counter", {"particle": T("counter_side"), "side": T("counter_side"), "top": T("counter_top"), "front": T("counter_front")},
            [box_el([0, 0, 0], [16, 12, 16], {"up": "#side", "down": "#side", "north": "#front", "south": "#side", "east": "#side", "west": "#side"}),
             box_el([0, 12, 0], [16, 14, 16], {"up": "#top", "down": "#side", "north": "#side", "south": "#side", "east": "#side", "west": "#side"})])
block_model("flower_box", {"particle": T("flower_box_wood"), "wood": T("flower_box_wood"), "top": T("flower_box_top")},
            [box_el([0, 0, 3], [16, 7, 13], "#wood"), box_el([1, 7, 4], [15, 9, 12], {"up": "#top", "down": "#wood", "north": "#top", "south": "#top", "east": "#top", "west": "#top"})])
for kind in ("carpenter", "mason", "smith", "cook", "tavern"):
    block_model(f"sign_{kind}", {"particle": T(f"sign_{kind}"), "front": T(f"sign_{kind}"), "back": T("sign_back")},
                [{"from": [1, 2, 7], "to": [15, 14, 9], "faces": {"north": face("#front", [0, 2, 16, 14]), "south": face("#back", [0, 2, 16, 14]),
                    "east": face("#back", [0, 2, 2, 14]), "west": face("#back", [0, 2, 2, 14]), "up": face("#back", [0, 0, 16, 2]), "down": face("#back", [0, 0, 16, 2])}},
                 box_el([0, 14, 7], [16, 16, 9], "#back")])
block_model("bulletin_board", {"particle": T("marker_post"), "post": T("marker_post"), "board": T("bulletin_board_front"), "back": T("order_board_back")},
            [box_el([1, 0, 7], [3, 16, 9], "#post"), box_el([13, 0, 7], [15, 16, 9], "#post"),
             {"from": [0, 3, 7.5], "to": [16, 16, 8.5], "faces": {"north": face("#board", [0, 0, 16, 13]), "south": face("#back", [0, 0, 16, 13]),
                 "east": face("#post", [0, 0, 1, 13]), "west": face("#post", [0, 0, 1, 13]), "up": face("#post", [0, 0, 16, 1]), "down": face("#post", [0, 0, 16, 1])}}])
block_model("market_stall", {"particle": T("market_stall_wood"), "wood": T("market_stall_wood"), "awning": T("market_stall_awning"), "crate": T("market_stall_crate")},
            [box_el([0, 0, 4], [16, 10, 12], "#wood"), box_el([0, 10, 3], [16, 11, 13], "#crate"),
             box_el([0, 0, 12], [2, 16, 14], "#wood"), box_el([14, 0, 12], [16, 16, 14], "#wood"),
             box_el([0, 14, 2], [16, 16, 14], {"up": "#awning", "down": "#awning", "north": "#awning", "south": "#awning", "east": "#awning", "west": "#awning"}),
             box_el([2, 11, 5], [7, 14, 10], "#crate"), box_el([9, 11, 6], [13, 13, 10], "#crate")])
# banner: pole, cross bar and a cloth model per colour and emblem
DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
w(f"assets/{NS}/models/block/settlement_banner_template.json", {"parent": "block/block", "display": DISPLAY, "textures": {"particle": T("marker_post")},
    "elements": [box_el([7, 0, 7], [9, 16, 9], "#pole"), box_el([2, 14, 7], [14, 16, 9], "#pole"), box_el([5, 0, 5], [11, 1, 11], "#pole"),
        {"from": [3, 1, 7.5], "to": [13, 14, 8.5], "faces": {"north": face("#cloth", [0, 0, 16, 16]), "south": face("#cloth", [0, 0, 16, 16]),
            "east": face("#cloth", [0, 0, 1, 16]), "west": face("#cloth", [0, 0, 1, 16]), "up": face("#cloth", [0, 0, 16, 1]), "down": face("#cloth", [0, 0, 16, 1])}}]})
variants = {}
for dye in DYES:
    for e in range(4):
        w(f"assets/{NS}/models/block/settlement_banner_{dye}_{e}.json", {"parent": f"{NS}:block/settlement_banner_template",
            "textures": {"pole": T("marker_post"), "cloth": T(f"banner_{dye}_{e}")}})
        for d, rot in STD.items():
            v = {"model": f"{NS}:block/settlement_banner_{dye}_{e}"}
            if rot: v["y"] = rot
            variants[f"cloth={dye},emblem={e},facing={d}"] = v
w(f"assets/{NS}/blockstates/settlement_banner.json", {"variants": variants})
w(f"assets/{NS}/items/settlement_banner.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/settlement_banner_red_0"}})
for name in ("tavern_keg", "lamp_post", "signpost", "bench", "well", "counter", "flower_box", "bulletin_board", "market_stall",
             "sign_carpenter", "sign_mason", "sign_smith", "sign_cook", "sign_tavern"):
    item(name)
    loot(name)
loot("settlement_banner")
shapeless("tavern_keg", ["minecraft:barrel", "minecraft:bowl"])
shaped("lamp_post", ["L", "I", "I"], {"L": "minecraft:lantern", "I": "minecraft:iron_ingot"})
shaped("signpost", ["PPP", " S ", " S "], {"P": "#minecraft:planks", "S": "minecraft:stick"}, 2)
shaped("bench", ["PPP", "S S"], {"P": "#minecraft:planks", "S": "minecraft:stick"})
shaped("well", ["C C", "CBC", "CCC"], {"C": "minecraft:cobblestone", "B": "minecraft:water_bucket"})
shaped("counter", ["SSS", "PPP"], {"S": "#minecraft:wooden_slabs", "P": "#minecraft:planks"})
shaped("flower_box", ["DDD", "PPP"], {"D": "#minecraft:small_flowers", "P": "#minecraft:planks"})
for kind, extra in (("carpenter", "minecraft:crafting_table"), ("mason", "minecraft:stonecutter"), ("smith", "minecraft:iron_ingot"),
                    ("cook", "minecraft:furnace"), ("tavern", "minecraft:barrel")):
    shapeless(f"sign_{kind}", ["#minecraft:planks", "minecraft:stick", extra])
shaped("settlement_banner", ["WW", "WW", " S"], {"W": "#minecraft:wool", "S": "minecraft:stick"})
shapeless("bulletin_board", ["#minecraft:planks", "minecraft:paper", "minecraft:paper", "minecraft:stick"])
shaped("market_stall", ["WWW", "P P"], {"W": "#minecraft:wool", "P": "#minecraft:planks"})

# ======================================================================= wave H1: coziness (furniture, well multiblock, food)
import copy
DISPLAY_BIG = copy.deepcopy(DISPLAY)
DISPLAY_BIG["gui"]["scale"] = [1.0, 1.0, 1.0]
DISPLAY_BIG["gui"]["translation"] = [0, -1.5, 0]
DISPLAY_BIG["ground"]["scale"] = [0.5, 0.5, 0.5]
DISPLAY_BIG["fixed"]["scale"] = [1.0, 1.0, 1.0]
DISPLAY_BIG["thirdperson_righthand"]["scale"] = [0.55, 0.55, 0.55]
DISPLAY_BIG["firstperson_righthand"]["scale"] = [0.6, 0.6, 0.6]
DISPLAY_BIG["firstperson_lefthand"]["scale"] = [0.6, 0.6, 0.6]

def model(name, textures, elements, display=None):
    w(f"assets/{NS}/models/block/{name}.json", {"parent": "block/block", "display": display or DISPLAY, "textures": textures, "elements": elements})

def shifted(elements, dy):
    out = copy.deepcopy(elements)
    for e in out:
        e["from"][1] += dy; e["to"][1] += dy
    return out

def ub(fr, to, tex, uv):
    """box with explicit uv per face: uv is a rect used on all faces or a dict face -> (tex, rect)."""
    faces = {}
    for d in ALL6:
        if isinstance(uv, dict):
            if d in uv: faces[d] = face(uv[d][0], uv[d][1])
        else:
            faces[d] = face(tex, uv)
    return {"from": fr, "to": to, "faces": faces}

ALL6 = ("north", "south", "east", "west", "up", "down")
def tx(*names):
    return {"particle": T(names[0]), **{n: T(n) for n in names}}

def facing_variants(model_name, rot=STD, extra=""):
    return {f"{extra}facing={d}": ({"model": f"{NS}:block/{model_name}", "y": r} if r else {"model": f"{NS}:block/{model_name}"}) for d, r in rot.items()}

def simple_item(name, model_name=None):
    w(f"assets/{NS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{model_name or name}"}})

def loot_counted(name, maxn):
    entries = [{"type": "minecraft:item", "name": f"{NS}:{name}", "functions": [
        {"function": "minecraft:set_count", "count": n, "add": False,
         "conditions": [{"condition": "minecraft:block_state_property", "block": f"{NS}:{name}", "properties": {"count": str(n)}}]} for n in range(2, maxn + 1)]}]
    w(f"data/{NS}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": entries,
        "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": f"{NS}:blocks/{name}"})

# ---- the well: single (legacy), basin (master of the multiblock) and the parts
w_tex = {"particle": T("well_stone"), "stone": T("well_stone"), "water": T("well_water"), "roof": T("well_roof"), "post": T("well_post"),
         "rope": T("rope"), "bucket": T("bucket"), "axle": T("well_axle")}
def rot_variants(model_for_state, states, rot=STD):
    v = {}
    for st in states:
        for d, r in rot.items():
            m = model_for_state(st)
            v[f"{st},facing={d}" if st else f"facing={d}"] = {"model": f"{NS}:block/{m}", **({"y": r} if r else {})}
    return v

# legacy one-block well (old saves) keeps the old look but with the new textures
model("well_single", w_tex, [box_el([0, 0, 0], [16, 7, 3], "#stone"), box_el([0, 0, 13], [16, 7, 16], "#stone"),
    box_el([0, 0, 3], [3, 7, 13], "#stone"), box_el([13, 0, 3], [16, 7, 13], "#stone"), box_el([3, 0, 3], [13, 5, 13], "#water"),
    box_el([1, 7, 1], [3, 14, 3], "#post"), box_el([13, 7, 1], [15, 14, 3], "#post"), box_el([1, 7, 13], [3, 14, 15], "#post"), box_el([13, 7, 13], [15, 14, 15], "#post"),
    box_el([0, 14, 0], [16, 16, 16], "#roof"), box_el([7, 7, 7], [9, 14, 9], "#rope"), box_el([5, 6, 5], [11, 9, 11], "#bucket")])
model("well_basin", w_tex, [box_el([0, 0, 0], [16, 1, 16], "#stone"), box_el([0, 1, 0], [16, 6, 16], "#water")])
model("well_ring_edge", w_tex, [box_el([0, 0, 0], [16, 10, 5], "#stone"), box_el([0, 0, 5], [16, 1, 16], "#stone"), box_el([0, 1, 5], [16, 6, 16], "#water")])
model("well_ring_corner", w_tex, [box_el([0, 0, 0], [16, 10, 5], "#stone"), box_el([0, 0, 5], [5, 10, 16], "#stone"),
    box_el([5, 0, 5], [16, 1, 16], "#stone"), box_el([5, 1, 5], [16, 6, 16], "#water")])
model("well_post", w_tex, [box_el([6, 0, 0.5], [10, 16, 4.5], "#post")])
model("well_rope", w_tex, [box_el([7.5, 6, 7.5], [8.5, 16, 8.5], "#rope"), box_el([5, 0, 5], [11, 6, 11], "#bucket"), box_el([5.5, 6, 7.5], [10.5, 6.8, 8.5], "#post")])
model("well_roof_edge", w_tex, [box_el([0, 6, 0], [16, 8, 16], "#roof"), box_el([0, 8, 3], [16, 10, 16], "#roof"), box_el([0, 10, 8], [16, 12, 16], "#roof")])
model("well_roof_post", w_tex, [box_el([6, 0, 0.5], [10, 6, 4.5], "#post"), box_el([6.5, 2, 4.5], [9.5, 4.5, 16], "#axle"),
    box_el([0, 6, 0], [16, 8, 16], "#roof"), box_el([0, 8, 3], [16, 10, 16], "#roof"), box_el([0, 10, 8], [16, 12, 16], "#roof")])
model("well_roof_corner", w_tex, [box_el([0, 6, 0], [16, 8, 16], "#roof"), box_el([3, 8, 3], [16, 10, 16], "#roof"), box_el([8, 10, 8], [16, 12, 16], "#roof")])
model("well_roof_center", w_tex, [box_el([0, 6, 0], [16, 8, 16], "#roof"), box_el([0, 8, 0], [16, 10, 16], "#roof"), box_el([0, 10, 0], [16, 12, 16], "#roof"),
    box_el([3, 12, 3], [13, 14, 13], "#roof"), box_el([6, 14, 6], [10, 16, 10], "#roof"),
    box_el([0, 2, 6.5], [16, 4.5, 9.5], "#axle"), box_el([6, 1.5, 6], [10, 5, 10], "#rope"), box_el([14.5, 0.5, 7], [15.5, 6.5, 8], "#axle")])
PARTS = {"ring_edge": "well_ring_edge", "ring_corner": "well_ring_corner", "post": "well_post", "rope": "well_rope", "roof_corner": "well_roof_corner",
         "roof_edge": "well_roof_edge", "roof_post": "well_roof_post", "roof_center": "well_roof_center"}
w(f"assets/{NS}/blockstates/well_part.json", {"variants": {f"facing={d},part={p}": {"model": f"{NS}:block/{m}", **({"y": r} if r else {})}
                                                          for p, m in PARTS.items() for d, r in STD.items()}})
w(f"assets/{NS}/blockstates/well.json", {"variants": {f"complete={c},facing={d}": {"model": f"{NS}:block/{'well_basin' if c == 'true' else 'well_single'}", **({"y": r} if r else {})}
                                                      for c in ("false", "true") for d, r in STD.items()}})
w(f"assets/{NS}/items/well.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/well_single"}})
w(f"data/{NS}/loot_table/blocks/well.json", {"type": "minecraft:block", "pools": [], "random_sequence": f"{NS}:blocks/well"})  # dropped by code (once for the whole well)
w(f"data/{NS}/loot_table/blocks/well_part.json", {"type": "minecraft:block", "pools": [], "random_sequence": f"{NS}:blocks/well_part"})

# ---- tables
TW = tx("table_wood", "table_top", "table_wood_dark", "long_table_top")
model("round_table", TW, [box_el([2, 12, 2], [14, 14, 14], {"up": "#table_top", "down": "#table_wood", "north": "#table_wood", "south": "#table_wood", "east": "#table_wood", "west": "#table_wood"}),
    box_el([0, 12, 4], [16, 14, 12], {"up": "#table_top", "down": "#table_wood", "north": "#table_wood", "south": "#table_wood", "east": "#table_wood", "west": "#table_wood"}),
    box_el([4, 12, 0], [12, 14, 16], {"up": "#table_top", "down": "#table_wood", "north": "#table_wood", "south": "#table_wood", "east": "#table_wood", "west": "#table_wood"}),
    box_el([6, 2, 6], [10, 12, 10], "#table_wood_dark"), box_el([3, 0, 3], [13, 2, 13], "#table_wood_dark")])
w(f"assets/{NS}/blockstates/round_table.json", {"variants": {"": {"model": f"{NS}:block/round_table"}}})
def long_table(a, b):
    top = {"up": "#long_table_top", "down": "#table_wood", "north": "#table_wood", "south": "#table_wood", "east": "#table_wood", "west": "#table_wood"}
    els = [box_el([0, 12, 0], [16, 14, 16], top), box_el([0, 10, 2], [16, 12, 3], "#table_wood_dark"), box_el([0, 10, 13], [16, 12, 14], "#table_wood_dark")]
    if not a: els += [box_el([1, 0, 1], [3, 12, 3], "#table_wood_dark"), box_el([1, 0, 13], [3, 12, 15], "#table_wood_dark"), box_el([1, 5, 3], [3, 6, 13], "#table_wood_dark")]
    if not b: els += [box_el([13, 0, 1], [15, 12, 3], "#table_wood_dark"), box_el([13, 0, 13], [15, 12, 15], "#table_wood_dark"), box_el([13, 5, 3], [15, 6, 13], "#table_wood_dark")]
    return els
lv = {}
for a in (0, 1):
    for b in (0, 1):
        model(f"long_table_{a}{b}", TW, long_table(a, b))
        for ax, r in (("x", 0), ("z", 90)):
            lv[f"axis={ax},a={'true' if a else 'false'},b={'true' if b else 'false'}"] = {"model": f"{NS}:block/long_table_{a}{b}", **({"y": r} if r else {})}
w(f"assets/{NS}/blockstates/long_table.json", {"variants": lv})
simple_item("round_table"); simple_item("long_table", "long_table_00")

# ---- seats
CW = tx("chair_wood", "cushion", "stool_top")
model("chair", CW, [box_el([3, 8, 3], [13, 10, 13], {"up": "#cushion", "down": "#chair_wood", "north": "#chair_wood", "south": "#chair_wood", "east": "#chair_wood", "west": "#chair_wood"}),
    box_el([3, 0, 3], [5, 8, 5], "#chair_wood"), box_el([11, 0, 3], [13, 8, 5], "#chair_wood"),
    box_el([3, 0, 11], [5, 16, 13], "#chair_wood"), box_el([11, 0, 11], [13, 16, 13], "#chair_wood"),
    box_el([5, 11, 11.5], [11, 13, 12.5], "#chair_wood"), box_el([5, 14, 11.5], [11, 16, 12.5], "#chair_wood"), box_el([5, 4, 3.5], [11, 5, 4.5], "#chair_wood")])
model("stool", CW, [box_el([3, 9, 3], [13, 11, 13], {"up": "#stool_top", "down": "#chair_wood", "north": "#chair_wood", "south": "#chair_wood", "east": "#chair_wood", "west": "#chair_wood"}),
    *[box_el([x, 0, z], [x + 2, 9, z + 2], "#chair_wood") for x in (4, 10) for z in (4, 10)],
    box_el([4, 4, 4], [12, 5, 6], "#chair_wood"), box_el([4, 4, 10], [12, 5, 12], "#chair_wood")])
for n in ("chair", "stool"):
    w(f"assets/{NS}/blockstates/{n}.json", {"variants": facing_variants(n)})
    simple_item(n)

# ---- small stacks: bottles, mugs, plates, candles, candlestick
BT = tx("bottles")
def bottle_els(pos):
    els = []
    for i, (x, z) in enumerate(pos):
        x0 = (i % 3) * 5
        els.append(ub([x, 0, z], [x + 4, 7, z + 4], "#bottles", None) | {"faces": {
            **{d: face("#bottles", [x0, 3, x0 + 4, 10]) for d in ("north", "south", "east", "west")},
            "up": face("#bottles", [x0, 3, x0 + 4, 4]), "down": face("#bottles", [x0, 11, x0 + 4, 12])}})
        els.append({"from": [x + 1, 7, z + 1], "to": [x + 3, 11, z + 3], "faces": {
            **{d: face("#bottles", [x0 + 1, 0, x0 + 3, 4]) for d in ("north", "south", "east", "west")},
            "up": face("#bottles", [x0 + 1, 0, x0 + 3, 1])}})
    return els
BPOS = {1: [(6, 6)], 2: [(3, 8), (9, 3)], 3: [(2, 2), (9, 3), (5, 9)]}
MT = tx("mug")
def mug_els(pos):
    els = []
    for (x, z) in pos:
        els.append({"from": [x, 0, z], "to": [x + 5, 5, z + 5], "faces": {**{d: face("#mug", [0, 0, 8, 6]) for d in ("north", "south", "east", "west")},
                    "up": face("#mug", [0, 8, 8, 16]), "down": face("#mug", [0, 0, 8, 1])}})
        els.append({"from": [x + 5, 1, z + 1.5], "to": [x + 7, 4, z + 3.5], "faces": {d: face("#mug", [8, 1, 10, 4]) for d in ALL6}})
    return els
MPOS = {1: [(4, 5)], 2: [(1, 2), (7, 8)], 3: [(1, 1), (8, 2), (4, 9)]}
CT = tx("candle_wax", "candle_flame")
def candle_els(pos):
    els = []
    for i, (x, z, h) in enumerate(pos):
        els.append({"from": [x, 0, z], "to": [x + 2, h, z + 2], "faces": {**{d: face("#candle_wax", [0, 0, 8, 8]) for d in ("north", "south", "east", "west")}, "up": face("#candle_wax", [0, 0, 2, 2])}})
        els.append({"from": [x, h, z + 1], "to": [x + 2, h + 3, z + 1], "faces": {"north": face("#candle_flame", [3, 3, 7, 9]), "south": face("#candle_flame", [3, 3, 7, 9])}})
        els.append({"from": [x + 1, h, z], "to": [x + 1, h + 3, z + 2], "faces": {"east": face("#candle_flame", [3, 3, 7, 9]), "west": face("#candle_flame", [3, 3, 7, 9])}})
    return els
CPOS = {1: [(7, 7, 6)], 2: [(4, 7, 5), (9, 6, 8)], 3: [(3, 4, 5), (9, 5, 8), (6, 10, 6)]}
def plate_els(kind):
    base = [{"from": [2, 0, 2], "to": [14, 1, 14], "faces": {**{d: face("#plates", [8, 0, 16, 1]) for d in ("north", "south", "east", "west")}, "up": face("#plates", [0, 0, 8, 8]), "down": face("#plates", [8, 0, 16, 1])}}]
    if kind == "bread":
        base += [{"from": [4, 1, 5], "to": [11, 4, 11], "faces": {**{d: face("#plates", [8, 8, 16, 11]) for d in ("north", "south", "east", "west")}, "up": face("#plates", [0, 8, 8, 16])}},
                 {"from": [10, 1, 9], "to": [13, 3, 12], "faces": {**{d: face("#plates", [8, 8, 16, 11]) for d in ("north", "south", "east", "west")}, "up": face("#plates", [0, 8, 8, 16])}}]
    elif kind == "roast":
        base += [{"from": [4, 1, 4], "to": [12, 5, 11], "faces": {**{d: face("#plates", [8, 12, 16, 16]) for d in ("north", "south", "east", "west")}, "up": face("#plates", [8, 12, 16, 16])}}]
    else:
        base += [{"from": [3, 1, 5], "to": [10, 3, 11], "faces": {**{d: face("#plates", [8, 2, 14, 6]) for d in ("north", "south", "east", "west")}, "up": face("#plates", [8, 2, 14, 6])}},
                 {"from": [10, 1, 6], "to": [13, 2, 10], "faces": {**{d: face("#plates", [14, 2, 16, 6]) for d in ("north", "south", "east", "west")}, "up": face("#plates", [14, 2, 16, 6])}}]
    return base
PT = tx("plates")
STARS = {}
def small_family(name, tex, builder, maxn, display=DISPLAY_BIG):
    for n in range(1, maxn + 1):
        els = builder(n)
        model(f"{name}_{n}", tex, els, display)
        model(f"{name}_{n}_t", tex, shifted(els, -2), display)
    v = {}
    for n in range(1, 4):
        m = min(n, maxn)
        for d, r in STD.items():
            for t in ("false", "true"):
                v[f"count={n},facing={d},on_table={t}"] = {"model": f"{NS}:block/{name}_{m}{'_t' if t == 'true' else ''}", **({"y": r} if r else {})}
    w(f"assets/{NS}/blockstates/{name}.json", {"variants": v})
    simple_item(name, f"{name}_{maxn}")
small_family("bottles", BT, lambda n: bottle_els(BPOS[n]), 3)
small_family("mug", MT, lambda n: mug_els(MPOS[n]), 3)
small_family("candles", CT, lambda n: candle_els(CPOS[n]), 3)
for kind in ("bread", "roast", "fish"):
    small_family(f"plate_{kind}", PT, lambda n, k=kind: plate_els(k), 1)
CS = tx("candlestick_metal", "candle_wax", "candle_flame")
small_family("candlestick", CS, lambda n: [box_el([4, 0, 4], [12, 1, 12], "#candlestick_metal"), box_el([7, 1, 7], [9, 6, 9], "#candlestick_metal"), box_el([5, 6, 5], [11, 7, 11], "#candlestick_metal"),
    {"from": [7, 7, 7], "to": [9, 13, 9], "faces": {**{d: face("#candle_wax", [0, 0, 8, 8]) for d in ("north", "south", "east", "west")}, "up": face("#candle_wax", [0, 0, 2, 2])}},
    {"from": [7, 13, 8], "to": [9, 16, 8], "faces": {"north": face("#candle_flame", [3, 3, 7, 9]), "south": face("#candle_flame", [3, 3, 7, 9])}},
    {"from": [8, 13, 7], "to": [8, 16, 9], "faces": {"east": face("#candle_flame", [3, 3, 7, 9]), "west": face("#candle_flame", [3, 3, 7, 9])}}], 1)

# ---- wall shelf, hanging herbs, barrel rack, crate, firewood, rugs, hanging lantern
SH = tx("shelf_wood", "shelf_jars")
def jar(x, y, q):
    x0, y0 = (q % 2) * 8, (q // 2) * 8
    return {"from": [x, y, 11], "to": [x + 4, y + 5, 15], "faces": {**{d: face("#shelf_jars", [x0, y0, x0 + 8, y0 + 8]) for d in ("north", "south", "east", "west", "up")}}}
model("wall_shelf", SH, [box_el([0, 5, 10], [16, 7, 16], "#shelf_wood"), box_el([0, 12, 10], [16, 14, 16], "#shelf_wood"),
    box_el([1, 2, 13], [3, 5, 16], "#shelf_wood"), box_el([13, 2, 13], [15, 5, 16], "#shelf_wood"), box_el([1, 9, 13], [3, 12, 16], "#shelf_wood"), box_el([13, 9, 13], [15, 12, 16], "#shelf_wood"),
    jar(1, 7, 0), jar(6, 7, 1), jar(11, 7, 2), jar(2, 14, 3), jar(9, 14, 1)], DISPLAY_BIG)
w(f"assets/{NS}/blockstates/wall_shelf.json", {"variants": facing_variants("wall_shelf")})
simple_item("wall_shelf")
HB = tx("herbs", "shelf_wood", "herb_string")
def bunch(x, q, h=9):
    x0, y0 = (q % 2) * 8, (q // 2) * 8
    return {"from": [x, 14 - h, 6.5], "to": [x + 4, 14, 9.5], "faces": {d: face("#herbs", [x0, y0, x0 + 8, y0 + 8]) for d in ("north", "south", "east", "west")}}
model("hanging_herbs", HB, [box_el([0, 14, 7], [16, 16, 9], "#shelf_wood"), bunch(1, 0), bunch(6, 1, 8), bunch(11, 2, 7),
    box_el([2.5, 13, 7.5], [3.5, 14, 8.5], "#herb_string"), box_el([7.5, 13, 7.5], [8.5, 14, 8.5], "#herb_string"), box_el([12.5, 13, 7.5], [13.5, 14, 8.5], "#herb_string")], DISPLAY_BIG)
w(f"assets/{NS}/blockstates/hanging_herbs.json", {"variants": facing_variants("hanging_herbs")})
simple_item("hanging_herbs")
BR = tx("barrel_side", "barrel_end", "shelf_wood")
def barrel(fr, to):
    return {"from": fr, "to": to, "faces": {"north": face("#barrel_end", [0, 0, 16, 16]), "south": face("#barrel_end", [0, 0, 16, 16]),
            **{d: face("#barrel_side", [0, 0, 16, 16]) for d in ("east", "west", "up", "down")}}}
model("barrel_rack", BR, [box_el([0, 0, 1], [16, 1, 15], "#shelf_wood"), box_el([0, 1, 1], [1, 9, 15], "#shelf_wood"), box_el([15, 1, 1], [16, 9, 15], "#shelf_wood"),
    barrel([1, 1, 2], [8, 8, 14]), barrel([8, 1, 2], [15, 8, 14]), barrel([4.5, 8, 2], [11.5, 15, 14]), box_el([0, 8, 1], [16, 9, 3], "#shelf_wood")])
w(f"assets/{NS}/blockstates/barrel_rack.json", {"variants": facing_variants("barrel_rack")})
simple_item("barrel_rack")
model("crate", tx("crate_side", "crate_top"), [box_el([0, 0, 0], [16, 16, 16], {"north": "#crate_side", "south": "#crate_side", "east": "#crate_side", "west": "#crate_side", "up": "#crate_top", "down": "#crate_top"})])
w(f"assets/{NS}/blockstates/crate.json", {"variants": {"": {"model": f"{NS}:block/crate"}}})
simple_item("crate")
FW = tx("firewood_end", "firewood_bark")
def log(x, y, z):
    return {"from": [0, y, z], "to": [16, y + 4, z + 4], "faces": {"east": face("#firewood_end", [0, 0, 16, 16]), "west": face("#firewood_end", [0, 0, 16, 16]),
            **{d: face("#firewood_bark", [0, 0, 16, 4]) for d in ("north", "south", "up", "down")}}}
model("firewood_pile", FW, [log(0, 0, z) for z in (0, 4, 8, 12)] + [log(0, 4, z) for z in (2, 6, 10)] + [log(0, 8, z) for z in (4, 8)])
w(f"assets/{NS}/blockstates/firewood_pile.json", {"variants": facing_variants("firewood_pile")})
simple_item("firewood_pile")
for c in ("red", "blue"):
    model(f"rug_{c}", tx(f"rug_{c}"), [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": {"up": face(f"#rug_{c}", [0, 0, 16, 16]), "down": face(f"#rug_{c}", [0, 0, 16, 16], "down"),
          **{d: face(f"#rug_{c}", [0, 0, 16, 1]) for d in ("north", "south", "east", "west")}}}], DISPLAY_BIG)
    w(f"assets/{NS}/blockstates/rug_{c}.json", {"variants": facing_variants(f"rug_{c}")})
    simple_item(f"rug_{c}")
HL = tx("hanging_lantern", "chain")
def lantern_els(dy):
    return [{"from": [5, 0 + dy, 5], "to": [11, 1 + dy, 11], "faces": {d: face("#hanging_lantern", [0, 8, 8, 10]) for d in ("north", "south", "east", "west", "down")}},
            {"from": [5.5, 1 + dy, 5.5], "to": [10.5, 8 + dy, 10.5], "faces": {**{d: face("#hanging_lantern", [0, 0, 8, 8]) for d in ("north", "south", "east", "west")}}},
            {"from": [5, 8 + dy, 5], "to": [11, 9 + dy, 11], "faces": {d: face("#hanging_lantern", [0, 8, 8, 10]) for d in ("north", "south", "east", "west", "up")}},
            {"from": [6.5, 9 + dy, 6.5], "to": [9.5, 10.5 + dy, 9.5], "faces": {d: face("#hanging_lantern", [8, 8, 16, 10]) for d in ("north", "south", "east", "west", "up")}}]
model("hanging_lantern", HL, lantern_els(0), DISPLAY_BIG)
model("hanging_lantern_hanging", HL, lantern_els(1) + [{"from": [7.5, 11.5, 7.5], "to": [8.5, 16, 8.5], "faces": {d: face("#chain", [0, 0, 1, 5]) for d in ("north", "south", "east", "west")}}], DISPLAY_BIG)
w(f"assets/{NS}/blockstates/hanging_lantern.json", {"variants": {"hanging=false": {"model": f"{NS}:block/hanging_lantern"}, "hanging=true": {"model": f"{NS}:block/hanging_lantern_hanging"}}})
simple_item("hanging_lantern")

# ---- loot tables
for n in ("round_table", "long_table", "chair", "stool", "plate_bread", "plate_roast", "plate_fish", "candlestick", "wall_shelf", "hanging_herbs", "barrel_rack", "crate",
          "firewood_pile", "rug_red", "rug_blue", "hanging_lantern"):
    loot(n)
for n in ("bottles", "mug", "candles"):
    loot_counted(n, 3)

# ---- food items
FOODS = "fried_fish fish_pie smoked_fish fish_soup meat_stew sausage cheese_wedge berry_pie apple_pie meat_pie rye_loaf honey_bun porridge roast_vegetables ale mead cider herbal_tea".split()
for f in FOODS:
    gen_item(f)

# ---- recipes: furniture
P, S_ = "#minecraft:planks", "minecraft:stick"
shaped("round_table", ["PPP", " S ", "SSS"], {"P": P, "S": S_})
shaped("long_table", ["PPP", "S S"], {"P": P, "S": S_}, 2)
shaped("chair", ["S  ", "PPP", "S S"], {"P": P, "S": S_})
shaped("stool", ["PPP", "S S"], {"P": "#minecraft:wooden_slabs", "S": S_}, 2)
shapeless("bottles", ["minecraft:glass_bottle", "minecraft:glass_bottle", "minecraft:glass_bottle"], 3)
shaped("mug", ["P P", "PPP"], {"P": "#minecraft:planks"}, 3)
shaped("plate_bread", ["B", "C"], {"B": "minecraft:bread", "C": "minecraft:clay_ball"})
shaped("plate_roast", ["B", "C"], {"B": "minecraft:cooked_chicken", "C": "minecraft:clay_ball"})
shaped("plate_fish", ["B", "C"], {"B": "minecraft:cooked_cod", "C": "minecraft:clay_ball"})
shaped("candles", ["H", "S"], {"H": "minecraft:honeycomb", "S": "minecraft:string"}, 3)
shaped("candlestick", ["H", "I"], {"H": "minecraft:honeycomb", "I": "minecraft:iron_nugget"})
shaped("wall_shelf", ["PPP", "J J"], {"P": "#minecraft:wooden_slabs", "J": "minecraft:glass_bottle"})
shaped("hanging_herbs", ["SSS", "FFF"], {"S": "minecraft:string", "F": "minecraft:fern"})
shaped("barrel_rack", ["PBP", "PPP"], {"P": P, "B": "minecraft:barrel"})
shaped("crate", ["PSP", "SPS", "PSP"], {"P": P, "S": S_})
shaped("firewood_pile", ["LLL", " L "], {"L": "minecraft:oak_log"}, 2)
shaped("rug_red", ["WW"], {"W": "minecraft:red_carpet"}, 2)
shaped("rug_blue", ["WW"], {"W": "minecraft:blue_carpet"}, 2)
shaped("hanging_lantern", ["I", "L"], {"I": "minecraft:iron_nugget", "L": "minecraft:lantern"})

# ---- recipes: food (vanilla ingredients; categories for the recipe book)
def sh_food(name, ingredients, count=1):
    w(f"data/{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ingredients, "result": {"count": count, "id": f"{NS}:{name}"}})
sh_food("fried_fish", ["minecraft:cooked_salmon", "minecraft:wheat"], 2)
sh_food("fish_pie", ["minecraft:cooked_cod", "minecraft:potato", "minecraft:wheat", "minecraft:wheat"])
sh_food("fish_soup", ["minecraft:bowl", "minecraft:cooked_cod", "minecraft:carrot"])
sh_food("meat_stew", ["minecraft:bowl", "minecraft:cooked_beef", "minecraft:carrot", "minecraft:potato"])
sh_food("sausage", ["minecraft:porkchop", "minecraft:beef", "minecraft:string"], 3)
sh_food("cheese_wedge", ["minecraft:milk_bucket", "minecraft:dried_kelp"], 4)
sh_food("berry_pie", ["minecraft:sweet_berries", "minecraft:sweet_berries", "minecraft:sugar", "minecraft:egg"])
sh_food("apple_pie", ["minecraft:apple", "minecraft:apple", "minecraft:sugar", "minecraft:egg"])
sh_food("meat_pie", ["minecraft:cooked_beef", "minecraft:potato", "minecraft:wheat", "minecraft:egg"])
sh_food("rye_loaf", ["minecraft:wheat", "minecraft:wheat", "minecraft:cocoa_beans"])
sh_food("honey_bun", ["minecraft:wheat", "minecraft:wheat", "minecraft:honey_bottle"], 2)
sh_food("porridge", ["minecraft:bowl", "minecraft:wheat", "minecraft:wheat", "minecraft:sugar"])
sh_food("roast_vegetables", ["minecraft:baked_potato", "minecraft:carrot", "minecraft:beetroot"])
sh_food("ale", ["minecraft:glass_bottle", "minecraft:wheat", "minecraft:wheat", "minecraft:sugar"])
sh_food("mead", ["minecraft:glass_bottle", "minecraft:honey_bottle", "minecraft:wheat"])
sh_food("cider", ["minecraft:glass_bottle", "minecraft:apple", "minecraft:apple", "minecraft:sugar"])
sh_food("herbal_tea", ["minecraft:glass_bottle", "#minecraft:small_flowers", "minecraft:sweet_berries", "minecraft:fern"])
w(f"data/{NS}/recipe/smoked_fish.json", {"type": "minecraft:smoking", "category": "food", "ingredient": "minecraft:tropical_fish", "result": {"id": f"{NS}:smoked_fish"}, "experience": 0.35, "cookingtime": 100})
w(f"data/{NS}/recipe/smoked_fish_smelting.json", {"type": "minecraft:smelting", "category": "food", "ingredient": "minecraft:tropical_fish", "result": {"id": f"{NS}:smoked_fish"}, "experience": 0.35, "cookingtime": 200})
print("assets written")
