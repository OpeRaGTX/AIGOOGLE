"""bmcp.py — tiny client for the MCP-for-Blender addon socket (localhost:9876), for scripts and for sessions where the
`blender` MCP tools aren't loaded yet. Blender must be open with the addon's server running (addon: video/tools/addons/mcp_for_blender.py).

  python bmcp.py info                       scene summary
  python bmcp.py exec <file.py|-> [k=v ...]  run code in Blender (stdin with -); k=v become globals ARGS['k']
  python bmcp.py shot <out.png> [size]       viewport screenshot (offscreen render of the 3D view)
"""
import json, math, os, socket, sys

PORT = int(os.environ.get('BLENDER_MCP_PORT', 9876))


def call(kind, params=None, timeout=600):
    s = socket.create_connection(('localhost', PORT), timeout=timeout)
    s.sendall(json.dumps({'type': kind, 'params': params or {}}).encode())
    buf = b''
    while True:
        chunk = s.recv(1 << 20)
        if not chunk:
            break
        buf += chunk
        try:
            return json.loads(buf.decode('utf-8'))
        except ValueError:
            continue
    return json.loads(buf.decode('utf-8'))


def run(code, **args):
    r = call('execute_code', {'code': f'ARGS = {args!r}\n' + code})
    res = r.get('result', {})
    if r.get('status') != 'success' or isinstance(res, dict) and res.get('error'):
        raise RuntimeError(json.dumps(r, ensure_ascii=False)[:2000])
    return res.get('result', '') if isinstance(res, dict) else res


def shot(path, size=800):
    r = call('get_viewport_screenshot', {'max_size': size, 'filepath': os.path.abspath(path)})
    if not r.get('result', {}).get('success'):
        raise RuntimeError(json.dumps(r, ensure_ascii=False)[:2000])
    return path


VIEWS = {  # (pitch from vertical, yaw) of the viewport, character facing -Y
    'front': (1.45, 0.0), 'side': (1.45, math.pi / 2), 'threequarter': (1.38, -0.75), 'back34': (1.38, 2.6),
    'low': (1.62, -0.6), 'top': (0.55, -0.6)}


def frames(out_dir, frame_list, view='threequarter', dist=4.6, target=(0, -0.2, 0.95), size=640, shading='SOLID'):
    """Screenshot the viewport at each frame from a fixed review camera (no overlays) -> out_dir/f####_view.png."""
    os.makedirs(out_dir, exist_ok=True)
    pitch, yaw = VIEWS[view]
    run(f'''
import bpy
from mathutils import Euler, Vector
for w in bpy.context.window_manager.windows:
    for a in w.screen.areas:
        if a.type == 'VIEW_3D':
            sp = a.spaces[0]; sp.shading.type = {shading!r}; sp.overlay.show_overlays = False
            r = sp.region_3d; r.view_perspective = 'PERSP'
            r.view_rotation = Euler(({pitch}, 0, {yaw})).to_quaternion(); r.view_location = Vector({tuple(target)!r}); r.view_distance = {dist}
''')
    out = []
    for f in frame_list:
        run(f'import bpy; bpy.context.scene.frame_set({int(f)})')
        out.append(shot(os.path.join(out_dir, f'f{int(f):04d}_{view}.png'), size))
    return out


if __name__ == '__main__':
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    cmd, rest = sys.argv[1], sys.argv[2:]
    if cmd == 'info':
        print(json.dumps(call('get_scene_info'), ensure_ascii=False, indent=1))
    elif cmd == 'exec':
        src = sys.stdin.read() if rest[0] == '-' else open(rest[0], encoding='utf-8').read()
        print(run(src, **dict(a.split('=', 1) for a in rest[1:])))
    elif cmd == 'shot':
        print(shot(rest[0], int(rest[1]) if len(rest) > 1 else 800))
    elif cmd == 'frames':  # frames <out_dir> <1,24,33> [view] [dist]
        print('\n'.join(frames(rest[0], [int(x) for x in rest[1].split(',')], *(rest[2:3] or ['threequarter']),
                               **({'dist': float(rest[3])} if len(rest) > 3 else {}))))
