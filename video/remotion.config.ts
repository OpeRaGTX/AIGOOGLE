import {Config} from '@remotion/cli/config';
import {existsSync, readdirSync} from 'fs';
import {cpus} from 'os';

// cloud sandbox ships its own headless shell (version and folder layout change between images);
// elsewhere Remotion downloads one on first render
const PW = '/opt/pw-browsers';
const SANDBOX_SHELL = existsSync(PW)
	? readdirSync(PW)
			.filter((d) => d.startsWith('chromium_headless_shell-'))
			.flatMap((d) => [`${PW}/${d}/chrome-headless-shell-linux64/chrome-headless-shell`, `${PW}/${d}/chrome-linux/headless_shell`])
			.find((f) => existsSync(f))
	: undefined;
if (SANDBOX_SHELL) {
	Config.setBrowserExecutable(SANDBOX_SHELL);
}
if (process.platform === 'win32') {
	Config.setChromiumOpenGlRenderer('angle'); // GPU compositing instead of software
}
// more threads than ~8 starve the video frame extractor ("No frame found", delayRender timeouts)
Config.setConcurrency(Math.max(1, Math.min(8, cpus().length - 2)));
Config.setDelayRenderTimeoutInMilliseconds(120000);
Config.setX264Preset('veryfast');
