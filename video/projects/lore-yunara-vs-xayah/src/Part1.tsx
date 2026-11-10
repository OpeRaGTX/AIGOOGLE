import React from 'react';
import {AbsoluteFill, Easing, OffthreadVideo, continueRender, delayRender, interpolate, staticFile, useCurrentFrame} from 'remotion';
import edl from '../inbox/p1/edl.json';
import data from './words.json';
import {Captions, Word, buildChunks} from './Captions';

// Part 1: background = Veo clips cut exactly by inbox/p1/edl.json (pipeline/prep_clips.py -> public/p1/bg.mp4),
// slow 1.00->1.04 push on held frames, 2-frame flashes on 1-13 «Залп» and 1-14, word captions, end card (last 2 s).
export const FPS = 30;
export const P1_FRAMES = data.frames;

const fontHandle = delayRender('fonts');
Promise.all(
	(['cyrillic', 'latin'] as const).flatMap((sub) =>
		[800, 900].map((w) => new FontFace('Montserrat', `url(${staticFile(`fonts/montserrat-${sub}-${w}-normal.woff2`)}) format('woff2')`, {
			weight: String(w),
			unicodeRange: sub === 'cyrillic' ? 'U+0301, U+0400-045F, U+0490-0491, U+04B0-04B1, U+2116' : 'U+0000-00FF, U+2000-206F, U+20AC, U+2122',
		}).load().then((f) => document.fonts.add(f))),
	),
).then(() => continueRender(fontHandle));

const SEGS = edl.segments.map((s) => {
	const f0 = Math.round(s.start * FPS);
	const f1 = Math.round((s.start + s.dur) * FPS);
	return {...s, f0, f1, nHold: Math.round(s.hold * FPS)};
});
const HOLDS = SEGS.filter((s) => s.nHold >= 3);
const FLASHES = SEGS.filter((s) => s.clip === '1-13' || s.clip === '1-14').map((s) => s.f0);
const CHUNKS = buildChunks(data.words as Word[], FPS);
const CARD = 2 * FPS;

const EndCard: React.FC<{f: number}> = ({f}) => {
	if (f < 0) return null;
	const s = interpolate(f, [0, 7], [0.6, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2))});
	return (
		<div style={{position: 'absolute', left: 0, width: 1080, top: 250, display: 'flex', justifyContent: 'center'}}>
			<div style={{
				transform: `scale(${s})`, opacity: Math.min(1, f / 4), padding: '26px 44px', borderRadius: 28, textAlign: 'center',
				background: 'rgba(10,6,20,0.82)', border: '5px solid #FF5FA2', boxShadow: '0 0 0 5px #5CE1E6, 0 0 50px rgba(199,125,255,0.55)',
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
	return (
		<AbsoluteFill style={{background: '#000'}}>
			<AbsoluteFill style={{transform: `scale(${zoom})`}}>
				<OffthreadVideo src={staticFile('p1/bg.mp4')} muted />
			</AbsoluteFill>
			{flash !== undefined && <AbsoluteFill style={{background: '#fff', opacity: flash === 0 ? 0.85 : 0.4}} />}
			<Captions chunks={CHUNKS} />
			<EndCard f={f - (P1_FRAMES - CARD)} />
		</AbsoluteFill>
	);
};
