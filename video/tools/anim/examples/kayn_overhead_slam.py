"""Overhead slam, 3 s @ 30 fps, animated from scratch on the control rig (no game clips) — reference example for the
`character-animator` skill. Stand -> anticipation (crouch, weapon back) -> leap with an overhead swing that overshoots
-> heavy landing that settles; hair and skirt get spring follow-through after the bake.

Live (Blender open with the MCP addon):  python ../bmcp.py exec kayn_overhead_slam.py glb=<kayn.glb> tools=<tools/anim> out=<x.blend>
Headless:                                blender -b -P kayn_overhead_slam.py -- <kayn.glb> <out.blend>
Timing: 120 BPM -> a beat is 15 frames; poses land on beats and half/quarter beats.
"""
import importlib, math, os, sys
import bpy
from mathutils import Matrix, Vector

if 'ARGS' not in globals():
    a = sys.argv[sys.argv.index('--') + 1:]
    ARGS = dict(glb=a[0], out=a[1] if len(a) > 1 else '', tools=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, ARGS['tools'])
import control_rig as CR, spring_bones as SB
importlib.reload(CR); importlib.reload(SB)

FPS, N = 30, 90
V = Vector

# ---------------------------------------------------------------- scene + rig
if bpy.context.object and bpy.context.object.mode != 'OBJECT':
    bpy.ops.object.mode_set(mode='OBJECT')
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
for coll in (bpy.data.actions, bpy.data.armatures, bpy.data.meshes):
    for x in list(coll):
        coll.remove(x)
scn = bpy.context.scene
scn.render.fps, scn.frame_start, scn.frame_end = FPS, 1, N
arm = CR.import_glb(ARGS['glb'])
rig = CR.build(arm)
B = arm.data.bones
REST = {n: B[n].matrix_local.copy() for n in ('CTRL_hips', 'CTRL_foot_L', 'CTRL_foot_R', 'CTRL_hand_L', 'CTRL_hand_R')}


def rot(pitch=0, yaw=0, roll=0):
    return (Matrix.Rotation(math.radians(yaw), 4, 'Z') @ Matrix.Rotation(math.radians(pitch), 4, 'X')
            @ Matrix.Rotation(math.radians(roll), 4, 'Y'))


def place(name, f, pos, R=Matrix.Identity(4)):
    rest = REST[name]
    rig.key(name, f, matrix=Matrix.Translation(V(pos)) @ R @ rest.to_3x3().to_4x4())


def shaft(theta, lean=0.12):
    """Shaft direction in the swing plane: theta 0 = up, +90 = forward (-Y), -90 = back; blade leads the swing."""
    t = math.radians(theta)
    s = V((-lean, -math.sin(t), math.cos(t))).normalized()
    b = V((0, -math.cos(t), -math.sin(t)))
    return s, b


# ---------------------------------------------------------------- 1. blocking (key poses) + 2. breakdowns
# hips: (frame, dx, dy, dz, pitch, yaw)   — hips lead every move
z0 = REST['CTRL_hips'].translation.z
HIPS = [(1, 0, 0.00, -0.05, 0, -10), (12, 0, 0.01, -0.06, 0, -10),
        (20, 0.02, 0.06, -0.24, -4, -16), (24, 0.02, 0.07, -0.28, -5, -18),          # anticipation: sink + coil
        (28, 0.0, -0.04, 0.02, 4, -8), (31, 0, -0.12, 0.30, 6, -2), (33, 0, -0.16, 0.34, 4, 0),   # launch, air, apex
        (36, 0, -0.22, 0.12, 10, 2), (38, 0, -0.25, -0.20, 14, 3), (40, 0, -0.27, -0.41, 18, 4),  # fall, contact, squash
        (44, 0, -0.27, -0.31, 15, 3), (49, 0, -0.27, -0.36, 16, 3), (57, 0, -0.27, -0.33, 15, 3), (90, 0, -0.27, -0.32, 14, 2)]
# torso / head absolute world pitch (+forward), yaw — 1-2 frames behind the hips (overlap)
TORSO = [(1, 6, -10), (13, 7, -10), (22, -12, -20), (26, -16, -22), (30, -20, -10), (34, -24, 0), (36, 12, 2),
         (39, 42, 5), (41, 48, 6), (45, 38, 4), (50, 42, 5), (59, 40, 4), (90, 36, 3)]
HEAD = [(1, 0, -6), (14, 2, -6), (23, 12, -14), (27, 14, -16), (31, -6, -6), (35, -14, 0), (38, 18, 2),
        (41, 32, 4), (44, 27, 3), (49, 29, 3), (62, 20, 2), (78, 8, 2), (90, 6, 2)]
# feet: (frame, L pos, R pos) — planted until the launch, staggered landing (L 37, R 38)
SL, SR = (0.20, -0.16, 0.10), (-0.24, 0.18, 0.10)
FEET = [(1, SL, SR), (27, SL, SR), (30, (0.20, -0.20, 0.32), (-0.22, 0.12, 0.30)),
        (33, (0.21, -0.30, 0.42), (-0.22, 0.02, 0.44)), (35, (0.22, -0.42, 0.22), (-0.24, -0.08, 0.26)),
        (37, (0.22, -0.46, 0.10), (-0.25, -0.09, 0.16)), (38, (0.22, -0.46, 0.10), (-0.26, -0.09, 0.10)),
        (90, (0.22, -0.46, 0.10), (-0.26, -0.09, 0.10))]
# right hand path (an arc, not a straight line) + weapon angle; the weapon trails the hand by a frame
HAND = [(1, (-0.40, -0.06, 0.98), -12), (12, (-0.40, -0.05, 0.99), -14), (18, (-0.36, 0.10, 1.02), -60),
        (22, (-0.30, 0.30, 0.98), -135), (26, (-0.26, 0.34, 0.92), -152), (29, (-0.22, 0.28, 1.35), -95),
        (31, (-0.16, 0.12, 1.80), -35), (33, (-0.12, -0.05, 2.02), -5), (35, (-0.12, -0.32, 1.86), 35),
        (37, (-0.12, -0.52, 1.40), 80), (39, (-0.12, -0.64, 0.94), 'floor'),        # impact: blade meets the floor
        (41, (-0.12, -0.66, 0.84), 'floor-'), (44, (-0.12, -0.63, 0.90), 'floor+'),  # overshoot (bites in), recoil
        (49, (-0.12, -0.64, 0.87), 'floor'), (58, (-0.12, -0.63, 0.89), 'floor'), (90, (-0.12, -0.61, 0.92), 'floor')]
IMPACT = {37, 38, 39}
CONTACT = {'floor': 0.02, 'floor-': -0.03, 'floor+': 0.07}   # lowest weapon point above the floor


def contact_theta(grip, z):
    """Swing angle at which the weapon's lowest point is at height z for this grip (contact, no clipping)."""
    return min((90 + a * 0.5 for a in range(0, 160)),
               key=lambda th: abs(rig.weapon_lowest(rig.weapon_pose(grip, *shaft(th), u=0.42)) - z))

for f, dx, dy, dz, p, y in HIPS:
    rest = REST['CTRL_hips']
    rig.key('CTRL_hips', f, matrix=Matrix.Translation(rest.translation + V((dx, dy, dz))) @ rot(p, y) @ rest.to_3x3().to_4x4())
for f, p, y in TORSO:
    rig.turn('CTRL_torso', f, pitch=p, yaw=y)
for f, p, y in HEAD:
    rig.turn('CTRL_head', f, pitch=p, yaw=y)
for f, l, r in FEET:
    place('CTRL_foot_L', f, l, rot(yaw=10)); place('CTRL_foot_R', f, r, rot(yaw=-25))
for name, pos in (('CTRL_pole_leg_L', (0.35, -0.95, 0.6)), ('CTRL_pole_leg_R', (-0.42, -0.55, 0.6)),
                  ('CTRL_pole_arm_R', (-0.75, 0.35, 1.15)), ('CTRL_pole_arm_L', (0.75, 0.35, 1.15))):
    rig.key(name, 1, loc=pos)

W_REST = B['CTRL_weapon'].matrix_local
for f, pos, th in HAND:
    s, b = shaft(contact_theta(V(pos), CONTACT[th]) if isinstance(th, str) else th)
    Wd = rig.weapon_pose((0, 0, 0), s, b) @ W_REST.inverted()      # weapon rotation for this angle
    place('CTRL_hand_R', f, pos, Wd.to_3x3().to_4x4())
# left hand: free at the start, rides the shaft (grip_L) from the wind-up on
for f in (1, 14, 18):  # hold the free pose until the grip blends in
    place('CTRL_hand_L', f, (0.36, 0.02 - 0.01 * (f > 1), 1.0), rot(roll=-8))
rig.prop('grip_L', 1, 0); rig.prop('grip_L', 18, 0); rig.prop('grip_L', 21, 1)
rig.prop('weapon_hand', 1, 1)

# weapon: keyed through the hand (Child Of), grip = where the hand actually is
for f, pos, th in HAND:
    bpy.context.scene.frame_set(f)
    grip = rig.world('R_Hand').translation
    s, b = shaft(contact_theta(grip, CONTACT[th]) if isinstance(th, str) else th)
    rig.key_weapon(min(N, f + (1 if 1 < f < 29 else 0)), rig.weapon_pose(grip, s, b, u=0.42))  # trails the hand, except in the strike
# left hand own pose on the weapon: lower on the shaft, computed once in weapon space
bpy.context.scene.frame_set(22)
Wd = rig.world('CTRL_weapon') @ W_REST.inverted()
want = Matrix.Translation(rig.shaft_point(0.16)) @ Wd.to_3x3().to_4x4() @ REST['CTRL_hand_L'].to_3x3().to_4x4()
own = W_REST @ rig.world('CTRL_weapon').inverted() @ want
for f in (20, 90):
    rig.key('CTRL_hand_L', f, matrix=own)

# ---------------------------------------------------------------- 3. spline + 4. polish
for fc in CR.fcurves(arm):
    for kp in fc.keyframe_points:
        kp.interpolation = 'BEZIER'
        kp.handle_left_type = kp.handle_right_type = 'AUTO_CLAMPED'
        if int(round(kp.co.x)) in IMPACT and ('CTRL_hips' in fc.data_path or 'CTRL_hand_R' in fc.data_path or 'CTRL_weapon' in fc.data_path):
            kp.handle_left_type = 'VECTOR'          # accelerate into the hit, no ease-in
    fc.update()

# ---------------------------------------------------------------- bake + secondary motion
CR.bake(rig, 1, N)
chains = SB.find_chains(arm, ('hair',))
long = [c for c in chains if len(c) >= 4]                     # the ponytail: heavy, hangs and swings
SB.bake_springs(arm, 1, N, chains=[c for c in chains if c not in long], preset='hair')
SB.bake_springs(arm, 1, N, chains=long, preset='cloth', stiffness=6.0, damping=3.0, gravity=1.0)
skirts = SB.find_chains(arm, ('skirt',))
SB.bake_springs(arm, 1, N, chains=skirts, preset='cloth')
print('springs:', chains, skirts)
if ARGS.get('out'):
    bpy.ops.wm.save_as_mainfile(filepath=ARGS['out'], copy=True)
    print('saved', ARGS['out'])
