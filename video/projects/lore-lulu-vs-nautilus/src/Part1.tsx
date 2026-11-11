import React from 'react';
import {AbsoluteFill, Easing, OffthreadVideo, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import './fonts';
import edl from './edl-p1.json'; // = inbox/p1/edl.json, written by pipeline/prep_clips.py
import data from './words-p1.json';
import {Captions, Word, buildChunks} from './Captions';
import {Living} from './Living';
import {Cam, Key, camAt, place, shakeAt} from './camera';

// Part 1 (copied from lore-yunara-vs-xayah/src/Part2.tsx): Veo clips cut exactly by the EDL
// (pipeline/prep_clips.py -> public/p1/bg.mp4) + the four stills as «living pictures» (WebGL depth shader, maps from
// pipeline/living_maps.py), push on held frames, 2-frame flashes on the title and on «разносит» (+ shake),
// end card on the closing 1-00, word captions.
export const FPS = 30;
export const P1_FRAMES = data.frames;

const SEGS = edl.segments.map((s) => {
	const f0 = Math.round(s.start * FPS);
	const f1 = Math.round((s.start + s.dur) * FPS);
	return {...s, f0, f1, nHold: Math.round(s.hold * FPS), name: s.file.split('/').pop()!.replace(/\.\w+$/, '')};
});
const at = (name: string) => SEGS.find((s) => s.name === name)!.f0; // first use of a clip (1-00, 1-01, 1-12 repeat later)
const HOLDS = SEGS.filter((s) => s.type === 'video' && s.nHold >= 3);
const FLASHES = [at('1-00'), at('1-12')];
const SHAKES: [number, number][] = [[at('1-12'), 12]];
const CHUNKS = buildChunks(data.words as Word[], FPS);
const CARD = Math.round(2.2 * FPS); // pops with the light flare of the closing 1-00, on «во второй части»

const OUT = Easing.out(Easing.cubic);
const LIN = (x: number) => x;
const C = (z: number, cx: number, cy: number): Cam => ({z, cx, cy});
type Still = {cam: (D: number) => Key[]; par: [[number, number], [number, number]]; flow?: number; glow?: number; glowIn?: number; red?: number};
// «И Лулу впервые перестаёт смеяться»: seconds into 1-15 where the camera jumps from the cable to her face
const SERIOUS = (data.words as Word[]).find((w) => w.ph === 9)!.s - SEGS.find((s) => s.name === '1-15')!.start;
// camera per still: zoom, drift, depth-parallax offset (kept small: pixel-art outlines tear at the 1.2 used for the
// painted stills of the earlier videos); faces and the helmet stay above the caption line (y 1150)
const STILLS: Record<string, Still> = {
	// rises from the seabed: tilt up from the hand to the helmet and the lilac glow on the surface
	'1-04': {cam: (D) => [[0, C(1.06, 0.5, 0.62)], [D, C(1.18, 0.58, 0.38)]], par: [[-0.15, -0.5], [0.15, 0.5]], flow: 1.2, glow: 1.1, red: 1},
	// «очнулся гигантом»: the helmet window lights up, push-in
	'1-07': {cam: (D) => [[0, C(1, 0.5, 0.5)], [D, C(1.22, 0.47, 0.43), LIN]], par: [[-0.5, 0], [0.5, 0.1]], flow: 1.1, glow: 1.3, glowIn: 1.2, red: 0.6},
	// Lulu's line: from her wave up along the silent giant
	'1-11': {cam: (D) => [[0, C(1.22, 0.64, 0.6)], [1.3, C(1.2, 0.64, 0.58), LIN], [D, C(1.04, 0.52, 0.4)]], par: [[-0.25, -0.5], [0.25, 0.5]], flow: 0.8, glow: 0.9},
	// the cable drags her down, then a punch-in on her face
	'1-15': {
		cam: (D) => [[0, C(1.1, 0.5, 0.42)], [SERIOUS, C(1.22, 0.52, 0.6), LIN], [SERIOUS + 0.3, C(1.36, 0.6, 0.4), OUT], [D, C(1.44, 0.6, 0.4), LIN]],
		par: [[0.1, -0.35], [-0.1, 0.35]], flow: 1.1, glow: 1,
	},
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
	const glowUp = def.glowIn ? interpolate(t, [0, def.glowIn], [0.2, 1], {extrapolateRight: 'clamp', easing: Easing.in(Easing.quad)}) : 1;
	return (
		<Living
			dir="p1" img={name} t={t} par={par} focus={[cam.cx, cam.cy]} seed={seed}
			uvOff={[-p.x / p.W, -p.y / p.H]} uvScale={[1080 / p.W, 1920 / p.H]}
			flow={def.flow} glow={(def.glow ?? 0.8) * glowUp} red={def.red}
		/>
	);
};

const EndCard: React.FC<{f: number}> = ({f}) => {
	if (f < 0) return null;
	const s = interpolate(f, [0, 7], [0.6, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2))});
	return (
		<div style={{position: 'absolute', left: 0, width: 1080, top: 250, display: 'flex', justifyContent: 'center'}}>
			<div style={{
				transform: `scale(${s})`, opacity: Math.min(1, f / 4), padding: '26px 44px', borderRadius: 28, textAlign: 'center',
				background: 'rgba(8,8,22,0.82)', border: '5px solid #FF7BD5', boxShadow: '0 0 0 5px #3FD0C9, 0 0 50px rgba(150,130,255,0.55)',
				color: '#fff', fontFamily: 'Montserrat, "Noto Color Emoji"', fontWeight: 900, whiteSpace: 'nowrap',
			}}>
				<div style={{fontSize: 70, letterSpacing: -1}}>ЧАСТЬ 2 — ЗАВТРА 👀</div>
				<div style={{fontSize: 54, marginTop: 10, color: '#F5E6B8'}}>
					А ты за кого? <span style={{display: 'inline-block', transform: `translateY(${Math.sin(f / 3) * 6}px)`}}>👇</span>
				</div>
			</div>
		</div>
	);
};

export const Part1: React.FC = () => {
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
					<OffthreadVideo src={staticFile('p1/bg.mp4')} muted />
				</AbsoluteFill>
				{SEGS.filter((s) => s.type === 'image').map((s, i) => (
					<Sequence key={s.f0} from={s.f0} durationInFrames={s.f1 - s.f0} name={s.name}>
						<StillScene name={s.name} dur={s.f1 - s.f0} seed={i * 7.3} />
					</Sequence>
				))}
			</AbsoluteFill>
			{flash !== undefined && <AbsoluteFill style={{background: '#fff', opacity: flash === 0 ? 0.85 : 0.4}} />}
			<Captions chunks={CHUNKS} />
			<EndCard f={f - (P1_FRAMES - CARD)} />
		</AbsoluteFill>
	);
};
