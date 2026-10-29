"""Viego loader: one armature, actions from the separate animation GLBs stacked as NLA strips."""
import bpy, math
import stage as S

FILES = {'tpose': '16bb3970', 'idle': '7e7e9469', 'dance': '9133de31', 'attack': '956c3d0a', 'respawn': 'd7d22b75'}


def _import(key):
    before = set(bpy.data.objects); acts = set(bpy.data.actions)
    bpy.ops.import_scene.gltf(filepath=f'{S.IN}/{FILES[key]}.glb')
    new = [o for o in bpy.data.objects if o not in before]
    act = next((a for a in bpy.data.actions if a not in acts), None)
    return new, act


def viego(strips, loc=(0, 0, 0), rot_deg=0.0):
    """strips: [(anim_key, start_frame, speed, repeat, blend_in)] — first strip's file provides the mesh."""
    new, act0 = _import(strips[0][0])
    ico = [o for o in new if o.name.startswith('Icosphere')]
    new = [o for o in new if o not in ico]
    for o in ico:
        bpy.data.objects.remove(o, do_unlink=True)
    arm = next(o for o in new if o.type == 'ARMATURE'); arm.name = 'VIEGO'
    root = bpy.data.objects.new('ROOT_viego', None); bpy.context.scene.collection.objects.link(root)
    arm.parent = root; root.location = loc; root.rotation_euler[2] = math.radians(rot_deg)
    for o in new:
        if o.type == 'MESH':
            for slot in o.material_slots:
                S.tune_material(slot.material)
    acts = {strips[0][0]: act0}
    for key, *_ in strips[1:]:
        if key not in acts:
            objs, a = _import(key)
            for o in objs:
                bpy.data.objects.remove(o, do_unlink=True)
            acts[key] = a
    ad = arm.animation_data or arm.animation_data_create(); ad.action = None
    for i, (key, start, speed, repeat, blend) in enumerate(strips):
        tr = ad.nla_tracks.new(); tr.name = f't{i}_{key}'
        st = tr.strips.new(f'{key}{i}', int(start), acts[key])
        st.scale = 1.0 / speed; st.repeat = repeat
        st.blend_in = blend; st.extrapolation = 'HOLD_FORWARD' if i == len(strips) - 1 else 'NOTHING'
        if i < len(strips) - 1:
            st.extrapolation = 'NOTHING'
    return arm


def sword_points(arm, n=6, length=1.75):
    """World-space points along the blade at the current frame (hilt -> tip)."""
    pb = arm.pose.bones['Sword_vfx']
    h = arm.matrix_world @ pb.head; t = arm.matrix_world @ pb.tail
    d = (t - h).normalized()
    return [h + d * (length * k / (n - 1)) for k in range(n)]


def hide_until(arm, frame):
    for o in [arm] + list(arm.children):
        for f, h in ((1, True), (frame, False)):
            o.hide_render = h; o.keyframe_insert('hide_render', frame=f)
        for fc in S.iter_fcurves(o.animation_data.action):
            for kp in fc.keyframe_points:
                kp.interpolation = 'CONSTANT'
