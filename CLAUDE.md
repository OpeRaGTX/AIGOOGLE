# AIGOOGLE — Roblox survival-horror game

- Project skills live in `.claude/skills/` (sources: `.claude/skills/SOURCES.md`).
- For any non-trivial task, follow the `plan-first` skill: understand → ask key questions → plan → build.
- Keep token use low: targeted reads, short outputs, concise answers.
- The user writes in Russian — reply in Russian.
- TikTok video production lives in `video/` (studio: one folder per video in `video/projects/`). For any video task
  read `video/CLAUDE.md` first.
- Roblox Studio is reached through its built-in MCP server named `Roblox_Studio` (setup: `ROBLOX_STUDIO_SETUP.md`). Call `list_roblox_studios` once, pass `studio_id` on every call; save the place before large edits.
- Minecraft Java mods (Fabric, MC 26.x) live in `minecraft/mods/<modid>/`; start any mod task with the
  `minecraft-launcher-run` skill (toolchain, which skill for what, packaging for the official launcher).
- Gradle and test runs: write output to a file and show only errors (`> /tmp/b.log 2>&1; grep -iE 'error|FAIL' /tmp/b.log | head`).
