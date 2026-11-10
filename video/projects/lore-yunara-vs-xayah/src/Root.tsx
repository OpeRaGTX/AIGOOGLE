import React from 'react';
import {Composition} from 'remotion';
import {FPS, P1_FRAMES, Part1} from './Part1';
import {P2_FRAMES, Part2} from './Part2';

export const RemotionRoot: React.FC = () => (
	<>
		<Composition id="yunara-vs-xayah-p1" component={Part1} durationInFrames={P1_FRAMES} fps={FPS} width={1080} height={1920} />
		<Composition id="yunara-vs-xayah-p2" component={Part2} durationInFrames={P2_FRAMES} fps={FPS} width={1080} height={1920} />
	</>
);
