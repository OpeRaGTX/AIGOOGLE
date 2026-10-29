import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';

// Starter placeholder — replaced by the real edit once the brief is agreed. Assets: put them in ../public/ and
// load with staticFile('name.ext'); raw materials stay untouched in ../inbox/.
export const FPS = 60;
export const DURATION = 5 * FPS;
const TITLE = __TITLE_JS__;

export const Video: React.FC = () => {
	const frame = useCurrentFrame();
	const appear = interpolate(frame, [0, 20], [0, 1], {extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{background: '#0b0b0f', justifyContent: 'center', alignItems: 'center'}}>
			<div style={{opacity: appear, transform: `translateY(${(1 - appear) * 40}px)`, color: 'white', fontFamily: 'Inter, sans-serif', textAlign: 'center', padding: 80}}>
				<div style={{fontSize: 96, fontWeight: 800, lineHeight: 1.1}}>{TITLE}</div>
				<div style={{fontSize: 40, opacity: 0.5, marginTop: 24}}>черновик</div>
			</div>
		</AbsoluteFill>
	);
};
