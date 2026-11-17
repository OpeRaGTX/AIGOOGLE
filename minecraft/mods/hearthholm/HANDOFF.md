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
  - Test `src/gametest/...` (`HearthholmClientTest` + per-wave classes such as `WaveDTest`). `HH_ONLY_D=1` = wave D only.
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

## Status (all built, tested headless, pushed)
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

## Next
- **Wave E "Square and market"** (CONCEPTS.md §E): tier-unlocked buildings (hamlet → workshops, village → tavern &
  market, town → barracks & walls); new blueprints (tavern, market stall, farmhouse, wall section, workshop);
  tavern + innkeeper profession (evening gathering, mood); trader caravan weekly with llama, buys surplus for hearth
  marks, sells seeds/saplings/blueprints/rare-trait candidates; decor blocks (lamp post, signpost, settlement banner
  with colors, flower box, bench, well, counter, workshop signs) + "Beautiful street" thought; bulletin board quests
  (bring items, kill mob near a site) rewarding marks/renown/gifts. Also fix: fuel consumption, roof burning.
- **Wave F "Polish"**: in-game guide book "Founder's Chronicle" given on founding; balance pass; particles/sounds;
  fresh screenshots into `docs/screenshots/`; README/DESIGN refresh; final zip.

## Agent prompt template
"Read `minecraft/mods/hearthholm/AGENTS_BRIEF.md` first and follow its token rules. Implement wave X: <scope>.
Extend the client test (keep ≤ ~14 min), review only your new screenshots, update README/DESIGN briefly (RU),
package, commit, push. Never ask questions. Report ≤ 20 lines." On HTTP 429/crash: agent commits WIP; resume it
via SendMessage (or start a new agent pointing at `git log`).
