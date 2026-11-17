import React from 'react';
import {AbsoluteFill, Easing, interpolate, useCurrentFrame} from 'remotion';

// Word captions copied from lore-shaco-vs-jhin (style of lore-yunara-vs-xayah): UPPERCASE Montserrat 900, no outline,
// soft shadow, 4-frame pop-in, 1–2 words per chunk, a bit lower than centre. Colours: Aatrox crimson, Mordekaiser
// ghost green, «Царство смерти» green, «Крылья, кровь, удар» crimson; coloured words get a dark stroke (red on red frames).
export type Word = {w: string; p: string; s: number; e: number; ph: number; q: boolean};
type Chunk = {words: Word[]; from: number; to: number};

export const RED = '#E0303A';
export const GREEN = '#3CF08C';
const AATROX = /^атрокс/i;
const MORDE = /^мордекайз/i;
const REALM = (w: Word) => (w.w === 'Царство' || (w.w === 'смерти' && w.s > 74 && w.s < 76));
const STRIKE = (w: Word) => /^(крылья|кровь|удар)$/i.test(w.w) && w.s > 86 && w.s < 89;
const colorOf = (w: Word): string => (AATROX.test(w.w) || STRIKE(w) ? RED : MORDE.test(w.w) || REALM(w) ? GREEN : '#ffffff');

export const CAPTION_Y = 1150; // centre of the caption block (frame centre is 960); TikTok UI starts ~1500
const MAX_W = 840;
const BASE = 104;

export const buildChunks = (words: Word[], fps: number): Chunk[] => {
	const raw: Word[][] = [];
	for (let i = 0; i < words.length; i++) {
		const a = words[i];
		const b = words[i + 1];
		const len = (w: Word) => w.w.length;
		if (b && b.ph === a.ph && !a.p.trim() && (len(a) <= 3 || len(a) + len(b) <= 11) && len(a) + len(b) <= 15) {
			raw.push([a, b]);
			i++;
		} else raw.push([a]);
	}
	return raw.map((ws, i) => {
		const from = Math.max(0, Math.round(ws[0].s * fps) - 1);
		const next = raw[i + 1] ? Math.round(raw[i + 1][0].s * fps) - 1 : Infinity;
		return {words: ws, from, to: Math.min(next, Math.round((ws[ws.length - 1].e + 0.35) * fps))};
	});
};

let canvas: CanvasRenderingContext2D | null = null;
const measure = (text: string, italic: boolean) => {
	canvas ??= document.createElement('canvas').getContext('2d');
	if (!canvas) return text.length * 75;
	canvas.font = `${italic ? 'italic ' : ''}900 100px Montserrat`;
	return canvas.measureText(text).width;
};

export const Captions: React.FC<{chunks: Chunk[]; from?: number}> = ({chunks, from = 0}) => {
	const frame = useCurrentFrame();
	const c = frame < from ? undefined : chunks.find((k) => frame >= k.from && frame < k.to);
	if (!c) return null;
	const d = frame - c.from;
	const scale = interpolate(d, [0, 4], [0.55, 1], {extrapolateRight: 'clamp', easing: Easing.out(Easing.back(2.2))});
	const opacity = interpolate(d, [0, 2], [0, 1], {extrapolateRight: 'clamp'});
	const italic = c.words.some((w) => w.q);
	const up = c.words.map((w) => w.w.toUpperCase());
	const oneLine = measure(up.join(' '), italic) * (BASE / 100) <= MAX_W;
	const widest = oneLine ? measure(up.join(' '), italic) : Math.max(...up.map((u) => measure(u, italic)));
	const fontSize = Math.min(BASE, Math.floor((MAX_W * 100) / widest));
	return (
		<AbsoluteFill>
			<div style={{position: 'absolute', left: 0, width: 1080, top: CAPTION_Y, display: 'flex', justifyContent: 'center'}}>
				<div style={{
					width: MAX_W, textAlign: 'center', transform: `translateY(-50%) scale(${scale})`, opacity,
					fontFamily: 'Montserrat', fontWeight: 900, fontStyle: italic ? 'italic' : 'normal', fontSize, lineHeight: 1.02,
					letterSpacing: -1, textShadow: '0 4px 6px rgba(0,0,0,0.6), 0 8px 26px rgba(0,0,0,0.9)',
				}}>
					{c.words.map((w, i) => (
						<React.Fragment key={i}>
							{i > 0 && (oneLine ? ' ' : <br />)}
							<span style={colorOf(w) === '#ffffff' ? {color: '#ffffff'} : {color: colorOf(w), WebkitTextStroke: '14px rgba(8,4,6,0.9)', paintOrder: 'stroke fill'}}>{up[i]}</span>
						</React.Fragment>
					))}
				</div>
			</div>
		</AbsoluteFill>
	);
};
