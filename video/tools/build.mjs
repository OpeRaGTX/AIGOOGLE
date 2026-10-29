// npm run build -- <project> [args] — the project's own pipeline (pipeline/build.sh, run through Git Bash on Windows)
// when it has one (3D renders, asset prep...), otherwise just the Remotion render.
import {existsSync} from 'fs';
import {join} from 'path';
import {gitBash, run, project} from './lib.mjs';

const args = process.argv.slice(2);
const {slug, dir} = project(args.shift());
const sh = join(dir, 'pipeline', 'build.sh');
process.exit(existsSync(sh) ? run(gitBash(), [sh, ...args]) : run(process.execPath, [join('tools', 'render.mjs'), slug, ...args]));
