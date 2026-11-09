import React from 'react';
import {AbsoluteFill, Easing, Img, Sequence, continueRender, delayRender, interpolate, staticFile, useCurrentFrame} from 'remotion';
import tl from './timeline.json';
import {Captions} from './Captions';
import {Cam, Converge, FPS, Flash, Fog, Glow, IH, IW, Key, Rising, Sparks, camAt, place, shakeAt, toScreen} from './fx';

// «Битва по лору: Каин против Виего» — 15 stills, voice-driven EDL from timeline.json (pipeline/vo.py).
export {FPS};
export const DURATION = tl.frames;

const fontHandle = delayRender('fonts');
Promise.all(
	(['cyrillic', 'latin'] as const).flatMap((sub) =>
		[800, 900].map((w) => new FontFace('Montserrat', `url(${staticFile(`fonts/montserrat-${sub}-${w}-normal.woff2`)}) format('woff2')`, {
			weight: String(w),
			unicodeRange: sub === 'cyrillic' ? 'U+0301, U+0400-045F, U+0490-0491, U+04B0-04B1, U+2116' : 'U+0000-00FF, U+2000-206F, U+20AC, U+2122',
		}).load().then((f) => document.fonts.add(f))),
	),
).then(() => continueRender(fontHandle));

type Ctx = {t: number; f: number; D: number; w: (re: RegExp, n?: number) => number; we: (re: RegExp, n?: number) => number};
type Def = {
	cam: (c: Ctx) => Key[];
	shake?: (c: Ctx) => [number, number][];
	blur?: (c: Ctx) => number;
	fx?: (c: Ctx, cam: Cam) => React.ReactNode;
};
const OUT = Easing.out(Easing.cubic);
const LIN = (x: number) => x;
const C = (z: number, cx: number, cy: number): Cam => ({z, cx, cy});
const EMBER = ['#ff3b1f', '#ff6a2a', '#ffb347', '#ff2a2a'];
const JADE = ['#2EF2A0', '#7dffc9', '#19c47f'];
const fr = (s: number) => Math.round(s * FPS);

const DEFS: Def[] = [
	{ // 1 hook: fast push-in, flash on «убить»
		cam: ({D}) => [[0, C(1, 0.5, 0.47)], [0.6, C(1.12, 0.5, 0.45), OUT], [D, C(1.18, 0.5, 0.44), LIN]],
		fx: ({f, w}) => <Flash frame={f} at={[fr(w(/^убить/))]} />,
	},
	{ // 2 Rhaast: slow push, embers, red pulse
		cam: ({D}) => [[0, C(1, 0.45, 0.3)], [D, C(1.1, 0.45, 0.24), LIN]],
		fx: ({t}) => (
			<>
				<AbsoluteFill style={{background: '#ff3a24', mixBlendMode: 'screen', opacity: 0.05 + 0.05 * Math.sin(t * Math.PI * 2 * 1.1)}} />
				<Rising t={t} seed="e2" n={28} colors={EMBER} minY={700} />
			</>
		),
	},
	{ // 3 still alive: almost static, slight pull-back
		cam: ({D}) => [[0, C(1.06, 0.55, 0.3)], [D, C(1, 0.55, 0.3), LIN]],
	},
	{ // 4 Viego: tilt bottom→top + pull-back, green fog layers
		cam: ({D}) => [[0, C(1.16, 0.5, 0.78)], [D, C(1.03, 0.47, 0.3)]],
		fx: ({t}) => (
			<>
				<Fog t={t} seed="f4a" alpha={0.14} band={[0.45, 1.0]} />
				<Fog t={t} seed="f4b" n={5} alpha={0.1} band={[0.75, 1.1]} />
			</>
		),
	},
	{ // 5 Black Mist: drift right, fog creeps in
		cam: ({D}) => [[0, C(1.12, 0.36, 0.5)], [D, C(1.12, 0.64, 0.5), LIN]],
		fx: ({t, D}) => <Fog t={t} seed="f5" n={10} alpha={0.2} creep={Math.min(1, t / D)} band={[0.25, 1.05]} />,
	},
	{ // 6 Kayn is faster: smear entry, shake on «быстрее»
		cam: ({D}) => [[0, C(1.14, 0.32, 0.5)], [4 / FPS, C(1.08, 0.42, 0.5), OUT], [D, C(1.13, 0.42, 0.47), LIN]],
		blur: ({f}) => (f < 4 ? (4 - f) * 4 : 0),
		shake: ({w}) => [[fr(w(/^быстрее/)), 6]],
		fx: ({t}) => <Rising t={t} seed="e6" n={22} colors={EMBER} minY={800} />,
	},
	{ // 7 from the walls: fast push to the impact point, sparks on each place
		cam: ({D}) => [[0, C(1, 0.5, 0.5)], [0.8, C(1.3, 0.57, 0.5), OUT], [D, C(1.36, 0.57, 0.5), LIN]],
		fx: ({t, w}, cam) => {
			const p = toScreen(cam, 0.57, 0.5);
			return (
				<>
					<Rising t={t} seed="e7" n={18} colors={EMBER} minY={900} />
					<Sparks t={t} seed="s7" at={[w(/^стен/), w(/^пола/), w(/^потолка/)]} x={p.x} y={p.y} />
				</>
			);
		},
	},
	{ // 8 cut in half: impact frames + shake + punch-ins
		cam: ({D, w}) => {
			const h = [w(/^пополам/), w(/^снова/i, 0), w(/^снова/i, 1)];
			return [[0, C(1.04, 0.55, 0.5)], ...h.flatMap((s, i): Key[] => [[s, C(1.04 + 0.05 * i, 0.55, 0.5)], [s + 0.12, C(1.09 + 0.05 * i, 0.55, 0.5), OUT]]), [D, C(1.2, 0.55, 0.5), LIN]];
		},
		shake: ({w}) => [[fr(w(/^пополам/)), 10], [fr(w(/^снова/i, 0)), 10], [fr(w(/^снова/i, 1)), 12]],
		fx: ({t, f, w}) => (
			<>
				<Rising t={t} seed="e8" n={18} colors={EMBER} minY={900} />
				<Flash frame={f} at={[fr(w(/^пополам/)), fr(w(/^снова/i, 0)), fr(w(/^снова/i, 1))]} invertFirst />
			</>
		),
	},
	{ // 9 the mist reassembles the king: particles pulled in, slight pull-back
		cam: ({D}) => [[0, C(1.12, 0.5, 0.38)], [D, C(1.02, 0.5, 0.38), LIN]],
		fx: ({t, D}, cam) => {
			const p = toScreen(cam, 0.5, 0.33);
			return (
				<>
					<Fog t={t} seed="f9" n={5} alpha={0.1} />
					<Converge t={t} seed="c9" n={70} tx={p.x} ty={p.y} dur={D} />
				</>
			);
		},
	},
	{ // 10 only light works: vignette closes in on Kayn, darkening to the end of the phrase
		cam: ({D}) => [[0, C(1, 0.3, 0.6)], [D, C(1.08, 0.3, 0.6), LIN]],
		fx: ({t, we}, cam) => {
			const end = we(/^нет/);
			const p = toScreen(cam, 0.28, 0.6);
			const r = interpolate(t, [0, end], [950, 380], {extrapolateRight: 'clamp', easing: Easing.inOut(Easing.quad)});
			const dark = interpolate(t, [end * 0.5, end], [0, 0.22], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
			return (
				<>
					<AbsoluteFill style={{background: `radial-gradient(circle at ${p.x}px ${p.y}px, rgba(0,0,0,0) ${r}px, rgba(0,0,0,0.88) ${r + 560}px)`}} />
					<AbsoluteFill style={{background: '#000', opacity: dark}} />
				</>
			);
		},
	},
	{ // 11 the twist: slow push onto the red eye, tremble on «жутко»
		cam: ({D}) => [[0, C(1, 0.56, 0.38)], [D, C(1.32, 0.63, 0.27)]],
		shake: ({w}) => [[fr(w(/^жутко/)), 5], [fr(w(/^жутко/)) + 4, 4]],
		fx: ({t}, cam) => {
			const p = toScreen(cam, 0.63, 0.26);
			return (
				<>
					<Fog t={t} seed="f11" n={6} alpha={0.12} band={[0.6, 1.05]} />
					<Glow x={p.x} y={p.y} r={120 * cam.z} color="rgba(255,40,40,0.8)" opacity={0.35 + 0.15 * Math.sin(t * 4)} />
				</>
			);
		},
	},
	{ // 12 the mist may take Rhaast: push in, red glow dies, green takes over
		cam: ({D}) => [[0, C(1.08, 0.6, 0.36)], [D, C(1.28, 0.61, 0.32)]],
		fx: ({t, D}, cam) => {
			const p = toScreen(cam, 0.61, 0.32);
			const k = interpolate(t, [D * 0.25, D], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
			return (
				<>
					<Fog t={t} seed="f12" n={7} alpha={0.12 + 0.08 * k} band={[0.3, 1.05]} />
					<Glow x={p.x} y={p.y} r={110 * cam.z} color="rgba(0,0,0,0.9)" opacity={0.75 * k} blend="normal" />
					<Glow x={p.x} y={p.y} r={150 * cam.z} color="rgba(255,40,40,0.8)" opacity={0.6 * (1 - k)} />
					<Glow x={p.x} y={p.y} r={170 * cam.z} color="rgba(46,242,160,0.7)" opacity={0.45 * k} />
				</>
			);
		},
	},
	{ // 13 time is on his side: slow pull-back
		cam: ({D}) => [[0, C(1.16, 0.45, 0.55)], [D, C(1, 0.45, 0.5), LIN]],
		fx: ({t}) => <Fog t={t} seed="f13" n={7} alpha={0.13} band={[0.35, 1.05]} />,
	},
	{ // 14 climax: green flash on «смыкается», slow pull-back, particles
		cam: ({D}) => [[0, C(1.16, 0.45, 0.4)], [D, C(1.02, 0.45, 0.4), LIN]],
		fx: ({t, f, w}) => (
			<>
				<Fog t={t} seed="f14" n={7} alpha={0.13} />
				<Rising t={t} seed="g14" n={26} colors={JADE} minY={600} speed={[60, 140]} opacity={0.7} />
				<Flash frame={f} at={[fr(w(/^смыкается/))]} color="#2EF2A0" len={4} peak={0.75} />
			</>
		),
	},
	{ // 15 verdict + CTA plate
		cam: ({D}) => [[0, C(1, 0.5, 0.3)], [D, C(1.14, 0.52, 0.22), LIN]],
		fx: ({t, D}) => (
			<>
				<Fog t={t} seed="f15" n={6} alpha={0.12} />
				<Rising t={t} seed="g15" n={22} colors={JADE} minY={700} speed={[50, 120]} opacity={0.65} />
				<Plate t={t - (D - 2)} />
			</>
		),
	},
];

const Plate: React.FC<{t: number}> = ({t}) => {
	if (t < 0) return null;
	const s = interpolate(t, [0, 0.2], [0.6, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2))});
	return (
		<div style={{position: 'absolute', left: 0, width: 1080, top: 1290, display: 'flex', justifyContent: 'center'}}>
			<div style={{
				transform: `scale(${s})`, opacity: Math.min(1, t / 0.12), padding: '18px 40px', borderRadius: 24,
				background: 'rgba(8,10,12,0.8)', border: '5px solid #2EF2A0', boxShadow: '0 0 40px rgba(46,242,160,0.45)',
				color: '#fff', fontFamily: 'Montserrat, "Noto Color Emoji"', fontWeight: 900, fontSize: 64, whiteSpace: 'nowrap',
			}}>
				Кто следующий? <span style={{display: 'inline-block', transform: `translateY(${Math.sin(t * 9) * 6}px)`}}>👇</span>
			</div>
		</div>
	);
};

const SceneView: React.FC<{i: number}> = ({i}) => {
	const f = useCurrentFrame();
	const sc = tl.scenes[i];
	const t0 = sc.from / FPS;
	const find = (re: RegExp, n = 0) => {
		const m = sc.words.filter((x) => re.test(x.w))[n];
		if (!m) throw new Error(`scene ${i + 1}: no word ${re} #${n}`);
		return m;
	};
	const ctx: Ctx = {t: f / FPS, f, D: sc.dur / FPS, w: (re, n) => find(re, n).s - t0, we: (re, n) => find(re, n).e - t0};
	const def = DEFS[i];
	const cam = camAt(def.cam(ctx), ctx.t);
	const p = place(cam);
	const sh = shakeAt(f, def.shake?.(ctx) ?? [], 9);
	const shaking = sh.x !== 0 || sh.y !== 0;
	const blur = def.blur?.(ctx) ?? 0;
	return (
		<AbsoluteFill style={{background: '#000', overflow: 'hidden'}}>
			<AbsoluteFill style={{transform: `translate(${sh.x}px, ${sh.y}px) scale(${shaking ? 1.02 : 1})`}}>
				<Img src={staticFile(`${sc.img}.png`)} style={{
					position: 'absolute', left: 0, top: 0, width: IW, height: IH, transformOrigin: '0 0',
					transform: `translate(${p.x}px, ${p.y}px) scale(${cam.z})`, filter: blur ? `blur(${blur}px)` : undefined,
				}} />
				{def.fx?.(ctx, cam)}
			</AbsoluteFill>
		</AbsoluteFill>
	);
};

export const Lore: React.FC = () => (
	<AbsoluteFill style={{background: '#000'}}>
		{tl.scenes.map((s, i) => (
			<Sequence key={i} from={s.from} durationInFrames={s.dur} name={`${i + 1} · ${s.img}`}>
				<SceneView i={i} />
			</Sequence>
		))}
		<AbsoluteFill style={{background: 'radial-gradient(ellipse 75% 60% at 50% 45%, rgba(0,0,0,0) 55%, rgba(0,0,0,0.5) 100%)'}} />
		<Captions />
	</AbsoluteFill>
);
