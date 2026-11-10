import React from 'react';
import {Composition} from 'remotion';
import {FPS, P1_FRAMES, Part1} from './Part1';

export const RemotionRoot: React.FC = () => (
	<Composition id="yunara-vs-xayah-p1" component={Part1} durationInFrames={P1_FRAMES} fps={FPS} width={1080} height={1920} />
);
