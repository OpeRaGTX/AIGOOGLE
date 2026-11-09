import React from 'react';
import {Composition} from 'remotion';
import {DURATION, FPS, Lore} from './Lore';

export const RemotionRoot: React.FC = () => (
	<Composition id="lore-kayn-vs-viego" component={Lore} durationInFrames={DURATION} fps={FPS} width={1080} height={1920} />
);
