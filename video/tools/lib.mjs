// Shared helpers for the studio CLI (tools/*.mjs). Everything runs from video/ with the shared node_modules.
import {spawnSync} from 'child_process';
import {existsSync, readdirSync, readFileSync} from 'fs';
import {dirname, join, resolve} from 'path';
import {fileURLToPath} from 'url';

export const STUDIO = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export const PROJECTS = join(STUDIO, 'projects');
export const TEMPLATE = join(STUDIO, '_template');

export const die = (msg) => {
	console.error(msg);
	process.exit(1);
};

export const listProjects = () =>
	existsSync(PROJECTS) ? readdirSync(PROJECTS, {withFileTypes: true}).filter((d) => d.isDirectory()).map((d) => d.name) : [];

// exact slug, else a unique partial match ("kayn" -> "kayn-edit")
export function project(arg) {
	if (!arg) die(`which project? one of: ${listProjects().join(', ')}`);
	const all = listProjects();
	const hit = all.includes(arg) ? [arg] : all.filter((p) => p.includes(arg.toLowerCase()));
	if (hit.length !== 1) die(hit.length ? `"${arg}" is ambiguous: ${hit.join(', ')}` : `no project "${arg}". Have: ${all.join(', ')}`);
	return {slug: hit[0], dir: join(PROJECTS, hit[0])};
}

// composition ids declared in the project's Root.tsx
export const compositions = (dir) =>
	[...readFileSync(join(dir, 'src', 'Root.tsx'), 'utf8').matchAll(/<Composition[^>]*\bid=["']([^"']+)["']/g)].map((m) => m[1]);

export function gitBash() {
	if (process.platform !== 'win32') return 'bash';
	const cands = [process.env.GIT_BASH, 'C:\\Program Files\\Git\\bin\\bash.exe', 'C:\\Program Files (x86)\\Git\\bin\\bash.exe'];
	const git = spawnSync('where', ['git'], {encoding: 'utf8'}).stdout?.split(/\r?\n/)[0];
	if (git) cands.push(join(dirname(dirname(git)), 'bin', 'bash.exe'));
	const hit = cands.find((p) => p && existsSync(p));
	if (!hit) die('Git Bash not found: install Git for Windows or set GIT_BASH');
	return hit;
}

export function run(cmd, args, opts = {}) {
	const r = spawnSync(cmd, args, {stdio: 'inherit', cwd: STUDIO, shell: false, ...opts});
	return r.status ?? 1;
}

// npx resolves to npx.cmd on Windows, which spawnSync can only start through a shell
export const npx = (args, opts) => run(process.platform === 'win32' ? 'npx.cmd' : 'npx', args, {shell: process.platform === 'win32', ...opts});
