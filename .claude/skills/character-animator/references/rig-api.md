# Rig & tools cheat sheet (`video/tools/anim/`)

## control_rig.py
```python
import sys; sys.path.insert(0, r'D:\git\aigoogle\video\tools\anim')
import control_rig as CR
arm = CR.import_glb(path)            # removes League helper Icospheres
rig = CR.build(arm, weapon_hand='R_Hand')
rig.key('CTRL_hips', f, matrix=M)    # armature-space matrix; or loc=(x,y,z), rot=(deg x,y,z)/Quaternion/Matrix
rig.turn('CTRL_torso', f, pitch=20, yaw=-15)   # world-angle turn of the rest orientation, rotation-only key
rig.prop('grip_L', f, 1)             # ik_arm_L/R, ik_leg_L/R (1 IK, 0 FK), weapon_hand, grip_L, floor
rig.weapon_pose(grip, shaft_dir, blade_dir, u=0.45)   # weapon matrix: shaft point u at grip, aimed
rig.key_weapon(f, matrix)            # keys CTRL_weapon through the hand's Child Of
rig.weapon_lowest(matrix)            # lowest weapon point for contact solving
rig.shaft_point(u)                   # current shaft point (second-hand grips)
CR.fcurves(arm)                      # all F-curves (layered actions in Blender 4.4+/5.x)
CR.bake(rig, 1, N)                   # plain keys on model bones, constraints removed (strip=True deletes controls)
CR.limb_penetration(arm, 1, N, torso=None)  # [(frame, bone, depth)] forearms inside the torso mesh (add "Head")
```
Controls: `CTRL_root, CTRL_hips, CTRL_torso, CTRL_head, CTRL_hand_L/R, CTRL_foot_L/R, CTRL_pole_arm_L/R,
CTRL_pole_leg_L/R, CTRL_weapon, FK_*`. Key parents before children for any given frame range.
IK chains run between real joint positions (game-bone tails don't aim at children); model bones ride them through
`MCH_*` sockets; pole angles are calibrated numerically so the rest pose is exact.

## spring_bones.py
```python
import spring_bones as SB
chains = SB.find_chains(arm, ('hair',))          # or ('skirt','cape','tail',...)
SB.bake_springs(arm, 1, N, chains=chains, preset='hair')         # hair | cloth | stiff
SB.bake_springs(arm, 1, N, chains=long_ones, preset='cloth', stiffness=6, damping=3, gravity=1.0)  # heavy ponytail
```
Long/heavy chains: low stiffness so gravity wins; `preroll` (default 45) settles chains before frame 1.

## motion2rig.py / text2motion.sh
`bash video/tools/anim/text2motion.sh "a person ..." 3 work/anim/d.bvh inbox/model.glb work/anim/d.blend`
→ MoMask draft (~50 s on the RTX 3050) keyed every 2 frames on the control rig. `T2M_FACING=front` removes the
draft's turning. In Blender: `motion2rig.apply_joints(rig, 'd.npy', start=1, every=2, facing='front')`.
Treat it as blocking: delete what you don't need, re-pose the golden frames, then breakdowns/spline/polish.

## Looking at it
```bash
python video/tools/anim/bmcp.py exec script.py k=v ...   # run code in the open Blender (ARGS dict)
python video/tools/anim/bmcp.py frames <dir> 1,24,39 side 5.0   # screenshots per frame (views: front side
                                                              # threequarter back34 low top)
blender -b x.blend -P video/tools/anim/preview.py -- out.mp4 [target=x,y,z dist=4.8 yaw=-35]   # lit playblast
```
Viewport images are wide: crop the middle third before tiling (`crop=iw*0.34:ih:iw*0.33:0`).

## Spacing check (paste into bmcp exec)
```python
import bpy, math
arm = next(o for o in bpy.data.objects if o.type=='ARMATURE'); P = arm.pose.bones; s = bpy.context.scene
prev = None
for f in range(s.frame_start, s.frame_end + 1):
    s.frame_set(f); cur = {n: P[n].head.copy() for n in ('R_Hand', 'C_Weapon6', 'Head', 'Pelvis')}
    if prev: print(f, {n: round((cur[n]-prev[n]).length * s.render.fps, 1) for n in cur})
    prev = cur
```
