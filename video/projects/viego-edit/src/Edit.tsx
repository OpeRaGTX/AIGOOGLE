import React from 'react';
import {AbsoluteFill, Audio, OffthreadVideo, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import hits from './hits.json';

export const FPS = 60;
export const DURATION = Math.ceil(34.343 * FPS);
const f = (t: number) => Math.round(t * FPS);
const FONT = "Inter, 'Inter Display', sans-serif";
const EMERALD = '#36ffb4';

type Seg = {at: number; src: number; rate: number};
type Clip = {kind: 'shot'; id: string; from: number; to: number} | {kind: 'game'; from: number; to: number; segs: Seg[]};

// Grading is baked into the mp4s (tools/grade.sh): shots 1.1/1.05/1.12, gameplay 1.14/1.04/0.92.
// Edit decision list in track seconds (139.7 BPM). Gameplay src = seconds in the 60 fps gameplay file.
const EDL: Clip[] = [
	{kind: 'shot', id: 'V1_intro', from: 0, to: 4.969},
	{kind: 'game', from: 4.969, to: 8.39, segs: [{at: 4.969, src: 5.07, rate: 1}, {at: 7.25, src: 7.351, rate: 0.5}, {at: 7.65, src: 7.551, rate: 1}]},
	{kind: 'shot', id: 'V2_slash', from: 8.39, to: 10.34},
	{kind: 'game', from: 10.34, to: 12.69, segs: [{at: 10.34, src: 10.22, rate: 1}, {at: 11.75, src: 11.63, rate: 0.5}, {at: 12.15, src: 11.83, rate: 1}]},
	{kind: 'shot', id: 'V3_orbit', from: 12.69, to: 14.431},
	{kind: 'game', from: 14.431, to: 17.705, segs: [{at: 14.431, src: 14.146, rate: 1}, {at: 15.85, src: 15.565, rate: 0.5}, {at: 16.25, src: 15.765, rate: 1}]},
	{kind: 'shot', id: 'V4_dive', from: 17.705, to: 19.365},
	{kind: 'game', from: 19.365, to: 24.05, segs: [{at: 19.365, src: 18.978, rate: 1}, {at: 20.55, src: 20.163, rate: 0.4}, {at: 21.15, src: 20.403, rate: 1}]},
	{kind: 'shot', id: 'V5_track', from: 24.05, to: 25.53},
	{kind: 'game', from: 25.53, to: 26.912, segs: [{at: 25.53, src: 19.95, rate: 0.45}]},
	{kind: 'shot', id: 'V6_crane', from: 26.912, to: 28.97},
	{kind: 'shot', id: 'V7_outro', from: 28.97, to: 34.343},
];

// Hits: kills + 3D impacts get a punch-in and shake; flashes on the biggest moments.
const IMPACTS = [4.969, 5.648, 7.35, 9.68, 11.82, 13.235, 13.76, 15.935, 19.365, 20.637, 25.04, 26.912];
const FLASH = [
	{t: 4.969, a: 0.95}, {t: 5.648, a: 0.45}, {t: 7.35, a: 0.55}, {t: 9.68, a: 0.5}, {t: 11.82, a: 0.6},
	{t: 13.76, a: 0.4}, {t: 15.935, a: 0.6}, {t: 19.365, a: 0.7}, {t: 20.637, a: 0.9}, {t: 25.04, a: 0.45}, {t: 26.912, a: 0.5},
];
const KICKS = (hits.kicks as [number, number][]).filter(([t, s]) => s >= 0.45 && t > 4.9 && t < 30.7);

const decay = (t: number, at: number, tau: number) => (t < at ? 0 : Math.exp(-(t - at) / tau));

const Clips: React.FC = () => (
	<>
		{EDL.map((c, i) => {
			const dur = f(c.to) - f(c.from);
			if (c.kind === 'shot') {
				return (
					<Sequence key={i} from={f(c.from)} durationInFrames={dur}>
						<OffthreadVideo src={staticFile(`shots/${c.id}.mp4`)} muted style={{width: '100%', height: '100%'}} />
					</Sequence>
				);
			}
			return c.segs.map((s, k) => {
				const end = k + 1 < c.segs.length ? c.segs[k + 1].at : c.to;
				return (
					<Sequence key={`${i}-${k}`} from={f(s.at)} durationInFrames={f(end) - f(s.at)}>
						<OffthreadVideo
							src={staticFile('game.mp4')}
							muted
							startFrom={Math.round(s.src * FPS)}
							playbackRate={s.rate}
							style={{width: '100%', height: '100%'}}
						/>
					</Sequence>
				);
			});
		})}
	</>
);

const Grain: React.FC<{seed: number}> = ({seed}) => (
	<svg width="100%" height="100%" style={{position: 'absolute', inset: 0, mixBlendMode: 'overlay', opacity: 0.2}}>
		<filter id={`g${seed}`}>
			<feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves={2} seed={seed} stitchTiles="stitch" />
			<feColorMatrix type="saturate" values="0" />
		</filter>
		<rect width="100%" height="100%" filter={`url(#g${seed})`} />
	</svg>
);

const Title: React.FC = () => {
	const frame = useCurrentFrame();
	const sec = frame / FPS;
	const a = interpolate(sec, [30.7, 31.4], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const out = interpolate(sec, [33.7, 34.3], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	if (a <= 0) return null;
	const track = interpolate(sec, [30.7, 34.343], [40, 26]);
	const sub = interpolate(sec, [31.2, 31.8], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', fontFamily: FONT, color: '#fff', opacity: Math.min(a, out)}}>
			<div style={{textAlign: 'center', transform: `translateY(${330 + (1 - a) * 20}px)`}}>
				<div style={{fontSize: 160, fontWeight: 200, letterSpacing: track, marginRight: -track, textShadow: `0 0 40px rgba(54,255,180,.45)`}}>VIEGO</div>
				<div style={{fontSize: 28, fontWeight: 400, letterSpacing: 12, marginRight: -12, color: EMERALD, marginTop: 6, opacity: sub}}>THE RUINED KING</div>
			</div>
		</AbsoluteFill>
	);
};

export const ViegoEdit: React.FC = () => {
	const frame = useCurrentFrame();
	const t = frame / FPS;
	let zoom = 1;
	for (const [kt, s] of KICKS) zoom += 0.014 * s * decay(t, kt, 0.09);
	let shake = 0;
	for (const it of IMPACTS) {
		zoom += 0.08 * decay(t, it, 0.26);
		shake += decay(t, it, 0.15);
	}
	const sx = shake * 15 * Math.sin(t * 97.3) + shake * 6 * Math.sin(t * 213.1);
	const sy = shake * 11 * Math.cos(t * 83.7) + shake * 5 * Math.sin(t * 171.9);
	let whip = 0;
	for (const c of EDL) whip = Math.max(whip, decay(t, c.from, 0.05) * (c.from > 0 ? 1 : 0));
	let flash = 0;
	for (const fl of FLASH) flash = Math.max(flash, fl.a * decay(t, fl.t, 0.06));
	const fadeIn = interpolate(t, [0, 0.4], [0, 1], {extrapolateRight: 'clamp'});
	const fadeOut = interpolate(t, [33.75, 34.343], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{background: '#000'}}>
			<AbsoluteFill style={{transform: `translate(${sx}px,${sy}px) scale(${zoom + whip * 0.06})`, filter: whip > 0.05 ? `blur(${whip * 6}px)` : undefined, opacity: fadeIn * fadeOut}}>
				<Clips />
			</AbsoluteFill>
			<AbsoluteFill style={{background: 'radial-gradient(ellipse at center, rgba(0,0,0,0) 55%, rgba(0,12,8,.62) 100%)'}} />
			<Grain seed={frame % 6} />
			<Title />
			<AbsoluteFill style={{background: '#e9fff6', opacity: flash, mixBlendMode: 'screen'}} />
			<Audio src={staticFile('track.mp3')} />
		</AbsoluteFill>
	);
};
