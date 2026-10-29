"""Action toolkit for the kill shots: dash + ghosts, spin slash with energy trail, impacts, knockdowns."""
import bpy, math, random
from mathutils import Vector, Quaternion
import stage as S

TAU = math.tau


def face(src, dst):
    return math.degrees(math.atan2(dst[0] - src[0], -(dst[1] - src[1])))


def set_interp(obj, interp='BEZIER', data=False):
    ad = (obj.data if data else obj).animation_data
    if ad and ad.action:
        for fc in S.iter_fcurves(ad.action):
            for kp in fc.keyframe_points:
                kp.interpolation = interp


def emission_mat(name, color, strength, alpha=1.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    em = nt.nodes.new('ShaderNodeEmission'); em.inputs['Color'].default_value = (*color, 1); em.inputs['Strength'].default_value = strength
    if alpha < 1.0:
        tr = nt.nodes.new('ShaderNodeBsdfTransparent'); mix = nt.nodes.new('ShaderNodeMixShader')
        mix.inputs['Fac'].default_value = alpha
        nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(em.outputs[0], mix.inputs[2]); nt.links.new(mix.outputs[0], out.inputs[0])
    else:
        nt.links.new(em.outputs[0], out.inputs[0])
    return m, em


def key_value(socket, frame, value):
    socket.default_value = value; socket.keyframe_insert('default_value', frame=frame)


# ---------------------------------------------------------------- Kayn rig

class Kayn:
    def __init__(self, loc, rot_deg, ghosts=2):
        self.arm = S.champion('kayn', loc=loc, rot_deg=rot_deg)
        self.root = self.arm.parent
        self.ghosts = []
        for i in range(ghosts):
            g = S.champion('kayn', loc=loc, rot_deg=rot_deg)
            m, em = emission_mat(f'ghost{i}', (1.0, 0.12, 0.08), 0.7 - i * 0.2, alpha=0.16 - i * 0.05)
            for o in g.children:
                if o.type == 'MESH':
                    o.data.materials.clear(); o.data.materials.append(m)
                    o.visible_shadow = False
            self.ghosts.append(g)
        aura = S.point('kayn_aura', (0, 0, 1.2), (1.0, 0.1, 0.05), 0, radius=0.3)
        aura.parent = self.root
        self.aura = aura

    def keys(self, frame, loc=None, rot=None):
        if loc is not None:
            self.root.location = loc; self.root.keyframe_insert('location', frame=frame)
        if rot is not None:
            self.root.rotation_euler = tuple(math.radians(r) for r in rot); self.root.keyframe_insert('rotation_euler', frame=frame)

    def ghost_trail(self, f0, f1, lag=2):
        """Ghost copies replay the root motion delayed by lag*i frames, visible only during [f0, f1]."""
        src = self.root.animation_data.action
        for i, g in enumerate(self.ghosts, 1):
            gr = g.parent
            for f in range(f0, f1 + lag * i + 1):
                bpy.context.scene.frame_set(max(1, f - lag * i))
                loc, rot = self.root.location.copy(), self.root.rotation_euler.copy()
                gr.location = loc; gr.rotation_euler = rot
                gr.keyframe_insert('location', frame=f); gr.keyframe_insert('rotation_euler', frame=f)
            set_interp(gr, 'LINEAR')
            for o in [g] + list(g.children):
                for f, hide in ((1, True), (f0, False), (f1 + lag * i, True)):
                    o.hide_render = hide; o.keyframe_insert('hide_render', frame=f)
                for fc in S.iter_fcurves(o.animation_data.action):
                    if fc.data_path == 'hide_render':
                        for kp in fc.keyframe_points:
                            kp.interpolation = 'CONSTANT'

    def aura_pulse(self, frames_energy):
        for f, e in frames_energy:
            S.key(self.aura, f, energy=e)


# ---------------------------------------------------------------- slash trail

def slash_trail(parent, f_on, f_off, r_in=1.05, r_out=1.75, z=0.72, tilt=(0, 0), color=(1.0, 0.07, 0.03), arc_deg=170, head_deg=-22, strength=4.5):
    """Energy arc ribbon parented to Kayn's root (spins with him). Head at the blade angle, fading behind it
    (behind = positive angle, since the spin is clockwise). Fade stored in a vertex color attribute."""
    import bmesh
    me = bpy.data.meshes.new('TrailMesh'); bm = bmesh.new()
    seg = 90
    verts = []
    for i in range(seg + 1):
        u = i / seg
        a = math.radians(head_deg + arc_deg * u)
        rr_out = r_out * (1 - 0.18 * u)
        rr_in = r_in + (r_out - r_in) * 0.75 * u
        v1 = bm.verts.new((rr_in * math.cos(a), rr_in * math.sin(a), 0))
        v2 = bm.verts.new((rr_out * math.cos(a), rr_out * math.sin(a), 0.04 * u))
        verts.append((v1, v2))
    for i in range(seg):
        bm.faces.new((verts[i][0], verts[i][1], verts[i + 1][1], verts[i + 1][0]))
    bm.to_mesh(me); bm.free()
    attr = me.color_attributes.new('fade', 'FLOAT_COLOR', 'POINT')
    for i in range(seg + 1):
        f = (1 - i / seg) ** 3.2
        for j, edge in enumerate((0.25, 1.0)):
            attr.data[2 * i + j].color = (f * edge, f, f, 1)
    ring = bpy.data.objects.new('SlashTrail', me); bpy.context.scene.collection.objects.link(ring)
    ring.parent = parent; ring.location = (0, 0, z); ring.rotation_euler = (math.radians(tilt[0]), math.radians(tilt[1]), 0)
    m = bpy.data.materials.new('Trail'); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    ca = nt.nodes.new('ShaderNodeAttribute'); ca.attribute_name = 'fade'
    sep = nt.nodes.new('ShaderNodeSeparateColor'); nt.links.new(ca.outputs['Color'], sep.inputs[0])
    master = nt.nodes.new('ShaderNodeValue'); master.outputs[0].default_value = 0
    mul = nt.nodes.new('ShaderNodeMath'); mul.operation = 'MULTIPLY'; mul.use_clamp = True
    nt.links.new(sep.outputs['Red'], mul.inputs[0]); nt.links.new(master.outputs[0], mul.inputs[1])
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].color = (*color, 1); ramp.color_ramp.elements[1].color = (1.0, 0.8, 0.7, 1)
    ramp.color_ramp.elements[0].position = 0.55; ramp.color_ramp.elements[1].position = 0.98
    nt.links.new(sep.outputs['Green'], ramp.inputs['Fac'])
    em = nt.nodes.new('ShaderNodeEmission'); em.inputs['Strength'].default_value = strength
    nt.links.new(ramp.outputs['Color'], em.inputs['Color'])
    tr = nt.nodes.new('ShaderNodeBsdfTransparent'); mix = nt.nodes.new('ShaderNodeMixShader')
    nt.links.new(mul.outputs[0], mix.inputs['Fac']); nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs[0])
    me.materials.append(m)
    ring.visible_shadow = False; ring.visible_diffuse = False
    mo = master.outputs[0]
    for f, v in ((1, 0), (f_on - 1, 0), (f_on + 2, 0.9), (f_off - 4, 0.9), (f_off, 0)):
        mo.default_value = v; mo.keyframe_insert('default_value', frame=max(1, f))
    return ring


# ---------------------------------------------------------------- impact FX

def impact(pos, f, color=(1.0, 0.15, 0.06), core=(1.0, 0.9, 0.8), sparks=70, seed=1, scale=1.0, slow=1.0, ground_z=0.02):
    """Flash light + spark burst + expanding shock ring + floor ring. slow>1 stretches the timing (slow-mo)."""
    random.seed(seed)
    pos = Vector(pos)
    L = S.point('impact_light', pos, core, 0, radius=0.15)
    for ff, e in ((f - 1, 0), (f, 4500 * scale), (f + int(3 * slow), 1200 * scale), (f + int(10 * slow), 0)):
        S.key(L, max(1, ff), energy=e)
    L2 = S.point('impact_glow', pos, color, 0, radius=0.6)
    for ff, e in ((f - 1, 0), (f + 1, 3000 * scale), (f + int(18 * slow), 0)):
        S.key(L2, max(1, ff), energy=e)
    # sparks: stretched emissive bits flying out with gravity
    sm, _ = emission_mat('spark', (1.0, 0.55, 0.25), 22)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=6, ring_count=4, radius=1)
    proto = bpy.context.object; proto.data.materials.append(sm); proto.hide_render = True; proto.name = 'spark_proto'
    life = int(16 * slow)
    for i in range(sparks):
        o = bpy.data.objects.new(f'spark{seed}_{i}', proto.data); bpy.context.scene.collection.objects.link(o)
        d = Vector((random.gauss(0, 1), random.gauss(0, 1), random.uniform(-0.2, 1.2))).normalized()
        spd = random.uniform(2.5, 7.0) * scale
        n = 6
        for k in range(n + 1):
            ff = f + int(life * k / n)
            tt = (life * k / n) / 30.0 / slow
            p = pos + d * spd * tt * (1 - 0.35 * k / n) + Vector((0, 0, -4.5 * tt * tt))
            o.location = p; o.keyframe_insert('location', frame=ff)
            s = 0.012 * scale * (1 - k / n)
            o.scale = (s, s, s * 3.5); o.keyframe_insert('scale', frame=ff)
        o.rotation_mode = 'QUATERNION'; o.rotation_quaternion = d.to_track_quat('Z', 'Y')
        o.scale = (0, 0, 0); o.keyframe_insert('scale', frame=f - 1)
    # shock ring (vertical-ish disc facing up, expanding)
    sh, she = emission_mat('shock', color, 0, alpha=0.9)
    bpy.ops.mesh.primitive_torus_add(major_radius=1, minor_radius=0.012, location=pos)
    ring = bpy.context.object; ring.data.materials.append(sh); ring.visible_shadow = False
    ring.rotation_euler = (math.radians(random.uniform(60, 80)), 0, math.radians(random.uniform(0, 180)))
    for ff, sc, st in ((f - 1, 0.01, 0), (f, 0.12, 8), (f + int(12 * slow), 1.0 * scale, 0)):
        ring.scale = (sc, sc, sc); ring.keyframe_insert('scale', frame=max(1, ff)); key_value(she.inputs['Strength'], max(1, ff), st)
    # floor ring
    fl, fle = emission_mat('floorring', color, 0, alpha=0.85)
    bpy.ops.mesh.primitive_torus_add(major_radius=1, minor_radius=0.02, location=(pos.x, pos.y, ground_z))
    fr = bpy.context.object; fr.data.materials.append(fl); fr.scale = (1, 1, 0.2); fr.visible_shadow = False
    for ff, sc, st in ((f - 1, 0.01, 0), (f + 1, 0.3, 1.5), (f + int(20 * slow), 1.8 * scale, 0)):
        fr.scale = (sc, sc, 0.2); fr.keyframe_insert('scale', frame=max(1, ff)); key_value(fle.inputs['Strength'], max(1, ff), st)
    return L


# ---------------------------------------------------------------- victim reaction

def knockdown(root, f_hit, f_land, away_dir, dist=2.2, lift=0.6, fall_deg=-86, flash_color=(1, 0.2, 0.1)):
    """Victim flung away from the hit and falls backwards onto the floor (pivot at feet)."""
    p0 = root.location.copy(); r0 = root.rotation_euler.copy()
    away = Vector((away_dir[0], away_dir[1], 0)).normalized()
    mid = int(f_hit + (f_land - f_hit) * 0.45)
    root.keyframe_insert('location', frame=f_hit); root.keyframe_insert('rotation_euler', frame=f_hit)
    root.location = p0 + away * dist * 0.6 + Vector((0, 0, lift)); root.keyframe_insert('location', frame=mid)
    root.rotation_euler = (math.radians(fall_deg * 0.45), r0.y, r0.z); root.keyframe_insert('rotation_euler', frame=mid)
    root.location = p0 + away * dist; root.keyframe_insert('location', frame=f_land)
    root.rotation_euler = (math.radians(fall_deg), r0.y, r0.z); root.keyframe_insert('rotation_euler', frame=f_land)
    # tiny bounce
    root.location = p0 + away * (dist + 0.15) + Vector((0, 0, 0.06)); root.keyframe_insert('location', frame=f_land + 4)
    root.location = p0 + away * (dist + 0.2); root.keyframe_insert('location', frame=f_land + 8)
    hit = S.point('hitflash', p0 + Vector((0, 0, 1.3)), flash_color, 0, radius=0.4)
    hit.parent = root; hit.location = (0, 0, 1.3)
    for ff, e in ((f_hit - 1, 0), (f_hit, 1500), (f_hit + 8, 0)):
        S.key(hit, ff, energy=e)
    arm = next((c for c in root.children if c.type == 'ARMATURE'), None)
    if arm:
        body_reaction(arm, f_hit, f_land)


# ---------------------------------------------------------------- bone reaction (hit whiplash + fall)
# Pose deltas are layered over the idle (action blend COMBINE). Each joint bends its segment toward a
# direction in the champion's own frame: faces -Y, left = +X, up = +Z. Values in degrees:
# arch/head = lean back, arms = fling out sideways, elbows = flex, hips = thigh forward, knees = buckle.

REST = dict(arch=0, head=0, arms=0, elbows=0, hips=0, knees=0)
FALLEN = dict(arch=4, head=12, arms=85, elbows=20, hips=38, knees=60)


def _bend(B, bone, seg, toward, deg):
    ax = Vector(seg).cross(Vector(toward))
    ax = B[bone].matrix_local.to_3x3().inverted() @ ax   # armature space -> bone local
    return Quaternion(ax.normalized(), math.radians(deg))


def body_pose(arm, arch=0, head=0, arms=0, elbows=0, hips=0, knees=0):
    B = arm.data.bones
    up, back, fwd = (0, 0, 1), (0, 1, 0), (0, -1, 0)
    seg = lambda a, b: B[b].head_local - B[a].head_local
    # Volibear's torso is skinned to the *_SKN chain, not Spine1/2: bend only Chest there
    spine = ['Chest'] if 'Spine1_SKN' in B else [n for n in ('Spine1', 'Spine2', 'Chest') if n in B]
    q = {n: _bend(B, n, up, back, arch / len(spine)) for n in spine}
    for n, w in (('Neck', 0.4), ('Head', 0.6)):
        if n in B:
            q[n] = _bend(B, n, up, back, head * w)
    for sd, sg, amt in (('L', 1, 1.0), ('R', -1, 0.88)):   # slight asymmetry reads less robotic
        sh, el, ha = f'{sd}_Shoulder', f'{sd}_Elbow', f'{sd}_Hand'
        if sh in B and el in B:
            out = Vector((sg, 0, 0.35))   # cap so rigs whose rest arms are already raised don't flip overhead
            q[sh] = _bend(B, sh, seg(sh, el), out, min(arms, math.degrees(seg(sh, el).angle(out)) + 12) * amt)
        if el in B and ha in B:
            q[el] = _bend(B, el, seg(el, ha), fwd, elbows * amt)
        hip, ku, kl, ft = f'{sd}_Hip', f'{sd}_KneeUpper', f'{sd}_KneeLower', f'{sd}_Foot'
        if hip in B and ku in B:
            q[hip] = _bend(B, hip, seg(hip, ku), fwd, hips * (2 - amt))
        for k in (ku, kl):
            if k in B and ft in B:
                q[k] = _bend(B, k, seg(k, ft), back, knees * (2 - amt) / 2)
    return q


def key_pose(arm, frame, **pose):
    arm.animation_data.action_blend_type = 'COMBINE'
    for n, qq in body_pose(arm, **{**REST, **pose}).items():
        pb = arm.pose.bones[n]; pb.rotation_mode = 'QUATERNION'
        pb.rotation_quaternion = qq; pb.keyframe_insert('rotation_quaternion', frame=max(1, frame), group=n)


def body_reaction(arm, f_hit, f_land):
    """Whiplash on the hit (back arches, head snaps back, arms fling out, knees buckle), then the landing slam."""
    s = max(1.0, (f_land - f_hit) / 26)   # slow-mo shots stretch the whiplash too
    keys = [
        (f_hit - 1, REST),
        (f_hit + round(2 * s), dict(arch=38, head=45, arms=55, elbows=15, hips=15, knees=25)),
        (f_hit + round(6 * s), dict(arch=30, head=35, arms=85, elbows=30, hips=35, knees=55)),
        (int(f_hit + (f_land - f_hit) * 0.6), dict(arch=22, head=25, arms=72, elbows=25, hips=45, knees=70)),
        (f_land, dict(arch=0, head=30, arms=95, elbows=15, hips=40, knees=65)),
        (f_land + 3, dict(arch=8, head=-10, arms=80, elbows=25, hips=50, knees=75)),
        (f_land + 10, FALLEN),
    ]
    for f, pose in keys:
        key_pose(arm, f, **pose)
