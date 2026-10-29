// npm run render -- <project> [--comp <id>] [--out <file.mp4>] [any remotion render flags]
// -> projects/<project>/out/<project>.mp4, using that project's public/ as staticFile() root.
import {join, relative} from 'path';
import {STUDIO, compositions, die, npx, project} from './lib.mjs';

const args = process.argv.slice(2);
const take = (flag) => {
	const i = args.indexOf(flag);
	return i < 0 ? undefined : args.splice(i, 2)[1];
};
const {slug, dir} = project(args.shift());
const comps = compositions(dir);
const comp = take('--comp') ?? (comps.length === 1 ? comps[0] : die(`${slug} has ${comps.length} compositions (${comps.join(', ')}): pass --comp`));
const out = take('--out') ?? join('out', `${comps.length > 1 ? comp : slug}.mp4`);
const rel = (p) => relative(STUDIO, join(dir, p)).replaceAll('\\', '/');

// Remotion's frame extractor occasionally fails under load ("No frame found", delayRender timeout); a rerun passes
for (let t = 1; t <= 3; t++) {
	console.log(`== render ${slug}/${comp} (try ${t})`);
	const code = npx(['remotion', 'render', rel('src/index.ts'), comp, rel(out), `--public-dir=${rel('public')}`, ...args]);
	if (code === 0) {
		console.log(`done: ${join(dir, out)}`);
		process.exit(0);
	}
}
die(`render of ${slug} failed 3 times`);
