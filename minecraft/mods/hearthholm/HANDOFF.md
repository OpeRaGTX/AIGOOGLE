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

- **M1 bugs and small things** (`docs/waves/M1.md`): blueprint names + `util/Labels` fallback (never a raw key), `Workshop.smelts()` (furnace hint only where it matters), owners/co-owners are never fought by settlers
  (`SettlerEntity.isProtectedOwner`), `SettlementService.notify` reaches online co-owners, hunger x2.5 (`HUNGER_RATE_PERCENT`), shift-end `BaseJob.keepPlan` + `Job.shiftEndKeep/shiftEndFood` (miner keeps torches, ladders, filler, 8 food),
  builder picks pickaxe/shovel per block (`ToolKit`). Test `WaveM1Test` (`HH_ONLY_M1=1`).
- **M2 stake and miner** (`docs/waves/M2.md`): check marks per material + "Order missing" button (`crafting/MaterialOrders`, idempotent), miner `direction` option (forward/right/left/back/all) over the SHARED staircase
  (`MinePlanner.arms`, spiral hub behind the pillar), branches 12 long (`MINE_BRANCH_LENGTH`). Test `WaveM2Test` (`HH_ONLY_M2=1`).
- **L1 combat roles** (`docs/waves/L1.md`, package `combat/`): `CombatRoles` (hp 100/90/81, speed 80/100/100), `Distress`, `Shields`, `Strafe` (edge-safe), `Promotions` (25 marks: guard to knight, archer to crossbowman, warrior to cavalryman),
  `Cavalry` + `CavalryChargeGoal` + `CavalrymanJob` (settler steers the horse by handing out the HORSE's navigation, see docs/design/L1.md). Test `WaveL1Test` (`HH_ONLY_L1=1`).
- **L2 zones** (`docs/waves/L2.md`): farmer 30x30 / fisher 10x10 / warrior 50x50 on the J2/J3 framework (`zone/ZoneModules`, `WorkZone.Spec.FARMER/FISHER/WARRIOR`), new markers (fertilizer barrel, fish bucket, sword in stone, generated in `tools/gen_assets.py` + `gen_l2_tex.py`).
  Test `WaveL2Test` (`HH_ONLY_L2=1`). Markers must stand inside the settlement radius (village 72) to register as sites.
- **L3 visuals** (`docs/waves/L3.md`): own particles (`registry/ModParticles`, `client/particle/HearthParticles`, `tools/gen_l3_tex.py`) and the 30% ghost blueprint (`client/GhostPreview`, called from `ZonePreview`). Test `WaveL3Test` (`HH_ONLY_L3=1`).
- Test dispatch: `HH_ONLY_<W>=1` for the legacy waves D..J3 means "this wave and the later legacy ones"; the **standalone** waves K1, K2, L0, M1, M2, L1, L2, L3 run only alone (or several at once: `HH_ONLY_L1=1 HH_ONLY_L2=1`) or in the full run;
  waves M1 and later collect their failures (`WAVE <x> FAILED`) so the others still run. New wave = flag + `standalone` entry + one line in `HearthholmClientTest`.
- Tools: `minecraft/tools/tg-report.sh <text> [--zip f] [photos...]` sends the Telegram report (token/chat in `~/.config/hearthholm/`), `minecraft/tools/package-offline.sh <mod dir>` repacks the zip with a fresh jar when Modrinth is unreachable.
  Screenshots of the night run are in `docs/screenshots/hh_m1_*, hh_m2_*, hh_l1_*, hh_l2_*, hh_l3_*`.

## Owner feedback channel (Telegram bot)
The owner playtests in real survival and sends fixes + screenshots to a Telegram bot made for us. The token is NOT in
the repo (public). It is in `~/.config/hearthholm/telegram_bot_token` (lost on container restart → ask the owner once and
save it there again, chmod 600). Read only new messages:
`curl -s "https://api.telegram.org/bot$(cat ~/.config/hearthholm/telegram_bot_token)/getUpdates?offset=<last_update_id+1>"`;
photos: `getFile?file_id=` → `https://api.telegram.org/file/bot<token>/<file_path>` into the scratchpad. Last read
update_id = **331527548**. Ignore the old .mp4/.mp3 documents (another project).

## L0 DONE (2026-11-19 evening): lumberjack + levels + config
- Lumberjack: `LumberjackJob.Fell` (walk, chop from up to 6.2 blocks when the approach fails, give an unreachable tree a 1200-tick rest instead of retrying it forever); levels
  1.5x slower (`Balance.LEVEL_XP` {0,150,450,900,1425}); `config/hearthholm.json` now carries `CONFIG_VERSION` (2): an older file is moved to `.v1.bak` and the new defaults are used.
  `WaveL0Test` (`HH_ONLY_L0=1`) OK; docs `docs/waves/L0.md`, `docs/design/L0.md`.
- **Real cause of the random J2 failures** ("tree not chopped", "radius behaviour no longer chops"): the test trees stood on GRASS blocks, which are not in `BlockTags.DIRT`; the
  lumberjack only counts a log with a dirt-tag block below, so a tree became choppable only when the grass randomly turned into dirt (~1400 ticks). Fixed in the J2/L0 tests by putting
  dirt under the trunk (worldgen does the same). Production code was fine.
- Other test flakes fixed on the way: wave E fuel check (coal count vs concurrent deliveries), I2 loot (archer frozen), C chieftain minions (sampled), D healer hours.
  Still flaky and unrelated: wave C `brute did not break through the palisade` (the brute wanders off 34 blocks), seen once in about eight runs.
- (L1, L2 and L3 were done in the night run of 2026-11-19 to 20.)

## NIGHT PLAN 2026-11-19 -> 20: DONE (M1, M2, L1, L2, L3 implemented, each tested alone with its screenshots; see the Status lines above; the report of each wave went to Telegram)
(original plan text below, kept as the reference of the owner decisions; owner asleep: work through it without questions, report to Telegram after every wave)
Owner decisions (answered 2026-11-19 late): **order = bugs and small things first**; miner directions = **one choice on the stone** (forward / right / left / back / all);
food kept at the end of a shift = **miner 8, everybody else 2**; torches, ladders, pickaxes and tools are never handed in by miner and builder; reporting = **Telegram report + zip after every wave**.

**Telegram reporting** (owner's own bot chat): token in `~/.config/hearthholm/telegram_bot_token`, chat id in `~/.config/hearthholm/telegram_chat_id` (both chmod 600, NOT in the repo; if a
file is missing: ask the owner for the token once, the chat id is `message.chat.id` in `getUpdates`). After each finished wave:
`curl -s -X POST "https://api.telegram.org/bot$TOK/sendMessage" --data-urlencode chat_id=$CHAT --data-urlencode text="..."` and
`curl -s -X POST "https://api.telegram.org/bot$TOK/sendDocument" -F chat_id=$CHAT -F document=@minecraft/dist/hearthholm-0.1.0-mc26.3.zip -F caption="..."`
(zip is ~3 MB, limit 50 MB). Keep reports short: what changed, test evidence, what to look at in the game, known gaps. Last read update_id = 331527548.

**Order of work** (each wave: implement, own test `HH_ONLY_<W>=1`, docs `docs/waves|design/<W>.md` + TOC lines, zip, commit, push to `claude/lucid-hypatia-ri79og`, Telegram report;
one full run at the very end; unrelated known flaky tests are NOT worth more than one rerun):
1. **M1 bugs and small things** (all from messages 162-165):
   a. Blueprint names show the raw key `blueprint.hearthholm.c...` (photo 162): 4 of 16 blueprints have no names in `tools/gen_lang.py`: `cottage`, `townhouse`, `fisher_hut`, `l_house`
      (EN + RU). Also add a fallback label (humanized id) when a key is missing, and a test that every id in `blueprints/index.json` has both translations.
   b. The carpenter's workshop message "В комнате нет печи: плавки не будет" (`gui.hearthholm.heat_no`, photo 164): show the furnace hint only for workshops that
      smelt (find which: furnace recipes via `Recipes.usable(level, item, kind, true)` / `CraftPlanner`), never for the carpenter.
   c. **Owners vs guards:** a player who is an owner or co-owner (`Settlement.owner`, `Civic.coOwners`) who hits a guard must NOT be attacked or killed by guards (find the
      retaliation target logic of settlers/guards; the hit may still hurt, no retaliation, no "hostile" memory). Strangers keep the old behaviour.
   d. **Co-owners get the village chat notifications** the founder gets (raids, tier-ups, deaths, hunger...): every message sent to the owner also goes to every online co-owner.
   e. **Food use x2.5:** `SettlerEntity.tickNeeds` hunger rate `1/1800` per tick x2.5 (new `Balance.HUNGER_RATE_PERCENT = 250`; the settlement ate too little, food piled up).
   f. **End-of-shift unload keeps work supplies** (my K2 `BaseJob.keptGear/unloadInto`): add a per-job hook (`Job`/`BaseJob.keepsAtShiftEnd(ItemStack)`): the miner keeps torches,
      ladders, filler blocks he needs, his pickaxes, and up to **8** food (`SHIFT_END_KEEP_FOOD_MINER`; everybody else 2); the builder keeps shovel and pickaxes.
   g. **Builder fetches pickaxes** (like the shovel): `BuilderJob` tool handling takes a pickaxe from chests/warehouse when he has none or it broke, and digs stone/ores with it
      (equip the best tool for the block being cleared); today he clears stone by hand/shovel (x100 slower).
2. **M2 stake and miner QoL:**
   a. **Stake screen: checkmark / cross** per material (warehouse has enough / not) next to the icons (photo 162: "Материалы (9x7, высота 10)"), needs the stock counts from the server
      (extend the site payload); plus a button **"Добавить недостающее в заказ"**: orders for the missing items go to the workshops present in the settlement
      (`SettlementService.setOrder`), only items some present workshop can craft, the rest stay crosses with a chat hint; pressing twice must not duplicate orders.
   b. **Miner tunnels:** longer branches (`MinePlanner`/`DrawnPlanner`, new Balance key for the branch length, he under-digs the sides now) and a **direction option** on the stone:
      forward (the way the marker/sign faces) / right / left / back / all. He goes down the EXISTING access stair and digs the branch in the chosen direction, never re-digging the
      shaft; test: a plan per direction, the stair is shared.
3. **L1 combat roles** (spec in the next section; cavalryman LAST inside L1, highest risk: an NPC rider has to steer a horse; the vanilla `WOODEN/IRON/NETHERITE_SPEAR` items exist in 26.3).
4. **L2 work zones** (farmer 30x30 + fertilizer barrel, fisher 10x10 + fish bucket, warrior 50x50 + stone-with-sword marker), reuse `WorkZone`/`ZoneMapService`.
5. **L3 visuals** (custom particles, ghost blueprint preview ~30%).
Verbatim of the new Telegram messages (Russian):

```
[message 163]
Исправить баг с выводом названий чертежей, на множестве есть такая ошибка + добавить фичу, что галочкой отмечено то что есть на складе, а то чего нет крестиком.

[message 164 (photo caption)]
Какие функции сейчас исполняет плотник? В комнате нет печи - плавки не будет, что это за сообщение и если там обязательна печь и какая именно, то почему она не входит в чертеж дома

[message 165]
С плотником разобрался но сообщение с печью не особо актуально для дома плотника.

Дополнительно хочу добавить им такую логику:
На колышке строителя будет как я ранее сказал галочка и крестик, и рядом будет кнопка «добавить недостающее в заказ» чтобы автоматически то чего не хватает перешло в заказы к мастерам которые присутствуют в поселении.

Увеличить расход еды в 2.5 раза у поселения, т.к сейчас слишком быстро еда копиться

Также шахтеру в его тоннели добавить дальность самих веток, т.к сейчас он мало обкапывает по сторонам, и добавить направление «вперед к табличке, вправо, влево, назад» чтобы он мог копать в противоположных направлениях к таблице, но чтобы не перекапывал свой спуск вниз из-за этого, а спускался по уже имеющемуся и копал в другом направлении.

Также шахтер сейчас выкладывает факелы и еду в сундук по окончанию дня, что неудобно, нужно сделать чтобы они не выкладывали нужные инструменты для работы

И еще хочу чтобы строитель тоже автоматически в инструменты брал себе кирки для работы, т.к руками ему долго обкапывать если кирка сломалась и ты этого не заметил.

Также когда игрок которого добавили в владельцы деревни случайно бьет стражника, стражники нападают на него и убивают, этого не нужно делать, и плюс новому владельцу деревни не приходят уведомления о деревне в чат, он их не видит

пока на этом остановимся
```

## OWNER REQUEST 2026-11-19 evening (Telegram message 161, final edited text) - THE NEXT WORK, start after K1/K2 are packaged
Owner: "for now these changes; after my test we go on with everything else. Write the code architecturally, so functions can later be changed or
reworked without breakage." Verbatim (Russian):

```
Разница между воином и стражником должна быть более видимой:
Стражник должен получать сигнал о том, что кто то из жителей атакован, и бежать туда , где его атаковали, и убивать враждебного моба, также стражник должен быть больше по здоровью на 10% чем воин, и медленнее на 20% бегать.
Воин должен патрулировать территорию, без знака территорию поселения, со знаком территорию отмеченную игроком.
Также у него должно быть на 10% меньше здоровья чем у стражника, но он должен быть быстрее чем стражник.
При этом каждому из них можно выдать щит, которым он сможет блокировать удары врагов. 
Лучник должен быть меньше чем воин по здоровью на 10%, и стрелять во врагов по логике скелета, то есть ходить в бок или взад при прицеливании, чтобы по нему было сложнее попасть или догнать. Также лучнику можно выдать арбалет, если он достиг 5го уровня, и ему был повышен ранг до арбалетчика за 25 марок (столько же стоит повышение стражника до рыцаря, а воина можно повысить до Кавалериста, с этого момента, если на территории поселения есть лошадь, и в сундуках, либо на лошади, либо у него, есть седло, Кавалерист может сесть на седло а ему можно выдать копье, которым он будет таранить врагов, бежать дальше, разворачиватся и снова таранить врагов.)

Также понизить скорость повышения уровней жителей уменьшено еще в 1.5х раз для баланса.

Для фермера, и рыбака, воина, мы делаем логику как у шахтера и лесоруба.

У фермера будет бочка с удобрениями, по нажатию правой кнопкой мыши теперь можно дополнительно выбрать на карте где он будет работать, зона 30х30 нарисована.

У рыбака ведро с рыбой (рыбу можно взять текстуру с самого майнкрафта), и можно настроить 10х10 блоков на карте где он будет рыбачить

У воина можно на марке выбрать зону 50х50, по которой он будет патрулировать и обходить. Марка теперь не таблица, а аккуратная модель камня в который воткнут железный меч.

Также подтверждаю концепт по поводу своих частиц, и подтверждаю концепт призрачных принтов зданий для строителя. Это тоже хочу чтобы было реализовано, прозрачность примерно 30%, но можешь определить на глаз, если не понравится мой вариант.

Также нужно исправить баг с лесорубом, т.к. если листва мешает ему добраться до дерева он стоит и смотрит на него вечно. Нужно чтобы он либо прокапывал листву либо все равно добывал это дерево с немного большего расстояния, это реализуй так как это будет проще и меньше шансов что сломает оптимизацию.

Пока такие нововведения,делаем, после моего теста пойдем по всему дальше. Также не забывай писать архитектурно код, чтобы потом можно было без поломок поменять какие либо функции, и переработать их если что-то изменится не в лучшую сторону.
```

**Planned split (one wave at a time, test + docs + zip + push after each; defaults chosen where the text is ambiguous):**
- **L0 quick fixes:** (a) lumberjack stuck looking at a tree behind leaves: dig the leaves OR chop from a slightly larger distance, whichever is simpler and cheaper
  for performance; (b) level-up speed 1.5x slower again (`Balance.LEVEL_XP` x1.5, for every profession).
- **L1 combat roles:** guard = warrior HP x1.1 and 20% slower; warrior = guard HP x0.9 (the text says "10% less than the guard") and faster than the guard; archer = warrior HP
  x0.9. Guard gets a distress signal when ANY resident is attacked: runs to the place and kills the hostile mob. Shields for guard and warrior (offhand, real blocking: today only
  the knight blocks, see `SettlerEntity` knight shield code). Archer strafes like a skeleton while aiming (sideways/back). Crossbow for the archer at level 5 via promotion
  "crossbowman" for 25 marks; knight promotion (from guard) also costs 25 marks (now 10); warrior -> "cavalryman" for 25 marks: needs a horse in the settlement and a saddle (in the
  chests, on the horse or on him), mounts it, holds a spear (check the vanilla spear item of 26.3) and charges: ram, ride on, turn, ram again.
  Warrior without a territory marker patrols the settlement, with a marker the player's marked territory.
- **L2 work zones (the miner/lumberjack WorkZone + ZoneMapService framework, J2/J3):** farmer: barrel with fertilizer, right click opens the map, 30x30 zone; fisher: bucket with a
  fish (vanilla fish texture), 10x10 zone; warrior: the marker block becomes a neat model "stone with an iron sword stuck in it" (not a table), 50x50 patrol zone.
- **L3 visuals:** own particle types (concept confirmed) and ghost blueprint preview for the builder (concept confirmed, ~30% opacity, judge by eye).
Defaults to remember: ask nothing, record assumptions in the wave docs; keep every new number in `Balance`/config; keep new behaviour in separate small classes/activities
(like `ShiftEndActivity`) so it can be swapped.

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
