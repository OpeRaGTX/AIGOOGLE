import React from 'react';
import {Composition} from 'remotion';
import {DURATION, FPS, Video} from './Video';

export const RemotionRoot: React.FC = () => (
	<Composition id="order-tinkov-edit" component={Video} durationInFrames={DURATION} fps={FPS} width={1080} height={1920} />
);
