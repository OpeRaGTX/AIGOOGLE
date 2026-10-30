"""motion2rig.py — put a text-to-motion draft (HumanML3D 22-joint positions, .npy from MoMask) onto the control rig
as editable keys: hips/torso/head orientation, IK hand/foot targets, elbow/knee poles. The draft keeps the model's
proportions (limb vectors are rescaled from the rig's own shoulders/hips) and is keyed every `every` frames, so the
animator can refine it by hand (character-animator skill) before control_rig.bake().

In Blender:  import motion2rig as M2R; M2R.apply_joints(rig, 'draft.npy', start=1, every=2)
CLI:         blender -b -P motion2rig.py -- <model.glb> <joints.npy> <out.blend> [every=2] [facing=front]
"""
import os, sys
import bpy
import numpy as np
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import control_rig as CR  # noqa: E402

# HumanML3D / SMPL joint order
J = dict(pelvis=0, hip_L=1, hip_R=2, spine1=3, knee_L=4, knee_R=5, spine2=6, ankle_L=7, ankle_R=8, spine3=9, foot_L=10,
         foot_R=11, neck=12, collar_L=13, collar_R=14, head=15, shoulder_L=16, shoulder_R=17, elbow_L=18, elbow_R=19,
         wrist_L=20, wrist_R=21)


def load(path, fps, src_fps=20, facing=None):
    """(T,22,3) Y-up, facing +Z  ->  resampled to `fps`, Blender axes (Z-up, facing -Y, left = +X).
    facing='front' removes the draft's heading drift (body keeps facing -Y, root motion stays)."""
    a = np.load(path)
    a = np.stack([a[..., 0], -a[..., 2], a[..., 1]], -1)
    if facing == 'front':
        side = a[:, 1, :2] - a[:, 2, :2]
        yaw = np.unwrap(np.arctan2(side[:, 1], side[:, 0]))
        for f in range(len(a)):
            c, s = np.cos(-yaw[f]), np.sin(-yaw[f])
            d = a[f, :, :2] - a[f, 0, :2]
            a[f, :, :2] = a[f, 0, :2] + d @ np.array([[c, s], [-s, c]])
    t_src = np.arange(len(a)) / src_fps
    t = np.arange(0, t_src[-1] + 1e-9, 1.0 / fps)
    out = np.empty((len(t), 22, 3))
    for j in range(22):
        for k in range(3):
            out[:, j, k] = np.interp(t, t_src, a[:, j, k])
    return out


def frame_of(side, up):
    """Rotation whose X = side (character's left), Z = up — identity for an upright figure facing -Y."""
    x = Vector(side).normalized(); y = Vector(up).cross(x).normalized(); z = x.cross(y)
    return Matrix((x, y, z)).transposed().to_4x4()


def apply_joints(rig, path, start=1, every=2, fps=None, facing=None):
    arm, m = rig.arm, rig.m
    B = arm.data.bones
    fps = fps or bpy.context.scene.render.fps
    P = load(path, fps, facing=facing)
    v = lambda f, name: Vector(P[f, J[name]])
    s = B[m['leg']['L'][0]].head_local.z / max(v(0, 'hip_L').z, 1e-3)   # leg-height scale
    P *= s
    frames = list(range(0, len(P), every)) + ([len(P) - 1] if (len(P) - 1) % every else [])
    end = start + len(P) - 1
    bpy.context.scene.frame_end = max(bpy.context.scene.frame_end, end)
    hips_rest = B['CTRL_hips'].matrix_local
    arm_len = {sd: (B[c[2]].head_local - B[c[1]].head_local).length + (B[c[3]].head_local - B[c[2]].head_local).length
               for sd, c in m['arm'].items()}
    leg_len = {sd: (B[c[1]].head_local - B[c[0]].head_local).length + (B[c[2]].head_local - B[c[1]].head_local).length
               for sd, c in m['leg'].items()}
    foot_z = {sd: B[c[-1]].head_local.z for sd, c in m['leg'].items()}

    # pass 1: body (hips carry the root motion), torso, head
    for i in frames:
        f = start + i
        Fh = frame_of(v(i, 'hip_L') - v(i, 'hip_R'), v(i, 'spine3') - v(i, 'pelvis'))
        pos = hips_rest.translation + (v(i, 'pelvis') - v(0, 'pelvis'))
        rig.key('CTRL_hips', f, matrix=Matrix.Translation(pos) @ Fh @ hips_rest.to_3x3().to_4x4())
    for i in frames:
        f = start + i
        Ft = frame_of(v(i, 'shoulder_L') - v(i, 'shoulder_R'), v(i, 'neck') - v(i, 'spine3'))
        bpy.context.scene.frame_set(f)
        rest = B['CTRL_torso'].matrix_local
        rig.key('CTRL_torso', f, matrix=Matrix.Translation(rig.world('CTRL_torso').translation) @ Ft @ rest.to_3x3().to_4x4(), rot_only=True)
    for i in frames:
        f = start + i
        Fd = frame_of(v(i, 'shoulder_L') - v(i, 'shoulder_R'), v(i, 'head') - v(i, 'neck'))
        bpy.context.scene.frame_set(f)
        rest = B['CTRL_head'].matrix_local
        rig.key('CTRL_head', f, matrix=Matrix.Translation(rig.world('CTRL_head').translation) @ Fd @ rest.to_3x3().to_4x4(), rot_only=True)

    # pass 2: limbs from the rig's own shoulders / hip joints, rescaled to its limb lengths
    for i in frames:
        f = start + i
        bpy.context.scene.frame_set(f)
        Ft = frame_of(v(i, 'shoulder_L') - v(i, 'shoulder_R'), v(i, 'neck') - v(i, 'spine3'))
        for sd in 'LR':
            sh, el, wr = v(i, f'shoulder_{sd}'), v(i, f'elbow_{sd}'), v(i, f'wrist_{sd}')
            r = arm_len[sd] / max((el - sh).length + (wr - el).length, 1e-4)
            base = rig.world(m['arm'][sd][1]).translation
            hand = base + (wr - sh) * r
            elbow = base + (el - sh) * r
            mid = (base + hand) / 2
            pole = elbow + ((elbow - mid).normalized() if (elbow - mid).length > 1e-3 else Vector((0, 1, 0))) * 0.4 * arm_len[sd]
            rest = B[f'CTRL_hand_{sd}'].matrix_local
            rig.key(f'CTRL_hand_{sd}', f, matrix=Matrix.Translation(hand) @ Ft @ rest.to_3x3().to_4x4())
            rig.key(f'CTRL_pole_arm_{sd}', f, loc=pole)
            hp, kn, an = v(i, f'hip_{sd}'), v(i, f'knee_{sd}'), v(i, f'ankle_{sd}')
            r = leg_len[sd] / max((kn - hp).length + (an - kn).length, 1e-4)
            base = rig.world(m['leg'][sd][0]).translation
            foot = base + (an - hp) * r
            foot.z = max(foot.z, foot_z[sd])
            knee = base + (kn - hp) * r
            mid = (base + foot) / 2
            pole = knee + ((knee - mid).normalized() if (knee - mid).length > 1e-3 else Vector((0, -1, 0))) * 0.5 * leg_len[sd]
            Ff = Fh_yaw(v, i)
            rest = B[f'CTRL_foot_{sd}'].matrix_local
            rig.key(f'CTRL_foot_{sd}', f, matrix=Matrix.Translation(foot) @ Ff @ rest.to_3x3().to_4x4())
            rig.key(f'CTRL_pole_leg_{sd}', f, loc=pole)
    return start, end


def Fh_yaw(v, i):
    """Feet follow the hips' heading only (stay flat on the floor)."""
    side = v(i, 'hip_L') - v(i, 'hip_R'); side.z = 0
    return frame_of(side, Vector((0, 0, 1)))


if __name__ == '__main__':
    a = sys.argv[sys.argv.index('--') + 1:]
    opt = dict(x.split('=', 1) for x in a[3:])
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.context.scene.render.fps = int(opt.get('fps', 30))
    arm = CR.import_glb(a[0])
    rig = CR.build(arm)
    s, e = apply_joints(rig, a[1], every=int(opt.get('every', 2)), facing=opt.get('facing'))
    bpy.context.scene.frame_start, bpy.context.scene.frame_end = s, e
    bpy.ops.wm.save_as_mainfile(filepath=a[2])
    print(f'draft on rig: frames {s}-{e} -> {a[2]}')
