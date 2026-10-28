import React from 'react';
import {AbsoluteFill, Audio, OffthreadVideo, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import hits from './hits.json';

export const FPS = 60;
export const DURATION = Math.ceil(29.388 * FPS);
const f = (t: number) => Math.round(t * FPS);
const FONT = "Inter, 'Inter Display', sans-serif";

type Seg = {at: number; src: number; rate: number};
type Clip = {kind: 'shot'; id: string; from: number; to: number} | {kind: 'game'; from: number; to: number; segs: Seg[]};

// Edit decision list, in track seconds (beat-aligned, 132 BPM).
const EDL: Clip[] = [
	{kind: 'shot', id: 'S1_intro', from: 0, to: 3.228},
	{kind: 'shot', id: 'S2_pantheon', from: 3.228, to: 5.074},
	{kind: 'game', from: 5.074, to: 8.266, segs: [{at: 5.074, src: 4.574, rate: 1}, {at: 7.25, src: 6.75, rate: 0.5}, {at: 7.55, src: 6.9, rate: 1}]},
	{kind: 'shot', id: 'S3_ahri', from: 8.266, to: 9.857},
	{kind: 'game', from: 9.857, to: 12.156, segs: [{at: 9.857, src: 10.0, rate: 1}, {at: 10.7, src: 10.843, rate: 0.5}, {at: 11.0, src: 10.993, rate: 1}]},
	{kind: 'game', from: 12.156, to: 12.62, segs: [{at: 12.156, src: 15.48, rate: 1}]},
	{kind: 'shot', id: 'S4_volibear', from: 12.62, to: 14.443},
	{kind: 'game', from: 14.443, to: 18.123, segs: [{at: 14.443, src: 16.993, rate: 1}, {at: 16.65, src: 19.2, rate: 0.5}, {at: 17.0, src: 19.375, rate: 1}]},
	{kind: 'shot', id: 'S5_seraphine', from: 18.123, to: 20.863},
	{kind: 'game', from: 20.863, to: 24.532, segs: [{at: 20.863, src: 20.613, rate: 1}, {at: 23.05, src: 22.8, rate: 0.5}, {at: 23.4, src: 22.975, rate: 1}]},
	{kind: 'shot', id: 'S6_finale', from: 24.532, to: 26.366},
	{kind: 'game', from: 26.366, to: 27.736, segs: [{at: 26.366, src: 26.3, rate: 1}]},
	{kind: 'shot', id: 'S7_hero', from: 27.736, to: 29.388},
];

// Kill impacts: label + strong punch. Big = section accents without a label.
const KILLS = [
	{t: 7.349, until: 8.266, n: '01', name: 'PANTHEON'},
	{t: 10.757, until: 12.62, n: '02', name: 'AHRI'},
	{t: 16.742, until: 18.123, n: '03', name: 'VOLIBEAR'},
	{t: 23.15, until: 24.532, n: '04', name: 'SERAPHINE'},
];
const IMPACTS = [7.349, 10.757, 14.443, 16.742, 20.863, 23.15, 27.57];
const FLASH = [{t: 7.349, a: 0.75}, {t: 10.757, a: 0.4}, {t: 14.443, a: 0.95}, {t: 16.742, a: 0.75}, {t: 20.863, a: 0.6}, {t: 23.15, a: 0.75}, {t: 27.736, a: 0.35}];
const KICKS = (hits.kicks as [number, number][]).filter(([t, s]) => s >= 0.45 && t > 3.5 && t < 28.1);

const decay = (t: number, at: number, tau: number) => (t < at ? 0 : Math.exp(-(t - at) / tau));

const Clips: React.FC = () => (
	<>
		{EDL.map((c, i) => {
			const dur = f(c.to) - f(c.from);
			if (c.kind === 'shot') {
				return (
					<Sequence key={i} from={f(c.from)} durationInFrames={dur}>
						<OffthreadVideo src={staticFile(`shots/${c.id}.mp4`)} muted style={{width: '100%', height: '100%', filter: 'contrast(1.12) saturate(1.06) brightness(1.18)'}} />
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
							style={{width: '100%', height: '100%', filter: 'contrast(1.16) saturate(1.06) brightness(0.9)'}}
						/>
					</Sequence>
				);
			});
		})}
	</>
);

const Grain: React.FC<{seed: number}> = ({seed}) => (
	<svg width="100%" height="100%" style={{position: 'absolute', inset: 0, mixBlendMode: 'overlay', opacity: 0.22}}>
		<filter id={`g${seed}`}>
			<feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves={2} seed={seed} stitchTiles="stitch" />
			<feColorMatrix type="saturate" values="0" />
		</filter>
		<rect width="100%" height="100%" filter={`url(#g${seed})`} />
	</svg>
);

const KillLabel: React.FC<{t: number; until: number; n: string; name: string}> = ({t, until, n, name}) => {
	const frame = useCurrentFrame();
	const sec = frame / FPS;
	const inP = interpolate(sec, [t, t + 0.18], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const end = Math.min(t + 1.5, until);
	const outP = interpolate(sec, [end - 0.2, end - 1 / FPS], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const o = Math.min(inP, outP);
	if (o <= 0) return null;
	const ease = 1 - Math.pow(1 - inP, 3);
	return (
		<div style={{position: 'absolute', left: 80, bottom: 470, fontFamily: FONT, color: '#fff', opacity: o, transform: `translateX(${(1 - ease) * -30}px)`, textShadow: '0 2px 18px rgba(0,0,0,.85), 0 0 4px rgba(0,0,0,.6)'}}>
			<div style={{display: 'flex', alignItems: 'baseline', gap: 22}}>
				<span style={{fontSize: 96, fontWeight: 300, color: '#ff3030', letterSpacing: 2}}>{n}</span>
				<span style={{fontSize: 42, fontWeight: 500, letterSpacing: 14}}>{name}</span>
			</div>
			<div style={{height: 2, width: 520 * ease, background: 'linear-gradient(90deg,#ff2a2a,rgba(255,42,42,0))', marginTop: 6}} />
		</div>
	);
};

const Title: React.FC = () => {
	const frame = useCurrentFrame();
	const sec = frame / FPS;
	const a = interpolate(sec, [28.0, 28.45], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	if (a <= 0) return null;
	const track = interpolate(sec, [28.0, 29.388], [44, 28]);
	return (
		<AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', fontFamily: FONT, color: '#fff'}}>
			<div style={{opacity: a, textAlign: 'center', transform: 'translateY(330px)'}}>
				<div style={{fontSize: 170, fontWeight: 200, letterSpacing: track, marginRight: -track, textShadow: '0 0 40px rgba(255,20,20,.6)'}}>KAYN</div>
				<div style={{fontSize: 28, fontWeight: 400, letterSpacing: 12, marginRight: -12, color: '#ff4a4a', marginTop: 6}}>THE SHADOW REAPER</div>
			</div>
		</AbsoluteFill>
	);
};

export const KaynEdit: React.FC = () => {
	const frame = useCurrentFrame();
	const t = frame / FPS;
	// rhythm: kick pulse + impact punch-in + decaying shake
	let zoom = 1;
	for (const [kt, s] of KICKS) zoom += 0.016 * s * decay(t, kt, 0.09);
	let shake = 0;
	for (const it of IMPACTS) {
		zoom += 0.09 * decay(t, it, 0.28);
		shake += decay(t, it, 0.16);
	}
	const sx = shake * 16 * Math.sin(t * 97.3) + shake * 7 * Math.sin(t * 213.1);
	const sy = shake * 12 * Math.cos(t * 83.7) + shake * 6 * Math.sin(t * 171.9);
	// cut whip: brief scale-in + blur at the start of each clip
	let whip = 0;
	for (const c of EDL) whip = Math.max(whip, decay(t, c.from, 0.05) * (c.from > 0 ? 1 : 0));
	let flash = 0;
	for (const fl of FLASH) flash = Math.max(flash, fl.a * decay(t, fl.t, 0.055));
	const fadeOut = interpolate(t, [28.85, 29.388], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{background: '#000'}}>
			<AbsoluteFill style={{transform: `translate(${sx}px,${sy}px) scale(${zoom + whip * 0.06})`, filter: whip > 0.05 ? `blur(${whip * 6}px)` : undefined, opacity: fadeOut}}>
				<Clips />
			</AbsoluteFill>
			<AbsoluteFill style={{background: 'radial-gradient(ellipse at center, rgba(0,0,0,0) 55%, rgba(0,0,0,.6) 100%)'}} />
			<AbsoluteFill style={{background: 'linear-gradient(0deg, rgba(0,0,0,.6) 0%, rgba(0,0,0,0) 42%)'}} />
			<Grain seed={frame % 6} />
			{KILLS.map((k) => (
				<KillLabel key={k.n} {...k} />
			))}
			<AbsoluteFill style={{opacity: fadeOut < 1 ? Math.max(fadeOut, 0.0) : 1}}>
				<Title />
			</AbsoluteFill>
			<AbsoluteFill style={{background: '#fff', opacity: flash, mixBlendMode: 'screen'}} />
			<Audio src={staticFile('track.mp3')} />
		</AbsoluteFill>
	);
};
