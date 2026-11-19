# AIGOOGLE — Minecraft Java mods (Fabric, MC 26.x)

- Project skills live in `.claude/skills/` (sources: `.claude/skills/SOURCES.md`).
- For any non-trivial task, follow the `plan-first` skill: understand → ask key questions → plan → build.
- Keep token use low: targeted reads, short outputs, concise answers.
- The user writes in Russian — reply in Russian.
- Mods live in `minecraft/mods/<modid>/`; start any mod task with the `minecraft-launcher-run` skill (toolchain, which
  skill for what, packaging for the official launcher). Hearthholm: read `minecraft/mods/hearthholm/HANDOFF.md` first.
- Gradle and test runs: write output to a file and show only errors (`> /tmp/b.log 2>&1; grep -iE 'error|FAIL' /tmp/b.log | head`).
- Other projects moved out: video studio → `OpeRaGTX/video-studio`, Roblox game → `OpeRaGTX/roblox-game`.
