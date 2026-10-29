"""Shared stage for the Kayn edit: dark wet floor, fog, embers, champion loading, camera rigs."""
import bpy, math, random
from mathutils import Vector

IN = __import__('os').environ.get('KAYN_IN', __import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)), '..', 'work', 'in'))
FPS = 30


def reset(frames, res=(720, 1280), samples=16, fog=0.0, fog_color=(0.55, 0.6, 0.8)):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    s = bpy.context.scene
    s.render.fps = FPS
    s.frame_start, s.frame_end = 1, frames
    s.render.engine = 'CYCLES'
    s.cycles.device = 'CPU'
    s.cycles.samples = samples
    s.cycles.use_adaptive_sampling = True
    s.cycles.adaptive_threshold = 0.05
    s.cycles.use_denoising = True
    s.cycles.denoiser = 'OPENIMAGEDENOISE'
    s.cycles.max_bounces = 4
    s.cycles.volume_bounces = 0
    s.cycles.volume_step_rate = 4
    s.cycles.blur_glossy = 1.0
    s.cycles.sample_clamp_indirect = 4
    s.render.resolution_x, s.render.resolution_y = res
    s.render.use_persistent_data = True
    s.render.use_motion_blur = True
    s.render.motion_blur_shutter = 0.5
    s.render.film_transparent = False
    s.view_settings.view_transform = 'AgX'
    s.view_settings.look = 'AgX - Punchy'
    s.render.image_settings.file_format = 'PNG'
    s.render.image_settings.color_mode = 'RGB'
    # world: near-black with faint cool fill + homogeneous fog
    w = bpy.data.worlds.new('W'); s.world = w; w.use_nodes = True
    nt = w.node_tree; nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputWorld')
    bg = nt.nodes.new('ShaderNodeBackground'); bg.inputs[0].default_value = (0.006, 0.007, 0.012, 1); bg.inputs[1].default_value = 1
    nt.links.new(bg.outputs[0], out.inputs['Surface'])
    if fog > 0:
        vol = nt.nodes.new('ShaderNodeVolumePrincipled')
        vol.inputs['Density'].default_value = fog
        vol.inputs['Color'].default_value = (*fog_color, 1)
        vol.inputs['Anisotropy'].default_value = 0.35
        nt.links.new(vol.outputs[0], out.inputs['Volume'])
    floor()
    return s


def floor():
    bpy.ops.mesh.primitive_plane_add(size=80, location=(0, 0, 0))
    p = bpy.context.object; p.name = 'FLOOR'
    m = bpy.data.materials.new('FloorMat'); m.use_nodes = True
    nt = m.node_tree; b = nt.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (0.012, 0.012, 0.016, 1)
    # puddle-like roughness variation
    noise = nt.nodes.new('ShaderNodeTexNoise'); noise.inputs['Scale'].default_value = 0.6; noise.inputs['Detail'].default_value = 6
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = 0.42; ramp.color_ramp.elements[0].color = (0.05, 0.05, 0.05, 1)
    ramp.color_ramp.elements[1].position = 0.6; ramp.color_ramp.elements[1].color = (0.45, 0.45, 0.45, 1)
    nt.links.new(noise.outputs['Fac'], ramp.inputs['Fac'])
    nt.links.new(ramp.outputs['Color'], b.inputs['Roughness'])
    bump = nt.nodes.new('ShaderNodeBump'); bump.inputs['Strength'].default_value = 0.08
    n2 = nt.nodes.new('ShaderNodeTexNoise'); n2.inputs['Scale'].default_value = 25
    nt.links.new(n2.outputs['Fac'], bump.inputs['Height']); nt.links.new(bump.outputs['Normal'], b.inputs['Normal'])
    p.data.materials.append(m)


def champion(name, loc=(0, 0, 0), rot_deg=0.0, scale=1.0, anim_offset=0, anim_speed=1.0):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=f'{IN}/{name}.glb')
    new = [o for o in bpy.data.objects if o not in before]
    for o in new:
        if o.name.startswith('Icosphere'):
            bpy.data.objects.remove(o, do_unlink=True)
    new = [o for o in bpy.data.objects if o not in before]
    arm = next(o for o in new if o.type == 'ARMATURE')
    arm.name = name.upper()
    root = bpy.data.objects.new(f'ROOT_{name}', None); bpy.context.scene.collection.objects.link(root)
    arm.parent = root
    root.location = loc; root.rotation_euler[2] = math.radians(rot_deg); root.scale = (scale,) * 3
    # loop the idle via NLA strip, offset so champions don't breathe in sync
    ad = arm.animation_data
    if ad and ad.action:
        act = ad.action; ad.action = None
        tr = ad.nla_tracks.new(); st = tr.strips.new(act.name, int(1 - anim_offset), act)
        st.repeat = 50; st.scale = 1.0 / anim_speed
    for o in new:
        if o.type == 'MESH':
            o.name = f'{name}_mesh'
            for slot in o.material_slots:
                tune_material(slot.material)
    return arm


def tune_material(m):
    if not m or not m.use_nodes:
        return
    b = next((n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    if b:
        b.inputs['Roughness'].default_value = 0.55
        if 'Specular IOR Level' in b.inputs:
            b.inputs['Specular IOR Level'].default_value = 0.45


def area(name, loc, target, color, power, size=2.0, spread=180):
    d = bpy.data.lights.new(name, 'AREA'); d.energy = power; d.color = color; d.size = size
    d.spread = math.radians(spread)
    o = bpy.data.objects.new(name, d); bpy.context.scene.collection.objects.link(o)
    o.visible_camera = False; o.visible_glossy = False
    o.location = loc; aim(o, target)
    return o


def spot(name, loc, target, color, power, angle=35, blend=0.6, radius=0.1):
    d = bpy.data.lights.new(name, 'SPOT'); d.energy = power; d.color = color
    d.spot_size = math.radians(angle); d.spot_blend = blend; d.shadow_soft_size = radius
    o = bpy.data.objects.new(name, d); bpy.context.scene.collection.objects.link(o)
    o.visible_camera = False; o.visible_glossy = False
    o.location = loc; aim(o, target)
    return o


def point(name, loc, color, power, radius=0.2):
    d = bpy.data.lights.new(name, 'POINT'); d.energy = power; d.color = color; d.shadow_soft_size = radius
    o = bpy.data.objects.new(name, d); bpy.context.scene.collection.objects.link(o)
    o.visible_camera = False; o.visible_glossy = False
    o.location = loc
    return o


def aim(o, target):
    v = Vector(target) - Vector(o.location)
    o.rotation_euler = v.to_track_quat('-Z', 'Y').to_euler()


def empty(name, loc):
    e = bpy.data.objects.new(name, None); bpy.context.scene.collection.objects.link(e); e.location = loc
    return e


def camera(name, lens=50, fstop=2.0, focus=None, sensor=36):
    c = bpy.data.cameras.new(name); c.lens = lens; c.sensor_fit = 'VERTICAL'; c.sensor_height = 27; c.sensor_width = 27 * 9 / 16
    c.dof.use_dof = focus is not None; c.dof.aperture_fstop = fstop
    o = bpy.data.objects.new(name, c); bpy.context.scene.collection.objects.link(o)
    if focus is not None:
        c.dof.focus_object = focus
    bpy.context.scene.camera = o
    c.clip_start = 0.05; c.clip_end = 200
    return o


def track_to(obj, target):
    con = obj.constraints.new('TRACK_TO'); con.target = target
    con.track_axis = 'TRACK_NEGATIVE_Z'; con.up_axis = 'UP_Y'
    return con


def key(obj, frame, interp='BEZIER', **props):
    """key(obj, 10, location=(..), lens=..) — lens/fstop go to camera data."""
    for k, v in props.items():
        if k in ('lens',):
            obj.data.lens = v; obj.data.keyframe_insert('lens', frame=frame)
        elif k == 'fstop':
            obj.data.dof.aperture_fstop = v; obj.data.dof.keyframe_insert('aperture_fstop', frame=frame)
        elif k == 'energy':
            obj.data.energy = v; obj.data.keyframe_insert('energy', frame=frame)
        else:
            setattr(obj, k, v); obj.keyframe_insert(k, frame=frame)


def ease_all(interp='BEZIER', easing='AUTO'):
    for a in bpy.data.actions:
        for fc in iter_fcurves(a):
            if any(kp.interpolation in ('CONSTANT', 'LINEAR') for kp in fc.keyframe_points):
                continue
            for kp in fc.keyframe_points:
                kp.interpolation = interp; kp.easing = easing


def iter_fcurves(action):
    if hasattr(action, 'fcurves') and len(getattr(action, 'fcurves', [])):
        return list(action.fcurves)
    out = []
    for layer in getattr(action, 'layers', []):
        for strip in layer.strips:
            for cb in strip.channelbags:
                out.extend(cb.fcurves)
    return out


def embers(count=220, center=(0, 0, 1.2), size=(6, 6, 3), color=(1.0, 0.22, 0.03), strength=5, seed=1, rise=0.6, frames=200):
    """Glowing drifting embers as instanced tiny spheres (deterministic)."""
    random.seed(seed)
    m = bpy.data.materials.new('Ember'); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial'); em = nt.nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = (*color, 1); em.inputs['Strength'].default_value = strength
    nt.links.new(em.outputs[0], out.inputs[0])
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=1)
    proto = bpy.context.object; proto.name = 'EmberProto'; proto.data.materials.append(m)
    proto.hide_render = True; proto.hide_viewport = True
    col = bpy.data.collections.new('Embers'); bpy.context.scene.collection.children.link(col)
    for i in range(count):
        o = bpy.data.objects.new(f'ember{i}', proto.data); col.objects.link(o)
        r = random.uniform(0.003, 0.009); o.scale = (r, r, r)
        p0 = Vector((center[0] + random.uniform(-size[0], size[0]) / 2, center[1] + random.uniform(-size[1], size[1]) / 2,
                     center[2] + random.uniform(-size[2], size[2]) / 2))
        drift = Vector((random.uniform(-0.25, 0.25), random.uniform(-0.25, 0.25), random.uniform(0.3, 1.0) * rise))
        sec = frames / FPS
        o.location = p0; o.keyframe_insert('location', frame=1)
        o.location = p0 + drift * sec; o.keyframe_insert('location', frame=frames)
        for fc in iter_fcurves(o.animation_data.action):
            for kp in fc.keyframe_points:
                kp.interpolation = 'LINEAR'
    return col


def render(out_dir, start=None, end=None, step=1):
    s = bpy.context.scene
    a = start or s.frame_start; b = end or s.frame_end
    for f in range(a, b + 1, step):
        s.frame_set(f)
        s.render.filepath = f'{out_dir}/{f:04d}.png'
        bpy.ops.render.render(write_still=True)


def haze(name, loc, size, color, strength=0.6, rot=(90, 0, 0), falloff=0.9):
    """Soft glowing haze card (cheap fake volumetric): radial-gradient emission, transparent edges."""
    bpy.ops.mesh.primitive_plane_add(size=1, location=loc, rotation=tuple(math.radians(r) for r in rot))
    p = bpy.context.object; p.name = name; p.scale = (size[0], size[1], 1)
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    tc = nt.nodes.new('ShaderNodeTexCoord')
    grad = nt.nodes.new('ShaderNodeTexGradient'); grad.gradient_type = 'SPHERICAL'
    mp = nt.nodes.new('ShaderNodeMapping'); mp.inputs['Location'].default_value = (0.5, 0.5, 0); mp.inputs['Scale'].default_value = (2, 2, 2)
    sub = nt.nodes.new('ShaderNodeVectorMath'); sub.operation = 'SUBTRACT'; sub.inputs[1].default_value = (0.5, 0.5, 0)
    nt.links.new(tc.outputs['UV'], sub.inputs[0]); nt.links.new(sub.outputs[0], mp.inputs['Vector'])
    mp.inputs['Location'].default_value = (0, 0, 0)
    nt.links.new(mp.outputs[0], grad.inputs['Vector'])
    pw = nt.nodes.new('ShaderNodeMath'); pw.operation = 'POWER'; pw.inputs[1].default_value = 2.2 * falloff
    nt.links.new(grad.outputs['Fac'], pw.inputs[0])
    em = nt.nodes.new('ShaderNodeEmission'); em.inputs['Color'].default_value = (*color, 1); em.inputs['Strength'].default_value = strength
    tr = nt.nodes.new('ShaderNodeBsdfTransparent')
    mix = nt.nodes.new('ShaderNodeMixShader')
    nt.links.new(pw.outputs[0], mix.inputs['Fac']); nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs['Surface'])
    p.data.materials.append(m)
    p.visible_shadow = False; p.visible_glossy = True; p.visible_diffuse = False
    return p
