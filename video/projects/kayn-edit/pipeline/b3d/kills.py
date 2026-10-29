"""Action kill shots. Usage: python kills.py <shot> <samples> <still|full|frames:1,14,41> <out> [res_pct]"""
import sys, bpy, math
sys.path.insert(0, '' + __import__('os').path.dirname(__import__('os').path.abspath(__file__)) + '')
import stage as S
import action as A

RED = (1.0, 0.06, 0.03)


def cam_path(cam, tgt, keys):
    """keys: [(frame, cam_loc, tgt_loc, lens)]"""
    for f, cl, tl, lens in keys:
        S.key(cam, f, location=cl, lens=lens)
        S.key(tgt, f, location=tl)


def k_pantheon(N):
    # beats: f15 dash (3.692), f43 impact (4.609), f70 land (5.515)
    P, K0 = (0, 0, 0), (0, -5.5, 0)
    pan = S.champion('pantheon', loc=P, rot_deg=A.face(P, K0))
    r0 = A.face(K0, P)
    k = A.Kayn(K0, r0)
    k.keys(1, loc=K0, rot=(0, 0, r0))
    k.keys(13, loc=(0, -5.6, -0.08), rot=(-6, 0, r0))
    k.keys(15, loc=(0, -5.4, -0.04), rot=(10, 0, r0))
    k.keys(24, loc=(0, -1.9, 0.05), rot=(20, 0, r0))
    k.keys(32, loc=(0, -1.45, 0.28), rot=(8, -10, r0 - 380))
    k.keys(43, loc=(0, -1.3, 0.05), rot=(4, 6, r0 - 700))
    k.keys(58, loc=(0, -1.25, 0.0), rot=(0, 0, r0 - 735))
    k.ghost_trail(15, 25, lag=2)
    k.aura_pulse([(1, 0), (13, 0), (19, 700), (43, 1200), (62, 200)])
    A.slash_trail(k.root, 24, 49)
    A.impact((0, -0.45, 1.15), 43, color=(1.0, 0.45, 0.1), seed=2, slow=1.8)
    A.knockdown(pan.parent, 43, 70, away_dir=(0, 1), dist=2.8, lift=0.7)
    S.haze('hz_back', (0.6, 6.5, 2.4), (10, 6), (1.0, 0.5, 0.15), strength=0.4)
    S.spot('top', (0.3, 1.0, 8), (0, 0, 1.0), (1.0, 0.75, 0.4), 2600, angle=34, blend=0.5)
    S.area('pkey', (2.0, -2.5, 2.8), (0, 0, 1.6), (1.0, 0.82, 0.6), 420, size=1.2)
    S.area('prim', (-2.4, 2.4, 2.6), (0, 0, 1.6), (1.0, 0.5, 0.15), 1000, size=0.8)
    S.area('krim', (-3.0, -6.0, 2.2), (0, -3.5, 1.2), RED, 900, size=1.0)
    S.area('krim2', (3.0, -3.0, 2.4), (0, -1.6, 1.2), RED, 600, size=1.0)
    S.area('landing', (-1.5, 4.5, 3.0), (0, 2.8, 0.3), (1.0, 0.6, 0.3), 700, size=2.0)
    S.embers(120, center=(0, -2.5, 1.6), size=(7, 9, 3), frames=N, seed=3, color=(1.0, 0.35, 0.05))
    focus = S.empty('focus', K0); focus.parent = k.root; focus.location = (0, 0, 1.3)
    cam = S.camera('CAM_K1', lens=28, fstop=5.6, focus=focus)
    tgt = S.empty('tgt', (0, 0, 1.2)); S.track_to(cam, tgt)
    cam_path(cam, tgt, [
        (1, (0.7, -8.6, 0.95), (0, 0, 1.25), 30),
        (14, (0.6, -8.0, 0.9), (0, -0.5, 1.2), 30),
        (26, (0.9, -4.6, 1.15), (0, -0.8, 1.1), 26),
        (38, (4.2, -2.6, 4.6), (0, -1.4, 0.8), 24),
        (43, (3.9, -1.0, 4.0), (0, -0.7, 1.0), 24),
        (62, (3.4, 1.6, 2.6), (0, 1.0, 0.9), 28),
        (N, (3.8, 1.2, 1.6), (0, 4.0, 0.35), 30),
    ])


def k_ahri(N):
    # beats: f15 dash (8.731), f29 spin peak, f43 impact (9.648), f56 land (10.106)
    Ah, K0 = (0, 0, 0), (-5.0, -1.6, 0)
    ahri = S.champion('ahri', loc=Ah, rot_deg=A.face(Ah, K0))
    r0 = A.face(K0, Ah)
    k = A.Kayn(K0, r0)
    hit = (-1.25, -0.42, 0)
    k.keys(1, loc=K0, rot=(0, 0, r0))
    k.keys(13, loc=(-5.1, -1.65, -0.07), rot=(-5, 0, r0))
    k.keys(15, loc=(-4.9, -1.58, -0.03), rot=(12, 0, r0))
    k.keys(25, loc=hit, rot=(20, 0, r0))
    k.keys(34, loc=(-1.1, -0.36, 0.32), rot=(6, 12, r0 - 360))
    k.keys(43, loc=(-1.0, -0.33, 0.04), rot=(2, -4, r0 - 720))
    k.keys(58, loc=(-0.95, -0.3, 0.0), rot=(0, 0, r0 - 750))
    k.ghost_trail(15, 26, lag=2)
    k.aura_pulse([(1, 0), (13, 0), (19, 700), (43, 1200), (60, 200)])
    A.slash_trail(k.root, 25, 49, color=(1.0, 0.06, 0.08))
    A.impact((-0.2, -0.05, 1.1), 43, color=(1.0, 0.3, 0.75), seed=4, slow=1.6)
    A.knockdown(ahri.parent, 43, 62, away_dir=(0.95, 0.3), dist=2.6, lift=0.8)
    S.haze('hz', (1.5, 5.5, 2.0), (10, 6), (0.75, 0.2, 0.9), strength=0.4)
    S.haze('hz2', (-3.5, 4.0, 1.4), (6, 4), (1.0, 0.25, 0.55), strength=0.22)
    S.area('akey', (1.6, -2.4, 2.4), (0, 0, 1.3), (1.0, 0.55, 0.8), 420, size=1.2)
    S.area('arim', (-1.0, 2.4, 2.6), (0, 0, 1.3), (0.6, 0.3, 1.0), 900, size=0.8)
    S.area('land', (4.0, -1.0, 3.0), (2.5, 0.8, 0.3), (1.0, 0.45, 0.8), 650, size=2.0)
    S.area('krim', (-5.0, 1.5, 2.2), (-3, -1, 1.2), RED, 900, size=1.0)
    S.area('krim2', (-2.0, -4.0, 2.2), (-1.2, -0.4, 1.2), RED, 500, size=1.0)
    S.embers(110, center=(-1.5, -0.8, 1.4), size=(8, 6, 3), frames=N, seed=11, color=(1.0, 0.3, 0.6))
    focus = S.empty('focus', (0, 0, 0)); focus.parent = k.root; focus.location = (0, 0, 1.3)
    cam = S.camera('CAM_K2', lens=32, fstop=5.6, focus=focus)
    tgt = S.empty('tgt', (0, 0, 1.2)); S.track_to(cam, tgt)
    cam_path(cam, tgt, [
        (1, (-4.2, -5.2, 1.1), (-4.6, -1.4, 1.2), 34),      # side profile on Kayn
        (14, (-4.0, -5.0, 1.0), (-4.4, -1.4, 1.15), 34),
        (25, (-1.6, -4.6, 1.2), (-1.4, -0.5, 1.1), 28),     # fast truck alongside the dash
        (38, (1.8, -3.6, 2.8), (-0.8, -0.2, 0.9), 26),      # whip around to the front, rising
        (43, (2.2, -3.0, 2.4), (-0.4, 0, 1.0), 26),
        (N, (3.2, -2.0, 0.45), (2.2, 0.8, 0.4), 30),        # drop to the floor on Ahri's landing
    ])


def k_volibear(N):
    # beats: f8/f22/f35 stalking kicks, f42 leap (dip), f56 DROP impact (14.443)
    V, K0 = (0, 0, 0), (0, -7.0, 0)
    vb = S.champion('volibear', loc=V, rot_deg=A.face(V, K0))
    r0 = A.face(K0, V)
    k = A.Kayn(K0, r0)
    k.keys(1, loc=K0, rot=(0, 0, r0))
    k.keys(35, loc=(0, -5.0, 0), rot=(0, 0, r0))
    k.keys(41, loc=(0, -4.8, -0.14), rot=(-8, 0, r0))         # crouch
    k.keys(48, loc=(0, -3.3, 3.2), rot=(25, 0, r0 - 200))     # airborne, spinning
    k.keys(52, loc=(0, -2.1, 2.2), rot=(35, 0, r0 - 420))
    k.keys(56, loc=(0, -1.35, 0.0), rot=(15, 0, r0 - 720))    # slam
    k.keys(70, loc=(0, -1.3, -0.04), rot=(0, 0, r0 - 735))
    k.ghost_trail(42, 50, lag=2)
    k.aura_pulse([(1, 120), (35, 200), (42, 1500), (56, 2500), (75, 300)])
    A.slash_trail(k.root, 46, 60, tilt=(25, 0), color=(1.0, 0.08, 0.05))
    A.impact((0, -0.5, 1.3), 56, color=(0.35, 0.6, 1.0), seed=6, slow=1.5, scale=1.5)
    A.impact((0, -1.2, 0.2), 56, color=(1.0, 0.1, 0.05), seed=7, slow=1.5, scale=1.2, sparks=40)
    A.knockdown(vb.parent, 56, 78, away_dir=(0, 1), dist=3.2, lift=0.6)
    S.haze('hz', (0, 6.0, 2.6), (12, 7), (0.25, 0.45, 1.0), strength=0.4)
    S.area('vkey', (-2.0, -2.4, 3.2), (0, 0, 1.8), (0.65, 0.8, 1.0), 420, size=1.0)
    S.area('vrim', (1.8, 2.4, 3.0), (0, 0, 1.8), (0.35, 0.6, 1.0), 1200, size=0.8)
    S.area('krim', (2.6, -7.0, 2.4), (0, -5, 1.2), RED, 900, size=1.0)
    S.area('land', (2.5, 5.0, 3.0), (0, 3.2, 0.3), (0.5, 0.7, 1.0), 700, size=2.0)
    import shots as SH
    b1 = S.point('bolt1', (-1.2, 1.0, 3.0), (0.6, 0.8, 1.0), 120, radius=0.05)
    b2 = S.point('bolt2', (1.4, 0.6, 2.6), (0.7, 0.85, 1.0), 120, radius=0.05)
    SH.flicker(b1, N, 120, [9, 23, 36, 50], 1)
    SH.flicker(b2, N, 120, [16, 30, 44, 56, 57], 2)
    S.embers(120, center=(0, -2.5, 1.8), size=(7, 9, 3.5), frames=N, seed=5, color=(0.4, 0.6, 1.0))
    focus = S.empty('focus', (0, 0, 0)); focus.parent = k.root; focus.location = (0, 0, 1.2)
    cam = S.camera('CAM_K3', lens=26, fstop=5.6, focus=focus)
    tgt = S.empty('tgt', (0, -3, 1.0)); S.track_to(cam, tgt)
    cam_path(cam, tgt, [
        (1, (-4.0, -6.5, 7.5), (0, -3.0, 0.6), 26),        # drone, high and circling
        (22, (-1.5, -8.5, 7.0), (0, -3.2, 0.6), 26),
        (38, (2.4, -8.2, 5.4), (0, -4.2, 0.9), 26),
        (43, (0.9, -7.6, 0.3), (0, -4.2, 1.6), 22),        # snap down to worm's-eye behind Kayn
        (52, (0.9, -5.6, 0.5), (0, -2.4, 2.4), 20),        # look up at the leap
        (56, (2.4, -3.0, 1.0), (0, -0.9, 1.2), 22),        # slam
        (N, (5.2, -1.4, 3.2), (0, 3.4, 0.5), 26),          # pull back and up onto the fallen bear
    ])


def k_seraphine(N):
    # beats: f15/f30 walk, f30 dash (19.081), f42 spin, f56 impact (19.958), f70.. break: ultra slow-mo fall
    Sp, K0 = (0, 0, 0), (1.6, -6.0, 0)
    se = S.champion('seraphine', loc=Sp, rot_deg=A.face(Sp, K0))
    r0 = A.face(K0, Sp)
    k = A.Kayn(K0, r0)
    hit = (0.35, -1.35, 0)
    k.keys(1, loc=K0, rot=(0, 0, r0))
    k.keys(28, loc=(1.25, -4.7, 0), rot=(0, 0, r0))
    k.keys(30, loc=(1.22, -4.6, -0.06), rot=(10, 0, r0))
    k.keys(39, loc=hit, rot=(20, 0, r0))
    k.keys(48, loc=(0.3, -1.2, 0.3), rot=(6, -10, r0 - 380))
    k.keys(56, loc=(0.28, -1.1, 0.05), rot=(3, 4, r0 - 715))
    k.keys(83, loc=(0.27, -1.08, 0.0), rot=(0, 0, r0 - 728))
    k.ghost_trail(30, 40, lag=2)
    k.aura_pulse([(1, 100), (28, 150), (34, 900), (56, 1300), (83, 400)])
    A.slash_trail(k.root, 39, 62, color=(1.0, 0.06, 0.1))
    A.impact((0.0, -0.4, 1.1), 56, color=(1.0, 0.35, 0.8), seed=8, slow=3.2)
    A.knockdown(se.parent, 56, 120, away_dir=(-0.2, 1), dist=2.4, lift=0.9)   # ends mid-air: slow-mo into the break
    S.haze('hz', (0, 5.5, 2.4), (10, 7), (1.0, 0.35, 0.7), strength=0.33)
    S.haze('hz2', (3.0, 4.0, 1.6), (6, 4), (0.2, 0.8, 1.0), strength=0.2)
    S.spot('stage', (0, -0.5, 7.5), (0, 0, 1.0), (1.0, 0.85, 0.95), 2200, angle=26, blend=0.35)
    S.area('skey', (-1.8, -2.4, 2.0), (0, 0, 1.1), (1.0, 0.6, 0.85), 320, size=1.2)
    S.area('srim', (1.6, 2.2, 2.2), (0, 0, 1.1), (0.3, 0.85, 1.0), 800, size=0.8)
    S.area('krim', (3.8, -5.0, 2.2), (1.2, -4.0, 1.2), RED, 800, size=1.0)
    S.area('krim2', (-1.5, -3.0, 2.2), (0.4, -1.3, 1.2), RED, 450, size=1.0)
    S.embers(90, center=(0.5, -2.0, 1.4), size=(6, 8, 3), frames=N, seed=13, color=(1.0, 0.45, 0.75), rise=0.3)
    focus = S.empty('focus', (0, 0, 0)); focus.parent = k.root; focus.location = (0, 0, 1.3)
    cam = S.camera('CAM_K4', lens=32, fstop=4.0, focus=focus)
    tgt = S.empty('tgt', (0, 0, 1.1)); S.track_to(cam, tgt)
    cam_path(cam, tgt, [
        (1, (2.2, -8.8, 1.35), (0.3, 0, 1.1), 34),          # behind Kayn, Seraphine singing ahead
        (28, (1.9, -7.6, 1.3), (0.3, 0, 1.1), 36),
        (39, (2.0, -4.6, 1.5), (0.3, -0.6, 1.1), 28),       # chase the dash
        (50, (-2.8, -3.6, 2.9), (0.2, -0.7, 1.0), 26),      # whip to the other side, up
        (56, (-3.0, -2.8, 2.5), (0.0, -0.3, 1.1), 28),
        (N, (-3.6, -0.4, 1.5), (-0.25, 1.4, 1.0), 34),     # slow orbit following the slow-mo fall
    ])


def k_finale(N):
    # Kayn among the fallen; spiral crane up. Light pulses on kicks f18, f32, f49.
    k = A.Kayn((0, 0, 0), 0, ghosts=0)
    for i, (n, ang) in enumerate((('pantheon', -150), ('ahri', -60), ('volibear', 40), ('seraphine', 130))):
        a = math.radians(ang); p = (3.0 * math.sin(a), 3.0 * math.cos(a), 0)
        c = S.champion(n, loc=p, rot_deg=ang + 180, anim_offset=7 * i)
        c.parent.rotation_euler[0] = math.radians(-86)
        A.key_pose(c, 1, **A.FALLEN)
        S.area(f'cold{i}', (p[0] * 1.3, p[1] * 1.3, 2.4), p, (0.35, 0.5, 1.0), 90, size=0.8)
    k.aura_pulse([(1, 300), (18, 1800), (24, 400), (32, 1800), (38, 400), (49, 2200), (N, 600)])
    S.haze('hz', (0, 6.0, 2.5), (12, 7), (0.55, 0.05, 0.05), strength=0.3)
    S.area('kkey', (-1.4, -2.6, 2.2), (0, 0, 1.5), (1.0, 0.75, 0.65), 180, size=1.2)
    S.area('krimL', (-1.8, 1.4, 2.4), (0, 0, 1.4), RED, 900, size=0.7)
    S.area('krimR', (1.8, 1.4, 2.4), (0, 0, 1.4), RED, 900, size=0.7)
    S.embers(170, center=(0, 0, 1.6), size=(8, 8, 3.5), frames=N, seed=17)
    focus = S.empty('focus', (0, 0, 1.4))
    cam = S.camera('CAM_F', lens=24, fstop=4.0, focus=focus)
    tgt = S.empty('tgt', (0, 0, 1.3)); S.track_to(cam, tgt)
    pts = []
    for j, f in enumerate((1, 18, 32, 49, N)):
        u = j / 4
        a = math.radians(-20 + 160 * u)
        rad = 2.6 + 3.2 * u
        pts.append((f, (rad * math.sin(a), -rad * math.cos(a), 0.5 + 6.5 * u ** 1.4), (0, 0, 1.3 - 0.6 * u), 24))
    cam_path(cam, tgt, pts)


SHOTS = {'K1_pantheon': (k_pantheon, 83), 'K2_ahri': (k_ahri, 63), 'K3_volibear': (k_volibear, 83),
         'K4_seraphine': (k_seraphine, 83), 'K5_finale': (k_finale, 56)}

if __name__ == '__main__':
    args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    name, samples, mode, out = args[:4]
    pct = int(args[4]) if len(args) > 4 else 100
    fn, N = SHOTS[name]
    s = S.reset(N, samples=int(samples))
    s.render.resolution_percentage = pct
    fn(N)
    S.ease_all()
    if mode == 'full':
        S.render(f'{out}/{name}')
    else:
        frames = [int(x) for x in mode.split(':')[1].split(',')]
        for f in frames:
            s.frame_set(f); s.render.filepath = f'{out}/{name}_{f:03d}.png'; bpy.ops.render.render(write_still=True)
