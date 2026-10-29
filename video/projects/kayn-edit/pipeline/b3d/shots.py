"""All 3D shots of the Kayn edit. Usage: python shots.py <shot> <samples> <still|full> <out>"""
import sys, bpy, math, random
sys.path.insert(0, '' + __import__('os').path.dirname(__import__('os').path.abspath(__file__)) + '')
import stage as S

RED = (1.0, 0.06, 0.03)


def face(src, dst):
    return math.degrees(math.atan2(dst[0] - src[0], -(dst[1] - src[1])))


def flicker(light, frames, base, peaks, seed):
    random.seed(seed)
    for f in range(1, frames + 1):
        e = base
        if any(abs(f - p) <= 1 for p in peaks):
            e = base * random.uniform(6, 14)
        S.key(light, f, energy=e)
    for fc in S.iter_fcurves(light.data.animation_data.action):
        for kp in fc.keyframe_points:
            kp.interpolation = 'CONSTANT'


def intro(N):
    S.champion('kayn', loc=(0, 0, 0), rot_deg=0)
    hz = S.haze('hz', (0, 4.5, 2.4), (8, 6), (0.9, 0.08, 0.04), strength=0.3)
    em = hz.data.materials[0].node_tree.nodes['Emission'].inputs['Strength']
    for f, v in ((1, 0.0), (10, 0.0), (45, 0.3)):
        em.default_value = v; em.keyframe_insert('default_value', frame=f)
    S.haze('hz_floor', (0, 2.0, 0.3), (8, 2.0), (0.7, 0.05, 0.03), strength=0.08)
    r1 = S.area('rimL', (-2.2, 2.4, 2.6), (0, 0, 1.4), RED, 0, size=0.7)
    r2 = S.area('rimR', (2.2, 2.2, 2.4), (0, 0, 1.4), RED, 0, size=0.7)
    key = S.area('key', (-1.6, -2.6, 1.2), (0, 0, 1.55), (0.55, 0.65, 1.0), 0, size=1.5)
    for l, e, a, b in ((r1, 900, 4, 34), (r2, 700, 14, 44), (key, 70, 48, 80)):
        S.key(l, 1, energy=0); S.key(l, a, energy=0); S.key(l, b, energy=e)
    S.embers(160, center=(0, 0.5, 1.4), size=(5, 5, 3), frames=N, seed=7)
    focus = S.empty('focus', (0, -0.1, 1.62))
    cam = S.camera('CAM_Intro', lens=35, fstop=2.0, focus=focus)
    tgt = S.empty('tgt', (0, 0, 1.3)); S.track_to(cam, tgt)
    S.key(cam, 1, location=(0.7, -5.2, 0.45), lens=35)
    S.key(cam, N, location=(0.28, -2.15, 1.58), lens=55)
    S.key(tgt, 1, location=(0, 0, 1.15)); S.key(tgt, N, location=(0.05, 0, 1.62))


def pantheon(N):
    P, K = (0, 0, 0), (-0.75, -2.7, 0)
    S.champion('pantheon', loc=P, rot_deg=face(P, K))
    S.champion('kayn', loc=K, rot_deg=face(K, P), anim_offset=10)
    S.haze('hz_back', (0.6, 3.5, 2.2), (8, 5.5), (1.0, 0.5, 0.15), strength=0.45)
    S.haze('hz_low', (0, 1.5, 0.4), (12, 3), (0.9, 0.4, 0.12), strength=0.22)
    S.spot('top', (0.3, 1.0, 7), (0, 0, 1.2), (1.0, 0.75, 0.4), 2500, angle=30, blend=0.5)
    S.area('pkey', (1.8, -2.4, 2.6), (0, 0, 1.7), (1.0, 0.82, 0.6), 420, size=1.2)
    S.area('prim', (-2.4, 2.2, 2.6), (0, 0, 1.6), (1.0, 0.5, 0.15), 1000, size=0.8)
    S.area('krim', (-3.2, -1.0, 2.2), K[:2] + (1.3,), RED, 450, size=0.8)
    S.area('krim2', (0.4, -1.0, 2.2), K[:2] + (1.3,), RED, 300, size=0.8)
    S.embers(120, center=(-0.4, -1.2, 1.6), size=(6, 6, 3), frames=N, seed=3, color=(1.0, 0.35, 0.05))
    focus = S.empty('focus', (K[0], K[1], 1.6))
    cam = S.camera('CAM_Pantheon_OTS', lens=32, fstop=2.2, focus=focus)
    tgt = S.empty('tgt', (0.3, 0, 1.6)); S.track_to(cam, tgt)
    S.key(cam, 1, location=(-1.3, -5.6, 1.05), lens=32)
    S.key(cam, N, location=(-0.85, -4.5, 1.35), lens=40)
    S.key(tgt, 1, location=(-0.25, -0.9, 1.4)); S.key(tgt, N, location=(-0.1, -0.3, 1.8))
    S.key(focus, 1, location=(K[0], K[1], 1.6)); S.key(focus, int(N * 0.3), location=(K[0], K[1], 1.6)); S.key(focus, int(N * 0.65), location=(0, 0, 1.9))


def ahri(N):
    A, K = (0, 0, 0), (-0.7, -2.4, 0)
    S.champion('ahri', loc=A, rot_deg=face(A, K))
    S.champion('kayn', loc=K, rot_deg=face(K, A), anim_offset=5)
    S.haze('hz', (0.5, 3.8, 2.0), (8, 5.5), (0.75, 0.2, 0.9), strength=0.42)
    S.haze('hz2', (-2.5, 3.0, 1.2), (5, 4), (1.0, 0.25, 0.55), strength=0.22)
    S.area('akey', (1.6, -2.0, 2.2), (0, 0, 1.3), (1.0, 0.55, 0.8), 380, size=1.2)
    S.area('arim', (-1.8, 2.0, 2.4), (0, 0, 1.3), (0.6, 0.3, 1.0), 900, size=0.8)
    S.area('arim2', (2.0, 1.8, 1.8), (0, 0, 1.0), (1.0, 0.3, 0.7), 500, size=0.8)
    S.area('krim', (-2.6, -0.8, 2.0), K[:2] + (1.3,), RED, 450, size=0.8)
    S.embers(110, center=(0, -1.0, 1.4), size=(6, 6, 3), frames=N, seed=11, color=(1.0, 0.3, 0.6))
    focus = S.empty('focus', (0, 0, 1.4))
    cam = S.camera('CAM_Ahri_Truck', lens=45, fstop=2.0, focus=focus)
    tgt = S.empty('tgt', (0, 0, 1.25)); S.track_to(cam, tgt)
    S.key(cam, 1, location=(-2.6, -4.6, 1.25)); S.key(cam, N, location=(0.9, -4.6, 1.4))
    S.key(tgt, 1, location=(-0.3, -0.4, 1.2)); S.key(tgt, N, location=(0.1, 0, 1.35))


def volibear(N):
    V, K = (0, 0, 0), (1.0, -2.3, 0)
    S.champion('volibear', loc=V, rot_deg=face(V, K))
    S.champion('kayn', loc=K, rot_deg=face(K, V), anim_offset=20)
    S.haze('hz', (0, 4.0, 2.6), (8, 6), (0.25, 0.45, 1.0), strength=0.4)
    S.area('vkey', (-1.8, -2.0, 3.0), (0, 0, 1.8), (0.65, 0.8, 1.0), 380, size=1.0)
    S.area('vrim', (1.8, 2.2, 3.0), (0, 0, 1.8), (0.35, 0.6, 1.0), 1200, size=0.8)
    S.area('krim', (2.8, -0.6, 2.0), K[:2] + (1.3,), RED, 450, size=0.8)
    bolt1 = S.point('bolt1', (-1.2, 1.0, 3.0), (0.6, 0.8, 1.0), 120, radius=0.05)
    bolt2 = S.point('bolt2', (1.4, 0.6, 2.6), (0.7, 0.85, 1.0), 120, radius=0.05)
    flicker(bolt1, N, 120, [9, 23, 31, 44], 1)
    flicker(bolt2, N, 120, [15, 27, 38, N - 1, N], 2)
    S.embers(120, center=(0, -1.0, 1.6), size=(6, 6, 3), frames=N, seed=5, color=(0.4, 0.6, 1.0))
    focus = S.empty('focus', (0, 0, 1.9))
    cam = S.camera('CAM_Volibear_Low', lens=24, fstop=2.4, focus=focus)
    tgt = S.empty('tgt', (0, 0, 1.9)); S.track_to(cam, tgt)
    S.key(cam, 1, location=(-0.9, -4.4, 0.4), lens=24)
    S.key(cam, N, location=(-0.4, -2.3, 0.75), lens=30)
    S.key(tgt, 1, location=(0, 0, 1.7)); S.key(tgt, N, location=(0, 0, 2.05))


def seraphine(N):
    Sp, K0, K1 = (0, 0, 0), (1.3, -1.4, 0), (0.6, -1.15, 0)
    S.champion('seraphine', loc=Sp, rot_deg=-15)
    k = S.champion('kayn', loc=K0, rot_deg=face(K0, Sp), anim_offset=3)
    root = k.parent
    S.key(root, 1, location=K0); S.key(root, N, location=K1)
    S.haze('hz', (0, 3.8, 2.4), (8, 6), (1.0, 0.35, 0.7), strength=0.33)
    S.haze('hz2', (2.6, 3.0, 1.6), (6, 4), (0.2, 0.8, 1.0), strength=0.2)
    S.spot('stage', (0, -0.5, 7.5), (0, 0, 1.0), (1.0, 0.85, 0.95), 2200, angle=26, blend=0.35)
    S.area('skey', (-1.8, -2.4, 2.0), (0, 0, 1.1), (1.0, 0.6, 0.85), 320, size=1.2)
    S.area('srim', (1.6, 2.2, 2.2), (0, 0, 1.1), (0.3, 0.85, 1.0), 800, size=0.8)
    S.area('krim', (3.4, 0.2, 2.0), (1.8, -1.8, 1.3), RED, 380, size=0.8)
    S.embers(90, center=(0, -0.8, 1.4), size=(5, 5, 3), frames=N, seed=13, color=(1.0, 0.45, 0.75), rise=0.3)
    focus = S.empty('focus', (0, 0, 1.15))
    cam = S.camera('CAM_Seraphine_Orbit', lens=50, fstop=1.8, focus=focus)
    tgt = S.empty('tgt', (0, 0, 1.05)); S.track_to(cam, tgt)
    for f, ang in ((1, -32), (N, 8)):
        a = math.radians(ang)
        S.key(cam, f, location=(3.4 * math.sin(a), -3.4 * math.cos(a), 1.25))


def finale(N):
    K = (0, -1.2, 0)
    S.champion('kayn', loc=K, rot_deg=0)
    for i, (n, ang) in enumerate((('pantheon', -32), ('ahri', -11), ('volibear', 11), ('seraphine', 32))):
        a = math.radians(ang); p = (4.2 * math.sin(a), 4.2 * math.cos(a) - 0.6, 0)
        S.champion(n, loc=p, rot_deg=face(p, K), anim_offset=7 * i)
        S.area(f'cold{i}', (p[0] * 1.35, p[1] + 1.6, 2.6), (p[0], p[1], 1.4), (0.35, 0.5, 1.0), 70, size=0.8)
    S.haze('hz', (0, 5.2, 2.5), (11, 6), (0.55, 0.05, 0.05), strength=0.3)
    S.area('kkey', (-1.4, -3.0, 2.2), (0, -1.2, 1.5), (1.0, 0.75, 0.65), 160, size=1.2)
    S.area('krimL', (-1.8, 0.8, 2.4), (0, -1.2, 1.4), RED, 900, size=0.7)
    S.area('krimR', (1.8, 0.8, 2.4), (0, -1.2, 1.4), RED, 900, size=0.7)
    S.embers(170, center=(0, 0.5, 1.6), size=(7, 7, 3.5), frames=N, seed=17)
    focus = S.empty('focus', (0, -1.2, 1.5))
    cam = S.camera('CAM_Finale_Crane', lens=28, fstop=2.8, focus=focus)
    tgt = S.empty('tgt', (0, 0, 1.3)); S.track_to(cam, tgt)
    S.key(cam, 1, location=(0.5, -4.0, 0.55)); S.key(cam, N, location=(0, -7.6, 2.9))
    S.key(tgt, 1, location=(0, -1.2, 1.45)); S.key(tgt, N, location=(0, 0.6, 1.2))


def hero(N):
    S.champion('kayn', loc=(0, 0, 0), rot_deg=-12)
    S.haze('hz', (0.8, 3.5, 2.2), (5, 4), (0.85, 0.06, 0.04), strength=0.3)
    S.area('rimL', (-1.6, 1.6, 2.2), (0, 0, 1.6), RED, 700, size=0.5)
    S.area('rimR', (1.6, 1.4, 2.0), (0, 0, 1.6), RED, 600, size=0.5)
    key = S.area('key', (-1.2, -1.8, 1.9), (0, 0, 1.65), (0.6, 0.7, 1.0), 45, size=1.0)
    S.key(key, 1, energy=45); S.key(key, int(N * 0.6), energy=45); S.key(key, N, energy=0)
    S.embers(80, center=(0, -0.3, 1.6), size=(3, 3, 2), frames=N, seed=21)
    focus = S.empty('focus', (0.0, -0.12, 1.66))
    cam = S.camera('CAM_Hero_ECU', lens=85, fstop=1.6, focus=focus)
    tgt = S.empty('tgt', (0.0, 0, 1.64)); S.track_to(cam, tgt)
    S.key(cam, 1, location=(0.45, -2.2, 1.58)); S.key(cam, N, location=(0.25, -1.75, 1.66))


SHOTS = {  # name: (fn, frames @30fps)
    'S1_intro': (intro, 100), 'S2_pantheon': (pantheon, 58), 'S3_ahri': (ahri, 50), 'S4_volibear': (volibear, 57),
    'S5_seraphine': (seraphine, 85), 'S6_finale': (finale, 57), 'S7_hero': (hero, 52),
}

if __name__ == '__main__':
    name, samples, mode, out = sys.argv[-4:]
    fn, N = SHOTS[name]
    s = S.reset(N, samples=int(samples))
    fn(N)
    S.ease_all()
    if mode == 'still':
        for f in (1, N // 2, N):
            s.frame_set(f); s.render.filepath = f'{out}/{name}_{f:03d}.png'; bpy.ops.render.render(write_still=True)
    else:
        S.render(f'{out}/{name}')
