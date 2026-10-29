# Kayn edit — pipeline

## Quick start (Windows PC)

Needs: Blender ≥ 4.2, Node.js, ffmpeg on PATH, Git for Windows. Once: `npm install` in `video/kayn-edit/`.
Drop the sources into `pipeline/work/in/` (original names are fine: `*каин*.glb`, `*пантеон*.glb`, `ари*.glb`,
`*волиб*.glb`, `*сераф*.glb`, any `.mp3`, the gameplay `.mp4`). Then, from `video/kayn-edit/` in any terminal:

- `npm run build` — renders whatever is missing and the final video → `out/kayn_edit_tiktok.mp4`
- `npm run build -- K1_pantheon K3_volibear` — re-render only these 3D shots, then the edit
- `npm run build -- --force` — everything from scratch; `--no-edit` skips the Remotion step
- `npm run render` — only the edit (after changing `src/`); `npm run studio` — preview in the browser

Blender is looked up on PATH, in `D:\git\tools\Blender`, and in `Program Files`; otherwise set `BLENDER`.
Git Bash is found automatically (`GIT_BASH` to override). The manual steps below are what `build.sh` runs.

## Manual steps

Inputs (not in git): put them in `pipeline/work/in/`: `kayn.glb pantheon.glb ahri.glb volibear.glb seraphine.glb`, `track.mp3`, `game.mp4`.

1. 3D shots (Blender ≥ 4.2, run from `pipeline/`):
   `blender -b -P b3d/shots.py -- S1_intro 16 full work/renders` (static shots S1, S7)
   `blender -b -P b3d/kills.py -- K1_pantheon 12 full work/renders` (action shots K1–K5)
2. Convert to 60 fps 1080×1920 into `../public/shots/`: `./shot2mp4.sh S1_intro`, `./act2mp4.sh K1_pantheon`.
3. Gameplay + track → `../public/`: `./game2mp4.sh` (minterpolates to 60 fps unless the source already is, crops 9:16;
   pass `../out/gameplay_60fps.mp4` to skip the slow interpolation).
4. Edit: `cd .. && npx remotion render src/index.ts KaynEdit out/kayn_edit_tiktok.mp4`.

Color grading is baked in steps 2–3 (`grade.sh`, same math as the old CSS filters), so Remotion only composites.
`remotion.config.ts` renders with GPU compositing (ANGLE on Windows), cores−2 threads and x264 `veryfast`.
Changed only timing/labels in `src/`? Run step 4 alone. Changed victims' animation? Re-render only K1–K5.

Cycles picks the GPU itself (`gpu()` in `b3d/stage.py`: OptiX → CUDA → CPU); set `KAYN_CPU=1` to force CPU.
Victims react through bones (`body_reaction` in `b3d/action.py`): back arches, head snaps back, arms fling out, knees buckle, then a landing slam; finale bodies hold the fallen pose.
On Windows run the `.sh` scripts from Git Bash.
