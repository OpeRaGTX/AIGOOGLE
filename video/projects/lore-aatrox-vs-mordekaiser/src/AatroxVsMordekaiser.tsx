import React from 'react';
import {AbsoluteFill, Easing, OffthreadVideo, interpolate, staticFile, useCurrentFrame} from 'remotion';
import './fonts';
import edl from './edl.json'; // = inbox/edl.json, written by pipeline/prep_clips.py
import data from './words.json'; // = inbox/words.json + NOTES fixes, written by pipeline/words.py
import {Captions, GREEN, RED, Word, buildChunks} from './Captions';
import {shakeAt} from './camera';

// Copied from lore-shaco-vs-jhin/src/ShacoVsJhin.tsx: the Veo clips cut by inbox/edl.json (pipeline/prep_clips.py ->
// public/bg.mp4), push on held frames, white flashes + shakes on the hits, word captions, hook plate
// «АТРОКС vs МОРДЕКАЙЗЕР» over the 00 split, end card «Кого столкнуть следующим? 👇» on the closing 00.
export const FPS = 30;
export const FRAMES = data.frames;

const SEGS = edl.segments.map((s) => {
	const f0 = Math.round(s.start * FPS);
	const f1 = Math.round((s.start + s.dur) * FPS);
	return {...s, f0, f1, nHold: Math.round(s.hold * FPS), name: s.file.replace(/\.\w+$/, '')};
});
const t = (sec: number) => Math.round(sec * FPS);
const HOLDS = SEGS.filter((s) => s.nHold >= 3);
const HOOK_END = SEGS[1].f0; // 0–2.32 s: split 00 x1.3 with the title plate, no captions
// NOTES: hits on «тройной удар» 48.8, «сбивает» 51.3, «вбивает» 55.5, «раскалывается» 89.4 — flash + shake;
// «Крылья, кровь, удар» 86.7 / 87.7 / 88.4 — shake only. Plus a flash on the first frame (hook).
const HITS = [48.8, 51.3, 55.5, 89.4].map(t);
const FLASHES = [0, ...HITS];
const SHAKES: [number, number][] = [...HITS.map((f): [number, number] => [f, 14]), ...[86.7, 87.7, 88.4].map((s): [number, number] => [t(s), 11])];
const WORDS = data.words as Word[];
const CHUNKS = buildChunks(WORDS, FPS);
const CARD_FROM = t(WORDS.find((w) => w.w === 'Кого' && w.s > 100)!.s) - 1; // closing 00, on «Кого столкнуть следующим?»

const FRAME = {background: 'rgba(10,8,12,0.82)', color: '#fff', fontFamily: 'Montserrat, "Noto Color Emoji"', fontWeight: 900} as const;
const SHADOW = '0 4px 6px rgba(0,0,0,0.6), 0 8px 26px rgba(0,0,0,0.9)';

const HookPlate: React.FC<{f: number}> = ({f}) => {
	if (f >= HOOK_END) return null;
	const pop = (d: number) => interpolate(f - d, [0, 6], [0.5, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2.2))});
	const op = (d: number) => interpolate(f - d, [0, 3], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const out = interpolate(f, [HOOK_END - 4, HOOK_END], [1, 0], {extrapolateLeft: 'clamp'});
	const line = (text: string, color: string, size: number, d: number) => (
		<div style={{color, fontSize: size, transform: `scale(${pop(d)})`, opacity: op(d), textShadow: `${SHADOW}, 0 0 40px ${color}88`}}>{text}</div>
	);
	return (
		<AbsoluteFill style={{justifyContent: 'center', alignItems: 'center', opacity: out}}>
			<div style={{
				...FRAME, textAlign: 'center', lineHeight: 1.0, letterSpacing: -2, padding: '34px 44px 40px', borderRadius: 32,
				transform: `scale(${pop(0)})`, border: `5px solid ${RED}`, boxShadow: `0 0 0 5px ${GREEN}, 0 0 60px rgba(0,0,0,0.8)`,
			}}>
				{line('АТРОКС', RED, 140, 0)}
				{line('vs', '#fff', 90, 10)}
				{line('МОРДЕКАЙЗЕР', GREEN, 108, 18)}
			</div>
		</AbsoluteFill>
	);
};

const EndCard: React.FC<{f: number}> = ({f}) => {
	if (f < 0) return null;
	const s = interpolate(f, [0, 7], [0.6, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2))});
	return (
		<div style={{position: 'absolute', left: 0, width: 1080, top: 250, display: 'flex', justifyContent: 'center'}}>
			<div style={{
				...FRAME, transform: `scale(${s})`, opacity: Math.min(1, f / 4), padding: '26px 48px', borderRadius: 28, textAlign: 'center',
				border: `5px solid ${RED}`, boxShadow: `0 0 0 5px ${GREEN}, 0 0 50px rgba(60,240,140,0.45)`, fontSize: 66, lineHeight: 1.1, letterSpacing: -1,
			}}>
				КОГО СТОЛКНУТЬ
				<br />
				СЛЕДУЮЩИМ? <span style={{display: 'inline-block', transform: `translateY(${Math.sin(f / 3) * 6}px)`}}>👇</span>
			</div>
		</div>
	);
};

export const AatroxVsMordekaiser: React.FC = () => {
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
					<OffthreadVideo src={staticFile('bg.mp4')} muted />
				</AbsoluteFill>
			</AbsoluteFill>
			{flash !== undefined && <AbsoluteFill style={{background: '#fff', opacity: flash === 0 ? 0.85 : 0.4}} />}
			<HookPlate f={f} />
			<Captions chunks={CHUNKS} from={HOOK_END} />
			<EndCard f={f - CARD_FROM} />
		</AbsoluteFill>
	);
};
