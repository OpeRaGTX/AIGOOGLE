"""spring_bones.py — secondary motion (hair, capes, skirts, tails, ribbons): damped-spring simulation of bone chains,
baked into keys. Works headless (no viewport handler like Wiggle), so the result renders in `blender -b`.

Run after the body is baked onto the model's bones (control_rig.bake):
    import spring_bones as SB; SB.bake_springs(arm, 1, 90)                    # auto chains, 'hair' feel
    SB.bake_springs(arm, 1, 90, chains=SB.find_chains(arm, ('cape',)), preset='cloth')
Each chain joint is pulled toward where the animated parent would carry it (stiffness), loses energy (damping),
sags a little (gravity), and keeps its segment lengths, so it lags, overshoots and settles.
"""
import bpy
from mathutils import Matrix, Vector

PATTERNS = ('hair', 'skirt', 'cape', 'cloth', 'cloak', 'robe', 'tail', 'braid', 'ribbon', 'ribon', 'plume',
            'tassel', 'scarf', 'banner', 'chain', 'tentacle', 'ear')
PRESETS = {  # stiffness [1/s^2], damping [1/s], gravity scale, collision floor
    'hair': dict(stiffness=140.0, damping=9.0, gravity=0.35),
    'cloth': dict(stiffness=70.0, damping=6.0, gravity=0.6),
    'stiff': dict(stiffness=320.0, damping=16.0, gravity=0.15),
}
SKIP = ('CTRL_', 'MCH_', 'FK_', 'IK_')


def find_chains(arm, patterns=PATTERNS, min_len=0.005):
    """Chains of bones whose names match `patterns`, each starting at a matching bone with a non-matching parent.
    Branches become separate chains. Zero-length helper bones are skipped (they just follow their parent)."""
    B = arm.data.bones
    ok = lambda b: (not b.name.startswith(SKIP) and not b.name.endswith('_Ground')
                    and any(p in b.name.lower() for p in patterns))
    chains = []

    def walk(b, cur):
        if b.length >= min_len:
            cur = cur + [b.name]
        kids = [c for c in b.children if ok(c)]
        if not kids:
            if len(cur) >= 1:
                chains.append(cur)
            return
        for i, c in enumerate(kids):
            walk(c, cur if i == 0 else list(cur))

    for b in B:
        if ok(b) and not (b.parent and ok(b.parent)):
            walk(b, [])
    return [c for c in chains if c]


def _rest_rel(b):
    return (b.parent.matrix_local.inverted() @ b.matrix_local) if b.parent else b.matrix_local.copy()


def bake_springs(arm, start, end, chains=None, preset='hair', substeps=6, preroll=45, **over):
    """preroll: frames simulated on the first pose before recording, so chains settle (hang) before the shot starts."""
    P = dict(PRESETS[preset], **over)
    scn = bpy.context.scene
    fps = scn.render.fps / scn.render.fps_base
    dt = 1.0 / fps / substeps
    chains = chains if chains is not None else find_chains(arm)
    B, PB = arm.data.bones, arm.pose.bones
    g = Vector((0, 0, -9.81 * P['gravity']))
    floor = 0.0
    state = {}
    result = {name: {} for ch in chains for name in ch}

    for f in [start] * preroll + list(range(start, end + 1)):
        scn.frame_set(f)
        for ci, ch in enumerate(chains):
            bones = [B[n] for n in ch]
            anchor = PB[bones[0].parent.name].matrix.copy() if bones[0].parent else Matrix.Identity(4)
            # kinematic chain (where the animation alone puts every joint), built on the simulated parents
            basis = [PB[b.name].matrix_basis.copy() for b in bones]

            def kin(parents_world):
                mats, w = [], parents_world
                for b, bs in zip(bones, basis):
                    w = w @ _rest_rel(b) @ bs; mats.append(w)
                return mats
            K = kin(anchor)
            joints = [m.translation.copy() for m in K] + [K[-1] @ Vector((0, bones[-1].length, 0))]
            if ci not in state:
                state[ci] = ([j.copy() for j in joints], [Vector() for _ in joints])
            pos, vel = state[ci]
            lengths = [(joints[i + 1] - joints[i]).length for i in range(len(joints) - 1)]
            for _ in range(substeps):
                pos[0] = joints[0].copy(); vel[0] = Vector()
                for i in range(1, len(pos)):
                    a = (joints[i] - pos[i]) * P['stiffness'] - vel[i] * P['damping'] + g
                    vel[i] += a * dt; pos[i] += vel[i] * dt
                for i in range(1, len(pos)):  # keep segment lengths (stiff links) and stay above the floor
                    d = pos[i] - pos[i - 1]
                    pos[i] = pos[i - 1] + d.normalized() * lengths[i - 1] if d.length > 1e-9 else joints[i].copy()
                    if pos[i].z < floor:
                        pos[i].z = floor
            # joints -> bone rotations, parent first
            w = anchor
            for i, (b, bs) in enumerate(zip(bones, basis)):
                k = w @ _rest_rel(b) @ bs
                head = k.translation
                kin_dir = (k @ (_rest_rel(bones[i + 1]).translation if i + 1 < len(bones) else Vector((0, b.length, 0)))) - head
                sim_dir = pos[i + 1] - pos[i]
                R = kin_dir.rotation_difference(sim_dir).to_matrix().to_4x4()
                new = Matrix.Translation(head) @ R @ Matrix.Translation(-head) @ k
                result[b.name][f] = (w @ _rest_rel(b)).inverted() @ new
                w = new
    for name, frames in result.items():
        pb = PB[name]; pb.rotation_mode = 'QUATERNION'
        for f, m in frames.items():
            l, r, s = m.decompose()
            pb.location, pb.rotation_quaternion = l, r
            pb.keyframe_insert('location', frame=f, group=name)
            pb.keyframe_insert('rotation_quaternion', frame=f, group=name)
    return chains
