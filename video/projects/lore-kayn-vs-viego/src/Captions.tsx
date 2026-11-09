import React from 'react';
import {AbsoluteFill, Easing, interpolate, useCurrentFrame} from 'remotion';
import tl from './timeline.json';
import {FPS} from './fx';

// Word captions: 1–2 words per chunk, centre of frame, white 900 with 6 px black outline, 4-frame pop-in.
type Word = {w: string; p: string; s: number; e: number};
type Chunk = {words: Word[]; from: number; to: number};

const RED = /^(каин|рааст|даркин)/i;
const GREEN = /^(виего|туман|корол)/i;
const colorOf = (w: string) => (RED.test(w) ? '#FF3B3B' : GREEN.test(w) ? '#2EF2A0' : '#ffffff');
const len = (w: Word) => w.w.length;

const buildChunks = (): Chunk[] => {
	const raw: Word[][] = [];
	for (const sc of tl.scenes) {
		const ws = sc.words as Word[];
		for (let i = 0; i < ws.length; i++) {
			const a = ws[i];
			const b = ws[i + 1];
			// pair with the next word unless a phrase ends here; short function words always lean on the next word
			if (b && !a.p.trim() && (len(a) <= 3 || len(a) + len(b) <= 11) && len(a) + len(b) <= 15) {
				raw.push([a, b]);
				i++;
			} else raw.push([a]);
		}
	}
	return raw.map((words, i) => {
		const from = Math.max(0, Math.round(words[0].s * FPS) - 1);
		const next = raw[i + 1] ? Math.round(raw[i + 1][0].s * FPS) - 1 : Infinity;
		const to = Math.min(next, Math.round((words[words.length - 1].e + 0.35) * FPS));
		return {words, from, to};
	});
};
const CHUNKS = buildChunks();

export const Captions: React.FC = () => {
	const frame = useCurrentFrame();
	const c = CHUNKS.find((k) => frame >= k.from && frame < k.to);
	if (!c) return null;
	const d = frame - c.from;
	const scale = interpolate(d, [0, 4], [0.55, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2.2))});
	const opacity = interpolate(d, [0, 2], [0, 1], {extrapolateRight: 'clamp'});
	const chars = c.words.reduce((n, w) => n + w.w.length, 0) + c.words.length - 1;
	const fontSize = Math.min(88, Math.floor(800 / (0.66 * chars)));
	return (
		<AbsoluteFill style={{justifyContent: 'center', alignItems: 'center'}}>
			<div style={{
				width: 800, textAlign: 'center', transform: `scale(${scale})`, opacity,
				fontFamily: 'Montserrat', fontWeight: 900, fontSize, lineHeight: 1.05, letterSpacing: -0.5,
				WebkitTextStroke: '12px #000', paintOrder: 'stroke fill', textShadow: '0 8px 0 rgba(0,0,0,0.55)',
			}}>
				{c.words.map((w, i) => (
					<span key={i} style={{color: colorOf(w.w)}}>{(i ? ' ' : '') + w.w}</span>
				))}
			</div>
		</AbsoluteFill>
	);
};
