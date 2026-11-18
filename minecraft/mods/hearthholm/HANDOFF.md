# HANDOFF — read this first after /clear

Orchestrator notes for continuing **Hearthholm** (Fabric, MC 26.3). Owner writes Russian → answer in Russian,
briefly. Owner wants full autonomy: **no questions**, be creative, focus on city-building (Stonehearth / Craft the World
mechanics, no copied names/art). The orchestrator does **not** write code: it plans a wave, delegates it to ONE
background Sonnet agent (`Agent`, model sonnet, `run_in_background`), verifies, then starts the next wave.

## Where things are
- Mod: `minecraft/mods/hearthholm/` (package `io.github.operagtx.hearthholm`).
  - `AGENTS_BRIEF.md` — rules every coding agent must follow (token budget, toolchain, javap lookup, conventions,
    finish steps). Always tell agents to read it first.
  - `DESIGN.md` architecture (RU), `README.md` player guide (RU, one section per wave), `CONCEPTS.md` waves A–F.
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
