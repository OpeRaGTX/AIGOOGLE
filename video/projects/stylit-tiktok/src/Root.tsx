import React from 'react';
import {Composition} from 'remotion';
import {StylitTikTok} from './Video';

export const RemotionRoot: React.FC = () => (
	<Composition id="StylitTikTok" component={StylitTikTok} durationInFrames={480} fps={30} width={1080} height={1920} />
);
