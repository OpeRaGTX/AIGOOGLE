import {Easing, random} from 'remotion';

// Camera over an oversized still (copied from lore-kayn-vs-viego/src/fx.tsx).
export const IW = 1242; // public/<part>/img/*.png = cover 1080x1920 x1.15
export const IH = 2208;
export type Cam = {z: number; cx: number; cy: number}; // zoom, focus point (0..1 of image)
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

// image placement in the 1080x1920 frame; a 4 % margin keeps shader parallax/flow away from the edges
const M = 0.04;
export const place = (c: Cam) => {
	const W = IW * c.z;
	const H = IH * c.z;
	return {W, H, x: clamp(540 - c.cx * W, 1080 - W * (1 - M), -W * M), y: clamp(960 - c.cy * H, 1920 - H * (1 - M), -H * M)};
};

// decaying random shake: sum of hits [frame, amplitude px]
export const shakeAt = (frame: number, hits: [number, number][], len = 9) => {
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
