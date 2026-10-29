# Kayn edit — pipeline

Inputs (not in git): put them in `pipeline/work/in/`: `kayn.glb pantheon.glb ahri.glb volibear.glb seraphine.glb`, `track.mp3`, `game.mp4`.

1. 3D shots (Blender ≥ 4.2, run from `pipeline/`):
   `blender -b -P b3d/shots.py -- S1_intro 16 full work/renders` (static shots S1, S7)
   `blender -b -P b3d/kills.py -- K1_pantheon 12 full work/renders` (action shots K1–K5)
2. Convert to 60 fps 1080×1920 into `../public/shots/`: `./shot2mp4.sh S1_intro`, `./act2mp4.sh K1_pantheon`.
3. Gameplay: ffmpeg minterpolate to 60 fps, crop 9:16 → `../public/game.mp4`; track → `../public/track.mp3`.
4. Edit: `cd .. && npx remotion render src/index.ts KaynEdit out/kayn_edit_tiktok.mp4`.

With a GPU, set `s.cycles.device = 'GPU'` in `b3d/stage.py` — renders get many times faster.
