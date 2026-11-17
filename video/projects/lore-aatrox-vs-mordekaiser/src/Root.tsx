import React from 'react';
import {Composition} from 'remotion';
import {AatroxVsMordekaiser, FPS, FRAMES} from './AatroxVsMordekaiser';

export const RemotionRoot: React.FC = () => (
	<Composition id="aatrox-vs-mordekaiser" component={AatroxVsMordekaiser} durationInFrames={FRAMES} fps={FPS} width={1080} height={1920} />
);
