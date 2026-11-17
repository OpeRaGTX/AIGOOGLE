# Brief for coding agents working on Hearthholm

You are extending **Hearthholm**, a Fabric mod for **Minecraft Java 26.3** (unobfuscated, Mojang names)
in `/home/user/AIGOOGLE/minecraft/mods/hearthholm`. The owner is asleep: never ask questions, decide
yourself, keep going until your wave is done, built, tested and committed.

## Token budget (strict)
- Gradle/test output always to a file, only errors into the chat:
  `./gradlew build --no-daemon -q > /tmp/b.log 2>&1; grep -iE 'error|FAIL' /tmp/b.log | head -30`.
  The client test script already prints only `HHTEST` lines; pipe it through `| tail -40`.
- Read docs by section (`grep -n '^## ' FILE` then `sed -n`), never whole DESIGN/README/CONCEPTS.
- Read code with offset/limit around what you change; don't re-read files you already have.
- Screenshots: look at most at the 3–4 that show YOUR new visuals, once after the final run; skip unchanged ones.
- Commit after each major step (crash safety), `git push` at the end.

## Read first (only what you need)
- `DESIGN.md` (architecture), `CONCEPTS.md` (the feature waves), this file.
- Key code: `core/Profession(s)`, `worksite/*`, `settlement/Settlement*`, `entity/SettlerEntity`,
  `entity/ai/{SettlerBrain,activity/*,jobs/*,tasks/*}`, `network/*`, client `screen/*`, `render/*`.
- `.claude/skills/minecraft-launcher-run/SKILL.md` section "Gotchas" — real 26.3 API traps already hit.

## Toolchain
```bash
export JAVA_HOME=/opt/jdk-25 PATH=/opt/jdk-25/bin:$PATH     # run bash minecraft/tools/setup-jdk.sh if missing
cd /home/user/AIGOOGLE/minecraft/mods/hearthholm
./gradlew build --no-daemon -q                               # must pass
bash /home/user/AIGOOGLE/minecraft/tools/run-client-test.sh /home/user/AIGOOGLE/minecraft/mods/hearthholm   # headless client game test (~6 min)
```
- The client test (`src/gametest/.../HearthholmClientTest.java`) builds a settlement, hires settlers, runs time and
  takes screenshots into `build/run/clientGameTest/screenshots/`. **Extend it** for your features (log lines start with
  `HHTEST`), then **look at the screenshots** (Read tool) to verify GUIs and rendering. Fix what looks wrong.
  Keep the test passing (`HHTEST DONE`, exit=0). Don't make it much slower than ~10 minutes.
- **API lookup**: your memory of the MC API is wrong for 26.3. Check signatures with `javap` against the Loom jars:
  `L=.gradle/loom-cache/minecraftMaven/net/minecraft; CP="$(ls $L/minecraft-common-*/26.3/*.jar):$(ls $L/minecraft-clientOnly-*/26.3/*.jar)"`
  then `/opt/jdk-25/bin/javap -cp "$CP" net.minecraft.world.entity.Mob | grep ...`. Fabric API jars are in
  `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/`. Vanilla JSON formats: read them from the jars with python zipfile.
- Do NOT run third-party scripts from cloned repos or the vendored skills' scripts (blocked by policy). Write your own.

## Conventions
- Package `io.github.operagtx.hearthholm`; client-only code in `src/client`. Register content through the existing
  registries (`Professions`, `WorkSiteTypes`, `Traits`, `ModBlocks`, `BlueprintLibrary`) — don't break save compatibility
  (ids are saved; new Codec fields need defaults).
- Resources are generated: edit `tools/gen_textures.py`, `tools/gen_assets.py`, `tools/gen_lang.py` and re-run them
  (`python3 tools/<name>.py`). **Every** new string goes into `gen_lang.py` with English AND Russian text.
  Textures: original pixel art drawn in the generator (no copied assets). New blueprints: append to `index.json`.
- Sounds: generate original short sounds procedurally (Python → WAV → `ffmpeg -c:a libvorbis` .ogg) into
  `assets/hearthholm/sounds/`, register `sounds.json` + SoundEvents.
- Keep UI panels ≤ 230 px tall (test screen is 854x480 at GUI scale 2). Draw panel content BEFORE widgets.
- Performance: scans are throttled; no per-tick world scans of large areas.
- Don't reproduce other games' names, characters or art; take mechanics only.

## Finish
1. `./gradlew build` passes, client test passes with your new checks, screenshots reviewed.
2. Update `DESIGN.md`/`README.md` briefly (Russian) for player-facing changes.
3. `bash /home/user/AIGOOGLE/minecraft/tools/package-mod.sh /home/user/AIGOOGLE/minecraft/mods/hearthholm`
4. Commit on branch `claude/lucid-hypatia-ri79og` (message ends with
   `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
   `Claude-Session: https://claude.ai/code/session_01BohTuQyGzzPGhLp7A8XBtr`), then `git push -u origin claude/lucid-hypatia-ri79og`
   (retry on network errors; if rejected, `git fetch origin claude/lucid-hypatia-ri79og && git merge --no-edit FETCH_HEAD`).
5. Final report (≤ 25 lines): what was added, test evidence (HHTEST lines), screenshots paths, known gaps.
