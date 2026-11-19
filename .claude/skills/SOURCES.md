# Roblox skills — sources

Vendored copies of third-party Agent Skills. Update by re-copying from upstream.

| Skill | Upstream | Commit | License |
|---|---|---|---|
| roblox-dev-skill | MSayib/roblox-dev-skill | d5cfb72 | MIT |
| roblox-ui, luau-expert | afrxo/roblox-agent-skills (`skills/`) | ff50be7 | MIT |
| building-3d-objects, building-maps, roblox-design-consultant, roblox-ui-mastery | ohzw/roblox-dev-skills (`.claude/skills/`) | 8eb7488 | none declared upstream |
| roblox-ui-implementation, roblox-game-ux, roblox-world-level-design, roblox-asset-pipeline, roblox-animation-audio-feedback, roblox-luau-architecture, roblox-game-development-lifecycle | AshExplained/roblox-skills | a3a7b94 | MIT |
| survival-crafting, game-ai, ai-behavior-trees-utility-ai, audio-design, procedural-gen, roblox-characters, roblox-physics | gamedev-skills/awesome-gamedev-agent-skills (`skills/`) | d4b0e35 | Apache-2.0 (LICENSE + NOTICE in each folder) |
| roblox-genre-patterns, roblox-combat-systems, roblox-quest-progression-systems, roblox-economy-balancing, roblox-performance-optimization, roblox-playtest-qa, roblox-mobile-playtest, roblox-security-economy, roblox-social-systems, roblox-debugging-bugfix, roblox-policy-compliance | AshExplained/roblox-skills | a3a7b94 | MIT |
| plan-first | written for this repo | — | — |

## Video skills

| Skill | Upstream | Commit | License |
|---|---|---|---|
| remotion-best-practices, remotion-create, remotion-markup, remotion-render, remotion-studio, remotion-captions, remotion-docs, remotion-interactivity, remotion-maps, remotion-multimedia, remotion-saas, remotion-upgrade | remotion-dev/skills (`skills/`, official, v4.0.537) | 039e8a2 | none declared upstream (Remotion itself: Remotion License) |
| remotion-video, animation-principles, motion-background, beat-sync-editing, logo-animation, motion-art-direction, color-motion, shot-composition, after-effects | iart-ai/motion-design-skills | 3c129f7 | MIT |
| launch-video, ad-creative-video, testimonial-video | iart-ai/ad-video-skills | 0de0f2a | MIT |
| short-form-video, caption-animation, lower-thirds, countdown-video | iart-ai/tiktok-video-skills | 2a77533 | MIT |
| kinetic-typography | iart-ai/kinetic-typography-skills | fccc94b | MIT |
| youtube-intro-outro, audiogram | iart-ai/youtube-video-skills | f3df381 | MIT |
| ffmpeg-skill | kajisho5/ffmpeg-skill (SKILL.md, scripts, references, templates) | 1ae1bdf | MIT |
| threejs-animation, shader-glsl, particle-system | iart-ai/webgl-animation-skills | 50697d6 | MIT |
| creative-brief, video-delivery-specs | iart-ai/freelance-motion-skills | d85c2b4 | MIT |
| blender-director, camera-cinematography, animation, lighting, rendering, compositing, vfx-fx | arjun988/blender-skills (`.cursor/skills/`; shared `references/` moved to `blender-director/shared/`) | 8f778d2 | MIT |
| blender-toolkit | Dev-GOM/claude-code-marketplace (`plugins/blender-toolkit/skills/`; plugin hooks not used, local-install note added on top of SKILL.md; CLI built in `scripts/dist`) | eed6aa2 | Apache-2.0 |
| character-animator | own (this repo): studio animation method over `video/tools/anim/` | — | — |

Blender skills assume a Blender MCP. Locally the `blender` MCP server is configured in `.mcp.json` (ahujasid/mcp-for-blender,
MIT, telemetry off); its addon and the other Blender add-ons are gathered by `video/tools/addons/fetch_addons.sh`.
In cloud sessions Blender runs headless as the `bpy` module instead: `video/tools/setup-blender.sh`.

Animation tools used by `character-animator` (installed into git-ignored `video/tools/anim/vendor/`):

| Tool | Upstream | Commit / version | License |
|---|---|---|---|
| MCP for Blender (server via `uvx mcp-for-blender`, addon) | ahujasid/mcp-for-blender (formerly blender-mcp) | PyPI latest | MIT |
| MoMask text-to-motion (`vendor/install_momask.sh`) | EricGuo5513/momask-codes + HumanML3D weights (Google Drive) | 94a6636 | code MIT; weights trained on HumanML3D/AMASS — research / non-commercial |
| Wiggle 2 (optional live spring bones; loads in Blender 5.2) | shteeve3d/blender-wiggle-2 | v2.2.3 | GPL-3.0 |
| Not installed: mixamo-llm-mocap (+ GVHMR) | squall01337/mixamo-llm-mocap, zju3dv/GVHMR | — | MIT; GVHMR non-commercial; needs ~8 GB VRAM and SMPL-X (registration) |

## TikTok content-planning skills

| Skill | Upstream | Commit | License |
|---|---|---|---|
| content-strategy, social, video, marketing-psychology, marketing-council, marketing-ideas, copywriting, analytics | coreyhaines31/marketingskills (`skills/`, evals dropped) — ~58k★ | 88c79f6 | MIT |
| last30days | mvanhorn/last30days-skill (`skills/last30days`, demo `assets/` dropped) — ~66k★ | 6894d44 | MIT |
| yt-shorts, yt-retention, yt-viral, yt-script, yt-plan, yt-audit | Jakeschincariol/youtube-agent-skill — ~4k★ | a2feb21 | MIT |
| ig-reel, ig-viral, ig-plan | Jakeschincariol/instagram-agent-skill — ~4k★ | d03c56b | MIT |
| scrapecreators-api, trend-discovery, outlier-post-finder, comment-mining, creator-profile-teardown, competitor-social-research, audience-research, transcript-intelligence | ScrapeCreators/social-media-research-skills (need `SCRAPECREATORS_API_KEY`) — ~5k★ | 64ba7b4 | MIT |
| tiktok-content-team | own (this repo): orchestrator for the Wild Rift channel, data in `video/content/` | — | — |

## Writing / humanizer skills

| Skill | Upstream | Commit | License |
|---|---|---|---|
| humanizer | blader/humanizer | 8c307f3 | MIT |
| humanizer-ru | smixs/humanizer-ru | ff990df | MIT |

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
