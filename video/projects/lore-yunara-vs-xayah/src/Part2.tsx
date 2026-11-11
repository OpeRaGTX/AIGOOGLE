import React from 'react';
import {AbsoluteFill, Easing, OffthreadVideo, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import './fonts';
import edl from './edl-p2.json'; // inbox/p2/edl.json + cold open (pipeline/intro_p2.json), see pipeline/edl.py
import data from './words-p2.json';
import {Captions, Word, buildChunks} from './Captions';
import {Living} from './Living';
import {Cam, Key, camAt, place, shakeAt} from './camera';

// Part 2: cold open 1.5 s without voice (2-04 aura burst + flash + «ЧАСТЬ 2 · ФИНАЛ»), then Veo clips cut by the EDL
// shifted +1.5 s (pipeline/prep_clips.py -> public/p2/bg.mp4) + the six stills as «living pictures» (WebGL depth
// shader, maps from pipeline/living_maps.py), push on held frames, flashes on 2-04 / 2-12 / 2-13 (+ shake),
// corner badge for 3 s after the cold open, end card (last 2.5 s), word captions as in part 1.
export const FPS = 30;
export const P2_FRAMES = data.frames;

const SEGS = edl.segments.map((s) => {
	const f0 = Math.round(s.start * FPS);
	const f1 = Math.round((s.start + s.dur) * FPS);
	return {...s, f0, f1, nHold: Math.round(s.hold * FPS), name: s.file.split('/').pop()!.replace(/\.\w+$/, ''), intro: 'intro' in s};
});
const at = (name: string) => SEGS.find((s) => s.name === name && !s.intro)!.f0; // story segment, not the cold open
const COLD = SEGS.filter((s) => s.intro);
const COLD_END = COLD.length ? Math.max(...COLD.map((s) => s.f1)) : 0;
const COLD_HIT = COLD.flatMap((s) => ('flash_at' in s ? [Math.round((s.start + (s.flash_at as number)) * FPS)] : []));
const HOLDS = SEGS.filter((s) => s.type === 'video' && s.nHold >= 3);
const FLASHES = [...COLD_HIT, at('2-04'), at('2-12'), at('2-13')];
const SHAKES: [number, number][] = [...COLD_HIT.map((f): [number, number] => [f, 8]), [at('2-13'), 12]];
const CHUNKS = buildChunks(data.words as Word[], FPS);
const BADGE = 3 * FPS;
const CARD = Math.round(2.5 * FPS);

const OUT = Easing.out(Easing.cubic);
const LIN = (x: number) => x;
const C = (z: number, cx: number, cy: number): Cam => ({z, cx, cy});
type Still = {cam: (D: number) => Key[]; par: [[number, number], [number, number]]; flow?: number; glow?: number; red?: number; rgb?: number};
// camera per still: zoom 1.0->1.15-1.22, drift, depth-parallax offset; focus = face / light source
const STILLS: Record<string, Still> = {
	'2-07': {cam: (D) => [[0, C(1.16, 0.5, 0.8)], [D, C(1.03, 0.5, 0.42)]], par: [[-0.4, -1.2], [0.4, 1.2]], flow: 1.3, glow: 1.1}, // beam: tilt up
	'2-10': {cam: (D) => [[0, C(1, 0.5, 0.4)], [D, C(1.22, 0.56, 0.32), LIN]], par: [[-1.2, 0], [1.2, 0.2]], flow: 1}, // Xayah hunted
	'2-11': {cam: (D) => [[0, C(1.18, 0.42, 0.25)], [D, C(1.06, 0.55, 0.42)]], par: [[1.2, 0.3], [-1.2, -0.2]], flow: 1.2}, // point-blank
	'2-13': {cam: (D) => [[0, C(1, 0.45, 0.55)], [0.35, C(1.14, 0.45, 0.58), OUT], [D, C(1.2, 0.45, 0.6), LIN]], par: [[-1, 0], [1, 0.2]], rgb: 3}, // «Второй»
	'2-15': {cam: (D) => [[0, C(1, 0.5, 0.32)], [D, C(1.16, 0.56, 0.28), LIN]], par: [[1.2, 0], [-1.2, 0.2]], flow: 1, glow: 1}, // Yunara's quote
	'2-16': {cam: (D) => [[0, C(1, 0.5, 0.5)], [D, C(1.2, 0.6, 0.48), LIN]], par: [[-1.2, 0], [1.2, 0.2]], flow: 1}, // Xayah hisses
};

const StillScene: React.FC<{name: string; dur: number; seed: number}> = ({name, dur, seed}) => {
	const f = useCurrentFrame();
	const def = STILLS[name];
	const t = f / FPS;
	const D = dur / FPS;
	const cam = camAt(def.cam(D), t);
	const p = place(cam);
	const k = Easing.inOut(Easing.sin)(Math.min(1, t / D));
	const par: [number, number] = [def.par[0][0] + (def.par[1][0] - def.par[0][0]) * k, def.par[0][1] + (def.par[1][1] - def.par[0][1]) * k];
	return (
		<Living
			dir="p2" img={name} t={t} par={par} focus={[cam.cx, cam.cy]} seed={seed}
			uvOff={[-p.x / p.W, -p.y / p.H]} uvScale={[1080 / p.W, 1920 / p.H]}
			flow={def.flow} glow={def.glow} red={def.red} rgb={def.rgb && f < 3 ? def.rgb : 0}
		/>
	);
};

// cold open: big plate in the caption zone (no voice yet), punches on the burst, hands over to the corner badge
const ColdPlate: React.FC<{f: number}> = ({f}) => {
	if (f >= COLD_END) return null;
	const hit = COLD_HIT[0] ?? -99;
	const s = interpolate(f, [0, 5], [0.85, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2))})
		* (f >= hit && f < hit + 6 ? interpolate(f, [hit, hit + 6], [1.08, 1], {easing: Easing.out(Easing.cubic)}) : 1);
	const op = interpolate(f, [COLD_END - 5, COLD_END], [1, 0], {extrapolateLeft: 'clamp'});
	return (
		<div style={{position: 'absolute', left: 0, width: 1080, top: 1150, display: 'flex', justifyContent: 'center'}}>
			<div style={{
				transform: `translateY(-50%) scale(${s})`, opacity: op, padding: '22px 44px', borderRadius: 30, whiteSpace: 'nowrap',
				background: 'rgba(10,6,20,0.82)', border: '6px solid #FF5FA2', boxShadow: '0 0 0 6px #5CE1E6, 0 0 70px rgba(199,125,255,0.65)',
				color: '#fff', fontFamily: 'Montserrat', fontWeight: 900, fontSize: 92, letterSpacing: -1.5,
			}}>
				ЧАСТЬ 2 <span style={{color: '#5CE1E6'}}>·</span> <span style={{color: '#FF5FA2'}}>ФИНАЛ</span>
			</div>
		</div>
	);
};

const Badge: React.FC<{f: number}> = ({f}) => {
	if (f < 0 || f >= BADGE) return null;
	const op = interpolate(f, [0, 6, BADGE - 8, BADGE], [0, 1, 1, 0], {extrapolateRight: 'clamp'});
	const s = interpolate(f, [0, 7], [0.7, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2))});
	return (
		<div style={{
			position: 'absolute', left: 48, top: 175, opacity: op, transform: `scale(${s})`, transformOrigin: '0 0',
			padding: '12px 26px', borderRadius: 18, background: 'rgba(10,6,20,0.8)', border: '4px solid #FF5FA2',
			boxShadow: '0 0 0 4px #5CE1E6, 0 0 30px rgba(199,125,255,0.5)', color: '#fff', fontFamily: 'Montserrat',
			fontWeight: 900, fontSize: 48, letterSpacing: -0.5,
		}}>
			ЧАСТЬ 2 · ФИНАЛ
		</div>
	);
};

const EndCard: React.FC<{f: number}> = ({f}) => {
	if (f < 0) return null;
	const s = interpolate(f, [0, 7], [0.6, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2))});
	return (
		<div style={{position: 'absolute', left: 0, width: 1080, top: 250, display: 'flex', justifyContent: 'center'}}>
			<div style={{
				transform: `scale(${s})`, opacity: Math.min(1, f / 4), padding: '26px 48px', borderRadius: 28, textAlign: 'center',
				background: 'rgba(10,6,20,0.82)', border: '5px solid #FF5FA2', boxShadow: '0 0 0 5px #5CE1E6, 0 0 50px rgba(199,125,255,0.55)',
				color: '#fff', fontFamily: 'Montserrat, "Noto Color Emoji"', fontWeight: 900, fontSize: 66, lineHeight: 1.1, letterSpacing: -1,
			}}>
				КОГО СТОЛКНУТЬ
				<br />
				СЛЕДУЮЩИМ? <span style={{display: 'inline-block', transform: `translateY(${Math.sin(f / 3) * 6}px)`}}>👇</span>
			</div>
		</div>
	);
};

export const Part2: React.FC = () => {
	const f = useCurrentFrame();
	const hold = HOLDS.find((h) => f >= h.f1 - h.nHold && f < h.f1);
	const zoom = hold ? interpolate(f, [hold.f1 - hold.nHold, hold.f1 - 1], [1, 1.04], {easing: Easing.inOut(Easing.sin)}) : 1;
	const flash = FLASHES.map((s) => f - s).find((d) => d >= 0 && d < 2);
	const sh = shakeAt(f, SHAKES);
	const shaking = sh.x !== 0 || sh.y !== 0;
	return (
		<AbsoluteFill style={{background: '#000'}}>
			<AbsoluteFill style={{transform: `translate(${sh.x}px, ${sh.y}px) scale(${shaking ? 1.02 : 1})`}}>
				<AbsoluteFill style={{transform: `scale(${zoom})`}}>
					<OffthreadVideo src={staticFile('p2/bg.mp4')} muted />
				</AbsoluteFill>
				{SEGS.filter((s) => s.type === 'image').map((s, i) => (
					<Sequence key={s.f0} from={s.f0} durationInFrames={s.f1 - s.f0} name={s.name}>
						<StillScene name={s.name} dur={s.f1 - s.f0} seed={i * 7.3} />
					</Sequence>
				))}
			</AbsoluteFill>
			{flash !== undefined && <AbsoluteFill style={{background: '#fff', opacity: flash === 0 ? 0.85 : 0.4}} />}
			<Captions chunks={CHUNKS} />
			<ColdPlate f={f} />
			<Badge f={f - COLD_END} />
			<EndCard f={f - (P2_FRAMES - CARD)} />
		</AbsoluteFill>
	);
};
