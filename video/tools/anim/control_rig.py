"""control_rig.py — light animation control rig over an imported game skeleton (.glb), baked back onto the model's
own bones for rendering. Works on League-style rigs (Pelvis/Spine/L_Hip/L_KneeUpper...) and Mixamo rigs.

Controls (bone collection "CTRL"; mechanics in "MCH"):
  CTRL_root            whole character            CTRL_hips   pelvis / body (drives the skeleton root)
  CTRL_torso           chest (spine follows)      CTRL_head   head (neck follows halfway)
  CTRL_hand_L/R        arm IK targets             CTRL_pole_arm_L/R  elbow direction
  CTRL_foot_L/R        leg IK targets (+floor)    CTRL_pole_leg_L/R  knee direction
  FK_* chains          FK arms/legs               CTRL_weapon  weapon, held by a hand through Child Of
Armature-object properties (keyable, 0..1): ik_arm_L/R, ik_leg_L/R (1 = IK, 0 = FK), weapon_hand (1 = in hand,
0 = free in world), grip_L (1 = left hand rides the weapon), floor (1 = feet can't sink below the floor).

In Blender:   import control_rig as CR; rig = CR.build(arm, weapon_hand='R_Hand'); rig.key('CTRL_hips', 10, loc=...)
              ...;  CR.bake(rig, 1, 90)   -> plain keys on the model's bones, constraints removed
CLI:          blender -b -P control_rig.py -- <in.glb> <out.blend>   (import + rig, to open and animate by hand)
"""
import math
import bpy
from mathutils import Euler, Matrix, Quaternion, Vector

MAPS = {
    'league': dict(root='Root', hips='Pelvis', spine=['Spine1', 'Spine2', 'Chest'], neck='Neck', head='Head',
                   arm={s: [f'{s}_Clavicle', f'{s}_Shoulder', f'{s}_Elbow', f'{s}_Hand'] for s in 'LR'},
                   leg={s: [f'{s}_Hip', f'{s}_KneeLower', f'{s}_Foot'] for s in 'LR'},  # KneeUpper: knee hinge, follows the thigh
                   weapon=['C_Weapon', 'Weapon', 'R_Weapon', 'Sword', 'C_Sword']),
    'mixamo': dict(root='mixamorig:Hips', hips='mixamorig:Hips', spine=['mixamorig:Spine', 'mixamorig:Spine1', 'mixamorig:Spine2'],
                   neck='mixamorig:Neck', head='mixamorig:Head',
                   arm={s: [f'mixamorig:{n}Shoulder', f'mixamorig:{n}Arm', f'mixamorig:{n}ForeArm', f'mixamorig:{n}Hand']
                        for s, n in (('L', 'Left'), ('R', 'Right'))},
                   leg={s: [f'mixamorig:{n}UpLeg', f'mixamorig:{n}Leg', f'mixamorig:{n}Foot'] for s, n in (('L', 'Left'), ('R', 'Right'))},
                   weapon=[]),
}
PREFIXES = ('CTRL_', 'MCH_', 'FK_', 'IK_')


def detect(arm):
    B = arm.data.bones
    for name, m in MAPS.items():
        if m['hips'] in B and m['head'] in B:
            m = {**m, 'spine': [b for b in m['spine'] if b in B], 'name': name}
            m['weapon'] = next((w for w in m['weapon'] if w in B), None)
            return m
    raise RuntimeError(f'unknown skeleton: {list(B.keys())[:20]}')


def is_control(name):
    return name.startswith(PREFIXES)


# ---------------------------------------------------------------- build

def _widget(name, kind):
    """Wire shapes for controls (hidden objects in a WGT collection)."""
    if name in bpy.data.objects:
        return bpy.data.objects[name]
    import bmesh
    me = bpy.data.meshes.new(name); bm = bmesh.new()
    if kind == 'circle':
        bmesh.ops.create_circle(bm, segments=24, radius=1.0)
    elif kind == 'square':
        bmesh.ops.create_circle(bm, segments=4, radius=1.0)
    else:  # diamond
        bmesh.ops.create_icosphere(bm, subdivisions=1, radius=0.5)
    bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(name, me)
    col = bpy.data.collections.get('WGT') or bpy.data.collections.new('WGT')
    if col.name not in bpy.context.scene.collection.children:
        bpy.context.scene.collection.children.link(col)
    col.objects.link(o); col.hide_render = True; col.hide_viewport = True
    return o


def adopt_ground_chains(arm):
    """Some League rigs park a hair/tail chain on the floor (X_Ground bones) with skinned X_Skn bones as children;
    the game animates the floor chain. Re-parent the skinned bones into a chain hanging from the nearest body bone so
    they follow the character (rest pose unchanged)."""
    eb = arm.data.edit_bones
    body = [b for b in ('Head', 'Neck', 'Spine2', 'Chest', 'Spine1') if b in eb]
    for b in list(eb):
        p = b.parent
        if b.name.endswith('_Skn') and p and p.name.endswith('_Ground'):
            prev = next((x for x in eb if x.name.endswith('_Skn') and x.parent and x.parent == p.parent), None)
            b.parent = prev if prev and prev != b else min((eb[n] for n in body), key=lambda x: (x.head - b.head).length)
            b.use_connect = False


class Rig:
    def __init__(self, arm, m):
        self.arm, self.m = arm, m

    def pb(self, name):
        return self.arm.pose.bones[name]

    def world(self, name):
        bpy.context.view_layer.update()
        return self.pb(name).matrix.copy()

    def key(self, name, frame, loc=None, rot=None, matrix=None, rot_only=False):
        """Key a control in armature space at `frame`. loc: Vector/tuple; rot: Euler degrees (XYZ), Quaternion or Matrix.
        Anything not given keeps the control's current armature-space value at that frame. rot_only keys just the
        rotation (for controls that ride a parent, e.g. torso/head on the hips — key parents first)."""
        bpy.context.scene.frame_set(frame)
        pb = self.pb(name)
        cur = self.world(name)
        if matrix is None:
            l, r, s = cur.decompose()
            if loc is not None:
                l = Vector(loc)
            if rot is not None:
                r = rot if isinstance(rot, Quaternion) else rot.to_quaternion() if isinstance(rot, Matrix) else \
                    Euler([math.radians(a) for a in rot]).to_quaternion()
            matrix = Matrix.LocRotScale(l, r, s)
        pb.matrix = matrix
        if rot_only:
            pb.location = (0, 0, 0)
        bpy.context.view_layer.update()
        if not rot_only:
            pb.keyframe_insert('location', frame=frame, group=name)
        pb.keyframe_insert('rotation_quaternion', frame=frame, group=name)
        return matrix

    def turn(self, name, frame, pitch=0.0, yaw=0.0, roll=0.0):
        """Key a control's rotation as rest orientation turned by world-space angles (degrees): pitch about X
        (+ leans forward for a character facing -Y), yaw about Z (- turns to the character's right), roll about Y."""
        R = (Matrix.Rotation(math.radians(yaw), 4, 'Z') @ Matrix.Rotation(math.radians(pitch), 4, 'X')
             @ Matrix.Rotation(math.radians(roll), 4, 'Y'))
        bpy.context.scene.frame_set(frame)
        rest = self.arm.data.bones[name].matrix_local
        M = Matrix.Translation(self.world(name).translation) @ R @ rest.to_3x3().to_4x4()
        return self.key(name, frame, matrix=M, rot_only=True)

    def prop(self, name, frame, value):
        self.arm[name] = float(value)
        self.arm.keyframe_insert(f'["{name}"]', frame=frame)

    # weapon in hand space: keep the weapon's world pose while switching hand/world
    def weapon_world(self):
        return self.world(self.m['weapon'])

    def weapon_geometry(self):
        """Rest-pose weapon frame: bottom (root head), top (furthest point along the shaft), blade (furthest point off it)."""
        B = self.arm.data.bones; root = B[self.m['weapon']]
        pts = [b.head_local for b in root.children_recursive] + [root.tail_local]
        axis = root.y_axis if hasattr(root, 'y_axis') else (root.tail_local - root.head_local).normalized()
        axis = (root.matrix_local.to_3x3() @ Vector((0, 1, 0))).normalized()
        bottom = root.head_local.copy()
        top = max(pts, key=lambda p: (p - bottom).dot(axis))
        shaft = (top - bottom).normalized()
        off = lambda p: (p - bottom) - shaft * (p - bottom).dot(shaft)
        blade = off(max(pts, key=lambda p: off(p).length)).normalized()
        return bottom, top, shaft, blade

    def weapon_pose(self, grip, shaft_dir, blade_dir, u=0.45):
        """Armature-space matrix for the weapon root so that the shaft point at fraction `u` (0 bottom .. 1 top) sits at
        `grip`, the shaft points along `shaft_dir` and the blade towards `blade_dir`."""
        bottom, top, s0, b0 = self.weapon_geometry()
        s, b = Vector(shaft_dir).normalized(), Vector(blade_dir)
        b = (b - s * b.dot(s)).normalized()
        R = Matrix((s, b, s.cross(b))).transposed() @ Matrix((s0, b0, s0.cross(b0))).transposed().inverted()
        g0 = bottom.lerp(top, u)
        delta = Matrix.Translation(Vector(grip)) @ R.to_4x4() @ Matrix.Translation(-g0)
        return delta @ self.arm.data.bones[self.m['weapon']].matrix_local

    def weapon_lowest(self, matrix):
        """Lowest z of the weapon (all its bone joints) if the weapon root had armature-space `matrix`."""
        root = self.arm.data.bones[self.m['weapon']]
        D = matrix @ root.matrix_local.inverted()
        return min((D @ p).z for p in [root.head_local, root.tail_local] + [b.head_local for b in root.children_recursive]
                   + [b.tail_local for b in root.children_recursive])

    def shaft_point(self, u):
        """Current armature-space position of the shaft point at fraction u."""
        bottom, top, _, _ = self.weapon_geometry()
        W = self.world('CTRL_weapon') @ self.arm.data.bones['CTRL_weapon'].matrix_local.inverted()
        return W @ bottom.lerp(top, u)

    def key_weapon(self, frame, matrix):
        """Key CTRL_weapon so the weapon ends up at armature-space `matrix`, respecting the hand Child Of."""
        bpy.context.scene.frame_set(frame)
        con = self.pb('CTRL_weapon').constraints['in_hand']
        h = self.world(con.subtarget)
        w = self.arm.get('weapon_hand', 1.0)
        own = matrix if w < 0.5 else con.inverse_matrix.inverted() @ h.inverted() @ matrix
        return self.key('CTRL_weapon', frame, matrix=own)


def build(arm, weapon_hand='R_Hand', pole_dist=None):
    """Build the control rig on `arm` (an imported armature). Returns a Rig."""
    m = detect(arm)
    ctx = bpy.context
    ctx.view_layer.objects.active = arm
    for o in ctx.selected_objects:
        o.select_set(False)
    arm.select_set(True)
    if arm.animation_data:
        arm.animation_data.action = None
        for t in list(arm.animation_data.nla_tracks):
            arm.animation_data.nla_tracks.remove(t)
    bpy.ops.object.mode_set(mode='EDIT')
    eb = arm.data.edit_bones
    adopt_ground_chains(arm)
    head = eb[m['head']]
    height = head.head.z
    pole_dist = pole_dist or 0.35 * height

    def dup(src, name, parent=None):
        s = eb[src]; e = eb.new(name)
        e.head, e.tail, e.roll = s.head.copy(), s.tail.copy(), s.roll
        if (e.tail - e.head).length < 0.02 * height:  # tiny game bones: give the control a usable length
            e.tail = e.head + (s.tail - s.head).normalized() * 0.08 * height if (s.tail - s.head).length > 1e-6 else e.head + Vector((0, 0, 0.08 * height))
        e.parent = eb[parent] if parent else None
        e.use_deform = False
        return e

    root = eb.new('CTRL_root'); root.head = (0, 0, 0); root.tail = (0, -0.25 * height, 0); root.use_deform = False
    dup(m['root'], 'CTRL_hips', 'CTRL_root')
    top = m['spine'][-1] if m['spine'] else m['root']
    dup(top, 'CTRL_torso', 'CTRL_hips')
    for b in m['spine'][:-1]:
        dup(b, f'MCH_{b}', 'CTRL_torso')
    dup(m['head'], 'CTRL_head', 'CTRL_torso')
    if m['neck'] in eb:
        dup(m['neck'], f'MCH_{m["neck"]}', 'CTRL_head')
    def joint(name, a, b, parent):  # bone between real joint positions (game-bone tails don't aim at the child)
        e = eb.new(name); e.head = eb[a].head.copy(); e.tail = eb[b].head.copy()
        e.parent = eb[parent]; e.use_deform = False

    def chains(bones, end, base, pole_off, key):
        """IK joint chain + MCH sockets (model-bone rest, riding the IK chain) + FK chain + pole."""
        prev = base
        for b, nxt in zip(bones, bones[1:] + [end]):
            joint(f'IK_{b}', b, nxt, prev); dup(b, f'MCH_{b}', f'IK_{b}'); prev = f'IK_{b}'
        prev = base
        for b in bones + [end]:
            dup(b, f'FK_{b}', prev); prev = f'FK_{b}'
        p = eb.new(f'CTRL_pole_{key}'); p.head = eb[bones[-1]].head + Vector(pole_off)
        p.tail = p.head + Vector(pole_off).normalized() * 0.05 * height; p.parent = eb['CTRL_root']; p.use_deform = False

    for s, (clav, sh, el, ha) in m['arm'].items():
        chains([sh, el], ha, clav, (0, pole_dist, 0), f'arm_{s}')   # elbows bend backwards
        dup(ha, f'CTRL_hand_{s}', 'CTRL_root')
    for s, chain in m['leg'].items():
        chains(chain[:-1], chain[-1], m['hips'], (0, -pole_dist, 0), f'leg_{s}')  # knees bend forwards
        dup(chain[-1], f'CTRL_foot_{s}', 'CTRL_root')
    if m['weapon']:
        dup(m['weapon'], 'CTRL_weapon', None)
    foot_z = {s: eb[c[-1]].head.z for s, c in m['leg'].items()}
    bpy.ops.object.mode_set(mode='POSE')

    P = arm.pose.bones
    for pb in P:  # the importer leaves the pose of the game clip's first frame: start from the rest pose
        pb.rotation_mode = 'QUATERNION'
        pb.matrix_basis = Matrix.Identity(4)
    for k, v in dict(ik_arm_L=1, ik_arm_R=1, ik_leg_L=1, ik_leg_R=1, weapon_hand=1, grip_L=0, floor=1).items():
        arm[k] = float(v)
        arm.id_properties_ui(k).update(min=0.0, max=1.0)

    def drive(con, expr, prop):
        fc = con.driver_add('influence'); d = fc.driver; d.type = 'SCRIPTED'
        v = d.variables.new(); v.name = 'p'; v.type = 'SINGLE_PROP'
        v.targets[0].id_type = 'OBJECT'; v.targets[0].id = arm; v.targets[0].data_path = f'["{prop}"]'
        d.expression = expr

    def copy(owner, target, kind='COPY_TRANSFORMS', influence=1.0, name=None):
        c = P[owner].constraints.new(kind); c.target = arm; c.subtarget = target
        if hasattr(c, 'owner_space'):
            c.owner_space = c.target_space = 'WORLD'
        c.influence = influence
        if name:
            c.name = name
        return c

    copy(m['root'], 'CTRL_hips')
    n = len(m['spine'])
    for i, b in enumerate(m['spine']):
        copy(b, 'CTRL_torso' if i == n - 1 else f'MCH_{b}', 'COPY_ROTATION', 1.0 if i == n - 1 else (i + 1) / n)
    copy(m['head'], 'CTRL_head', 'COPY_ROTATION')
    if m['neck'] in P:
        copy(m['neck'], f'MCH_{m["neck"]}', 'COPY_ROTATION', 0.5)

    def limb(bones, end, ctrl, key):
        ik = P[f'IK_{bones[-1]}'].constraints.new('IK')
        ik.target = arm; ik.subtarget = ctrl; ik.pole_target = arm; ik.pole_subtarget = f'CTRL_pole_{key}'
        ik.chain_count = len(bones); ik.use_stretch = False
        for b in bones:
            copy(b, f'FK_{b}', name='FK')
            drive(copy(b, f'MCH_{b}', name='IK'), 'p', f'ik_{key}')
        copy(end, f'FK_{end}', 'COPY_ROTATION', name='FK')
        drive(copy(end, ctrl, 'COPY_ROTATION', name='IK'), 'p', f'ik_{key}')
        iks.append((ik, bones))

    def calibrate(ik, bones):
        """Pick the pole angle that leaves the chain in its rest pose (no formula guessing about bone rolls)."""
        def err(a):
            ik.pole_angle = a; ctx.view_layer.update()
            return sum(P[f'MCH_{b}'].matrix.to_quaternion().rotation_difference(arm.data.bones[b].matrix_local.to_quaternion()).angle
                       for b in bones)
        best = min((math.radians(a) for a in range(-180, 180, 5)), key=err)
        ik.pole_angle = min((best + math.radians(d / 4) for d in range(-20, 21)), key=err)

    iks = []
    for s, (clav, sh, el, ha) in m['arm'].items():
        limb([sh, el], ha, f'CTRL_hand_{s}', f'arm_{s}')
    for s, chain in m['leg'].items():
        limb(chain[:-1], chain[-1], f'CTRL_foot_{s}', f'leg_{s}')
        lim = P[f'CTRL_foot_{s}'].constraints.new('LIMIT_LOCATION')
        lim.use_min_z = True; lim.min_z = foot_z[s]; lim.owner_space = 'WORLD'; lim.name = 'floor'
        drive(lim, 'p', 'floor')

    for ik, bones in iks:
        calibrate(ik, bones)

    if m['weapon']:
        copy(m['weapon'], 'CTRL_weapon')
        hand = arm.data.bones[weapon_hand] if weapon_hand in arm.data.bones else None
        if hand:
            ch = P['CTRL_weapon'].constraints.new('CHILD_OF'); ch.name = 'in_hand'
            ch.target = arm; ch.subtarget = weapon_hand
            ch.inverse_matrix = hand.matrix_local.inverted()
            drive(ch, 'p', 'weapon_hand')
        left = m['arm']['L'][-1] if 'L' in m['arm'] else None
        if left:
            g = P['CTRL_hand_L'].constraints.new('CHILD_OF'); g.name = 'on_weapon'
            g.target = arm; g.subtarget = 'CTRL_weapon'
            g.inverse_matrix = arm.data.bones['CTRL_weapon'].matrix_local.inverted()
            drive(g, 'p', 'grip_L')

    # collections + shapes
    cols = {k: arm.data.collections.get(k) or arm.data.collections.new(k) for k in ('CTRL', 'MCH', 'DEF')}
    shapes = {'CTRL_root': ('square', 1.2), 'CTRL_hips': ('circle', 1.5), 'CTRL_torso': ('circle', 1.4), 'CTRL_head': ('circle', 1.2),
              'CTRL_weapon': ('diamond', 1.0)}
    for b in arm.data.bones:
        if b.name.startswith(('CTRL_', 'FK_')):
            cols['CTRL'].assign(b)
            kind, sc = shapes.get(b.name, ('diamond' if 'pole' in b.name else 'circle', 0.6))
            P[b.name].custom_shape = _widget(f'WGT_{kind}', kind)
            P[b.name].custom_shape_scale_xyz = (sc * 0.15 * height / max(b.length, 1e-3),) * 3
        elif b.name.startswith(('MCH_', 'IK_')):
            cols['MCH'].assign(b)
        else:
            cols['DEF'].assign(b)
    cols['MCH'].is_visible = False
    bpy.ops.object.mode_set(mode='OBJECT')
    return Rig(arm, m)


# ---------------------------------------------------------------- bake

def bake(rig, start, end, strip=False):
    """Bake the visual pose of every model bone into plain keys (new action), drop constraints from model bones.
    strip=True also deletes the control/mechanism bones (clean armature for export)."""
    arm = rig.arm; scn = bpy.context.scene
    bones = [b for b in arm.data.bones if not is_control(b.name)]
    order = sorted(bones, key=lambda b: len(b.parent_recursive))
    mats = {}
    for f in range(start, end + 1):
        scn.frame_set(f)
        mats[f] = {b.name: arm.pose.bones[b.name].matrix.copy() for b in bones}
    props = {k: arm.get(k) for k in ('weapon_hand', 'grip_L')}
    old = arm.animation_data.action if arm.animation_data else None
    act = bpy.data.actions.new(f'{arm.name}_baked')
    arm.animation_data_create(); arm.animation_data.action = act
    for b in bones:
        pb = arm.pose.bones[b.name]
        for c in list(pb.constraints):
            pb.constraints.remove(c)
    for f in range(start, end + 1):
        for b in order:
            M = mats[f][b.name]
            if b.parent:
                basis = (b.parent.matrix_local.inverted() @ b.matrix_local).inverted() @ mats[f][b.parent.name].inverted() @ M
            else:
                basis = b.matrix_local.inverted() @ M
            pb = arm.pose.bones[b.name]
            l, r, s = basis.decompose()
            pb.location, pb.rotation_quaternion, pb.scale = l, r, s
            for p in ('location', 'rotation_quaternion', 'scale'):
                pb.keyframe_insert(p, frame=f, group=b.name)
    if strip:
        bpy.context.view_layer.objects.active = arm
        bpy.ops.object.mode_set(mode='EDIT')
        for e in [e for e in arm.data.edit_bones if is_control(e.name)]:
            arm.data.edit_bones.remove(e)
        bpy.ops.object.mode_set(mode='OBJECT')
    if old and old.users == 0:
        bpy.data.actions.remove(old)
    return act


def limb_penetration(arm, start, end, limbs=None, torso=None, margin=0.01, max_depth=0.12):
    """Frames where forearm/shin sample points are inside the deformed torso surface (real mesh, not a capsule).
    Returns [(frame, bone, depth_m)]. Torso = mesh faces whose vertices are weighted mostly to pelvis/spine bones."""
    from mathutils.bvhtree import BVHTree
    m = detect(arm)
    torso = set(torso or [m['root'], m['hips'], *m['spine'], m['neck']])
    limbs = limbs or [(c[2], c[3]) for c in m['arm'].values()]          # (elbow, hand) pairs
    me = next(o for o in bpy.data.objects if o.type == 'MESH' and any(md.type == 'ARMATURE' and md.object == arm for md in o.modifiers))
    names = {g.index: g.name for g in me.vertex_groups}
    main = [names.get(max(v.groups, key=lambda g: g.weight).group) if len(v.groups) else None for v in me.data.vertices]
    polys = [list(p.vertices) for p in me.data.polygons if sum(main[i] in torso for i in p.vertices) * 2 > len(p.vertices)]
    out, P = [], arm.pose.bones
    for f in range(start, end + 1):
        bpy.context.scene.frame_set(f)
        dg = bpy.context.evaluated_depsgraph_get(); ev = me.evaluated_get(dg); mesh = ev.to_mesh()
        tree = BVHTree.FromPolygons([me.matrix_world @ v.co for v in mesh.vertices], polys)
        ev.to_mesh_clear()
        for a, b in limbs:
            worst = 0.0
            for k in range(1, 5):
                p = arm.matrix_world @ P[a].head.lerp(P[b].head, k / 4)
                loc, nrm, _, dist = tree.find_nearest(p)
                if loc is not None and (p - loc).dot(nrm) < 0 and margin < dist < max_depth:  # deeper = open-shell noise
                    worst = max(worst, dist)
            if worst:
                out.append((f, a, round(worst, 3)))
    return out


def fcurves(obj):
    """All F-curves of the object's action (Blender 4.4+ layered actions and the legacy API)."""
    act = obj.animation_data.action if obj.animation_data else None
    if not act:
        return []
    if hasattr(act, 'layers') and len(act.layers):
        return [fc for layer in act.layers for strip in layer.strips for cb in strip.channelbags for fc in cb.fcurves]
    return list(getattr(act, 'fcurves', []))


def import_glb(path):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    new = [o for o in bpy.data.objects if o not in before]
    for o in list(new):
        if o.name.startswith('Icosphere'):
            bpy.data.objects.remove(o, do_unlink=True)
    return next(o for o in bpy.data.objects if o.type == 'ARMATURE' and o in new)


if __name__ == '__main__':
    import sys
    a = sys.argv[sys.argv.index('--') + 1:]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    arm = import_glb(a[0])
    build(arm)
    bpy.ops.wm.save_as_mainfile(filepath=a[1])
    print('rig saved:', a[1])
