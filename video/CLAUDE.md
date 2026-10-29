# TikTok video studio (`video/`)

Many independent TikTok videos on one shared Remotion engine. The user writes in Russian — reply in Russian.

## Layout
- `projects/<slug>/` — one video, self-contained:
  `brief.md` (user's brief) · `NOTES.md` (you keep it: `Статус:` line, decisions, materials map, rebuild, versions) ·
  `inbox/` raw materials as the user dropped them — **read-only, never modify or delete** · `work/` intermediates ·
  `public/` prepared assets = `staticFile()` root · `src/` Remotion code (`index.ts` → `Root.tsx` → compositions) ·
  `out/` finished renders · optional `pipeline/` (project scripts: Blender, asset prep; `pipeline/build.sh` = full build).
- `tools/` shared CLI and helpers; `_template/` what `npm run new` copies. Shared React/TS code reused by 2+ videos
  goes to `video/shared/` (create it when first needed); a project never imports from another project.
- Git keeps only briefs, notes and code (`.gitignore`): inbox/, work/, public/, out/ stay on this PC.

## Commands (run from `video/`)
`npm run new -- "Название"` (or the user double-clicks `Новое видео.bat`) · `npm run list` ·
`npm run studio -- <slug>` (preview) · `npm run render -- <slug> [--comp id] [--out f.mp4] [remotion flags]` ·
`npm run build -- <slug> [args]` (project's `pipeline/build.sh` if present, else render). A partial slug works.
Pipeline scripts `source ../../../tools/env.sh` for `$BLENDER`, `$TOOLS`, `grade()`, `winpath()`; run `.sh` via Git Bash.
Music analysis: `python tools/beats.py track.mp3` (tempo, beats, loudness), `python tools/kicks.py track.mp3 hits.json`.

## Workflow for «сделай ролик X»
1. `plan-first` skill. Read `brief.md` + `NOTES.md`; inventory `inbox/` with ffprobe (duration, fps, size, audio).
   Brief thin or missing → `creative-brief` skill, ask the key questions in one AskUserQuestion; write answers into brief.md.
2. Plan (shots/EDL, music sync, text, effects, which assets go to public/), confirm, set `Статус: план` in NOTES.md.
3. Build: convert materials from inbox/ into work/ and public/ (ffmpeg); code in `src/`; render a few stills or a
   short `--frames` range to check, then the full render to `out/<slug>.mp4`. Send the user the result
   (SendUserFile; >30 MB → also mention it is only visible in the desktop app). Update NOTES.md (status, version).
4. Commit only when asked; never add inbox/work/public/out files.

## Skills to load by task
- Format, hooks, pacing, TikTok specifics: `short-form-video`, `video-delivery-specs`, `ad-creative-video`, `launch-video`.
- Editing & music: `beat-sync-editing`, `audio-design`, `audiogram`; direction: `motion-art-direction`, `shot-composition`, `animation-principles`.
- Text: `caption-animation`, `remotion-captions`, `kinetic-typography`, `lower-thirds`, `countdown-video`, `logo-animation`.
- Remotion: `remotion-best-practices` (router) and `remotion-*`; `remotion-video` for the basics.
- Video processing: `ffmpeg-skill`; color: `color-motion`; backgrounds/FX: `motion-background`, `particle-system`, `shader-glsl`, `threejs-animation`.
- 3D in Blender: `blender-director` (entry), `animation`, `camera-cinematography`, `lighting`, `rendering`, `compositing`, `vfx-fx`.
- Other formats: `testimonial-video`, `youtube-intro-outro`, `after-effects`.

## Defaults and lessons
- 1080×1920, 9:16, 60 fps (30 for talking/UI videos), H.264 yuv420p + AAC. Keep text out of TikTok UI zones:
  top ~150 px, bottom ~420 px, right ~140 px.
- Don't put CSS `filter` on full-frame video — bake grading into the mp4 (`grade()` in tools/grade.sh, CSS-equivalent).
- Use `OffthreadVideo`; Remotion config caps threads at 8 (more starves the frame extractor) and render.mjs retries.
- Blender: Cycles on OptiX with GPU denoise (see projects/kayn-edit/pipeline/b3d/stage.py `gpu()`); render PNG
  frames to work/, convert with ffmpeg. Input file names may be Cyrillic — copy to ASCII names in work/ or inbox/.
