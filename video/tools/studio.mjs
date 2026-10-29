// npm run studio -- <project> — live preview of one project in the browser (its public/ is the staticFile root)
import {join, relative} from 'path';
import {STUDIO, npx, project} from './lib.mjs';

const args = process.argv.slice(2);
const {dir} = project(args.shift());
const rel = (p) => relative(STUDIO, join(dir, p)).replaceAll('\\', '/');
process.exit(npx(['remotion', 'studio', rel('src/index.ts'), `--public-dir=${rel('public')}`, ...args]));
