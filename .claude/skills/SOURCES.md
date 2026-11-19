# Skills — sources

Vendored copies of third-party Agent Skills. Update by re-copying from upstream.

| Skill | Upstream | Commit | License |
|---|---|---|---|
| survival-crafting, game-ai, ai-behavior-trees-utility-ai, audio-design, procedural-gen | gamedev-skills/awesome-gamedev-agent-skills (`skills/`) | d4b0e35 | Apache-2.0 (LICENSE + NOTICE in each folder) |
| plan-first | written for this repo | — | — |

## Minecraft Java modding skills (Fabric, MC 26.x)

| Skill | Upstream | Commit | License |
|---|---|---|---|
| fabric-project-setup, fabric-mixins, fabric-networking, fabric-client-rendering, fabric-config-and-keybinds, fabric-crash-debugging, fabric-version-porting, fabric-release; commands `new`/`port`/`check` renamed to fabric-new, fabric-port, fabric-check | vvs1off/fabric-skills (`skills/`) | 8bdc1ba | MIT |
| mc-entities, mc-blocks-items, mc-source-lookup, mc-build-loop, mc-project-detect (mc-mixins, mc-port-version not taken: covered by fabric-*) | A2M33/mixinsmith (`skills/`) | 04edce8 | MIT |
| minecraft-modding, minecraft-resource-pack, minecraft-datapack, minecraft-testing, minecraft-ci-release, minecraft-world-generation (plugin/server/imagegen skills not taken) | Jahrome907/minecraft-agent-skills (`.codex/skills/`) | 22d61ce | MIT |
| pixel-art-studio (SKILL.md, references, scripts, pixel-art; docs/examples dropped) | Gamezxz/pixel-art-studio | f8c2466 | MIT |
| 3dixel (SKILL.md, core, examples; `images/` dropped) | mankidev/3dixel | 29cfd61 | MIT |
| minecraft-launcher-run | own (this repo): build in cloud → zip for the official launcher, router over the skills above | — | — |

Tools: `minecraft/tools/setup-jdk.sh` (Temurin JDK 25 → `/opt/jdk-25`), `minecraft/tools/package-mod.sh`
(mod jar + Fabric API from Modrinth + УСТАНОВКА.txt → `minecraft/dist/*.zip`).
