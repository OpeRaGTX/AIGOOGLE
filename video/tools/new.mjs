// npm run new -- "Название ролика" [--open] — new project from _template/: brief.md, NOTES.md, starter Remotion code,
// empty inbox/ work/ public/ out/. --open shows inbox/ in Explorer so materials can be dropped in right away.
import {cpSync, existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync} from 'fs';
import {spawn} from 'child_process';
import {join} from 'path';
import {PROJECTS, TEMPLATE, die} from './lib.mjs';

const args = process.argv.slice(2);
const open = args.includes('--open');
const title = args.filter((a) => a !== '--open').join(' ').trim();
if (!title) die('usage: npm run new -- "Название ролика" [--open]');

const RU = {а: 'a', б: 'b', в: 'v', г: 'g', ґ: 'g', д: 'd', е: 'e', є: 'ye', ё: 'yo', ж: 'zh', з: 'z', и: 'i', і: 'i', ї: 'yi', й: 'y',
	к: 'k', л: 'l', м: 'm', н: 'n', о: 'o', п: 'p', р: 'r', с: 's', т: 't', у: 'u', ф: 'f', х: 'h', ц: 'ts', ч: 'ch', ш: 'sh', щ: 'sch',
	ъ: '', ы: 'y', ь: '', э: 'e', ю: 'yu', я: 'ya'};
const base = [...title.toLowerCase()].map((c) => RU[c] ?? c).join('').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40) || 'video';
let slug = base;
for (let i = 2; existsSync(join(PROJECTS, slug)); i++) slug = `${base}-${i}`;

const dir = join(PROJECTS, slug);
cpSync(TEMPLATE, dir, {recursive: true});
const vars = {__SLUG__: slug, __TITLE_JS__: JSON.stringify(title), __TITLE__: title, __DATE__: new Date().toISOString().slice(0, 10)};
const fill = (d) => {
	for (const f of readdirSync(d)) {
		const p = join(d, f);
		if (statSync(p).isDirectory()) fill(p);
		else writeFileSync(p, Object.entries(vars).reduce((s, [k, v]) => s.replaceAll(k, v), readFileSync(p, 'utf8')));
	}
};
fill(dir);
for (const sub of ['inbox', 'work', 'public', 'out']) mkdirSync(join(dir, sub), {recursive: true});

console.log(`Создан проект: ${dir}
  бриф:       ${join(dir, 'brief.md')}
  материалы:  ${join(dir, 'inbox')}
Дальше: заполни бриф (или опиши ролик в чате) и скажи Claude: «сделай ролик ${slug}».`);
if (open && process.platform === 'win32') spawn('explorer.exe', [join(dir, 'inbox')], {detached: true, stdio: 'ignore'}).unref();
