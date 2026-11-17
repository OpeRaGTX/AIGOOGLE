import {random} from 'remotion';

// Copied from lore-lulu-vs-nautilus/src/camera.ts (only the shake; this video has no stills).
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
