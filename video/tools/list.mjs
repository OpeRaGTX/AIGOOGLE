// npm run list — every project with its status: brief filled in, materials in inbox/, finished renders in out/
import {existsSync, readdirSync, readFileSync, statSync} from 'fs';
import {join} from 'path';
import {PROJECTS, listProjects} from './lib.mjs';

const files = (d) => (existsSync(d) ? readdirSync(d).filter((f) => statSync(join(d, f)).isFile()) : []);
const rows = listProjects().map((slug) => {
	const dir = join(PROJECTS, slug);
	const brief = existsSync(join(dir, 'brief.md')) ? readFileSync(join(dir, 'brief.md'), 'utf8') : '';
	const status = (existsSync(join(dir, 'NOTES.md')) ? readFileSync(join(dir, 'NOTES.md'), 'utf8') : '').match(/^Статус:\s*(.+)$/m)?.[1] ?? '—';
	const out = files(join(dir, 'out')).filter((f) => f.endsWith('.mp4'));
	return {
		проект: slug,
		бриф: !brief ? 'нет' : /\[заполни\]/.test(brief) ? 'не заполнен' : 'есть',
		материалы: files(join(dir, 'inbox')).length,
		рендеры: out.length ? out.join(', ') : '—',
		статус: status,
	};
});
rows.length ? console.table(rows) : console.log('Проектов пока нет: npm run new -- "название"');
