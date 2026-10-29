---
name: tiktok-video
description: Make a TikTok / short vertical video in this repo's video studio (video/projects/<name>/). Use whenever the user asks to make, continue, fix or re-render a video, edit, ролик, эдит, тикток, клип, reel or short — e.g. "сделай ролик X", "новый эдит", "пересобери ролик", "/tiktok-video X" — or drops materials for one. Creates the project if needed, reads the brief and inbox, plans with the user, builds with Remotion/ffmpeg/Blender and delivers out/<name>.mp4.
---

# TikTok video in the studio

Everything lives in `video/` (shared engine) — read `video/CLAUDE.md` first: layout, commands, skill map, defaults.
Arguments: a project name or a new video title (may be empty → ask which video, show `npm run list`).

## 0. Where am I
- Run commands from `video/`. If `video/node_modules` is missing, this session is probably in a git worktree:
  materials, renders and node_modules exist only in the main checkout `D:\git\aigoogle\video`. Tell the user to open
  the session in `D:\git\aigoogle` without a worktree (or run `npm install` here and copy the project's inbox/).

## 1. Project
- Existing: `npm run list`, pick by (partial) name. New: `npm run new -- "<title>"` and tell the user to drop materials
  into the printed `inbox/` path (they can also use `Новое видео.bat`).

## 2. Understand (plan-first)
- Read `brief.md`, `NOTES.md`; list `inbox/` and probe each file (`ffprobe -v error -show_entries format=duration:stream=codec_type,width,height,r_frame_rate`).
  For music: `python tools/beats.py <track>` (tempo, beats, loudness) and `python tools/kicks.py <track> <project>/src/hits.json`.
- Missing essentials (idea/hook, length, track, which moments, text, 3D or not) → `creative-brief` + one AskUserQuestion
  (≤4 questions, recommended option first). Write the answers into `brief.md`.

## 3. Plan → confirm
- Short beat-aligned EDL: hook (0–2 s), segments with source + in/out, text, transitions, effects, ending/loop.
  Load the skills the plan needs (map in `video/CLAUDE.md`). Set `Статус: план` in NOTES.md.

## 4. Build
- Prepare assets with ffmpeg into `public/` (crop 9:16, 60 fps, bake grading with `grade()`); never touch `inbox/`.
- Code the edit in `src/` (keep one composition unless the brief asks for variants); 3D via a `pipeline/` like
  `projects/kayn-edit/pipeline`. Check with stills / `--frames=a-b`, then `npm run render -- <slug>`.
- Deliver: SendUserFile `out/<slug>.mp4` (over 30 MB it shows only in the desktop app — say so), a contact sheet of
  key frames if useful. Update NOTES.md: status, decisions, materials map, rebuild command, version line.
- Commit only when the user asks; inbox/work/public/out are never committed.
