// node pipeline/bash.mjs <script.sh> [args] — run a pipeline .sh through Git Bash on Windows (bash elsewhere),
// so `npm run build` works from cmd/PowerShell without bash on PATH.
import {spawnSync} from 'child_process';
import {existsSync} from 'fs';
import {dirname, join} from 'path';

function gitBash() {
	if (process.platform !== 'win32') return 'bash';
	const cands = [
		process.env.GIT_BASH,
		'C:\\Program Files\\Git\\bin\\bash.exe',
		'C:\\Program Files (x86)\\Git\\bin\\bash.exe',
	];
	const git = spawnSync('where', ['git'], {encoding: 'utf8'}).stdout?.split(/\r?\n/)[0];
	if (git) cands.push(join(dirname(dirname(git)), 'bin', 'bash.exe'));
	const hit = cands.find((p) => p && existsSync(p));
	if (!hit) throw new Error('Git Bash not found: install Git for Windows or set GIT_BASH');
	return hit;
}

const [script, ...args] = process.argv.slice(2);
const r = spawnSync(gitBash(), [script, ...args], {stdio: 'inherit'});
process.exit(r.status ?? 1);
