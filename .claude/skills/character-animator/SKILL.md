---
name: character-animator
description: Professional character animation in Blender for the TikTok video studio — authoring motion from scratch on the studio's control rig (IK/FK, hips/torso/head, weapon in hand), the animator's pipeline (blocking on beat subdivisions → breakdowns → spline → Graph Editor polish), the 12 principles, a catalog of "power" poses, spring follow-through for hair/capes, and a mandatory visual check of key frames through Blender MCP before any render. Use it whenever a video needs a character to move — a game champion swinging a weapon, a hero pose, a jump, a landing, an attack, a dance, a reaction — even if the user just says "оживи персонажа", "сделай анимацию удара", "пусть он замахнётся", "3D-сцена с персонажем", "анимация Виего/Каина", or hands over a .glb with a skeleton. Prefer it over reusing the model's game clips.
---

# Character animator (video studio)

You are the studio's character animator. You compose motion yourself, pose by pose, the way a feature animator
does — game clips and mocap are only references or a last resort. The tools live in `video/tools/anim/`
(API cheat sheet: `references/rig-api.md`; poses: `references/pose-catalog.md`; a complete worked example:
`video/tools/anim/examples/kayn_overhead_slam.py`).

Related skills: `animation-principles` (timing/easing theory), `animation` (Blender animation tooling),
`camera-cinematography` (shot design around the action), `vfx-fx` (trails, sparks, impacts on the key frames).

## Source priority
1. **Own animation on the control rig** — optionally start from a text-to-motion draft
   (`video/tools/anim/text2motion.sh "<prompt>" <sec> <out.bvh> <model.glb> <out.blend>`), then rework it with keys.
2. Mocap from the user's video (`inbox/mocap/`) — not installed on this PC (GVHMR needs ~8 GB VRAM, the GPU has 4).
3. Mixamo FBX retargeted with the `blender-toolkit` skill.
4. Game clips inside the .glb — reference only (study their timing, don't ship them).

## Setup
- Blender open with the MCP addon → you can see the viewport (`blender` MCP tools, or `python video/tools/anim/bmcp.py`
  `exec/shot/frames` when the tools aren't loaded in this session). Without it, render stills headless instead.
- `import control_rig as CR; arm = CR.import_glb(glb); rig = CR.build(arm)` — the rig is built over the model's own
  skeleton; it auto-detects League-style and Mixamo bone names. Check the rest pose didn't move (print drift) before animating.
- Find the facing: the studio's rigs face **-Y**, left = +X, up = +Z. `rig.turn(..., pitch=+)` leans forward.

## The pipeline (do the steps in order — each one fixes a different kind of problem)
1. **Plan the beat.** Timeline in frames at 30 fps; take the music BPM (`python video/tools/beats.py track`) or default
   120 BPM = 15 frames per beat. Golden poses land on beats; accents on halves/quarters (7–8, 3–4 frames).
   Write the plan as a table: frame → what the body does → which principle carries it.
2. **Blocking.** Only the storytelling poses: start, anticipation, contact/impact, extreme, settle. Key hips first,
   then torso, head, feet, hands, weapon — children are keyed after parents (`rot_only=True` for torso/head).
   Poses must read as silhouettes from the camera angle: strong line of action, weight clearly on a foot.
3. **Breakdowns.** Add the in-between pose that defines the *path* between keys: arcs for hands and weapon tips
   (3–5 keys along an arc, never two keys and a straight line), the moment the hips overtake the chest.
4. **Spline.** Bezier/auto-clamped everywhere, then check spacing numerically: per-frame speed of hand, weapon tip,
   head, pelvis (see `references/rig-api.md` → "spacing check"). A single frame with 2–3× the neighbours' speed
   is a pop; a "stop" that comes before the contact frame is a floaty hit.
5. **Polish.** Vector (sharp) handles into impacts, ease-out after them; moving holds (2–4% drift) instead of dead
   holds; offset parts by 1–3 frames (overlap); add overshoot keys and a decaying settle; finally bake
   (`CR.bake(rig, s, e)`) and run springs (`spring_bones.bake_springs`) for hair, capes, skirts, tassels.

## Principles that matter most here
- **Anticipation** before every big action: move opposite first (crouch, weapon back, chest coils) and hold it a
  few frames with an ease-in — the bigger the hit, the longer the wind-up (6–10 frames at 30 fps).
- **Follow-through & overlap**: hips lead → chest (+1–2 f) → shoulder → arm (+1–2 f) → weapon (+1 f) → hair/cloth
  (springs). After the stop, parts keep going and settle at different times.
- **Overshoot & settle**: impacts pass the target (squash lower, weapon bites in) then recoil and damp out
  (two smaller bounces, each about half the previous).
- **Arcs**: hands, weapon tips, head paths are curves. Check them in the side and top views.
- **Weight & inertia**: heavy things accelerate slowly and stop late; the landing compresses knees and hips
  (0.3–0.4 m for a heroic slam), the torso keeps falling a frame after the feet stop.
- **No floating feet**: planted feet keep the exact same world position between contact keys (key them, the floor
  clamp keeps them above z = rest height); weight shifts happen in the hips, not by sliding feet.
- **Contact, not clipping**: weapons and feet touch surfaces — solve the angle so the lowest point sits at the floor
  (the example's `contact_theta`), never through it.
- **Silhouette & line of action**: one clear curve through the body at each golden pose; avoid twins (both arms
  or legs doing the same thing).

## Mandatory visual check (before any real render)
Do this every pass — numbers lie less than you, the viewport lies less than numbers:
1. Screenshots of every key/golden frame from at least two views (side + three-quarter), mesh visible, overlays off
   (`bmcp.py frames <dir> 1,24,33,39 side 5`), cropped and tiled into one strip; look at it.
2. A spacing check (speeds per frame) for the fast parts.
3. A quick lit playblast (`blender -b x.blend -P video/tools/anim/preview.py -- out.mp4`) and look at its contact sheet.
Write down what is wrong (pose, timing, clipping, pops, stiff hair), fix, and repeat until nothing is. Only then
render the real shot. Tell the user what you fixed between passes.

## Deliverables
- The animation script in the project (`projects/<slug>/pipeline/anim/*.py`) — re-runnable, values in tables.
- The baked `.blend` in `work/`, the preview mp4 in `out/`, a key-frame strip for the user.
- Notes in `NOTES.md`: beat plan, which principles carry which moments, what was changed after review.
