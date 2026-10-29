"""Viego 3D shots. Usage: python vshots.py <shot> <samples> <full|frames:a,b,c> <out> [res_pct]
Frame f of a shot maps to track time t0 + (f-1)/30."""
import sys, bpy, math
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
import stage as S
import viego as VG
import fx as FX

E = FX.EMERALD


def cam_rig(lens, fstop, focus_loc, focus_parent=None):
    focus = S.empty('focus', focus_loc)
    if focus_parent:
        focus.parent = focus_parent
    cam = S.camera('CAM', lens=lens, fstop=fstop, focus=focus)
    tgt = S.empty('tgt', (0, 0, 1.3)); S.track_to(cam, tgt)
    return cam, tgt


def path(cam, tgt, keys):
    for f, cl, tl, lens in keys:
        S.key(cam, f, location=cl, lens=lens); S.key(tgt, f, location=tl)


def env(N, seed=3, mist=0.32):
    FX.blocks(N, seed=seed)
    FX.mist(strength=mist)
    S.embers(170, center=(0, 0, 2.0), size=(9, 9, 4), frames=N, seed=seed + 10, color=E, strength=4, rise=0.8)


def v1_intro(N):
    # 0.000-4.969: empty dark -> Viego crashes down (Respawn) on kick 1.544 (f47), readies sword,
    # bass drops out 3.4 (f103): camera rushes in to the face; edit flashes into gameplay at the drop.
    arm = VG.viego([('respawn', 44, 0.85, 1, 0), ('idle', 44 + int(71 / 0.85), 1.0, 6, 6)])
    VG.hide_until(arm, 44)
    env(N, seed=3)
    FX.stage_lights(key=150, rim=1000)
    FX.sword_trail(arm, N, active=[(44, 60)], strength=5)
    FX.burst((0, 0, 0.15), 50, scale=1.3, seed=1, slow=1.6)
    cam, tgt = cam_rig(26, 4.0, (0, 0, 1.5))
    path(cam, tgt, [
        (1, (1.2, -10.5, 0.55), (0, 0, 1.6), 26),
        (44, (1.6, -8.0, 0.7), (0, 0, 1.4), 26),
        (86, (2.4, -5.4, 1.15), (0, 0, 1.3), 30),
        (104, (1.8, -4.4, 1.4), (0, 0, 1.45), 34),
        (N, (0.45, -1.55, 1.78), (0, 0, 1.75), 45),
    ])


def v2_slash_behind(N):
    # 8.390-10.340: Attack_Close, swing lands on 9.68 (f40); camera from behind sweeps up and over.
    arm = VG.viego([('attack', -20, 1.0, 1, 0), ('idle', 69, 1.0, 4, 6)])
    env(N, seed=5)
    FX.stage_lights()
    FX.sword_trail(arm, N, active=[(28, 50)])
    FX.burst((0, -1.4, 1.1), 41, scale=0.9, seed=2, slow=1.4)
    cam, tgt = cam_rig(28, 4.5, (0, 0, 1.4))
    path(cam, tgt, [
        (1, (0.9, 4.2, 1.1), (0, -0.5, 1.4), 28),
        (30, (2.6, 2.4, 2.6), (0, -0.4, 1.2), 26),
        (N, (1.6, -2.6, 5.4), (0, -0.2, 0.9), 24),
    ])


def v3_low_orbit(N):
    # 12.690-14.431: worm's-eye orbit; aura bursts on kicks 13.235 (f17) and 13.76 (f33).
    arm = VG.viego([('dance', 1, 0.8, 3, 0)])
    env(N, seed=7)
    FX.stage_lights(rim=1300)
    FX.burst((0, 0, 1.2), 17, scale=0.8, seed=3)
    FX.burst((0, 0, 0.2), 33, scale=1.2, seed=4)
    cam, tgt = cam_rig(22, 4.0, (0, 0, 1.5))
    pts = []
    for f, ang in ((1, -40), (25, 10), (N, 55)):
        a = math.radians(ang)
        pts.append((f, (2.3 * math.sin(a), -2.3 * math.cos(a), 0.18), (0, 0, 1.9), 22))
    path(cam, tgt, pts)


def v4_dive(N):
    # 17.705-19.365: overhead dive to over-the-shoulder; swing impact on 19.365 (f50).
    arm = VG.viego([('attack', -10, 1.0, 1, 0)])
    env(N, seed=9)
    FX.stage_lights()
    FX.sword_trail(arm, N, active=[(38, N)])
    FX.burst((0, -1.4, 1.0), 50, scale=1.1, seed=5)
    cam, tgt = cam_rig(24, 5.0, (0, 0, 1.2))
    path(cam, tgt, [
        (1, (0.2, -0.6, 10.0), (0, -0.2, 0.6), 24),
        (22, (0.8, 1.2, 6.0), (0, -0.3, 0.9), 24),
        (42, (1.3, 2.6, 1.9), (0, -0.6, 1.3), 26),
        (N, (1.1, 2.2, 1.7), (-0.2, -1.2, 1.2), 28),
    ])


def v5_track(N):
    # 24.050-25.530: lateral tracking shot, swing peak on 25.04 (f30).
    arm = VG.viego([('attack', -30, 1.0, 1, 0)])
    env(N, seed=11)
    FX.stage_lights()
    FX.sword_trail(arm, N, active=[(18, 40)])
    FX.burst((0, -1.4, 1.1), 31, scale=0.9, seed=6)
    cam, tgt = cam_rig(35, 4.0, (0, 0, 1.4))
    path(cam, tgt, [
        (1, (4.2, -3.2, 1.3), (0.4, 0, 1.3), 35),
        (N, (-4.0, -3.4, 1.5), (-0.4, 0, 1.3), 35),
    ])


def v6_crane(N):
    # 26.912-28.970: spiral crane up from low behind to overhead; mist pulses on kicks.
    arm = VG.viego([('idle', 1, 0.9, 6, 0)])
    env(N, seed=13, mist=0.4)
    FX.stage_lights(rim=1300)
    FX.burst((0, 0, 0.15), 3, scale=1.0, seed=7)
    FX.burst((0, 0, 1.6), 21, scale=0.7, seed=8)
    cam, tgt = cam_rig(24, 4.0, (0, 0, 1.4))
    pts = []
    for j, f in enumerate((1, 22, 42, N)):
        u = j / 3; a = math.radians(160 - 200 * u)
        rad = 1.8 + 3.0 * u
        pts.append((f, (rad * math.sin(a), -rad * math.cos(a), 0.6 + 6.5 * u ** 1.3), (0, 0, 1.5 - 0.7 * u), 24))
    path(cam, tgt, pts)


def v7_outro(N):
    # 28.970-34.343: tight on face/crown, slow push; from 30.6 (f50) long pull back to the wide for the title.
    arm = VG.viego([('respawn', -36, 0.6, 1, 0), ('idle', 82, 0.8, 8, 10)])
    env(N, seed=17, mist=0.36)
    FX.stage_lights(key=140, rim=1100)
    cam, tgt = cam_rig(70, 2.4, (0, 0, 1.75))
    path(cam, tgt, [
        (1, (0.55, -2.4, 1.75), (0, 0, 1.75), 70),
        (50, (0.45, -2.0, 1.78), (0, 0, 1.75), 70),
        (110, (2.2, -6.0, 2.6), (0, 0, 1.5), 40),
        (N, (3.0, -11.5, 4.2), (0, 0, 1.6), 30),
    ])


SHOTS = {  # name: (fn, frames @30)
    'V1_intro': (v1_intro, 150), 'V2_slash': (v2_slash_behind, 59), 'V3_orbit': (v3_low_orbit, 53),
    'V4_dive': (v4_dive, 51), 'V5_track': (v5_track, 45), 'V6_crane': (v6_crane, 63), 'V7_outro': (v7_outro, 162),
}

if __name__ == '__main__':
    args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    name, samples, mode, out = args[:4]
    pct = int(args[4]) if len(args) > 4 else 100
    fn, N = SHOTS[name]
    s = S.reset(N, samples=int(samples))
    s.render.resolution_percentage = pct
    fn(N)
    S.ease_all()
    print('pruned blocks', FX.prune_blocks(N))
    if mode == 'full':
        S.render(f'{out}/{name}')
    else:
        for f in [int(x) for x in mode.split(':')[1].split(',')]:
            s.frame_set(f); s.render.filepath = f'{out}/{name}_{f:03d}.png'; bpy.ops.render.render(write_still=True)
