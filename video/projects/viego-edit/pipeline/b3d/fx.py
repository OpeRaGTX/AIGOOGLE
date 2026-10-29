"""Viego scene FX: floating stone blocks, emerald mist, sword trail ribbon."""
import bpy, math, random, bmesh
from mathutils import Vector
import stage as S
import action as A
import viego as VG

EMERALD = (0.15, 1.0, 0.62)
TEAL = (0.2, 0.85, 0.8)


def stone_mat():
    m = bpy.data.materials.new('Stone'); m.use_nodes = True
    nt = m.node_tree; b = nt.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (0.018, 0.022, 0.024, 1)
    b.inputs['Roughness'].default_value = 0.55
    noise = nt.nodes.new('ShaderNodeTexNoise'); noise.inputs['Scale'].default_value = 6
    bump = nt.nodes.new('ShaderNodeBump'); bump.inputs['Strength'].default_value = 0.35
    nt.links.new(noise.outputs['Fac'], bump.inputs['Height']); nt.links.new(bump.outputs['Normal'], b.inputs['Normal'])
    # emerald rim glow on grazing angles (reads as mist-lit edges)
    lw = nt.nodes.new('ShaderNodeLayerWeight'); lw.inputs['Blend'].default_value = 0.25
    ramp = nt.nodes.new('ShaderNodeValToRGB'); ramp.color_ramp.elements[0].position = 0.7
    ramp.color_ramp.elements[0].color = (0, 0, 0, 1); ramp.color_ramp.elements[1].color = (*EMERALD, 1)
    nt.links.new(lw.outputs['Facing'], ramp.inputs['Fac'])
    nt.links.new(ramp.outputs['Color'], b.inputs['Emission Color'])
    b.inputs['Emission Strength'].default_value = 0.9
    return m


def blocks(frames, n=46, seed=3, r_min=2.6, r_max=10, z=(0.4, 6.5), center=(0, 0)):
    random.seed(seed)
    m = stone_mat()
    out = []
    for i in range(n):
        a = random.uniform(0, math.tau); r = random.uniform(r_min, r_max)
        size = random.uniform(0.18, 1.1) * (0.6 + r / r_max * 0.8)
        loc = Vector((center[0] + r * math.cos(a), center[1] + r * math.sin(a), random.uniform(*z)))
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        o = bpy.context.object; o.name = f'block{i}'
        o.scale = (size * random.uniform(0.6, 1.4), size * random.uniform(0.6, 1.4), size * random.uniform(0.6, 1.4))
        bev = o.modifiers.new('bev', 'BEVEL'); bev.width = 0.03 * size * 3; bev.segments = 2
        o.data.materials.append(m)
        rot0 = Vector((random.uniform(0, 6), random.uniform(0, 6), random.uniform(0, 6)))
        spin = Vector((random.uniform(-0.6, 0.6), random.uniform(-0.6, 0.6), random.uniform(-0.6, 0.6)))
        bob = random.uniform(0.15, 0.45); ph = random.uniform(0, 6)
        for f in range(1, frames + 1, 6):
            t = f / S.FPS
            o.rotation_euler = rot0 + spin * t
            o.location = loc + Vector((0, 0, bob * math.sin(t * 0.9 + ph)))
            o.keyframe_insert('rotation_euler', frame=f); o.keyframe_insert('location', frame=f)
        out.append(o)
    return out


def mist(center=(0, 0), strength=0.35):
    S.haze('mist_back', (center[0], center[1] + 6.5, 2.6), (14, 8), (0.05, 0.45, 0.32), strength=strength)
    S.haze('mist_low1', (center[0], center[1] + 2.5, 0.35), (12, 2.0), (0.06, 0.5, 0.35), strength=strength * 0.45)
    S.haze('mist_low2', (center[0] - 2, center[1] - 2.5, 0.25), (9, 1.6), (0.05, 0.4, 0.3), strength=strength * 0.3)


def stage_lights(target=(0, 0, 1.3), key=180, rim=1100):
    tx, ty, tz = target
    S.area('rimL', (tx - 2.2, ty + 2.4, 2.8), target, EMERALD, rim, size=0.8)
    S.area('rimR', (tx + 2.2, ty + 2.2, 2.6), target, TEAL, rim * 0.8, size=0.8)
    S.area('key', (tx + 1.6, ty - 2.8, 2.6), target, (0.75, 0.9, 1.0), key, size=1.4)
    S.spot('top', (tx, ty + 0.6, 8.5), target, (0.5, 1.0, 0.8), 1800, angle=28, blend=0.5)


def sword_trail(arm, frames, active, seg=4, color=EMERALD, strength=3.0):
    """Ribbon swept by the blade over the last `seg` frames. active: list of (f0, f1) windows."""
    cache = {}
    sc = bpy.context.scene
    for f in range(1, frames + 1):
        sc.frame_set(f)
        pts = VG.sword_points(arm, n=2, length=1.75)
        mid = pts[0].lerp(pts[1], 0.62)
        cache[f] = (mid.copy(), pts[1].copy())
    me = bpy.data.meshes.new('TrailMesh'); obj = bpy.data.objects.new('SwordTrail', me)
    sc.collection.objects.link(obj)
    obj.visible_shadow = False; obj.visible_diffuse = False
    try:
        obj.cycles.use_motion_blur = False
    except AttributeError:
        pass
    m = bpy.data.materials.new('SwordTrail'); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    at = nt.nodes.new('ShaderNodeAttribute'); at.attribute_name = 'fade'
    sep = nt.nodes.new('ShaderNodeSeparateColor'); nt.links.new(at.outputs['Color'], sep.inputs[0])
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].color = (*color, 1); ramp.color_ramp.elements[1].color = (0.85, 1.0, 0.95, 1)
    ramp.color_ramp.elements[0].position = 0.75; ramp.color_ramp.elements[1].position = 1.0
    nt.links.new(sep.outputs['Green'], ramp.inputs['Fac'])
    em = nt.nodes.new('ShaderNodeEmission'); em.inputs['Strength'].default_value = strength
    nt.links.new(ramp.outputs['Color'], em.inputs['Color'])
    tr = nt.nodes.new('ShaderNodeBsdfTransparent'); mix = nt.nodes.new('ShaderNodeMixShader')
    nt.links.new(sep.outputs['Red'], mix.inputs['Fac']); nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs[0])
    me.materials.append(m)

    def on(f):
        return any(a <= f <= b for a, b in active)

    def rebuild(scene, *_):
        f = scene.frame_current
        bm = bmesh.new()
        hist = [k for k in range(f - seg, f + 1) if k in cache and on(k)]
        if len(hist) >= 2:
            # subdivide between cached frames for a smooth arc
            samples = []
            for i in range(len(hist) - 1):
                a0, a1 = cache[hist[i]], cache[hist[i + 1]]
                for s in range(4):
                    u = s / 4
                    samples.append((a0[0].lerp(a1[0], u), a0[1].lerp(a1[1], u)))
            samples.append(cache[hist[-1]])
            vs = []
            for mid, tip in samples:
                vs.append((bm.verts.new(mid), bm.verts.new(tip)))
            for i in range(len(vs) - 1):
                bm.faces.new((vs[i][0], vs[i][1], vs[i + 1][1], vs[i + 1][0]))
        bm.to_mesh(me); bm.free()
        if len(me.vertices):
            attr = me.color_attributes.get('fade') or me.color_attributes.new('fade', 'FLOAT_COLOR', 'POINT')
            nverts = len(me.vertices) // 2
            for i in range(nverts):
                age = 1 - i / max(1, nverts - 1)   # 0 newest .. 1 oldest (samples go old -> new)
                fade = (1 - age) ** 2.6
                attr.data[2 * i].color = (fade * 0.15, fade * 0.4, 0, 1)
                attr.data[2 * i + 1].color = (fade * 0.65, fade, 0, 1)
        me.update()

    bpy.app.handlers.frame_change_post.append(rebuild)
    return obj


def burst(pos, f, scale=1.0, seed=1, slow=1.0):
    """Emerald mist burst = impact FX in Viego colors."""
    return A.impact(pos, f, color=EMERALD, core=(0.75, 1.0, 0.9), seed=seed, scale=scale, slow=slow, spark_color=(0.55, 1.0, 0.8))


def prune_blocks(frames, clearance=2.4):
    """Remove floating blocks that pass too close to the camera (they read as huge pale slabs)."""
    sc = bpy.context.scene; cam = sc.camera
    doomed = set()
    blocks_ = [o for o in bpy.data.objects if o.name.startswith('block')]
    for f in range(1, frames + 1, 2):
        sc.frame_set(f)
        cp = cam.matrix_world.translation
        for o in blocks_:
            if (o.matrix_world.translation - cp).length < clearance + max(o.scale) * 0.7:
                doomed.add(o.name)
    for n in doomed:
        bpy.data.objects.remove(bpy.data.objects[n], do_unlink=True)
    sc.frame_set(1)
    return len(doomed)
