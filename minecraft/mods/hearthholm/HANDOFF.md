# HANDOFF — read this first after /clear

Orchestrator notes for continuing **Hearthholm** (Fabric, MC 26.3). Owner writes Russian → answer in Russian,
briefly. Owner wants autonomy: ask only truly key questions (the owner allows them), be creative, focus on city-building (Stonehearth / Craft the World
mechanics, no copied names/art). The orchestrator does **not** write code: it plans a wave, delegates it to ONE
background Sonnet agent (`Agent`, model sonnet, `run_in_background`), verifies, then starts the next wave.

## Where things are
- Mod: `minecraft/mods/hearthholm/` (package `io.github.operagtx.hearthholm`).
  - `AGENTS_BRIEF.md` — rules every coding agent must follow (token budget, toolchain, javap lookup, conventions,
    finish steps). Always tell agents to read it first.
  - `DESIGN.md` + `README.md` = short TOCs; per-wave files `docs/design/<WAVE>.md` (architecture, RU; `core.md` = sections 1-6) and `docs/waves/<WAVE>.md` (player guide, RU), `CONCEPTS.md` waves A–F.
  - Main code `src/main/java/.../`: `core` (professions, traits, stats), `worksite`, `settlement` (SavedData, rooms,
    storage), `entity` (SettlerEntity, `ai/` brain, activities, jobs, tasks; `ashborn/` enemies), `crafting`, `social`,
    `threat`, `party`, `blueprint`, `network`, `block`, `item`, `registry`, `api` (extension point), `mixin`.
  - Client `src/client/...`: `render/` (HumanoidModel settlers, layered tinted skins), `screen/` (town hall tabs,
    settler, work site, order board).
  - Test `src/gametest/...` (`HearthholmClientTest` + per-wave classes such as `WaveDTest`). `HH_ONLY_D/E/F=1` = run only that wave (plus the base).
  - Resource generators `tools/gen_textures.py, gen_assets.py, gen_lang.py (EN+RU), gen_sounds.py, bubbles.py`.
  - Blueprints `src/main/resources/data/hearthholm/blueprints/index.json` (append only).
- Tools: `minecraft/tools/{setup-jdk.sh, run-client-test.sh, package-mod.sh}`; zip → `minecraft/dist/hearthholm-0.1.0-mc26.3.zip`.
- Skill router: `.claude/skills/minecraft-launcher-run/SKILL.md` (Gotchas = real 26.3 API traps).
- Token rules: `.claude/settings.json` denies reading build caches; Gradle output → file, grep errors only.

## Commands
```bash
export JAVA_HOME=/opt/jdk-25 PATH=/opt/jdk-25/bin:$PATH   # bash minecraft/tools/setup-jdk.sh if missing
cd minecraft/mods/hearthholm && ./gradlew build --no-daemon -q > /tmp/b.log 2>&1; grep -iE 'error|FAIL' /tmp/b.log | head
bash minecraft/tools/run-client-test.sh minecraft/mods/hearthholm | tail -40   # ~10 min, needs HHTEST DONE / exit=0
bash minecraft/tools/package-mod.sh minecraft/mods/hearthholm
```
Git: branch `claude/lucid-hypatia-ri79og`, commit trailers
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` + `Claude-Session: https://claude.ai/code/session_01BohTuQyGzzPGhLp7A8XBtr`.
No PR unless asked. Stop hook demands commit+push before ending a turn.

## Status (waves A-F built, tested headless, pushed)
- **Base v0.1**: town hall desk, hiring (food + bed + renown), rooms/houses auto-detect, warehouse, stats/traits,
  builder (blueprints + free mode), miner (spiral/tunnel/shaft), lumberjack, farmer, fisher, guard, warrior, zone markers.
- **RU/EN localization** (all strings in `gen_lang.py`).
- **A Workshops**: carpenter, mason, smith, cook, hauler, order board, hearth marks (mint). Gaps: furnace fuel not
  consumed; mason/smith not exercised in the test.
- **B Voices**: speech bubbles (~180 lines), relationships, thoughts, harvest festival, legendary candidates, sounds.
  Gap: legendary hire not covered by the test.
- **C Threats**: Ashborn raids (raider, firestarter, archer, brute), rift nights, Ash Chieftain boss, alarm bell,
  palisade + gate, watchtower/palisade blueprints, defense tab. Gaps: fire markers in memory only; raiders still
  burn wooden roofs; casualties not deterministic (0–2 in test).
- **D Retinue**: war horn party (≤4, teleport, persisted), archer, healer + herbalist table + healing salve, knight
  promotion (lvl 5, 10 marks + iron armor, shield, taunt), barracks + weapon rack + blueprint, combat scaling,
  retreat at 30% HP, raider door-breaking/target drop, no friendly fire. Gaps: party members in unloaded chunks stay
  behind; cross-dimension teleport untested.

- **E Square and market**: tier-unlocked blueprints (hamlet: workshop, farmhouse; village: tavern, market stall, watchtower;
  town: barracks, wall), tavern + innkeeper, trader caravan (weekly, llama, buys surplus for marks), decor blocks +
  "Beautiful street", bulletin board quests, fuel consumption, roof-fire fix.
- **F Polish**: Founder's Chronicle guide book (given once at founding, `civic.guideGiven`), `core/Balance.java` (all economy
  numbers; changes in DESIGN §7), `town/Fx` particles + 5 new procedural sounds, decor items in the carpenter/mason/smith
  order catalogues (lazy `Supplier` catalogue, paged picker), watchtower needs village tier, trade label ellipsis,
  README/DESIGN refreshed, screenshots in `docs/screenshots/`, zip rebuilt. Test: `WaveFTest` (`HH_ONLY_F=1`).

- **G Reshoot and gaps**: retroactive guide book (desk visit), `config/hearthholm.json` (Balance via reflection), `party/PartyRoster` +
  force-load rescue of members in unloaded chunks, nether round trip tested, horn summons stragglers, GUI-less overview shot
  (GUI hidden via `ctx.getInput().pressKey(o -> o.keyToggleGui)`), tavern reshoot. Test: `WaveGTest` (`HH_ONLY_G=1`).

- **H1 Coziness** (furniture, well 3x3, 18 foods, reworked textures) and **H2 Houses and people**: pitched-roof furnished blueprints
  (generator `tools/gen_h2_blueprints.py`; small_house, farmhouse, workshop, tavern, market_stall, barracks, watchtower redesigned,
  new cottage/townhouse/fisher_hut/l_house appended), 20 settler skin presets (`Appearance.PRESETS`, layers by `tools/gen_h2_skins.py`),
  richer desk/keg/lamp post/lantern models, well posts grounded. Test `WaveH2Test` (`HH_ONLY_H2=1`).

- **H3 Visual polish**: the well holds real water (centre + 8 ring blocks `waterlogged`, `WellBlock`/`WellPartBlock implements SimpleWaterloggedBlock`,
  bucket fills without emptying it, no flow, `collapse` removes the water; old `complete` wells get water by the property default); slim wrought-iron lamp post
  model; women: long hair/braids/tails on outer layers + faces (`tools/gen_h3_skins.py`), skirt flare + hair volume via `client/render/SettlerModel`; profession
  overlays are accents only (filter in `gen_h3_skins.py`); stair corner shapes (`PlaceBlock.reshapeStairs`, `gen_h2_blueprints.fix_stairs`); `BuilderJob` no longer
  loops "unloading" when pockets are full of needed materials. Test `WaveH3Test` (`HH_ONLY_H3=1`) also re-arms the base builder and checks it finishes `small_house`.

- **I1 Playability: workers** (owner playtest feedback): GUI overlaps fixed (settler + stake screens, ellipsis, HP bar/line also in the town hall list), walk-through markers,
  builder 3x slower with `Balance.BUILD_DELAY_TICKS`, origin +1 block from the stake, any bed/carpet/wool colour and wood fallback (`Blueprint.accepts/variant`), miner 2-wide
  with torches/vein mining/vanilla pickaxe speed, tools wear and are re-fetched (`BaseJob.toolTrip`, bubbles `need_pickaxe|axe|hoe`), XP time-based (`workTick`, curve `Balance.LEVEL_XP`).
  Test `WaveI1Test` (`HH_ONLY_I1=1`); earlier waves run with an accelerated builder/miner (`Balance` overridden in `HearthholmClientTest`). Next: **I2** guard/archer combat, shifts, loot pickup.

- **I2 Playability: guards and archers**: combat settlers walk 4.3 / chase 5.6 b/s (`SettlerEntity.setSpeed`), damage comes from the held weapon (bare hand 1, vanilla modifiers, attack-speed cooldown, 1 durability per hit, re-arm from post chest/warehouse), archer uses real arrows (draw 1 s, ~6 dmg, bow wear, refill, `needs_arrows` status + bubble, melee fallback), loot (`town/Loot`, `tasks/Fetch`, tag `hh_loot`), shifts Always/Day/Night per settler and per post (`shift` option, settler screen button), alarm calls everybody. Test `WaveI2Test` (`HH_ONLY_I2=1`). Test rule: run only your wave while developing, full test once at the end.

- **J1 Architecture: settler inventory** (first of a series; owner decided: per-profession work-site blocks with a map GUI for miner/lumberjack = J2/J3, working inventory for all settlers = J1): `menu/SettlerMenu` + `SettlerMenuProvider` + `registry/ModMenus` (Fabric `ExtendedMenuType`, opening data = entity id), client `SettlerInventoryScreen` (background `textures/gui/settler_inventory.png` drawn in `gen_textures.py`), Shift+RMB or the settler-screen button opens it, owner only, <= 6.5 blocks, settler paused while open (`isImmobile` + `holdForInventory`). I2 leftovers: archer retreat (`wantsArcherRetreat`), shift values `SHIFT_POST/DAY/NIGHT/ALWAYS` (0 stays "post default"), lumberjack sweep filter. `api/ProfessionModule(s)` = registry of thin adapters (job factory, site screen opener, zone data); J2/J3 plug in there (DESIGN §14). Test `WaveJ1Test` (`HH_ONLY_J1=1`). Next: **J2** lumberjack work-site block + zone map GUI, **J3** miner.

- **J2 Map zones + chopping block**: generic `zone/WorkZone` + `ZoneMapService` (snapshot, throttle, validated diffs) + client `ZoneMapWidget/Screen`; the lumberjack's block is a chopping block with an axe (same block id), its job works only inside the drawn zone (empty zone = old radius), plants saplings in the zone (`plant` option); settler screen bottom row sized to labels. Test `WaveJ2Test` (`HH_ONLY_J2=1`). Next: **J3** the miner reuses it (`WorkZone.register(MINE_ID, Spec(100, 2, true))`, layer mode snapshots, `ZoneMapScreen` layer row).

- **J3 Miner's stone + 3D dig plan**: `zone/MinerModule`, `WorkZone` heights 1-4 + `Spec.MINER` (50x50 cells of 2), `entity/ai/jobs/DrawnPlanner` (access stair 1:1, flood by layer, islands, open-to-sky = no stair), `zone/MineGuard` (protected places), options `plan`/`after`, map screen `ZoneMapScreen.initMiner` (slider, height, ghosts, ore, red cells, summary), block `mine_marker` = boulder with pickaxe (`tools/gen_j3_tex.py`). Zone edits: reach 16, rejection told to the owner (`message.zone_rejected`). Test `WaveJ3Test` (`HH_ONLY_J3=1`) incl. REAL simulated mouse painting on both maps. I2/D tests made robust (loot race, chase speed by game time, barracks wait). DESIGN section 16.

- **K1 Builder, recipes, co-owners**: builder reshapes neighbours (panes/bars/fences/walls/double chests/stairs), attachables only on an existing support (`Blueprint.needsSupport`, `PlaceBlock.hasSupport`), missing material = skip and return (cursor stays on the first unfinished op), stake option `stake` keep/carry (never dropped), all mod recipes unlocked on login, Chronicle chapter 10 with recipe pictures (`recipe_book.json`), co-owners (town hall tab "Owners", `Civic.coOwners`, founder-only removal and hall break). Test `WaveK1Test` (`HH_ONLY_K1=1`) incl. recipe collision check against all vanilla recipes. Docs split into docs/waves + docs/design; K1 full client test passed; zip rebuilt. Next: **K2**.

- **K2 Workday, shift-end unload, miner pace**: civilians work from sunrise (tick 0 = 06:00) for 75% of daylight (0..9000, ~15:00), then leisure (idle/chat/tavern), sleep at night; the military (guard, archer, warrior, knight) keep their old hours and explicit shifts (`Balance.inWorkday`, `SettlerEntity.workAt`). `ShiftEndActivity` + `Job.endOfShift`/`BaseJob.unloadInto`: once per workday everything except tool/weapon, armour, shield (archer: arrows) and 2 food items goes to the chest by the work site, else the warehouse. Lumberjack sweeps every drop again (J1 filter reverted, `LumberjackJob.wanted`). Miner: `breakTicks` x2.0 at level 1 falling to x1.0 at level 5 (`SettlerEntity.minerSlowdown`, only the miner; this slows the digging time only, not walking/placing). Config keys in `config/hearthholm.json`: `WORKDAY_START_TICK`, `WORKDAY_DAYLIGHT_PERCENT`, `SHIFT_END_KEEP_FOOD`, `MINER_SLOW_PERCENT_AT_LEVEL1`, `MINER_FULL_PACE_LEVEL`. Test `WaveK2Test` (`HH_ONLY_K2=1`). Docs `docs/waves/K2.md`, `docs/design/K2.md`.

## Owner feedback channel (Telegram bot)
The owner playtests in real survival and sends fixes + screenshots to a Telegram bot made for us. The token is NOT in
the repo (public). It is in `~/.config/hearthholm/telegram_bot_token` (lost on container restart → ask the owner once and
save it there again, chmod 600). Read only new messages:
`curl -s "https://api.telegram.org/bot$(cat ~/.config/hearthholm/telegram_bot_token)/getUpdates?offset=<last_update_id+1>"`;
photos: `getFile?file_id=` → `https://api.telegram.org/file/bot<token>/<file_path>` into the scratchpad. Last read
update_id = **331527542**. Ignore the old .mp4/.mp3 documents (another project).

## RESUME HERE (2026-11-19)
- **Owner plan (2026-11-19):** the owner is play-testing the next professions one by one and will give direction on 2026-11-20; the project then
  goes profession by profession, each polished "to ideal". There is NO "K3" in any plan: after K2 only the idea list at the bottom exists.
  Do not start a new wave before the owner's message; first make sure K2 is green and packaged.
- **K2 status:** implemented, `HH_ONLY_K2=1` passes alone (workday, leisure, unload to the work-site chest and to the warehouse, once per workday, exact miner
  pace vs the old formula). Full-run findings fixed on the way: wave D healer test needs the civilians' workday (`time set 2000` for that phase);
  warriors/knights keep the old hours (D/I2 tests rely on them at 10000); wave C chieftain minion count is sampled every 10 ticks (one count after
  50 ticks was a race. The three minions DO spawn (max seen = 3); who removes them within 50 ticks is not established: the settlers or the test's noon sun, unverified).
- **K1 is finished.** `HH_ONLY_K1=1`: `K1 RESULT: OK` + `HHTEST DONE` (skip-missing, panes/chests/fences connected, stake keep/carry, 75 recipes without
  collisions, recipe book 77/77, chronicle pages, co-owners). **Root cause of the two old full-run failures** (`skip failed`, `panes missing`): NOT the builder
  logic but the test platform. The blueprint floor was cleared out of a STONE layer with an iron SHOVEL (wrong tool, x100): 76..195 ticks per block depending on
  the settler's random traits, 20 blocks overran the 2400-tick phase A budget, so nothing else was built yet (panes still air). Seen in a real full run
  (progress 11 -> 23 in 2400 ticks). Fix: the top layer of the test platform is DIRT (phase A now ends after ~240 ticks). The "stale builder job" theory
  was wrong; the test still resets the builder (UNEMPLOYED -> BUILDER) and switches off other stakes, harmless.
- Full client test in this 4-vCPU container: early runs failed at random places (wave D `warehouse full` once, J2 `tree not chopped` once, wave C
  chieftain minions once); the last full run passed C, D, E, F, G, H, H2, H3, I1, I2, J1, J2, J3 and failed only at K1 (fixed above). If D/J2 fail again, they are
  fixture timing (D `stockUp` now prints positions/loaded containers/empty slots). Note `HH_ONLY_<W>=1` means "that wave and every later one except K1" (K1 runs only with `HH_ONLY_K1`
  or with no flag), so `HH_ONLY_D` is ~20 min.
- New container checklist (this one had none of it): `apt-get install openjdk-25-jdk-headless libegl1 libegl-mesa0 libgles2 libgbm1`
  and `ln -s /usr/lib/jvm/java-25-openjdk-amd64 /opt/jdk-25`. Without EGL the client opens no window, Gradle still says
  BUILD SUCCESSFUL and `run-client-test.sh` prints `exit=0` with ZERO `HHTEST` lines: always check for `HHTEST DONE`.
  Allowed hosts needed: maven.fabricmc.net, meta.fabricmc.net, piston-meta/piston-data.mojang.com, libraries.minecraft.net,
  resources.download.minecraft.net, launcher.mojang.com. api.modrinth.com / cdn.modrinth.com are NOT allowed there, so
  `package-mod.sh` cannot run; the K1 zip was assembled by hand (fresh jar + the Fabric API jar and УСТАНОВКА.txt from the previous zip,
  same versions). Maven Central may answer 429 once: just rerun the build.
- Token economy: run long tests with plain background bash (`run-client-test.sh ... > file; tail`), not inside an agent.
  Gemini CLI is unusable (Google ended personal-subscription access, June 2026). Next: **K2**.
- Default branch is the old `claude/msayib-roblox-dev-skill-setup-xhh2j6`; it has 4 video commits (Aatrox vs
  Mordekaiser, calibration) not yet in `OpeRaGTX/video-studio`; move them there BEFORE merging PR #1.

## Waves K1 and K2 (both implemented, kept as the reference of the owner decisions)
**Retest note:** owner reported "drawing on the zone map does nothing" (J2 build); J3 could not reproduce it with real
simulated mouse input, raised edit reach to 16 and now shows the rejection reason in chat. Ask the owner to retest J3.

**K1 — builder, recipes, co-owners**
0. Zone map brush bug (owner retest, Windows): MC 26.3 SDL uses `InputConstants.MOUSE_BUTTON_LEFT=1, RIGHT=3`; `ZoneMapWidget`/`ZoneMapScreen` and J2/J3 tests used 0/1 → left click panned. Fix = constants everywhere (sent to the K1 agent).
1. Builder (owner photo: glass panes and chests stand unconnected):
   - after placing, update neighbour shapes: panes, fences, walls, iron bars, chests (double chests), stairs;
   - attachables (torches, lanterns, ladders, buttons, wall signs, trapdoors, doors) are placed only after their support
     block, never into air; facing is kept;
   - missing material: skip that block, keep building the rest, return to skipped blocks when materials arrive;
   - when the build is finished the stake is NOT dropped: it stays (option) or the builder carries it to the warehouse chest.
2. Recipes:
   - unlock ALL mod recipes in the recipe book on login;
   - add a Founder's Chronicle chapter with recipe pictures;
   - signpost and miner stone recipes must not collide with vanilla. The test checks every mod recipe against vanilla
     recipes for collisions.
3. Town hall "Add owner":
   - list of online players with "Add", co-owner list with "Remove";
   - co-owners manage everything; only the founder removes co-owners or breaks the town hall;
   - Codec default is an empty list.

**K2 — daily routine, miner pace**
1. The workday starts when undead begin to burn (sunrise, MC time ~0 = 06:00) and lasts 0.75 of daylight
   (~06:00–15:00). Then leisure (walk, talk, tavern) until dark; sleep at night. Guards keep their shifts.
2. At the end of the shift settlers deposit EVERYTHING except the held tool/weapon, armor and a couple of food items:
   to the chest near their work site, else the warehouse. Lumberjack: revert the J1 sweep filter (pick up all junk again).
3. Miner 2x slower than now; reaches the current (I1) speed only at level 5.

## Orchestration notes
- One background Sonnet agent at a time (they share files). Test rules are in AGENTS_BRIEF "Test runs": develop on
  `HH_ONLY_<wave>=1`, one full run at the end (~15–18 min).
- After a rate limit (429) or a container restart: commit+push the agent's leftover changes, then SendMessage the same
  agent to continue (it keeps its context). The stop hook demands pushing WIP whenever the tree is dirty or ahead.
- Verify each wave yourself: `git log`, `HHTEST DONE`, 1–3 new screenshots; then send the zip to the owner (SendUserFile).

## Remaining gaps / ideas
- Balance is theory + headless test only: tune after real play (now via `config/hearthholm.json`).
- End dimension untested for the party; raid casualties non-deterministic.
- Legendary hire, mason and smith lightly covered by the test.
- Ideas: ghost preview of blueprints, families and children, custom particle types, Balance as a JSON config.

## Agent prompt template
"Read `minecraft/mods/hearthholm/AGENTS_BRIEF.md` first and follow its token rules. Implement wave X: <scope>.
Extend the client test (keep ≤ ~14 min), review only your new screenshots, update README/DESIGN briefly (RU),
package, commit, push. Never ask questions. Report ≤ 20 lines." On HTTP 429/crash: agent commits WIP; resume it
via SendMessage (or start a new agent pointing at `git log`).
