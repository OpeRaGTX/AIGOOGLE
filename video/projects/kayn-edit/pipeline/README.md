# Kayn edit — pipeline

## Build (from `video/`, any terminal)

Sources go to `../inbox/` (project folder `video/projects/kayn-edit/inbox/`), original names are fine:
`*каин*.glb`, `*пантеон*.glb`, `ари*.glb`, `*волиб*.glb`, `*сераф*.glb`, any `.mp3`, the gameplay `.mp4`.

- `npm run build -- kayn-edit` — renders whatever 3D shot is missing, then the edit → `out/kayn-edit.mp4`
- `npm run build -- kayn-edit K1_pantheon K3_volibear` — only these shots, then the edit
- `npm run build -- kayn-edit --force` — everything; `--no-edit` skips the Remotion step
- `npm run render -- kayn-edit` — only the edit (after changing `src/`); `npm run studio -- kayn-edit` — preview

Blender, ffmpeg and `grade()` come from `video/tools/env.sh`. The manual steps below are what `build.sh` runs.

## Manual steps (run from `pipeline/` in Git Bash)

1. 3D shots: `blender -b -P b3d/shots.py -- S1_intro 16 full <abs path to ../work/renders>` (static S1, S7),
   `blender -b -P b3d/kills.py -- K1_pantheon 12 full <…/work/renders>` (action K1–K5). Models are read from `../inbox/`.
2. Convert to 60 fps 1080×1920 into `../public/shots/`: `./shot2mp4.sh S1_intro`, `./act2mp4.sh K1_pantheon`.
3. Gameplay + track → `../public/`: `./game2mp4.sh [src]` (minterpolates to 60 fps unless the source already is,
   crops 9:16; pass `../work/gameplay_60fps.mp4` to skip the slow interpolation).
4. Edit: `npm run render -- kayn-edit` from `video/`.

Color grading is baked in steps 2–3 (`grade()` in `video/tools/grade.sh`, same math as CSS filters).

Cycles picks the GPU itself (`gpu()` in `b3d/stage.py`: OptiX → CUDA → CPU); set `KAYN_CPU=1` to force CPU.
Victims react through bones (`body_reaction` in `b3d/action.py`): back arches, head snaps back, arms fling out, knees buckle, then a landing slam; finale bodies hold the fallen pose.
