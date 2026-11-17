import {continueRender, delayRender, staticFile} from 'remotion';

// Montserrat 800/900 (cyrillic + latin) for captions and cards, loaded once for every composition.
const fontHandle = delayRender('fonts');
Promise.all(
	(['cyrillic', 'latin'] as const).flatMap((sub) =>
		[800, 900].map((w) => new FontFace('Montserrat', `url(${staticFile(`fonts/montserrat-${sub}-${w}-normal.woff2`)}) format('woff2')`, {
			weight: String(w),
			unicodeRange: sub === 'cyrillic' ? 'U+0301, U+0400-045F, U+0490-0491, U+04B0-04B1, U+2116' : 'U+0000-00FF, U+2000-206F, U+20AC, U+2122',
		}).load().then((f) => document.fonts.add(f))),
	),
).then(() => continueRender(fontHandle));
