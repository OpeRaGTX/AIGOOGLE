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
print("assets written")
