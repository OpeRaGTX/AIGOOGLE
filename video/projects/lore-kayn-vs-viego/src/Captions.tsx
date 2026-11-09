import React from 'react';
import {AbsoluteFill, Easing, interpolate, useCurrentFrame} from 'remotion';
import tl from './timeline.json';
import {FPS} from './fx';

// Word captions: 1–2 words per chunk, centre of frame, UPPERCASE Montserrat 900, no outline, soft dark shadow, 4-frame pop-in.
type Word = {w: string; p: string; s: number; e: number};
type Chunk = {words: Word[]; from: number; to: number};

const RED = /^(каин|рааст|даркин)/i; // reference style: key words in color, the rest white
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

// width of uppercase text in Montserrat 900 at 100 px (fonts are loaded before the first frame renders)
let canvas: CanvasRenderingContext2D | null = null;
const measure = (text: string) => {
	canvas ??= document.createElement('canvas').getContext('2d');
	if (!canvas) return text.length * 75;
	canvas.font = '900 100px Montserrat';
	return canvas.measureText(text).width;
};
const MAX_W = 840;
const BASE = 104;

export const Captions: React.FC = () => {
	const frame = useCurrentFrame();
	const c = CHUNKS.find((k) => frame >= k.from && frame < k.to);
	if (!c) return null;
	const d = frame - c.from;
	const scale = interpolate(d, [0, 4], [0.55, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2.2))});
	const opacity = interpolate(d, [0, 2], [0, 1], {extrapolateRight: 'clamp'});
	const words = c.words.map((w) => w.w.toUpperCase());
	// one line if it fits at the base size, otherwise one word per line; shrink only words wider than the safe box
	const oneLine = measure(words.join(' ')) * (BASE / 100) <= MAX_W;
	const widest = oneLine ? measure(words.join(' ')) : Math.max(...words.map(measure));
	const fontSize = Math.min(BASE, Math.floor((MAX_W * 100) / widest));
	return (
		<AbsoluteFill style={{justifyContent: 'center', alignItems: 'center'}}>
			<div style={{
				width: MAX_W, textAlign: 'center', transform: `scale(${scale})`, opacity,
				fontFamily: 'Montserrat', fontWeight: 900, fontSize, lineHeight: 1.02, letterSpacing: -1,
				textShadow: '0 4px 6px rgba(0,0,0,0.55), 0 8px 26px rgba(0,0,0,0.85)',
			}}>
				{c.words.map((w, i) => (
					<React.Fragment key={i}>
						{i > 0 && (oneLine ? ' ' : <br />)}
						<span style={{color: colorOf(w.w)}}>{words[i]}</span>
					</React.Fragment>
				))}
			</div>
		</AbsoluteFill>
	);
};
