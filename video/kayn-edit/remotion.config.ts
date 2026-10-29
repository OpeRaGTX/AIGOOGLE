import {Config} from '@remotion/cli/config';
import {existsSync} from 'fs';
import {cpus} from 'os';

// cloud sandbox ships its own headless shell; elsewhere Remotion downloads one on first render
const SANDBOX_SHELL = '/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell';
if (existsSync(SANDBOX_SHELL)) {
	Config.setBrowserExecutable(SANDBOX_SHELL);
}
if (process.platform === 'win32') {
	Config.setChromiumOpenGlRenderer('angle'); // GPU compositing instead of software
}
Config.setConcurrency(Math.max(1, cpus().length - 2));
Config.setX264Preset('veryfast');
