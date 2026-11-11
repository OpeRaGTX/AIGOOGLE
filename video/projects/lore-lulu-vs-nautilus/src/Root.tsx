import React from 'react';
import {Composition} from 'remotion';
import {DURATION, FPS, Video} from './Video';

export const RemotionRoot: React.FC = () => (
	<Composition id="lore-lulu-vs-nautilus" component={Video} durationInFrames={DURATION} fps={FPS} width={1080} height={1920} />
);
