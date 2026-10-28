import React from 'react';
import {Composition} from 'remotion';
import {DURATION, FPS, KaynEdit} from './Edit';

export const RemotionRoot: React.FC = () => (
	<Composition id="KaynEdit" component={KaynEdit} durationInFrames={DURATION} fps={FPS} width={1080} height={1920} />
);
