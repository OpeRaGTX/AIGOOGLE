import React from 'react';
import {AbsoluteFill, Audio, OffthreadVideo, interpolate, random, staticFile, useCurrentFrame} from 'remotion';
import meta from './meta.json';

// Timeline, crop keys and effect frames come from meta.json (pipeline/prep.py bakes it from edl.json together with
// public/gameplay.mp4, where cuts, speed, grade and RGB shift are already in the pixels). Here: the 1080x1200 window,
// the crop that follows Samira, zoom punches, shake, flashes and the one caption.
export const FPS = meta.fps;
export const DURATION = meta.frames;

const WIN = {y: 360, w: 1080, h: 1200}; // gameplay window inside 1080x1920
const PUNCH = 0.12; // zoom punch 1.0 -> 1.12
const SHAKE = {px: 22, frames: 16};
const FLASH = [0.85, 0.45, 0.15];

type Key = {t: number; cx: number; cy: number; h?: number};
const keys = meta.keys as Key[];

// source time shown at a timeline frame
const srcTime = (f: number) => {
	const s = [...meta.segments].reverse().find((x) => f >= x.f0) ?? meta.segments[0];
	return s.src + ((f - s.f0) / FPS) * s.speed;
};

// crop centre/height at a source time: smoothstep between keys
const cropAt = (t: number) => {
	const i = keys.findIndex((k) => k.t > t);
	const a = keys[i < 0 ? keys.length - 1 : Math.max(0, i - 1)];
	const b = keys[i < 0 ? keys.length - 1 : i];
	const u = b.t > a.t ? Math.min(1, Math.max(0, (t - a.t) / (b.t - a.t))) : 0;
	const e = u * u * (3 - 2 * u);
	const mix = (x: number, y: number) => x + (y - x) * e;
	return {cx: mix(a.cx, b.cx), cy: mix(a.cy, b.cy), h: mix(a.h ?? meta.window.h, b.h ?? meta.window.h)};
};

// frames since the latest mark (Infinity if none yet)
const since = (marks: number[], f: number) => {
	const past = marks.filter((m) => m <= f);
	return past.length ? f - past[past.length - 1] : Infinity;
};

export const Video: React.FC = () => {
	const frame = useCurrentFrame();
	const {src, baked, fx} = meta;

	// zoom punch: 2 frames in, ease back over ~18
	const pd = since(fx.punch, frame);
	const punch = pd === 0 ? 0.55 : pd < 20 ? Math.exp(-(pd - 1) / 4.5) : 0;
	const zoom = 1 + PUNCH * punch;

	// shake: decaying offsets, applied to the crop position so no edge ever shows
	const sd = since(fx.shake, frame);
	const amp = sd < SHAKE.frames ? SHAKE.px * (1 - sd / SHAKE.frames) ** 2 : 0;
	const shakeX = amp * (random(`x${frame}`) * 2 - 1);
	const shakeY = amp * (random(`y${frame}`) * 2 - 1);

	const fd = since(fx.flash, frame);
	const flash = fd < FLASH.length ? FLASH[fd] : 0;

	// crop window in source px -> video box in window px
	const c = cropAt(srcTime(frame));
	const ch = c.h * src.h;
	const cw = ch * meta.window.aspect;
	const s = WIN.h / ch;
	const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));
	const wx = clamp(c.cx * src.w - cw / 2 + shakeX / s, baked.x, baked.x + baked.w - cw);
	const wy = clamp(c.cy * src.h - ch / 2 + shakeY / s, baked.y, baked.y + baked.h - ch);

	// caption: pop-in over 6 frames with a small overshoot
	const pop = interpolate(frame, [0, 4, 6], [0.5, 1.1, 1], {extrapolateRight: 'clamp'});
	const popOpacity = interpolate(frame, [0, 3], [0, 1], {extrapolateRight: 'clamp'});
	const [word, emoji] = [meta.text.replace(/\s*\p{Extended_Pictographic}.*$/u, ''), meta.text.match(/\p{Extended_Pictographic}.*$/u)?.[0] ?? ''];

	return (
		<AbsoluteFill style={{background: 'black'}}>
			<Audio src={staticFile('track.wav')} />
			<div style={{position: 'absolute', left: 0, top: WIN.y, width: WIN.w, height: WIN.h, overflow: 'hidden'}}>
				<div style={{position: 'absolute', inset: 0, transform: `scale(${zoom})`, transformOrigin: '50% 50%'}}>
					<OffthreadVideo
						muted
						src={staticFile('gameplay.mp4')}
						style={{position: 'absolute', left: -(wx - baked.x) * s, top: -(wy - baked.y) * s, width: baked.w * s, height: baked.h * s, maxWidth: 'none'}}
					/>
				</div>
				<AbsoluteFill style={{background: 'radial-gradient(ellipse at center, transparent 62%, rgba(0,0,0,0.38) 100%)'}} />
				{flash > 0 ? <AbsoluteFill style={{background: 'white', opacity: flash}} /> : null}
			</div>
			<div
				style={{
					position: 'absolute',
					left: 0,
					width: WIN.w,
					top: WIN.y + (WIN.h * 5) / 6, // centre of the window's lower third
					transform: `translateY(-50%) scale(${pop})`,
					opacity: popOpacity,
					textAlign: 'center',
					fontFamily: "Inter, 'Segoe UI', sans-serif",
					fontWeight: 900,
					fontSize: 96,
					lineHeight: 1,
					letterSpacing: -1,
					color: 'white',
					whiteSpace: 'nowrap',
				}}
			>
				<span style={{WebkitTextStroke: '12px black', paintOrder: 'stroke fill', textShadow: '0 6px 0 rgba(0,0,0,0.6), 0 12px 28px rgba(0,0,0,0.75)'}}>{word}</span>
				<span style={{fontFamily: "'Apple Color Emoji', 'Segoe UI Emoji', 'Noto Color Emoji'", fontWeight: 400, marginLeft: 18, filter: 'drop-shadow(0 6px 10px rgba(0,0,0,0.7))'}}>
					{emoji}
				</span>
			</div>
		</AbsoluteFill>
	);
};
