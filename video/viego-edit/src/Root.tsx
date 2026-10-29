import React from 'react';
import {Composition} from 'remotion';
import {DURATION, FPS, ViegoEdit} from './Edit';

export const RemotionRoot: React.FC = () => (
	<Composition id="ViegoEdit" component={ViegoEdit} durationInFrames={DURATION} fps={FPS} width={1080} height={1920} />
);
