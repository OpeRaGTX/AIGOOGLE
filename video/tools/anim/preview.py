"""preview.py — quick lit playblast of an animated .blend (Eevee, 9:16) for reviewing motion before the real render.

  blender -b <anim.blend> -P preview.py -- <out.mp4> [target=x,y,z] [dist=4.8] [yaw=-35] [height=1.3] [res=720]
Adds a camera aimed at `target` (3/4 front-right by default for a character facing -Y), a floor, key/rim/fill
lights; renders PNGs next to the mp4 and joins them at 60 fps (frame doubling, like the studio's 3D shots).
"""
import math, os, subprocess, sys
import bpy
from mathutils import Vector

a = sys.argv[sys.argv.index('--') + 1:]
out = os.path.abspath(a[0])
opt = dict(x.split('=', 1) for x in a[1:])
target = Vector([float(v) for v in opt.get('target', '0,-0.25,1.05').split(',')])
dist, yaw, h = float(opt.get('dist', 4.8)), math.radians(float(opt.get('yaw', -35))), float(opt.get('height', 1.3))
res = int(opt.get('res', 720))

s = bpy.context.scene
s.render.engine = 'BLENDER_EEVEE' if 'BLENDER_EEVEE' in {e.identifier for e in bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items} else 'BLENDER_EEVEE_NEXT'
s.render.resolution_x, s.render.resolution_y = res, int(res * 16 / 9)
s.render.image_settings.file_format = 'PNG'
s.view_settings.view_transform = 'AgX'
for o in bpy.data.objects:
    if o.name.startswith('WGT_'):
        o.hide_render = True

cam_d = bpy.data.cameras.new('PreviewCam'); cam_d.lens = 32; cam_d.sensor_fit = 'VERTICAL'
cam = bpy.data.objects.new('PreviewCam', cam_d); s.collection.objects.link(cam); s.camera = cam
cam.location = target + Vector((math.sin(yaw) * dist, -math.cos(yaw) * dist, h - target.z))
cam.rotation_euler = (target - cam.location).to_track_quat('-Z', 'Y').to_euler()

bpy.ops.mesh.primitive_plane_add(size=40)
floor = bpy.context.object; m = bpy.data.materials.new('PreviewFloor'); m.use_nodes = True
m.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = (0.018, 0.018, 0.022, 1)
m.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = 0.85
floor.data.materials.append(m)
w = s.world or bpy.data.worlds.new('W'); s.world = w; w.use_nodes = True
w.node_tree.nodes['Background'].inputs[0].default_value = (0.02, 0.022, 0.03, 1)


def light(name, kind, loc, energy, color=(1, 1, 1), size=1.0):
    d = bpy.data.lights.new(name, kind); d.energy = energy; d.color = color
    if kind == 'AREA':
        d.size = size
    o = bpy.data.objects.new(name, d); s.collection.objects.link(o); o.location = loc
    o.rotation_euler = (target - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()


light('Key', 'AREA', (-3.0, -3.0, 3.5), 900, (1.0, 0.92, 0.85), 2.5)
light('Rim', 'AREA', (2.5, 3.0, 3.0), 700, (1.0, 0.25, 0.2), 1.5)
light('Fill', 'AREA', (3.5, -2.5, 1.5), 250, (0.6, 0.7, 1.0), 3.0)

frames_dir = os.path.splitext(out)[0] + '_frames'
os.makedirs(frames_dir, exist_ok=True)
s.render.filepath = os.path.join(frames_dir, '')
bpy.ops.render.render(animation=True)
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-framerate', str(s.render.fps), '-i', os.path.join(frames_dir, '%04d.png'),
                '-vf', 'fps=60', '-c:v', 'libx264', '-crf', '18', '-pix_fmt', 'yuv420p', out], check=True)
print('preview:', out)
