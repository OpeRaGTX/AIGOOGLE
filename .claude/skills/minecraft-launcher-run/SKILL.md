---
name: minecraft-launcher-run
description: >
  Build a Fabric mod in this cloud container and hand the player a zip that runs from the
  official Minecraft Launcher (Fabric installer profile + mods folder). Use when the user asks
  to "собери мод", "дай jar", "как запустить мод", "установи в лаунчер", "проверь что мод
  грузится", or after finishing a mod feature that the user will want to play. Router for the
  whole Minecraft mod toolchain in this repo.
---

# Build → package → play in the official launcher

Mods live in `minecraft/mods/<modid>/` (one Gradle project each). Player zips go to `minecraft/dist/`.

## Which skill for what (load only what the step needs)

| Step | Skill |
|---|---|
| Detect loader/versions of a project | `mc-project-detect` |
| New project / build files | `fabric-new` (scaffold), `fabric-project-setup` |
| Check a real 26.x API before using it | `mc-source-lookup` (never write MC code from memory) |
| Blocks, items, recipes, lang | `mc-blocks-items`, `minecraft-modding` |
| Mobs and behavior (AI goals, brains, spawns) | `mc-entities`; decision design: `game-ai`, `ai-behavior-trees-utility-ai` |
| Change vanilla code | `fabric-mixins` |
| Packets / HUD / screens / keys+config | `fabric-networking`, `fabric-client-rendering`, `fabric-config-and-keybinds` |
| Textures 16×16 PNG | `pixel-art-studio`; models/blockstates/item defs: `minecraft-resource-pack` |
| Blocky mob/prop models | `3dixel` (then port the cubes to an `EntityModel`/JSON model) |
| Recipes, loot, tags, worldgen JSON | `minecraft-datapack`, `minecraft-world-generation` |
| Build errors, crashes, logs | `mc-build-loop`, `fabric-crash-debugging` |
| Game tests, CI, releases | `minecraft-testing`, `minecraft-ci-release`, `fabric-release` |
| Bump Minecraft version | `fabric-version-porting`, `fabric-port` |

## 1. Toolchain (once per container)

```bash
bash minecraft/tools/setup-jdk.sh          # Temurin JDK 25 -> /opt/jdk-25 (26.x needs Java 25)
export JAVA_HOME=/opt/jdk-25 PATH=/opt/jdk-25/bin:$PATH
```

Versions come from `meta.fabricmc.net` / `fabricmc.net/develop`, never from memory
(checked 2026-11: MC 26.3, Loader 0.20.1, Fabric API 0.166.2+26.3, Loom 1.19, Gradle 9.8.2).
Get the Gradle wrapper with an installed `gradle wrapper --gradle-version <v>` or the official
distribution, not by copying a `gradlew` from a random repository.

## 2. Build and prove it loads

```bash
cd minecraft/mods/<modid> && ./gradlew build --no-daemon -q     # jar -> build/libs/
```

- Headless load check: `./gradlew runServer` with `run/eula.txt` = `eula=true`; wait for `Done (`,
  then `stop`. Client-only code is checked by compile + `runGametest`/client game tests
  (`minecraft-testing`). No display here, so `runClient` is for the user's PC.
- Never report "works" without a passing build; paste the first real error otherwise.

## 3. Package for the player

```bash
bash minecraft/tools/package-mod.sh minecraft/mods/<modid>
```

Produces `minecraft/dist/<name>-<ver>-mc<mc>.zip` = mod jar + matching Fabric API jar
(downloaded from Modrinth, SHA-512 checked) + `УСТАНОВКА.txt` in Russian. Commit the zip to the
work branch and push; the user downloads it from GitHub (file → "Download raw file").
Optionally add a GitHub Actions build (`minecraft-ci-release`) that uploads the same zip.

## 4. What the player does (tell the user in short)

1. Fabric installer (fabricmc.net/use/installer) → Client → Minecraft `<mc>` → Install.
   The official launcher gets an installation `fabric-loader-<mc>`.
2. `%appdata%\.minecraft\mods` (Win+R) → copy both jars from the zip's `mods/`.
3. Launcher → pick `fabric-loader-<mc>` → Play. Tip: give that installation its own game folder.

Crash on start → ask for `.minecraft/logs/latest.log` or `crash-reports/*.txt` → `fabric-crash-debugging`.

## Gotchas

- `fabric.mod.json` must depend on `fabric-api` and `"minecraft": "~<mc>"`; the zip version
  must match the profile the user picks.
- 26.1+ is unobfuscated: plugin `net.fabricmc.fabric-loom`, no `mappings` line, Mojang names
  (`Minecraft`, `GuiGraphics`, `Component`); 1.21 Yarn tutorials do not compile.
- Texture paths: `assets/<modid>/textures/item|block|entity/*.png`, lowercase names, PNG RGBA.
