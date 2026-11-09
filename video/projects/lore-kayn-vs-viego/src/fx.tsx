import React from 'react';
import {AbsoluteFill, Easing, interpolate, random} from 'remotion';

// Shared FX for the lore video: camera over an oversized still, pixel particles, fog, flashes, shake.
export const FPS = 30;
export const IW = 1242; // public/*.png = cover 1080x1920 x1.15
export const IH = 2208;

export type Cam = {z: number; cx: number; cy: number}; // zoom over the oversized still, focus point (0..1 of image)
export type Key = [number, Cam, ((t: number) => number)?]; // [local seconds, camera, easing into this key]

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));
const SMOOTH = Easing.inOut(Easing.cubic);

export const camAt = (keys: Key[], t: number): Cam => {
	if (t <= keys[0][0]) return keys[0][1];
	for (let i = 1; i < keys.length; i++) {
		const [t1, c1, ease] = keys[i];
		const [t0, c0] = keys[i - 1];
		if (t <= t1) {
			const p = (ease ?? SMOOTH)(clamp((t - t0) / Math.max(1e-6, t1 - t0), 0, 1));
			return {z: c0.z + (c1.z - c0.z) * p, cx: c0.cx + (c1.cx - c0.cx) * p, cy: c0.cy + (c1.cy - c0.cy) * p};
		}
	}
	return keys[keys.length - 1][1];
};

// image placement in the 1080x1920 frame for a camera; keeps a 4 % margin so shader parallax/flow never hits the edge
const M = 0.04;
export const place = (c: Cam) => {
	const W = IW * c.z;
	const H = IH * c.z;
	return {W, H, x: clamp(540 - c.cx * W, 1080 - W * (1 - M), -W * M), y: clamp(960 - c.cy * H, 1920 - H * (1 - M), -H * M)};
};
export const toScreen = (c: Cam, px: number, py: number) => {
	const p = place(c);
	return {x: p.x + px * p.W, y: p.y + py * p.H};
};

// decaying random shake: sum of hits [frame, amplitude px]
export const shakeAt = (frame: number, hits: [number, number][], len = 8) => {
	let x = 0;
	let y = 0;
	for (const [f, amp] of hits) {
		const d = frame - f;
		if (d < 0 || d >= len) continue;
		const a = amp * (1 - d / len);
		x += (random(`sx${f}-${d}`) * 2 - 1) * a;
		y += (random(`sy${f}-${d}`) * 2 - 1) * a;
	}
	return {x, y};
};

// Flash overlay: white / colored glow or inversion for `len` frames from `at`
export const Flash: React.FC<{frame: number; at: number[]; color?: string; len?: number; invertFirst?: boolean; peak?: number}> = ({
	frame, at, color = '#ffffff', len = 2, invertFirst = false, peak = 0.9,
}) => {
	const hit = at.find((f) => frame >= f && frame < f + len);
	if (hit === undefined) return null;
	const d = frame - hit;
	if (invertFirst && d === 0) return <AbsoluteFill style={{background: '#fff', mixBlendMode: 'difference'}} />;
	return <AbsoluteFill style={{background: color, opacity: peak * (1 - d / (len + 1)), mixBlendMode: color === '#ffffff' ? 'normal' : 'screen'}} />;
};

// Rising pixel embers / sparks (squares snapped to a 3px grid, glow via box-shadow)
export const Rising: React.FC<{t: number; seed: string; n: number; colors: string[]; minY?: number; speed?: [number, number]; size?: number[]; opacity?: number}> = ({
	t, seed, n, colors, minY = 900, speed = [90, 220], size = [6, 9, 12], opacity = 0.85,
}) => (
	<AbsoluteFill style={{pointerEvents: 'none'}}>
		{Array.from({length: n}, (_, i) => {
			const r = (k: string) => random(`${seed}-${i}-${k}`);
			const v = speed[0] + r('v') * (speed[1] - speed[0]);
			const life = (1960 - minY) / v;
			const age = (t + r('ph') * life) % life;
			const x = r('x') * 1080 + Math.sin(t * (1 + r('f') * 2) + r('p') * 6.28) * 18;
			const y = 1960 - age * v;
			const s = size[Math.floor(r('s') * size.length)];
			const c = colors[Math.floor(r('c') * colors.length)];
			const fade = Math.min(1, age / 0.4) * Math.min(1, (life - age) / (life * 0.45));
			const flick = 0.7 + 0.3 * Math.sin(t * 13 + i);
			return (
				<div key={i} style={{
					position: 'absolute', left: Math.round(x / 3) * 3, top: Math.round(y / 3) * 3, width: s, height: s,
					background: c, boxShadow: `0 0 ${s * 1.6}px ${c}`, opacity: opacity * fade * flick,
				}} />
			);
		})}
	</AbsoluteFill>
);

// Soft fog blobs (radial gradients, screen blend). creep 0..1 pulls the fog in from the edges.
export const Fog: React.FC<{t: number; seed: string; n?: number; color?: string; alpha?: number; creep?: number; band?: [number, number]}> = ({
	t, seed, n = 7, color = '46,242,160', alpha = 0.16, creep, band = [0.55, 1.05],
}) => (
	<AbsoluteFill style={{mixBlendMode: 'screen', pointerEvents: 'none'}}>
		{Array.from({length: n}, (_, i) => {
			const r = (k: string) => random(`${seed}-fog-${i}-${k}`);
			const w = 700 + r('w') * 700;
			const h = w * (0.45 + r('h') * 0.3);
			const side = r('side') < 0.5 ? -1 : 1;
			const edge = side < 0 ? -w * 0.55 : 1080 - w * 0.45;
			const home = r('x') * 1080 - w / 2;
			const x0 = creep === undefined ? home : edge + (home - edge) * creep;
			const x = x0 + Math.sin(t * (0.15 + r('f') * 0.2) + r('p') * 6.28) * 90 + t * (r('d') - 0.5) * 40;
			const y = 1920 * (band[0] + r('y') * (band[1] - band[0])) - h / 2 + Math.sin(t * 0.3 + i) * 30;
			const a = alpha * (0.6 + 0.4 * Math.sin(t * 0.5 + r('a') * 6.28)) * (creep === undefined ? 1 : 0.35 + 0.65 * creep);
			return (
				<div key={i} style={{
					position: 'absolute', left: x, top: y, width: w, height: h, borderRadius: '50%',
					background: `radial-gradient(closest-side, rgba(${color},${a}), rgba(${color},${a * 0.45}) 45%, rgba(${color},0))`,
				}} />
			);
		})}
	</AbsoluteFill>
);

// Particles pulled into a point (scene 8: the fog reassembles the king)
export const Converge: React.FC<{t: number; seed: string; n: number; tx: number; ty: number; dur: number; color?: string}> = ({
	t, seed, n, tx, ty, dur, color = '#2EF2A0',
}) => (
	<AbsoluteFill style={{pointerEvents: 'none'}}>
		{Array.from({length: n}, (_, i) => {
			const r = (k: string) => random(`${seed}-cv-${i}-${k}`);
			const life = 1.1 + r('l') * 0.9;
			const start = r('st') * Math.max(0.1, dur - life * 0.6);
			const p = (t - start) / life;
			if (p < 0 || p > 1) return null;
			const a = r('a') * Math.PI * 2;
			const d = (480 + r('d') * 520) * (1 - Easing.in(Easing.quad)(p));
			const s = [6, 9, 12][Math.floor(r('s') * 3)];
			const op = Math.min(1, p / 0.15) * Math.min(1, (1 - p) / 0.2);
			return (
				<div key={i} style={{
					position: 'absolute', left: Math.round((tx + Math.cos(a) * d) / 3) * 3, top: Math.round((ty + Math.sin(a) * d * 1.3) / 3) * 3,
					width: s, height: s, background: color, boxShadow: `0 0 ${s * 2}px ${color}`, opacity: 0.9 * op,
				}} />
			);
		})}
	</AbsoluteFill>
);

// Spark bursts at given local seconds from a screen point (with gravity)
export const Sparks: React.FC<{t: number; seed: string; at: number[]; x: number; y: number; n?: number}> = ({t, seed, at, x, y, n = 22}) => (
	<AbsoluteFill style={{pointerEvents: 'none'}}>
		{at.flatMap((t0, b) => {
			const age = t - t0;
			if (age < 0 || age > 0.7) return [];
			return Array.from({length: n}, (_, i) => {
				const r = (k: string) => random(`${seed}-sp-${b}-${i}-${k}`);
				const a = r('a') * Math.PI * 2;
				const v = 350 + r('v') * 800;
				const px = x + Math.cos(a) * v * age;
				const py = y + Math.sin(a) * v * age + 900 * age * age;
				const s = [6, 9][Math.floor(r('s') * 2)];
				const c = ['#ffffff', '#ffd27a', '#ff6a3a', '#ff3b3b'][Math.floor(r('c') * 4)];
				return (
					<div key={`${b}-${i}`} style={{
						position: 'absolute', left: Math.round(px / 3) * 3, top: Math.round(py / 3) * 3, width: s, height: s,
						background: c, boxShadow: `0 0 ${s * 2}px ${c}`, opacity: interpolate(age, [0, 0.5, 0.7], [1, 0.8, 0]),
					}} />
				);
			});
		})}
	</AbsoluteFill>
);

// Radial glow at a screen point (screen blend)
export const Glow: React.FC<{x: number; y: number; r: number; color: string; opacity: number; blend?: 'screen' | 'normal' | 'multiply'}> = ({
	x, y, r, color, opacity, blend = 'screen',
}) => (
	<div style={{
		position: 'absolute', left: x - r, top: y - r, width: r * 2, height: r * 2, borderRadius: '50%', opacity,
		background: `radial-gradient(closest-side, ${color}, ${color.replace(/[\d.]+\)$/, '0)')})`, mixBlendMode: blend,
	}} />
);
